package mx.nexuspos.server.modules.inventory

import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID
import kotlin.math.abs

class InventoryService(private val db: DatabaseFactory) {

    fun getInventorySummary(): InventorySummaryDto {
        return db.inTransaction { conn ->
            val sql = """
                SELECT 
                    COUNT(*) as total_count,
                    COUNT(CASE WHEN uses_inventory = TRUE THEN 1 END) as tracked_count,
                    COUNT(CASE WHEN uses_inventory = TRUE AND COALESCE(sb.quantity_millis, 0) <= p.min_stock_millis AND COALESCE(sb.quantity_millis, 0) > 0 THEN 1 END) as low_stock_count,
                    COUNT(CASE WHEN uses_inventory = TRUE AND COALESCE(sb.quantity_millis, 0) <= 0 THEN 1 END) as out_of_stock_count,
                    COALESCE(SUM(CASE WHEN uses_inventory = TRUE AND COALESCE(sb.quantity_millis, 0) > 0 THEN (sb.quantity_millis * p.cost_cents) / 1000 ELSE 0 END), 0) as total_valuation_cents
                FROM products p
                LEFT JOIN stock_balances sb ON p.id = sb.product_id
                WHERE p.is_active = TRUE
            """.trimIndent()

            val stmt = conn.prepareStatement(sql)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                InventorySummaryDto(
                    totalProductsCount = rs.getInt("total_count"),
                    trackedProductsCount = rs.getInt("tracked_count"),
                    lowStockCount = rs.getInt("low_stock_count"),
                    outOfStockCount = rs.getInt("out_of_stock_count"),
                    totalValuationCents = rs.getLong("total_valuation_cents")
                )
            } else {
                InventorySummaryDto(0, 0, 0, 0, 0L)
            }.also {
                rs.close()
                stmt.close()
            }
        }
    }

    fun getStockMovements(
        productId: String? = null,
        type: String? = null,
        limit: Int = 100
    ): List<StockMovementDto> {
        return db.inTransaction { conn ->
            val sql = StringBuilder("""
                SELECT sm.id, sm.product_id, p.name as product_name, p.sku as product_sku,
                       sm.type, sm.quantity_millis, sm.reason, sm.reference, sm.user_id,
                       COALESCE(u.name, 'Cajero Principal') as user_name, sm.created_at
                FROM stock_movements sm
                JOIN products p ON sm.product_id = p.id
                LEFT JOIN users u ON sm.user_id = u.id
                WHERE 1=1
            """.trimIndent())

            val params = mutableListOf<Any>()
            if (!productId.isNullOrBlank()) {
                sql.append(" AND sm.product_id = ?")
                params.add(productId)
            }
            if (!type.isNullOrBlank()) {
                sql.append(" AND sm.type = ?")
                params.add(type.uppercase())
            }

            sql.append(" ORDER BY sm.created_at DESC LIMIT ?")
            params.add(limit.coerceIn(1, 500))

            val stmt = conn.prepareStatement(sql.toString())
            params.forEachIndexed { index, param ->
                stmt.setObject(index + 1, param)
            }

            val rs = stmt.executeQuery()
            val list = mutableListOf<StockMovementDto>()
            while (rs.next()) {
                list.add(
                    StockMovementDto(
                        id = rs.getString("id"),
                        productId = rs.getString("product_id"),
                        productName = rs.getString("product_name"),
                        productSku = rs.getString("product_sku"),
                        type = rs.getString("type"),
                        quantityMillis = rs.getLong("quantity_millis"),
                        reason = rs.getString("reason"),
                        reference = rs.getString("reference") ?: "",
                        userId = rs.getString("user_id"),
                        userName = rs.getString("user_name"),
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun registerMovement(req: RegisterStockMovementRequest): StockMovementDto {
        if (req.productId.isBlank()) throw ValidationException("ID de producto requerido")
        val movementType = req.type.uppercase()
        if (movementType !in setOf("ENTRY", "LOSS", "ADJUSTMENT")) {
            throw ValidationException("Tipo de movimiento inválido: $movementType (permitidos: ENTRY, LOSS, ADJUSTMENT)")
        }

        return db.inTransaction { conn ->
            // 1. Verificar producto
            val prodStmt = conn.prepareStatement("SELECT id, name, sku, uses_inventory FROM products WHERE id = ?")
            prodStmt.setString(1, req.productId)
            val prodRs = prodStmt.executeQuery()
            if (!prodRs.next()) {
                prodRs.close()
                prodStmt.close()
                throw NotFoundException("Producto con ID '${req.productId}' no encontrado")
            }
            val prodName = prodRs.getString("name")
            val prodSku = prodRs.getString("sku")
            val usesInventory = prodRs.getBoolean("uses_inventory")
            prodRs.close()
            prodStmt.close()

            if (!usesInventory) {
                throw ValidationException("El producto '$prodName' no tiene activado el control de inventario")
            }

            // 2. Obtener saldo actual con bloqueo
            val balStmt = conn.prepareStatement("SELECT quantity_millis FROM stock_balances WHERE product_id = ? FOR UPDATE")
            balStmt.setString(1, req.productId)
            val balRs = balStmt.executeQuery()
            val currentStock = if (balRs.next()) balRs.getLong("quantity_millis") else 0L
            balRs.close()
            balStmt.close()

            // 3. Calcular delta según tipo
            val deltaMillis = when (movementType) {
                "ENTRY" -> {
                    if (req.quantityMillis <= 0) throw ValidationException("La cantidad de entrada debe ser mayor a 0")
                    abs(req.quantityMillis)
                }
                "LOSS" -> {
                    if (req.quantityMillis == 0L) throw ValidationException("La cantidad de merma debe ser distinta de 0")
                    -abs(req.quantityMillis)
                }
                "ADJUSTMENT" -> {
                    if (req.quantityMillis == 0L) throw ValidationException("La diferencia de ajuste no puede ser 0")
                    req.quantityMillis
                }
                else -> throw ValidationException("Tipo de movimiento no soportado")
            }

            val newStock = currentStock + deltaMillis
            val now = System.currentTimeMillis()

            // 4. Actualizar o insertar stock_balances
            val updateBalStmt = conn.prepareStatement(
                "UPDATE stock_balances SET quantity_millis = ?, updated_at = ? WHERE product_id = ?"
            )
            updateBalStmt.setLong(1, newStock)
            updateBalStmt.setLong(2, now)
            updateBalStmt.setString(3, req.productId)
            val updatedRows = updateBalStmt.executeUpdate()
            updateBalStmt.close()

            if (updatedRows == 0) {
                val insertBalStmt = conn.prepareStatement(
                    "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)"
                )
                insertBalStmt.setString(1, req.productId)
                insertBalStmt.setLong(2, newStock)
                insertBalStmt.setLong(3, now)
                insertBalStmt.executeUpdate()
                insertBalStmt.close()
            }

            // 5. Registrar movimiento en stock_movements
            val movementId = "mov-" + UUID.randomUUID().toString().take(12)
            val movStmt = conn.prepareStatement("""
                INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent())
            movStmt.setString(1, movementId)
            movStmt.setString(2, req.productId)
            movStmt.setString(3, movementType)
            movStmt.setLong(4, deltaMillis)
            movStmt.setString(5, req.reason.trim().ifBlank { if (movementType == "ENTRY") "Entrada manual" else if (movementType == "LOSS") "Merma" else "Ajuste de inventario" })
            movStmt.setString(6, req.reference.trim())
            movStmt.setString(7, req.userId)
            movStmt.setLong(8, now)
            movStmt.executeUpdate()
            movStmt.close()

            StockMovementDto(
                id = movementId,
                productId = req.productId,
                productName = prodName,
                productSku = prodSku,
                type = movementType,
                quantityMillis = deltaMillis,
                balanceAfterMillis = newStock,
                reason = req.reason.trim().ifBlank { if (movementType == "ENTRY") "Entrada manual" else if (movementType == "LOSS") "Merma" else "Ajuste de inventario" },
                reference = req.reference.trim(),
                userId = req.userId,
                userName = "Cajero",
                createdAt = now
            )
        }
    }

    fun getReorderSuggestions(
        departmentId: String? = null,
        onlyOutOfStock: Boolean = false
    ): ReorderSuggestionsReportDto {
        return db.inTransaction { conn ->
            val sql = StringBuilder("""
                SELECT 
                    p.id, p.sku, p.name, p.department_id, d.name as department_name,
                    p.base_unit, p.cost_cents, p.min_stock_millis, p.max_stock_millis,
                    COALESCE(sb.quantity_millis, 0) as current_stock_millis,
                    COALESCE(
                        (SELECT pp.barcode FROM product_presentations pp WHERE pp.product_id = p.id ORDER BY pp.factor_millis ASC LIMIT 1),
                        'S/C'
                    ) as barcode
                FROM products p
                LEFT JOIN departments d ON p.department_id = d.id
                LEFT JOIN stock_balances sb ON p.id = sb.product_id
                WHERE p.is_active = TRUE 
                  AND p.uses_inventory = TRUE
            """.trimIndent())

            val params = mutableListOf<Any>()
            if (!departmentId.isNullOrBlank()) {
                sql.append(" AND p.department_id = ?")
                params.add(departmentId)
            }

            if (onlyOutOfStock) {
                sql.append(" AND COALESCE(sb.quantity_millis, 0) <= 0")
            } else {
                sql.append(" AND (COALESCE(sb.quantity_millis, 0) <= 0 OR (p.min_stock_millis > 0 AND COALESCE(sb.quantity_millis, 0) <= p.min_stock_millis))")
            }

            sql.append(" ORDER BY CASE WHEN COALESCE(sb.quantity_millis, 0) <= 0 THEN 0 ELSE 1 END, p.name ASC")

            val stmt = conn.prepareStatement(sql.toString())
            params.forEachIndexed { idx, p -> stmt.setObject(idx + 1, p) }
            val rs = stmt.executeQuery()

            val items = mutableListOf<ReorderSuggestionItemDto>()
            while (rs.next()) {
                val currentStock = rs.getLong("current_stock_millis")
                val minStock = rs.getLong("min_stock_millis")
                val maxStock = rs.getLong("max_stock_millis")
                val costCents = rs.getLong("cost_cents")

                val suggestedMillis = when {
                    maxStock > minStock && maxStock > currentStock -> maxStock - currentStock
                    minStock > 0 -> ((2 * minStock) - currentStock).coerceAtLeast(minStock)
                    else -> 1000L // 1 unidad base si no hay mínimo configurado pero está en 0
                }

                val estimatedCost = (suggestedMillis * costCents) / 1000L
                val urgency = if (currentStock <= 0L) ReorderUrgency.CRITICAL_OUT_OF_STOCK else ReorderUrgency.LOW_STOCK

                items.add(
                    ReorderSuggestionItemDto(
                        productId = rs.getString("id"),
                        sku = rs.getString("sku"),
                        barcode = rs.getString("barcode") ?: "S/C",
                        productName = rs.getString("name"),
                        departmentId = rs.getString("department_id"),
                        departmentName = rs.getString("department_name"),
                        baseUnit = rs.getString("base_unit"),
                        currentStockMillis = currentStock,
                        minStockMillis = minStock,
                        maxStockMillis = maxStock,
                        suggestedOrderMillis = suggestedMillis,
                        costCents = costCents,
                        estimatedInvestmentCents = estimatedCost,
                        urgency = urgency
                    )
                )
            }
            rs.close()
            stmt.close()

            val now = System.currentTimeMillis()
            ReorderSuggestionsReportDto(
                generatedAt = now,
                totalItemsCount = items.size,
                criticalOutOfStockCount = items.count { it.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK },
                totalEstimatedInvestmentCents = items.sumOf { it.estimatedInvestmentCents },
                items = items
            )
        }
    }
}

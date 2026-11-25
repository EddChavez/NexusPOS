package mx.nexuspos.server.modules.inventory

import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.LotWriteOffRequest
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class ProductLotsService(private val db: DatabaseFactory) {

    fun createLot(req: CreateProductLotRequest): ProductLotDto {
        if (req.productId.isBlank()) throw ValidationException("ID de producto requerido")
        if (req.quantityMillis <= 0L) throw ValidationException("La cantidad del lote debe ser mayor a 0")
        val expDate = try {
            LocalDate.parse(req.expirationDate.trim())
        } catch (e: Exception) {
            throw ValidationException("Fecha de caducidad inválida: '${req.expirationDate}'. Debe tener formato YYYY-MM-DD")
        }

        val cleanLotNum = req.lotNumber.trim().ifBlank { "S/L" }
        val now = System.currentTimeMillis()

        return db.inTransaction { conn ->
            // 1. Verificar producto y obtener datos
            val prodStmt = conn.prepareStatement(
                """
                SELECT p.id, p.sku, p.name, p.base_unit, p.cost_cents, p.is_perishable, p.shelf_life_alert_days,
                       EXISTS(SELECT 1 FROM product_kit_components k WHERE k.kit_product_id = p.id) AS is_kit,
                       COALESCE((SELECT barcode FROM product_presentations WHERE product_id = p.id ORDER BY factor_millis ASC LIMIT 1), 'S/C') as barcode
                FROM products p
                WHERE p.id = ? AND p.is_active = TRUE
                """.trimIndent()
            )
            prodStmt.setString(1, req.productId)
            val prodRs = prodStmt.executeQuery()
            if (!prodRs.next()) {
                prodRs.close()
                prodStmt.close()
                throw NotFoundException("Producto con ID '${req.productId}' no encontrado")
            }

            if (prodRs.getBoolean("is_kit")) {
                prodRs.close()
                prodStmt.close()
                throw ValidationException("Registra los lotes en los componentes del kit.")
            }
            val prodSku = prodRs.getString("sku")
            val prodName = prodRs.getString("name")
            val baseUnit = prodRs.getString("base_unit")
            val fallbackCost = prodRs.getLong("cost_cents")
            val shelfAlertDays = prodRs.getInt("shelf_life_alert_days")
            val barcode = prodRs.getString("barcode")
            prodRs.close()
            prodStmt.close()

            val finalCost = if (req.costCents > 0L) req.costCents else fallbackCost

            // 2. Buscar si ya existe el lote exacto para acumular
            val findStmt = conn.prepareStatement(
                "SELECT id, quantity_millis, initial_quantity_millis FROM product_lots WHERE product_id = ? AND lot_number = ? AND expiration_date = ?"
            )
            findStmt.setString(1, req.productId)
            findStmt.setString(2, cleanLotNum)
            findStmt.setString(3, req.expirationDate.trim())
            val findRs = findStmt.executeQuery()

            val lotId: String
            val newQty: Long
            val newInitQty: Long

            if (findRs.next()) {
                lotId = findRs.getString("id")
                val curQty = findRs.getLong("quantity_millis")
                val curInitQty = findRs.getLong("initial_quantity_millis")
                newQty = curQty + req.quantityMillis
                newInitQty = curInitQty + req.quantityMillis
                findRs.close()
                findStmt.close()

                val updateLotStmt = conn.prepareStatement(
                    """
                    UPDATE product_lots 
                    SET quantity_millis = ?, initial_quantity_millis = ?, cost_cents = ?, notes = ?, is_active = TRUE, updated_at = ?
                    WHERE id = ?
                    """.trimIndent()
                )
                updateLotStmt.setLong(1, newQty)
                updateLotStmt.setLong(2, newInitQty)
                updateLotStmt.setLong(3, finalCost)
                updateLotStmt.setString(4, req.notes.trim())
                updateLotStmt.setLong(5, now)
                updateLotStmt.setString(6, lotId)
                updateLotStmt.executeUpdate()
                updateLotStmt.close()
            } else {
                findRs.close()
                findStmt.close()

                lotId = "lot-" + UUID.randomUUID().toString().take(12)
                newQty = req.quantityMillis
                newInitQty = req.quantityMillis

                val insertLotStmt = conn.prepareStatement(
                    """
                    INSERT INTO product_lots (id, product_id, lot_number, expiration_date, quantity_millis, initial_quantity_millis, cost_cents, notes, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                    """.trimIndent()
                )
                insertLotStmt.setString(1, lotId)
                insertLotStmt.setString(2, req.productId)
                insertLotStmt.setString(3, cleanLotNum)
                insertLotStmt.setString(4, req.expirationDate.trim())
                insertLotStmt.setLong(5, newQty)
                insertLotStmt.setLong(6, newInitQty)
                insertLotStmt.setLong(7, finalCost)
                insertLotStmt.setString(8, req.notes.trim())
                insertLotStmt.setLong(9, now)
                insertLotStmt.setLong(10, now)
                insertLotStmt.executeUpdate()
                insertLotStmt.close()
            }

            // 3. Reingreso al stock general si addToGeneralStock = true
            if (req.addToGeneralStock) {
                // Actualizar stock_balances
                val curBalStmt = conn.prepareStatement("SELECT quantity_millis FROM stock_balances WHERE product_id = ? FOR UPDATE")
                curBalStmt.setString(1, req.productId)
                val curBalRs = curBalStmt.executeQuery()
                val curStock = if (curBalRs.next()) curBalRs.getLong("quantity_millis") else 0L
                curBalRs.close()
                curBalStmt.close()

                val updatedStock = curStock + req.quantityMillis

                val updateBalStmt = conn.prepareStatement("UPDATE stock_balances SET quantity_millis = ?, updated_at = ? WHERE product_id = ?")
                updateBalStmt.setLong(1, updatedStock)
                updateBalStmt.setLong(2, now)
                updateBalStmt.setString(3, req.productId)
                val affected = updateBalStmt.executeUpdate()
                updateBalStmt.close()

                if (affected == 0) {
                    val insBalStmt = conn.prepareStatement("INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)")
                    insBalStmt.setString(1, req.productId)
                    insBalStmt.setLong(2, updatedStock)
                    insBalStmt.setLong(3, now)
                    insBalStmt.executeUpdate()
                    insBalStmt.close()
                }

                // Registrar en Kardex
                val movStmt = conn.prepareStatement(
                    """
                    INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at)
                    VALUES (?, ?, 'ENTRY', ?, ?, ?, ?, ?)
                    """.trimIndent()
                )
                movStmt.setString(1, "mov-" + UUID.randomUUID().toString().take(12))
                movStmt.setString(2, req.productId)
                movStmt.setLong(3, req.quantityMillis)
                movStmt.setString(4, "Ingreso de Lote $cleanLotNum")
                movStmt.setString(5, "LOTE:$cleanLotNum VENCE:${req.expirationDate.trim()}")
                movStmt.setString(6, req.userId)
                movStmt.setLong(7, now)
                movStmt.executeUpdate()
                movStmt.close()
            }

            // Marcar producto como perecedero si aún no lo estaba
            val markPerishableStmt = conn.prepareStatement("UPDATE products SET is_perishable = TRUE WHERE id = ? AND is_perishable = FALSE")
            markPerishableStmt.setString(1, req.productId)
            markPerishableStmt.executeUpdate()
            markPerishableStmt.close()

            val today = LocalDate.now()
            val days = ChronoUnit.DAYS.between(today, expDate).toInt()
            val status = when {
                days <= 0 -> LotExpirationStatus.EXPIRED
                days <= shelfAlertDays -> LotExpirationStatus.NEAR_EXPIRATION
                else -> LotExpirationStatus.OPTIMAL
            }

            ProductLotDto(
                id = lotId,
                productId = req.productId,
                productName = prodName,
                productSku = prodSku,
                barcode = barcode,
                baseUnit = baseUnit,
                lotNumber = cleanLotNum,
                expirationDate = req.expirationDate.trim(),
                daysUntilExpiration = days,
                quantityMillis = newQty,
                initialQuantityMillis = newInitQty,
                costCents = finalCost,
                valuationCents = (newQty * finalCost) / 1000L,
                status = status,
                notes = req.notes.trim(),
                isActive = true,
                createdAt = now
            )
        }
    }

    fun getLots(
        productId: String? = null,
        statusFilter: LotExpirationStatus? = null
    ): List<ProductLotDto> {
        val today = LocalDate.now()
        return db.inTransaction { conn ->
            val sql = StringBuilder("""
                SELECT pl.id, pl.product_id, p.name as product_name, p.sku as product_sku,
                       p.base_unit, p.shelf_life_alert_days, pl.lot_number, pl.expiration_date,
                       pl.quantity_millis, pl.initial_quantity_millis, pl.cost_cents, pl.notes,
                       pl.is_active, pl.created_at,
                       COALESCE((SELECT barcode FROM product_presentations WHERE product_id = p.id ORDER BY factor_millis ASC LIMIT 1), 'S/C') as barcode
                FROM product_lots pl
                JOIN products p ON pl.product_id = p.id
                WHERE pl.is_active = TRUE
            """.trimIndent())

            val params = mutableListOf<Any>()
            if (!productId.isNullOrBlank()) {
                sql.append(" AND pl.product_id = ?")
                params.add(productId.trim())
            }

            sql.append(" ORDER BY pl.expiration_date ASC, pl.lot_number ASC")

            val stmt = conn.prepareStatement(sql.toString())
            params.forEachIndexed { index, param -> stmt.setObject(index + 1, param) }
            val rs = stmt.executeQuery()

            val list = mutableListOf<ProductLotDto>()
            while (rs.next()) {
                val expStr = rs.getString("expiration_date")
                val expDate = try { LocalDate.parse(expStr) } catch (e: Exception) { today }
                val days = ChronoUnit.DAYS.between(today, expDate).toInt()
                val alertDays = rs.getInt("shelf_life_alert_days").coerceAtLeast(1)

                val itemStatus = when {
                    days <= 0 -> LotExpirationStatus.EXPIRED
                    days <= alertDays -> LotExpirationStatus.NEAR_EXPIRATION
                    else -> LotExpirationStatus.OPTIMAL
                }

                if (statusFilter != null && itemStatus != statusFilter) {
                    continue
                }

                val qtyMillis = rs.getLong("quantity_millis")
                val costCents = rs.getLong("cost_cents")

                list.add(
                    ProductLotDto(
                        id = rs.getString("id"),
                        productId = rs.getString("product_id"),
                        productName = rs.getString("product_name"),
                        productSku = rs.getString("product_sku"),
                        barcode = rs.getString("barcode"),
                        baseUnit = rs.getString("base_unit"),
                        lotNumber = rs.getString("lot_number"),
                        expirationDate = expStr,
                        daysUntilExpiration = days,
                        quantityMillis = qtyMillis,
                        initialQuantityMillis = rs.getLong("initial_quantity_millis"),
                        costCents = costCents,
                        valuationCents = (qtyMillis * costCents) / 1000L,
                        status = itemStatus,
                        notes = rs.getString("notes") ?: "",
                        isActive = rs.getBoolean("is_active"),
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun getExpirationsReport(): ExpirationsReportDto {
        val allLots = getLots()
        val activeLots = allLots.filter { it.quantityMillis > 0L }
        val expired = activeLots.filter { it.status == LotExpirationStatus.EXPIRED }
        val nearExp = activeLots.filter { it.status == LotExpirationStatus.NEAR_EXPIRATION }

        return ExpirationsReportDto(
            generatedAt = System.currentTimeMillis(),
            totalLotsCount = activeLots.size,
            expiredLotsCount = expired.size,
            nearExpirationLotsCount = nearExp.size,
            totalExpiredValuationCents = expired.sumOf { it.valuationCents },
            totalAtRiskValuationCents = nearExp.sumOf { it.valuationCents },
            lots = activeLots
        )
    }

    fun writeOffLot(lotId: String, req: LotWriteOffRequest): ProductLotDto {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        return db.inTransaction { conn ->
            val lotStmt = conn.prepareStatement(
                """
                SELECT pl.id, pl.product_id, pl.lot_number, pl.expiration_date, pl.quantity_millis,
                       pl.initial_quantity_millis, pl.cost_cents, pl.notes, pl.created_at,
                       p.name as product_name, p.sku as product_sku, p.base_unit, p.shelf_life_alert_days,
                       COALESCE((SELECT barcode FROM product_presentations WHERE product_id = p.id ORDER BY factor_millis ASC LIMIT 1), 'S/C') as barcode
                FROM product_lots pl
                JOIN products p ON pl.product_id = p.id
                WHERE pl.id = ? AND pl.is_active = TRUE
                FOR UPDATE
                """.trimIndent()
            )
            lotStmt.setString(1, lotId)
            val rs = lotStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                lotStmt.close()
                throw NotFoundException("Lote con ID '$lotId' no encontrado o inactivo")
            }

            val curLotQty = rs.getLong("quantity_millis")
            val writeOffQty = req.quantityMillis?.coerceAtMost(curLotQty) ?: curLotQty
            if (writeOffQty <= 0L) {
                rs.close()
                lotStmt.close()
                throw ValidationException("La cantidad a dar de baja debe ser mayor a 0")
            }

            val prodId = rs.getString("product_id")
            val lotNum = rs.getString("lot_number")
            val expStr = rs.getString("expiration_date")
            val costCents = rs.getLong("cost_cents")
            val prodName = rs.getString("product_name")
            val prodSku = rs.getString("product_sku")
            val baseUnit = rs.getString("base_unit")
            val barcode = rs.getString("barcode")
            val alertDays = rs.getInt("shelf_life_alert_days")
            val initQty = rs.getLong("initial_quantity_millis")
            val notes = rs.getString("notes") ?: ""
            val createdAt = rs.getLong("created_at")

            val newLotQty = curLotQty - writeOffQty
            val isStillActive = (newLotQty > 0L)

            rs.close()
            lotStmt.close()

            // 1. Actualizar product_lots
            val updateLotStmt = conn.prepareStatement(
                "UPDATE product_lots SET quantity_millis = ?, is_active = ?, updated_at = ? WHERE id = ?"
            )
            updateLotStmt.setLong(1, newLotQty)
            updateLotStmt.setBoolean(2, isStillActive)
            updateLotStmt.setLong(3, now)
            updateLotStmt.setString(4, lotId)
            updateLotStmt.executeUpdate()
            updateLotStmt.close()

            // 2. Descontar de stock_balances
            val balStmt = conn.prepareStatement("SELECT quantity_millis FROM stock_balances WHERE product_id = ? FOR UPDATE")
            balStmt.setString(1, prodId)
            val balRs = balStmt.executeQuery()
            val curStock = if (balRs.next()) balRs.getLong("quantity_millis") else 0L
            balRs.close()
            balStmt.close()

            val newStock = (curStock - writeOffQty).coerceAtLeast(0L)
            val updateBalStmt = conn.prepareStatement("UPDATE stock_balances SET quantity_millis = ?, updated_at = ? WHERE product_id = ?")
            updateBalStmt.setLong(1, newStock)
            updateBalStmt.setLong(2, now)
            updateBalStmt.setString(3, prodId)
            updateBalStmt.executeUpdate()
            updateBalStmt.close()

            // 3. Registrar movimiento de Kardex tipo LOSS
            val finalReason = req.reason.trim().ifBlank { "Merma por Caducidad Lote $lotNum" }
            val movStmt = conn.prepareStatement(
                """
                INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at)
                VALUES (?, ?, 'LOSS', ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            movStmt.setString(1, "mov-" + UUID.randomUUID().toString().take(12))
            movStmt.setString(2, prodId)
            movStmt.setLong(3, -writeOffQty)
            movStmt.setString(4, finalReason)
            movStmt.setString(5, "LOTE:$lotNum VENCE:$expStr")
            movStmt.setString(6, req.userId)
            movStmt.setLong(7, now)
            movStmt.executeUpdate()
            movStmt.close()

            val expDate = try { LocalDate.parse(expStr) } catch (e: Exception) { today }
            val days = ChronoUnit.DAYS.between(today, expDate).toInt()
            val status = when {
                days <= 0 -> LotExpirationStatus.EXPIRED
                days <= alertDays -> LotExpirationStatus.NEAR_EXPIRATION
                else -> LotExpirationStatus.OPTIMAL
            }

            ProductLotDto(
                id = lotId,
                productId = prodId,
                productName = prodName,
                productSku = prodSku,
                barcode = barcode,
                baseUnit = baseUnit,
                lotNumber = lotNum,
                expirationDate = expStr,
                daysUntilExpiration = days,
                quantityMillis = newLotQty,
                initialQuantityMillis = initQty,
                costCents = costCents,
                valuationCents = (newLotQty * costCents) / 1000L,
                status = status,
                notes = notes,
                isActive = isStillActive,
                createdAt = createdAt
            )
        }
    }

    fun getExpiringLotForProduct(productId: String): ProductLotDto? {
        val lots = getLots(productId = productId).filter { it.quantityMillis > 0L }
        return lots.minByOrNull { it.expirationDate }
    }
}

package mx.nexuspos.server.modules.catalog

import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.PresentationPriceTier
import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CatalogImportRowDto
import mx.nexuspos.contracts.catalog.CatalogImportRowResult
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID

class CatalogService(private val db: DatabaseFactory) {

    init {
        seedInitialCatalog()
    }

    private fun seedInitialCatalog() {
        db.inTransaction { conn ->
            val countStmt = conn.prepareStatement("SELECT COUNT(*) FROM products")
            val rs = countStmt.executeQuery()
            rs.next()
            val count = rs.getInt(1)
            rs.close()
            countStmt.close()

            if (count == 0) {
                val now = System.currentTimeMillis()

                // 1. Departments
                val depts = listOf(
                    DepartmentDto("dept-despensa", "Despensa y Abarrotes"),
                    DepartmentDto("dept-bebidas", "Bebidas y Refrescos"),
                    DepartmentDto("dept-lacteos", "Lácteos y Huevos"),
                    DepartmentDto("dept-botanas", "Botanas y Dulces"),
                    DepartmentDto("dept-granel", "Frutas y Granel"),
                    DepartmentDto("dept-limpieza", "Limpieza y Hogar")
                )
                val deptStmt = conn.prepareStatement("INSERT INTO departments (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")
                for (d in depts) {
                    deptStmt.setString(1, d.id)
                    deptStmt.setString(2, d.name)
                    deptStmt.executeUpdate()
                }
                deptStmt.close()

                // 2. Products and Presentations
                data class SeedItem(
                    val prodId: String,
                    val sku: String,
                    val name: String,
                    val deptId: String,
                    val unit: String,
                    val costCents: Long,
                    val taxRate: Double,
                    val initialStockMillis: Long,
                    val presentations: List<Pair<String, Pair<Long, Long>>> // barcode -> (factorMillis, priceCents)
                )

                val items = listOf(
                    SeedItem("p-ab-1", "AB-001", "Arroz Morelos 1kg", "dept-despensa", "PZA", 2100L, 0.0, 45000L,
                        listOf("7501000111204" to (1000L to 2850L))),
                    SeedItem("p-ab-2", "AB-002", "Frijol Negro Verde Valle 1kg", "dept-despensa", "PZA", 2900L, 0.0, 40000L,
                        listOf("7501000111211" to (1000L to 3800L))),
                    SeedItem("p-ab-3", "AB-003", "Aceite 123 1L", "dept-despensa", "PZA", 3350L, 0.0, 35000L,
                        listOf("7501011115501" to (1000L to 4200L))),
                    SeedItem("p-ab-4", "AB-004", "Atún Dolores en Agua 140g", "dept-despensa", "PZA", 1600L, 0.0, 60000L,
                        listOf("7501020512345" to (1000L to 2250L))),
                    SeedItem("p-ab-5", "AB-005", "Azúcar Zulka 1kg", "dept-despensa", "PZA", 2400L, 0.0, 50000L,
                        listOf("7501030411112" to (1000L to 3200L))),
                    SeedItem("p-beb-1", "BEB-001", "Coca-Cola Original 600ml", "dept-bebidas", "PZA", 1350L, 0.16, 72000L,
                        listOf("7501055300078" to (1000L to 1850L), "7501055300078-24" to (24000L to 41000L))),
                    SeedItem("p-lac-1", "LAC-001", "Leche Lala Entera 1L", "dept-lacteos", "PZA", 2150L, 0.0, 36000L,
                        listOf("7501020555555" to (1000L to 2700L), "7501020555555-12" to (12000L to 31000L))),
                    SeedItem("p-lac-2", "LAC-002", "Huevo Blanco San Juan 1kg", "dept-lacteos", "PZA", 3900L, 0.0, 30000L,
                        listOf("7501000888999" to (1000L to 4800L))),
                    SeedItem("p-lim-1", "LIM-001", "Cloralex Rendidor 950ml", "dept-limpieza", "PZA", 1500L, 0.16, 25000L,
                        listOf("7501025401234" to (1000L to 2100L))),
                    SeedItem("p-lim-2", "LIM-002", "Jabón Zote Rosa 400g", "dept-limpieza", "PZA", 1750L, 0.16, 30000L,
                        listOf("7501025409999" to (1000L to 2400L))),
                    // Bulk weight products (Granel KG)
                    SeedItem("p-gr-1", "GR-001", "Jitomate Saladet (Granel)", "dept-granel", "KG", 2400L, 0.0, 25000L,
                        listOf("2000000000011" to (1000L to 3400L))),
                    SeedItem("p-gr-2", "GR-002", "Cebolla Blanca (Granel)", "dept-granel", "KG", 1950L, 0.0, 30000L,
                        listOf("2000000000028" to (1000L to 2900L))),
                    SeedItem("p-gr-3", "GR-003", "Limón con Semilla (Granel)", "dept-granel", "KG", 3200L, 0.0, 20000L,
                        listOf("2000000000035" to (1000L to 4500L))),
                    SeedItem("p-gr-4", "GR-004", "Plátano Tabasco (Granel)", "dept-granel", "KG", 1500L, 0.0, 35000L,
                        listOf("2000000000042" to (1000L to 2200L)))
                )

                val prodStmt = conn.prepareStatement(
                    "INSERT INTO products (id, sku, name, department_id, base_unit, cost_cents, tax_rate, uses_inventory, min_stock_millis, max_stock_millis, is_active, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )
                val presStmt = conn.prepareStatement(
                    "INSERT INTO product_presentations (id, product_id, barcode, name, factor_millis, price_cents, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                val stockStmt = conn.prepareStatement(
                    "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)"
                )

                for (it in items) {
                    prodStmt.setString(1, it.prodId)
                    prodStmt.setString(2, it.sku)
                    prodStmt.setString(3, it.name)
                    prodStmt.setString(4, it.deptId)
                    prodStmt.setString(5, it.unit)
                    prodStmt.setLong(6, it.costCents)
                    prodStmt.setDouble(7, it.taxRate)
                    prodStmt.setBoolean(8, true)
                    prodStmt.setLong(9, 5000L)
                    prodStmt.setLong(10, 100000L)
                    prodStmt.setBoolean(11, true)
                    prodStmt.setLong(12, now)
                    prodStmt.executeUpdate()

                    var presIdx = 1
                    for ((barcode, factorAndPrice) in it.presentations) {
                        val presId = "pres-${it.prodId}-$presIdx"
                        val (factor, price) = factorAndPrice
                        presStmt.setString(1, presId)
                        presStmt.setString(2, it.prodId)
                        presStmt.setString(3, barcode)
                        presStmt.setString(4, it.name + if (factor > 1000L) " (Paquete x${factor / 1000})" else "")
                        presStmt.setLong(5, factor)
                        presStmt.setLong(6, price)
                        presStmt.setBoolean(7, true)
                        presStmt.executeUpdate()
                        presIdx++
                    }

                    stockStmt.setString(1, it.prodId)
                    stockStmt.setLong(2, it.initialStockMillis)
                    stockStmt.setLong(3, now)
                    stockStmt.executeUpdate()
                }

                prodStmt.close()
                presStmt.close()
                stockStmt.close()
            }
        }
    }

    fun getAllDepartments(): List<DepartmentDto> {
        return db.inTransaction { conn ->
            val list = mutableListOf<DepartmentDto>()
            val stmt = conn.prepareStatement("SELECT id, name FROM departments ORDER BY name")
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(DepartmentDto(rs.getString("id"), rs.getString("name")))
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun createDepartment(request: CreateDepartmentRequest): DepartmentDto {
        val trimmedName = request.name.trim()
        if (trimmedName.isBlank()) throw ValidationException("El nombre del departamento es obligatorio")

        return db.inTransaction { conn ->
            val checkStmt = conn.prepareStatement("SELECT id FROM departments WHERE LOWER(name) = LOWER(?)")
            checkStmt.setString(1, trimmedName)
            val rs = checkStmt.executeQuery()
            val exists = rs.next()
            rs.close()
            checkStmt.close()

            if (exists) {
                throw ValidationException("Ya existe un departamento con el nombre '$trimmedName'")
            }

            val id = "dept-" + UUID.randomUUID().toString().take(8)
            val insertStmt = conn.prepareStatement("INSERT INTO departments (id, name) VALUES (?, ?)")
            insertStmt.setString(1, id)
            insertStmt.setString(2, trimmedName)
            insertStmt.executeUpdate()
            insertStmt.close()

            DepartmentDto(id, trimmedName)
        }
    }

    fun updateDepartment(id: String, request: UpdateDepartmentRequest): DepartmentDto {
        val trimmedName = request.name.trim()
        if (trimmedName.isBlank()) throw ValidationException("El nombre del departamento es obligatorio")

        return db.inTransaction { conn ->
            val checkExistsStmt = conn.prepareStatement("SELECT id FROM departments WHERE id = ?")
            checkExistsStmt.setString(1, id)
            val existsRs = checkExistsStmt.executeQuery()
            val exists = existsRs.next()
            existsRs.close()
            checkExistsStmt.close()

            if (!exists) {
                throw NotFoundException("Departamento no encontrado")
            }

            val checkDuplicateStmt = conn.prepareStatement("SELECT id FROM departments WHERE LOWER(name) = LOWER(?) AND id != ?")
            checkDuplicateStmt.setString(1, trimmedName)
            checkDuplicateStmt.setString(2, id)
            val dupRs = checkDuplicateStmt.executeQuery()
            val duplicate = dupRs.next()
            dupRs.close()
            checkDuplicateStmt.close()

            if (duplicate) {
                throw ValidationException("Ya existe otro departamento con el nombre '$trimmedName'")
            }

            val updateStmt = conn.prepareStatement("UPDATE departments SET name = ? WHERE id = ?")
            updateStmt.setString(1, trimmedName)
            updateStmt.setString(2, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            DepartmentDto(id, trimmedName)
        }
    }

    fun deleteDepartment(id: String) {
        db.inTransaction { conn ->
            val checkExistsStmt = conn.prepareStatement("SELECT id FROM departments WHERE id = ?")
            checkExistsStmt.setString(1, id)
            val existsRs = checkExistsStmt.executeQuery()
            val exists = existsRs.next()
            existsRs.close()
            checkExistsStmt.close()

            if (!exists) {
                throw NotFoundException("Departamento no encontrado")
            }

            // Desvincular productos asociados (pasan a categoría General / null)
            val unassignStmt = conn.prepareStatement("UPDATE products SET department_id = NULL WHERE department_id = ?")
            unassignStmt.setString(1, id)
            unassignStmt.executeUpdate()
            unassignStmt.close()

            // Eliminar departamento
            val deleteStmt = conn.prepareStatement("DELETE FROM departments WHERE id = ?")
            deleteStmt.setString(1, id)
            deleteStmt.executeUpdate()
            deleteStmt.close()
        }
    }

    fun searchProducts(
        barcode: String? = null,
        query: String? = null,
        departmentId: String? = null,
        includeInactive: Boolean = false
    ): List<ProductDto> {
        return db.inTransaction { conn ->
            val sql = StringBuilder(
                """
                SELECT p.id, p.sku, p.name, p.department_id, d.name as dept_name,
                       p.base_unit, p.cost_cents, p.tax_rate, p.uses_inventory,
                       COALESCE(sb.quantity_millis, 0) as stock_millis,
                       p.min_stock_millis, p.max_stock_millis, p.is_perishable, p.shelf_life_alert_days, p.is_active, p.updated_at,
                       EXISTS(SELECT 1 FROM product_kit_components k WHERE k.kit_product_id = p.id) AS is_kit
                FROM products p
                LEFT JOIN departments d ON p.department_id = d.id
                LEFT JOIN stock_balances sb ON p.id = sb.product_id
                WHERE 1=1
                """.trimIndent()
            )

            if (!includeInactive) {
                sql.append(" AND p.is_active = TRUE")
            }

            val params = mutableListOf<String>()
            if (!departmentId.isNullOrBlank()) {
                sql.append(" AND p.department_id = ?")
                params.add(departmentId.trim())
            }

            if (!barcode.isNullOrBlank()) {
                sql.append(" AND p.id IN (SELECT product_id FROM product_presentations WHERE barcode = ?" +
                        (if (!includeInactive) " AND is_active = TRUE" else "") + ")")
                params.add(barcode.trim())
            } else if (!query.isNullOrBlank()) {
                sql.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ? OR p.id IN (SELECT product_id FROM product_presentations WHERE barcode LIKE ? OR LOWER(name) LIKE ?))")
                val q = "%${query.trim().lowercase()}%"
                params.add(q)
                params.add(q)
                params.add(q)
                params.add(q)
            }
            sql.append(" ORDER BY p.name LIMIT 100")

            val stmt = conn.prepareStatement(sql.toString())
            params.forEachIndexed { index, param -> stmt.setString(index + 1, param) }
            val rs = stmt.executeQuery()

            val products = mutableListOf<ProductDto>()
            val kitIds = mutableSetOf<String>()
            while (rs.next()) {
                val prodId = rs.getString("id")
                if (rs.getBoolean("is_kit")) kitIds.add(prodId)
                products.add(
                    ProductDto(
                        id = prodId,
                        sku = rs.getString("sku"),
                        name = rs.getString("name"),
                        departmentId = rs.getString("department_id"),
                        departmentName = rs.getString("dept_name"),
                        baseUnit = rs.getString("base_unit"),
                        costCents = rs.getLong("cost_cents"),
                        taxRate = rs.getDouble("tax_rate"),
                        usesInventory = rs.getBoolean("uses_inventory"),
                        currentStockMillis = rs.getLong("stock_millis"),
                        minStockMillis = rs.getLong("min_stock_millis"),
                        maxStockMillis = rs.getLong("max_stock_millis"),
                        isPerishable = rs.getBoolean("is_perishable"),
                        shelfLifeAlertDays = rs.getInt("shelf_life_alert_days"),
                        isActive = rs.getBoolean("is_active"),
                        updatedAt = rs.getLong("updated_at"),
                        presentations = emptyList() // populated below
                    )
                )
            }
            rs.close()
            stmt.close()

            // Fetch presentations for found products
            if (products.isNotEmpty()) {
                val presSql = if (includeInactive) {
                    "SELECT id, product_id, barcode, name, factor_millis, price_cents, price_tiers_json FROM product_presentations WHERE product_id = ? ORDER BY factor_millis ASC"
                } else {
                    "SELECT id, product_id, barcode, name, factor_millis, price_cents, price_tiers_json FROM product_presentations WHERE product_id = ? AND is_active = TRUE ORDER BY factor_millis ASC"
                }
                val presStmt = conn.prepareStatement(presSql)
                val fullProducts = products.map { prod ->
                    presStmt.setString(1, prod.id)
                    val presRs = presStmt.executeQuery()
                    val presList = mutableListOf<ProductPresentationDto>()
                    while (presRs.next()) {
                        presList.add(
                            ProductPresentationDto(
                                id = presRs.getString("id"),
                                productId = presRs.getString("product_id"),
                                barcode = presRs.getString("barcode"),
                                name = presRs.getString("name"),
                                factorMillis = presRs.getLong("factor_millis"),
                                priceCents = presRs.getLong("price_cents"),
                                priceTiers = Json.decodeFromString(presRs.getString("price_tiers_json"))
                            )
                        )
                    }
                    presRs.close()
                    prod.copy(
                        presentations = presList,
                        kitComponents = if (prod.id in kitIds) readKitComponents(conn, prod.id) else emptyList()
                    )
                }
                presStmt.close()
                fullProducts
            } else {
                emptyList()
            }
        }
    }

    fun getProductById(id: String): ProductDto {
        return db.inTransaction { conn ->
            fetchProductById(conn, id)
        }
    }

    fun createProduct(request: CreateProductRequest): ProductDto {
        return db.inTransaction { conn ->
            if (request.name.isBlank()) {
                throw ValidationException("El nombre del producto es obligatorio")
            }
            if (request.presentations.isEmpty()) {
                throw ValidationException("El producto debe tener al menos una presentación con código de barras y precio")
            }

            // Generate or validate SKU
            val finalSku = if (request.sku.isBlank()) {
                val counterStmt = conn.prepareStatement(
                    "INSERT INTO counters (name, current_val) VALUES ('product_sku', 1) " +
                            "ON CONFLICT (name) DO UPDATE SET current_val = counters.current_val + 1 RETURNING current_val"
                )
                val rs = counterStmt.executeQuery()
                rs.next()
                val nextVal = rs.getLong(1)
                rs.close()
                counterStmt.close()
                "PRD-" + nextVal.toString().padStart(4, '0')
            } else {
                request.sku.trim().uppercase()
            }

            // Check SKU uniqueness
            val checkSku = conn.prepareStatement("SELECT id FROM products WHERE LOWER(sku) = ?")
            checkSku.setString(1, finalSku.lowercase())
            val skuRs = checkSku.executeQuery()
            if (skuRs.next()) {
                skuRs.close()
                checkSku.close()
                throw ValidationException("Ya existe un producto con el SKU '$finalSku'")
            }
            skuRs.close()
            checkSku.close()

            // Check barcode uniqueness for all presentations
            val checkBarcode = conn.prepareStatement(
                "SELECT p.name, pp.barcode FROM product_presentations pp JOIN products p ON pp.product_id = p.id WHERE pp.barcode = ? AND pp.is_active = TRUE"
            )
            for (p in request.presentations) {
                if (p.barcode.isBlank()) {
                    throw ValidationException("El código de barras no puede estar vacío")
                }
                checkBarcode.setString(1, p.barcode.trim())
                val bRs = checkBarcode.executeQuery()
                if (bRs.next()) {
                    val existingName = bRs.getString("name")
                    bRs.close()
                    checkBarcode.close()
                    throw ValidationException("El código de barras '${p.barcode}' ya está registrado en '$existingName'")
                }
                bRs.close()
            }
            checkBarcode.close()

            val prodId = "prod-" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()

            val insertStmt = conn.prepareStatement(
                """
                INSERT INTO products (id, sku, name, department_id, base_unit, cost_cents, tax_rate, uses_inventory, min_stock_millis, max_stock_millis, is_perishable, shelf_life_alert_days, is_active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                """.trimIndent()
            )
            insertStmt.setString(1, prodId)
            insertStmt.setString(2, finalSku)
            insertStmt.setString(3, request.name.trim())
            insertStmt.setString(4, request.departmentId)
            insertStmt.setString(5, request.baseUnit.uppercase())
            insertStmt.setLong(6, request.costCents.coerceAtLeast(0L))
            insertStmt.setDouble(7, request.taxRate.coerceAtLeast(0.0))
            insertStmt.setBoolean(8, request.usesInventory)
            insertStmt.setLong(9, request.minStockMillis.coerceAtLeast(0L))
            insertStmt.setLong(10, request.maxStockMillis.coerceAtLeast(0L))
            insertStmt.setBoolean(11, request.isPerishable)
            insertStmt.setInt(12, request.shelfLifeAlertDays.coerceAtLeast(1))
            insertStmt.setLong(13, now)
            insertStmt.executeUpdate()
            insertStmt.close()

            // Presentations
            val presStmt = conn.prepareStatement(
                "INSERT INTO product_presentations (id, product_id, barcode, name, factor_millis, price_cents, is_active) VALUES (?, ?, ?, ?, ?, ?, TRUE)"
            )
            var pIdx = 1
            for (pres in request.presentations) {
                val presId = pres.id ?: "pres-$prodId-$pIdx"
                presStmt.setString(1, presId)
                presStmt.setString(2, prodId)
                presStmt.setString(3, pres.barcode.trim())
                presStmt.setString(4, pres.name.trim().ifBlank { request.name.trim() })
                presStmt.setLong(5, if (pres.factorMillis <= 0) 1000L else pres.factorMillis)
                presStmt.setLong(6, pres.priceCents.coerceAtLeast(0L))
                presStmt.executeUpdate()
                pIdx++
            }
            presStmt.close()

            // Stock balance
            val stockStmt = conn.prepareStatement(
                "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)"
            )
            stockStmt.setString(1, prodId)
            stockStmt.setLong(2, request.initialStockMillis.coerceAtLeast(0L))
            stockStmt.setLong(3, now)
            stockStmt.executeUpdate()
            stockStmt.close()

            if (request.initialStockMillis > 0L) {
                val movStmt = conn.prepareStatement(
                    "INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                )
                movStmt.setString(1, "mov-" + UUID.randomUUID().toString().take(8))
                movStmt.setString(2, prodId)
                movStmt.setString(3, "ENTRY")
                movStmt.setLong(4, request.initialStockMillis)
                movStmt.setString(5, "Inventario inicial al dar de alta")
                movStmt.setString(6, finalSku)
                movStmt.setString(7, "usr-admin")
                movStmt.setLong(8, now)
                movStmt.executeUpdate()
                movStmt.close()
            }

            fetchProductById(conn, prodId)
        }
    }

    fun updateProduct(id: String, request: UpdateProductRequest): ProductDto {
        return db.inTransaction { conn ->
            lockProduct(conn, id)
            val existing = fetchProductById(conn, id)

            if (request.name.isBlank()) {
                throw ValidationException("El nombre del producto es obligatorio")
            }
            if (request.presentations.isEmpty()) {
                throw ValidationException("El producto debe tener al menos una presentación")
            }

            if (existing.kitComponents.isNotEmpty()) {
                validateKitParent(request.baseUnit, request.usesInventory, request.presentations.map { it.factorMillis })
            }

            val finalSku = request.sku.trim().uppercase().ifBlank { existing.sku }

            // Check SKU uniqueness if changed
            if (!finalSku.equals(existing.sku, ignoreCase = true)) {
                val checkSku = conn.prepareStatement("SELECT id FROM products WHERE LOWER(sku) = ? AND id != ?")
                checkSku.setString(1, finalSku.lowercase())
                checkSku.setString(2, id)
                val skuRs = checkSku.executeQuery()
                if (skuRs.next()) {
                    skuRs.close()
                    checkSku.close()
                    throw ValidationException("Ya existe otro producto con el SKU '$finalSku'")
                }
                skuRs.close()
                checkSku.close()
            }

            val barcodes = request.presentations.map { it.barcode.trim() }
            if (barcodes.any { it.isBlank() } || barcodes.distinct().size != barcodes.size) {
                throw ValidationException("Cada presentación requiere un código de barras distinto.")
            }
            val updates = request.presentations.map { presentation ->
                val previous = if (presentation.id == null) {
                    existing.presentations.firstOrNull { it.barcode == presentation.barcode.trim() }
                } else existing.presentations.firstOrNull { it.id == presentation.id }
                presentation to previous
            }
            val assignedIds = request.presentations.mapNotNull { it.id }
            if (assignedIds.distinct().size != assignedIds.size) {
                throw ValidationException("Cada presentación se puede modificar una sola vez.")
            }
            conn.prepareStatement("SELECT 1 FROM product_presentations WHERE id = ? AND product_id <> ?").use { owners ->
                for (presentationId in assignedIds) {
                    owners.setString(1, presentationId)
                    owners.setString(2, id)
                    owners.executeQuery().use { if (it.next()) throw ValidationException("La presentación no pertenece al producto.") }
                }
            }
            val retainedIds = updates.mapNotNull { it.second?.id }
            if (retainedIds.distinct().size != retainedIds.size) {
                throw ValidationException("Cada presentación se puede modificar una sola vez.")
            }
            val removed = existing.presentations.filter { it.id !in retainedIds }
            conn.prepareStatement("SELECT 1 FROM sale_lines WHERE presentation_id = ?").use { sold ->
                fun wasSold(presentationId: String): Boolean {
                    sold.setString(1, presentationId)
                    return sold.executeQuery().use { it.next() }
                }
                if (removed.any { wasSold(it.id) }) {
                    throw ValidationException("Conserva las presentaciones que ya tienen ventas registradas.")
                }
                for ((update, previous) in updates) {
                    if (previous != null && update.factorMillis != previous.factorMillis && wasSold(previous.id)) {
                        throw ValidationException("La cantidad de una presentación vendida se conserva para las devoluciones.")
                    }
                }
            }

            conn.prepareStatement(
                "SELECT id, product_id FROM product_presentations WHERE barcode = ?"
            ).use { barcodeQuery ->
                for ((update, previous) in updates) {
                    barcodeQuery.setString(1, update.barcode.trim())
                    barcodeQuery.executeQuery().use { rows ->
                        if (rows.next() && rows.getString("id") != previous?.id &&
                            !(rows.getString("product_id") == id && removed.any { it.id == rows.getString("id") })
                        ) throw ValidationException("El código de barras '${update.barcode.trim()}' ya está registrado.")
                    }
                }
            }

            val now = System.currentTimeMillis()

            val updateStmt = conn.prepareStatement(
                """
                UPDATE products
                SET sku = ?, name = ?, department_id = ?, base_unit = ?, cost_cents = ?,
                    tax_rate = ?, uses_inventory = ?, min_stock_millis = ?, max_stock_millis = ?,
                    is_perishable = ?, shelf_life_alert_days = ?, is_active = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent()
            )
            updateStmt.setString(1, finalSku)
            updateStmt.setString(2, request.name.trim())
            updateStmt.setString(3, request.departmentId)
            updateStmt.setString(4, request.baseUnit.uppercase())
            updateStmt.setLong(5, request.costCents.coerceAtLeast(0L))
            updateStmt.setDouble(6, request.taxRate.coerceAtLeast(0.0))
            updateStmt.setBoolean(7, request.usesInventory)
            updateStmt.setLong(8, request.minStockMillis.coerceAtLeast(0L))
            updateStmt.setLong(9, request.maxStockMillis.coerceAtLeast(0L))
            updateStmt.setBoolean(10, request.isPerishable)
            updateStmt.setInt(11, request.shelfLifeAlertDays.coerceAtLeast(1))
            updateStmt.setBoolean(12, request.isActive)
            updateStmt.setLong(13, now)
            updateStmt.setString(14, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            conn.prepareStatement("DELETE FROM product_presentations WHERE id = ?").use { delete ->
                for (presentation in removed) {
                    delete.setString(1, presentation.id)
                    delete.executeUpdate()
                }
            }
            conn.prepareStatement("""
                UPDATE product_presentations SET barcode = ?, name = ?, factor_millis = ?, price_cents = ?, is_active = ?
                WHERE id = ?
            """.trimIndent()).use { update ->
                conn.prepareStatement("""
                    INSERT INTO product_presentations (barcode, name, factor_millis, price_cents, is_active, id, product_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()).use { insert ->
                    for ((presentation, previous) in updates) {
                        val statement = if (previous == null) insert else update
                        statement.setString(1, presentation.barcode.trim())
                        statement.setString(2, presentation.name.trim().ifBlank { request.name.trim() })
                        statement.setLong(3, if (presentation.factorMillis <= 0) 1000L else presentation.factorMillis)
                        statement.setLong(4, presentation.priceCents.coerceAtLeast(0L))
                        statement.setBoolean(5, request.isActive)
                        statement.setString(6, previous?.id ?: presentation.id ?: "pres-${UUID.randomUUID()}")
                        if (previous == null) statement.setString(7, id)
                        statement.executeUpdate()
                    }
                }
            }

            fetchProductById(conn, id)
        }
    }

    fun updatePresentationPrices(
        productId: String,
        presentationId: String,
        tiers: List<PresentationPriceTier>
    ): ProductDto = db.inTransaction { conn ->
        lockProduct(conn, productId)
        val product = fetchProductById(conn, productId)
        val presentation = product.presentations.firstOrNull { it.id == presentationId }
            ?: throw NotFoundException("No se encontró la presentación del producto.")
        val sorted = tiers.sortedBy { it.minimumQuantityMillis }
        val isPiece = ProductUnit.fromCode(product.baseUnit) == ProductUnit.PIECE
        var previousQuantity = 0L
        var previousPrice = presentation.priceCents
        for (tier in sorted) {
            if (tier.minimumQuantityMillis <= previousQuantity ||
                (isPiece && (tier.minimumQuantityMillis < 2000 || tier.minimumQuantityMillis % 1000 != 0L))
            ) throw ValidationException("Usa cantidades mínimas positivas, distintas y enteras desde 2 para piezas.")
            if (tier.unitPriceCents <= 0 || tier.unitPriceCents >= previousPrice) {
                throw ValidationException("Cada tarifa debe ser positiva y menor al precio del umbral anterior.")
            }
            previousQuantity = tier.minimumQuantityMillis
            previousPrice = tier.unitPriceCents
        }
        conn.prepareStatement("UPDATE product_presentations SET price_tiers_json = ? WHERE id = ? AND product_id = ?").use {
            it.setString(1, Json.encodeToString(sorted))
            it.setString(2, presentationId)
            it.setString(3, productId)
            it.executeUpdate()
        }
        conn.prepareStatement("UPDATE products SET updated_at = ? WHERE id = ?").use {
            it.setLong(1, System.currentTimeMillis())
            it.setString(2, productId)
            it.executeUpdate()
        }
        fetchProductById(conn, productId)
    }

    fun updateProductKit(productId: String, components: List<KitComponent>): ProductDto = db.inTransaction { conn ->
        val componentIds = components.map { it.productId }
        if (productId in componentIds || componentIds.distinct().size != components.size ||
            components.any { it.quantityMillis <= 0L }
        ) throw ValidationException("Elige componentes distintos del kit con cantidades positivas.")

        (componentIds + productId).sorted().forEach { lockProduct(conn, it) }
        val product = fetchProductById(conn, productId)
        if (components.isNotEmpty()) {
            validateKitParent(product.baseUnit, product.usesInventory, product.presentations.map { it.factorMillis })
            conn.prepareStatement("SELECT 1 FROM product_kit_components WHERE component_product_id = ?").use {
                it.setString(1, productId)
                it.executeQuery().use { result ->
                    if (result.next()) throw ValidationException("Este producto ya es componente de otro kit.")
                }
            }
            for (entry in components) {
                val component = fetchProductById(conn, entry.productId)
                if (!component.isActive || !component.usesInventory || component.kitComponents.isNotEmpty()) {
                    throw ValidationException("'${component.name}' debe ser un producto activo con inventario.")
                }
                if (ProductUnit.fromCode(component.baseUnit) == ProductUnit.PIECE && entry.quantityMillis.rem(1000L) != 0L) {
                    throw ValidationException("La cantidad de '${component.name}' debe ser un número entero de piezas.")
                }
            }
        }

        conn.prepareStatement("DELETE FROM product_kit_components WHERE kit_product_id = ?").use {
            it.setString(1, productId)
            it.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO product_kit_components (kit_product_id, component_product_id, quantity_millis) VALUES (?, ?, ?)"
        ).use { statement ->
            for (component in components) {
                statement.setString(1, productId)
                statement.setString(2, component.productId)
                statement.setLong(3, component.quantityMillis)
                statement.addBatch()
            }
            statement.executeBatch()
        }
        conn.prepareStatement("UPDATE products SET updated_at = ? WHERE id = ?").use {
            it.setLong(1, System.currentTimeMillis())
            it.setString(2, productId)
            it.executeUpdate()
        }
        fetchProductById(conn, productId)
    }

    private fun validateKitParent(baseUnit: String, usesInventory: Boolean, factors: List<Long>) {
        if (ProductUnit.fromCode(baseUnit) != ProductUnit.PIECE || usesInventory ||
            factors.isEmpty() || factors.any { it <= 0L || it % 1000 != 0L }
        ) throw ValidationException("El kit debe venderse por pieza, sin inventario propio y en presentaciones enteras.")
    }

    private fun readKitComponents(conn: java.sql.Connection, productId: String): List<KitComponent> =
        conn.prepareStatement("""
            SELECT component_product_id, quantity_millis FROM product_kit_components
            WHERE kit_product_id = ? ORDER BY component_product_id
        """.trimIndent()).use {
            it.setString(1, productId)
            it.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(KitComponent(rows.getString(1), rows.getLong(2)))
                }
            }
        }

    private fun lockProduct(conn: java.sql.Connection, productId: String) {
        conn.prepareStatement("SELECT id FROM products WHERE id = ? FOR UPDATE").use {
            it.setString(1, productId)
            it.executeQuery().use { result ->
                if (!result.next()) throw NotFoundException("Producto no encontrado")
            }
        }
    }

    fun deactivateProduct(id: String): ProductDto {
        return db.inTransaction { conn ->
            fetchProductById(conn, id)
            val now = System.currentTimeMillis()
            val stmt = conn.prepareStatement("UPDATE products SET is_active = FALSE, updated_at = ? WHERE id = ?")
            stmt.setLong(1, now)
            stmt.setString(2, id)
            stmt.executeUpdate()
            stmt.close()

            val pStmt = conn.prepareStatement("UPDATE product_presentations SET is_active = FALSE WHERE product_id = ?")
            pStmt.setString(1, id)
            pStmt.executeUpdate()
            pStmt.close()

            fetchProductById(conn, id)
        }
    }

    fun reactivateProduct(id: String): ProductDto {
        return db.inTransaction { conn ->
            fetchProductById(conn, id)
            val now = System.currentTimeMillis()
            val stmt = conn.prepareStatement("UPDATE products SET is_active = TRUE, updated_at = ? WHERE id = ?")
            stmt.setLong(1, now)
            stmt.setString(2, id)
            stmt.executeUpdate()
            stmt.close()

            val pStmt = conn.prepareStatement("UPDATE product_presentations SET is_active = TRUE WHERE product_id = ?")
            pStmt.setString(1, id)
            pStmt.executeUpdate()
            pStmt.close()

            fetchProductById(conn, id)
        }
    }

    private fun fetchProductById(conn: java.sql.Connection, id: String): ProductDto {
        val stmt = conn.prepareStatement(
            """
            SELECT p.id, p.sku, p.name, p.department_id, d.name as dept_name,
                   p.base_unit, p.cost_cents, p.tax_rate, p.uses_inventory,
                   COALESCE(sb.quantity_millis, 0) as stock_millis,
                   p.min_stock_millis, p.max_stock_millis, p.is_perishable, p.shelf_life_alert_days, p.is_active, p.updated_at
            FROM products p
            LEFT JOIN departments d ON p.department_id = d.id
            LEFT JOIN stock_balances sb ON p.id = sb.product_id
            WHERE p.id = ?
            """.trimIndent()
        )
        stmt.setString(1, id)
        val rs = stmt.executeQuery()
        if (!rs.next()) {
            rs.close()
            stmt.close()
            throw NotFoundException("Producto con ID '$id' no encontrado")
        }
        val prod = ProductDto(
            id = rs.getString("id"),
            sku = rs.getString("sku"),
            name = rs.getString("name"),
            departmentId = rs.getString("department_id"),
            departmentName = rs.getString("dept_name"),
            baseUnit = rs.getString("base_unit"),
            costCents = rs.getLong("cost_cents"),
            taxRate = rs.getDouble("tax_rate"),
            usesInventory = rs.getBoolean("uses_inventory"),
            currentStockMillis = rs.getLong("stock_millis"),
            minStockMillis = rs.getLong("min_stock_millis"),
            maxStockMillis = rs.getLong("max_stock_millis"),
            isPerishable = rs.getBoolean("is_perishable"),
            shelfLifeAlertDays = rs.getInt("shelf_life_alert_days"),
            isActive = rs.getBoolean("is_active"),
            updatedAt = rs.getLong("updated_at"),
            presentations = emptyList()
        )
        rs.close()
        stmt.close()

        val presStmt = conn.prepareStatement(
            "SELECT id, product_id, barcode, name, factor_millis, price_cents, price_tiers_json FROM product_presentations WHERE product_id = ? ORDER BY factor_millis ASC"
        )
        presStmt.setString(1, id)
        val presRs = presStmt.executeQuery()
        val presList = mutableListOf<ProductPresentationDto>()
        while (presRs.next()) {
            presList.add(
                ProductPresentationDto(
                    id = presRs.getString("id"),
                    productId = presRs.getString("product_id"),
                    barcode = presRs.getString("barcode"),
                    name = presRs.getString("name"),
                    factorMillis = presRs.getLong("factor_millis"),
                    priceCents = presRs.getLong("price_cents"),
                    priceTiers = Json.decodeFromString(presRs.getString("price_tiers_json"))
                )
            )
        }
        presRs.close()
        presStmt.close()

        return prod.copy(presentations = presList, kitComponents = readKitComponents(conn, prod.id))
    }

    fun exportCatalogCsv(): String {
        return db.inTransaction { conn ->
            val sb = java.lang.StringBuilder()
            sb.append("\uFEFF") // UTF-8 BOM para apertura perfecta en Excel
            sb.append("SKU,CODIGO_BARRAS,NOMBRE,DEPARTAMENTO,UNIDAD,COSTO,PRECIO,STOCK_ACTUAL,STOCK_MINIMO,ACTIVO\r\n")

            val sql = """
                SELECT p.sku, COALESCE(pp.barcode, '') as barcode, p.name,
                       COALESCE(d.name, 'General') as department, p.base_unit,
                       p.cost_cents, COALESCE(pp.price_cents, 0) as price_cents,
                       COALESCE(sb.quantity_millis, 0) as stock_millis,
                       p.min_stock_millis, p.is_active
                FROM products p
                LEFT JOIN product_presentations pp ON p.id = pp.product_id AND pp.is_active = TRUE
                LEFT JOIN departments d ON p.department_id = d.id
                LEFT JOIN stock_balances sb ON p.id = sb.product_id
                ORDER BY p.name ASC, pp.factor_millis ASC
            """.trimIndent()

            val stmt = conn.prepareStatement(sql)
            val rs = stmt.executeQuery()

            fun escapeCsv(value: String): String {
                return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else {
                    value
                }
            }

            while (rs.next()) {
                val sku = rs.getString("sku") ?: ""
                val barcode = rs.getString("barcode") ?: ""
                val name = rs.getString("name") ?: ""
                val dept = rs.getString("department") ?: "General"
                val unit = rs.getString("base_unit") ?: "PZA"
                val cost = String.format(java.util.Locale.US, "%.2f", rs.getLong("cost_cents") / 100.0)
                val price = String.format(java.util.Locale.US, "%.2f", rs.getLong("price_cents") / 100.0)
                val stock = (rs.getLong("stock_millis") / 1000.0).toString().removeSuffix(".0")
                val minStock = (rs.getLong("min_stock_millis") / 1000.0).toString().removeSuffix(".0")
                val active = if (rs.getBoolean("is_active")) "SI" else "NO"

                sb.append(escapeCsv(sku)).append(",")
                sb.append(escapeCsv(barcode)).append(",")
                sb.append(escapeCsv(name)).append(",")
                sb.append(escapeCsv(dept)).append(",")
                sb.append(escapeCsv(unit)).append(",")
                sb.append(cost).append(",")
                sb.append(price).append(",")
                sb.append(stock).append(",")
                sb.append(minStock).append(",")
                sb.append(active).append("\r\n")
            }
            rs.close()
            stmt.close()

            sb.toString()
        }
    }

    fun importCatalogRows(rows: List<CatalogImportRowDto>): CatalogImportResponse {
        return db.inTransaction { conn ->
            var createdCount = 0
            var updatedCount = 0
            var errorCount = 0
            val results = mutableListOf<CatalogImportRowResult>()
            val now = System.currentTimeMillis()

            val deptsMap = mutableMapOf<String, String>()
            val deptQuery = conn.prepareStatement("SELECT id, name FROM departments")
            val deptRs = deptQuery.executeQuery()
            while (deptRs.next()) {
                deptsMap[deptRs.getString("name").trim().lowercase()] = deptRs.getString("id")
            }
            deptRs.close()
            deptQuery.close()

            fun getOrCreateDeptId(deptName: String): String {
                val clean = deptName.trim()
                if (clean.isBlank()) return deptsMap.values.firstOrNull() ?: "dept-general"
                val key = clean.lowercase()
                val existingId = deptsMap[key]
                if (existingId != null) return existingId

                val newId = "dept-" + UUID.randomUUID().toString().take(6)
                val insertDept = conn.prepareStatement("INSERT INTO departments (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")
                insertDept.setString(1, newId)
                insertDept.setString(2, clean)
                insertDept.executeUpdate()
                insertDept.close()
                deptsMap[key] = newId
                return newId
            }

            for ((index, row) in rows.withIndex()) {
                val rowNum = if (row.rowNumber > 0) row.rowNumber else index + 1
                val cleanName = row.name.trim()
                val cleanSku = row.sku.trim().uppercase()
                val cleanBarcode = row.barcode.trim()

                if (cleanName.isBlank()) {
                    errorCount++
                    results.add(
                        CatalogImportRowResult(
                            rowNumber = rowNum,
                            sku = cleanSku,
                            barcode = cleanBarcode,
                            name = cleanName,
                            status = "ERROR",
                            message = "El nombre del producto no puede estar vacío"
                        )
                    )
                    continue
                }

                if (row.priceCents < 0L || row.costCents < 0L) {
                    errorCount++
                    results.add(
                        CatalogImportRowResult(
                            rowNumber = rowNum,
                            sku = cleanSku,
                            barcode = cleanBarcode,
                            name = cleanName,
                            status = "ERROR",
                            message = "El precio o costo no pueden ser negativos"
                        )
                    )
                    continue
                }

                try {
                    var existingProdId: String? = null

                    if (cleanSku.isNotBlank()) {
                        val findSkuStmt = conn.prepareStatement("SELECT id FROM products WHERE LOWER(sku) = ?")
                        findSkuStmt.setString(1, cleanSku.lowercase())
                        val rs = findSkuStmt.executeQuery()
                        if (rs.next()) {
                            existingProdId = rs.getString("id")
                        }
                        rs.close()
                        findSkuStmt.close()
                    }

                    if (existingProdId == null && cleanBarcode.isNotBlank()) {
                        val findBarcodeStmt = conn.prepareStatement("SELECT product_id FROM product_presentations WHERE barcode = ?")
                        findBarcodeStmt.setString(1, cleanBarcode)
                        val rs = findBarcodeStmt.executeQuery()
                        if (rs.next()) {
                            existingProdId = rs.getString("product_id")
                        }
                        rs.close()
                        findBarcodeStmt.close()
                    }

                    val deptId = getOrCreateDeptId(row.department)
                    val baseUnit = when (row.baseUnit.trim().uppercase()) {
                        "KG", "KILOGRAMO", "KILO" -> "KG"
                        "LT", "LITRO", "L" -> "LT"
                        else -> "PZA"
                    }

                    if (existingProdId != null) {
                        val updateStmt = conn.prepareStatement(
                            """
                            UPDATE products
                            SET name = ?, department_id = ?, cost_cents = ?,
                                min_stock_millis = ?, is_active = ?, updated_at = ?
                            WHERE id = ?
                            """.trimIndent()
                        )
                        updateStmt.setString(1, cleanName)
                        updateStmt.setString(2, deptId)
                        updateStmt.setLong(3, row.costCents)
                        updateStmt.setLong(4, row.minStockMillis)
                        updateStmt.setBoolean(5, row.isActive)
                        updateStmt.setLong(6, now)
                        updateStmt.setString(7, existingProdId)
                        updateStmt.executeUpdate()
                        updateStmt.close()

                        val presUpdateStmt = conn.prepareStatement(
                            """
                            UPDATE product_presentations
                            SET price_cents = ?, name = ?
                            WHERE product_id = ? AND is_active = TRUE
                            """.trimIndent()
                        )
                        presUpdateStmt.setLong(1, row.priceCents)
                        presUpdateStmt.setString(2, if (baseUnit == "KG") "Granel" else "Pieza")
                        presUpdateStmt.setString(3, existingProdId)
                        presUpdateStmt.executeUpdate()
                        presUpdateStmt.close()

                        if (row.currentStockMillis > 0L) {
                            val updateStock = conn.prepareStatement(
                                "UPDATE stock_balances SET quantity_millis = ?, updated_at = ? WHERE product_id = ?"
                            )
                            updateStock.setLong(1, row.currentStockMillis)
                            updateStock.setLong(2, now)
                            updateStock.setString(3, existingProdId)
                            val affected = updateStock.executeUpdate()
                            updateStock.close()

                            if (affected == 0) {
                                val insertStock = conn.prepareStatement(
                                    "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)"
                                )
                                insertStock.setString(1, existingProdId)
                                insertStock.setLong(2, row.currentStockMillis)
                                insertStock.setLong(3, now)
                                insertStock.executeUpdate()
                                insertStock.close()
                            }
                        }

                        updatedCount++
                        results.add(
                            CatalogImportRowResult(
                                rowNumber = rowNum,
                                sku = cleanSku,
                                barcode = cleanBarcode,
                                name = cleanName,
                                status = "UPDATED",
                                message = "Producto actualizado (Precio: $${row.priceCents / 100.0})"
                            )
                        )
                    } else {
                        val newProdId = "prod-" + UUID.randomUUID().toString().take(8)
                        val finalSku = if (cleanSku.isNotBlank()) cleanSku else "PRD-" + UUID.randomUUID().toString().take(6).uppercase()
                        val finalBarcode = if (cleanBarcode.isNotBlank()) cleanBarcode else finalSku

                        val insertStmt = conn.prepareStatement(
                            """
                            INSERT INTO products (id, sku, name, department_id, base_unit, cost_cents, tax_rate, uses_inventory, min_stock_millis, max_stock_millis, is_active, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, 0, ?, ?)
                            """.trimIndent()
                        )
                        insertStmt.setString(1, newProdId)
                        insertStmt.setString(2, finalSku)
                        insertStmt.setString(3, cleanName)
                        insertStmt.setString(4, deptId)
                        insertStmt.setString(5, baseUnit)
                        insertStmt.setLong(6, row.costCents)
                        insertStmt.setDouble(7, row.taxRate)
                        insertStmt.setLong(8, row.minStockMillis)
                        insertStmt.setBoolean(9, row.isActive)
                        insertStmt.setLong(10, now)
                        insertStmt.executeUpdate()
                        insertStmt.close()

                        val presId = "pres-" + UUID.randomUUID().toString().take(8)
                        val insertPresStmt = conn.prepareStatement(
                            """
                            INSERT INTO product_presentations (id, product_id, barcode, name, factor_millis, price_cents, is_active)
                            VALUES (?, ?, ?, ?, 1000, ?, TRUE)
                            """.trimIndent()
                        )
                        insertPresStmt.setString(1, presId)
                        insertPresStmt.setString(2, newProdId)
                        insertPresStmt.setString(3, finalBarcode)
                        insertPresStmt.setString(4, if (baseUnit == "KG") "Granel" else "Pieza")
                        insertPresStmt.setLong(5, row.priceCents)
                        insertPresStmt.executeUpdate()
                        insertPresStmt.close()

                        val stockStmt = conn.prepareStatement(
                            "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, ?, ?)"
                        )
                        stockStmt.setString(1, newProdId)
                        stockStmt.setLong(2, row.currentStockMillis)
                        stockStmt.setLong(3, now)
                        stockStmt.executeUpdate()
                        stockStmt.close()

                        createdCount++
                        results.add(
                            CatalogImportRowResult(
                                rowNumber = rowNum,
                                sku = finalSku,
                                barcode = finalBarcode,
                                name = cleanName,
                                status = "CREATED",
                                message = "Producto creado con éxito"
                            )
                        )
                    }
                } catch (e: Exception) {
                    errorCount++
                    results.add(
                        CatalogImportRowResult(
                            rowNumber = rowNum,
                            sku = cleanSku,
                            barcode = cleanBarcode,
                            name = cleanName,
                            status = "ERROR",
                            message = e.message ?: "Error al procesar fila"
                        )
                    )
                }
            }

            CatalogImportResponse(
                totalRows = rows.size,
                createdCount = createdCount,
                updatedCount = updatedCount,
                errorCount = errorCount,
                rows = results
            )
        }
    }
}

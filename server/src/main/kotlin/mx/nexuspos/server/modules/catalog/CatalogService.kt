package mx.nexuspos.server.modules.catalog

import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
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
                       p.min_stock_millis, p.max_stock_millis, p.is_active, p.updated_at
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
            while (rs.next()) {
                val prodId = rs.getString("id")
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
                    "SELECT id, product_id, barcode, name, factor_millis, price_cents FROM product_presentations WHERE product_id = ? ORDER BY factor_millis ASC"
                } else {
                    "SELECT id, product_id, barcode, name, factor_millis, price_cents FROM product_presentations WHERE product_id = ? AND is_active = TRUE ORDER BY factor_millis ASC"
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
                                priceCents = presRs.getLong("price_cents")
                            )
                        )
                    }
                    presRs.close()
                    prod.copy(presentations = presList)
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
                INSERT INTO products (id, sku, name, department_id, base_unit, cost_cents, tax_rate, uses_inventory, min_stock_millis, max_stock_millis, is_active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
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
            insertStmt.setLong(11, now)
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
            val existing = fetchProductById(conn, id)

            if (request.name.isBlank()) {
                throw ValidationException("El nombre del producto es obligatorio")
            }
            if (request.presentations.isEmpty()) {
                throw ValidationException("El producto debe tener al menos una presentación")
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

            // Check barcode uniqueness
            val checkBarcode = conn.prepareStatement(
                "SELECT p.name, pp.barcode FROM product_presentations pp JOIN products p ON pp.product_id = p.id WHERE pp.barcode = ? AND pp.product_id != ? AND pp.is_active = TRUE"
            )
            for (p in request.presentations) {
                if (p.barcode.isBlank()) {
                    throw ValidationException("El código de barras no puede estar vacío")
                }
                checkBarcode.setString(1, p.barcode.trim())
                checkBarcode.setString(2, id)
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

            val now = System.currentTimeMillis()

            val updateStmt = conn.prepareStatement(
                """
                UPDATE products
                SET sku = ?, name = ?, department_id = ?, base_unit = ?, cost_cents = ?,
                    tax_rate = ?, uses_inventory = ?, min_stock_millis = ?, max_stock_millis = ?,
                    is_active = ?, updated_at = ?
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
            updateStmt.setBoolean(10, request.isActive)
            updateStmt.setLong(11, now)
            updateStmt.setString(12, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            // Update presentations: replace presentations for product
            val delStmt = conn.prepareStatement("DELETE FROM product_presentations WHERE product_id = ?")
            delStmt.setString(1, id)
            delStmt.executeUpdate()
            delStmt.close()

            val presStmt = conn.prepareStatement(
                "INSERT INTO product_presentations (id, product_id, barcode, name, factor_millis, price_cents, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            var pIdx = 1
            for (pres in request.presentations) {
                val presId = pres.id ?: "pres-$id-$pIdx"
                presStmt.setString(1, presId)
                presStmt.setString(2, id)
                presStmt.setString(3, pres.barcode.trim())
                presStmt.setString(4, pres.name.trim().ifBlank { request.name.trim() })
                presStmt.setLong(5, if (pres.factorMillis <= 0) 1000L else pres.factorMillis)
                presStmt.setLong(6, pres.priceCents.coerceAtLeast(0L))
                presStmt.setBoolean(7, request.isActive)
                presStmt.executeUpdate()
                pIdx++
            }
            presStmt.close()

            fetchProductById(conn, id)
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
                   p.min_stock_millis, p.max_stock_millis, p.is_active, p.updated_at
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
            isActive = rs.getBoolean("is_active"),
            updatedAt = rs.getLong("updated_at"),
            presentations = emptyList()
        )
        rs.close()
        stmt.close()

        val presStmt = conn.prepareStatement(
            "SELECT id, product_id, barcode, name, factor_millis, price_cents FROM product_presentations WHERE product_id = ? ORDER BY factor_millis ASC"
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
                    priceCents = presRs.getLong("price_cents")
                )
            )
        }
        presRs.close()
        presStmt.close()

        return prod.copy(presentations = presList)
    }
}

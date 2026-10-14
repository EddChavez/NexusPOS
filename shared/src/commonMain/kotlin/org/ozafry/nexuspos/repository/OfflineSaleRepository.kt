package org.ozafry.nexuspos.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ozafry.nexuspos.currentEpochMillis
import org.ozafry.nexuspos.db.NexusDatabase
import org.ozafry.nexuspos.models.*

class OfflineSaleRepository(private val database: NexusDatabase) {
    private val queries = database.nexusDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true }

    fun seedInitialDataIfEmpty() {
        val existingProducts = queries.selectAllProducts().executeAsList()
        val hasLegacyProducts = existingProducts.any { it.id.startsWith("prod-") }
        if (existingProducts.isEmpty() || hasLegacyProducts) {
            val now = currentEpochMillis()

            val catDespensa = CategoryDto("cat-despensa", "Despensa y Abarrotes", "#1E88E5", "kitchen")
            val catBebidas = CategoryDto("cat-bebidas", "Bebidas y Refrescos", "#0284C7", "local_drink")
            val catLacteos = CategoryDto("cat-lacteos", "Lácteos y Huevos", "#059669", "egg")
            val catBotanas = CategoryDto("cat-botanas", "Botanas y Dulces", "#D97706", "cookie")
            val catGranel = CategoryDto("cat-granel", "Frutas y Granel", "#16A34A", "scale")
            val catLimpieza = CategoryDto("cat-limpieza", "Limpieza y Hogar", "#7C3AED", "cleaning_services")

            val categories = listOf(catDespensa, catBebidas, catLacteos, catBotanas, catGranel, catLimpieza)
            for (c in categories) {
                queries.insertCategory(c.id, c.name, c.colorHex, c.icon)
            }

            val products = listOf(
                // Despensa y Abarrotes
                ProductDto("p-ab-1", "AB-001", "7501000111204", "Arroz Morelos 1kg", catDespensa.id, 28.50, 21.00, 0.00, 45.0, "PZA", true, now),
                ProductDto("p-ab-2", "AB-002", "7501000111211", "Frijol Negro Verde Valle 1kg", catDespensa.id, 38.00, 29.00, 0.00, 40.0, "PZA", true, now),
                ProductDto("p-ab-3", "AB-003", "7501011115501", "Aceite Vegetal 123 1L", catDespensa.id, 42.00, 33.50, 0.00, 35.0, "PZA", true, now),
                ProductDto("p-ab-4", "AB-004", "7501020512345", "Atún Dolores en Agua 140g", catDespensa.id, 22.50, 16.00, 0.00, 60.0, "PZA", true, now),
                ProductDto("p-ab-5", "AB-005", "7501030411112", "Azúcar Estándar Zulka 1kg", catDespensa.id, 32.00, 24.00, 0.00, 50.0, "PZA", true, now),
                ProductDto("p-ab-6", "AB-006", "7501008044455", "Sopa de Fideo La Moderna 200g", catDespensa.id, 11.50, 7.50, 0.00, 80.0, "PZA", true, now),
                ProductDto("p-ab-7", "AB-007", "7501058611113", "Mayonesa McCormick Limón 390g", catDespensa.id, 46.00, 36.00, 0.16, 25.0, "PZA", true, now),
                ProductDto("p-ab-8", "AB-008", "7501059288882", "Café Nescafé Clásico 120g", catDespensa.id, 78.00, 61.00, 0.16, 20.0, "PZA", true, now),

                // Bebidas y Refrescos
                ProductDto("p-beb-1", "BEB-001", "7501055310884", "Coca-Cola Original 600ml", catBebidas.id, 19.00, 14.50, 0.16, 120.0, "PZA", true, now),
                ProductDto("p-beb-2", "BEB-002", "7501055310891", "Coca-Cola 2.5L No Retornable", catBebidas.id, 44.00, 34.00, 0.16, 40.0, "PZA", true, now),
                ProductDto("p-beb-3", "BEB-003", "7501055300052", "Agua Purificada Ciel 1L", catBebidas.id, 13.00, 7.50, 0.16, 90.0, "PZA", true, now),
                ProductDto("p-beb-4", "BEB-004", "7501055322214", "Jugo del Valle Mango 413ml", catBebidas.id, 18.00, 12.50, 0.16, 45.0, "PZA", true, now),
                ProductDto("p-beb-5", "BEB-005", "7501064191113", "Refresco Jarrito Mandarina 600ml", catBebidas.id, 14.00, 9.50, 0.16, 50.0, "PZA", true, now),

                // Lácteos y Huevos
                ProductDto("p-lac-1", "LAC-001", "7501020511119", "Leche Entera Lala 1L", catLacteos.id, 27.00, 21.50, 0.00, 60.0, "PZA", true, now),
                ProductDto("p-lac-2", "LAC-002", "7501020522221", "Crema Ácida Lala 450ml", catLacteos.id, 31.00, 23.00, 0.00, 30.0, "PZA", true, now),
                ProductDto("p-lac-3", "LAC-003", "7501032333334", "Queso Panela Fud 400g", catLacteos.id, 58.00, 44.00, 0.00, 18.0, "PZA", true, now),
                ProductDto("p-lac-4", "LAC-004", "7501020544445", "Yoghurt para Beber Fresa 220g", catLacteos.id, 15.50, 10.50, 0.00, 40.0, "PZA", true, now),

                // Botanas y Dulces
                ProductDto("p-bot-1", "BOT-001", "7501011123456", "Papas Sabritas Sal 45g", catBotanas.id, 22.00, 16.50, 0.16, 70.0, "PZA", true, now),
                ProductDto("p-bot-2", "BOT-002", "7501011123463", "Doritos Nacho 58g", catBotanas.id, 22.00, 16.50, 0.16, 65.0, "PZA", true, now),
                ProductDto("p-bot-3", "BOT-003", "7501000122224", "Galletas Marías Gamesa 170g", catBotanas.id, 18.50, 13.00, 0.00, 55.0, "PZA", true, now),
                ProductDto("p-bot-4", "BOT-004", "7501000133336", "Galletas Emperador Chocolate 101g", catBotanas.id, 20.00, 14.50, 0.16, 45.0, "PZA", true, now),
                ProductDto("p-bot-5", "BOT-005", "7501035011119", "Mazapán de la Rosa 28g", catBotanas.id, 7.00, 4.00, 0.16, 150.0, "PZA", true, now),

                // Frutas y Granel
                ProductDto("p-gra-1", "GRA-001", "2000001000001", "Jitomate Saladet (kg)", catGranel.id, 34.00, 22.00, 0.00, 25.0, "KG", true, now),
                ProductDto("p-gra-2", "GRA-002", "2000002000008", "Cebolla Blanca (kg)", catGranel.id, 28.00, 17.00, 0.00, 20.0, "KG", true, now),
                ProductDto("p-gra-3", "GRA-003", "2000003000005", "Plátano Tabasco (kg)", catGranel.id, 22.00, 13.50, 0.00, 30.0, "KG", true, now),
                ProductDto("p-gra-4", "GRA-004", "2000004000002", "Limón con Semilla (kg)", catGranel.id, 42.00, 28.00, 0.00, 18.0, "KG", true, now),
                ProductDto("p-gra-5", "GRA-005", "2000005000009", "Huevo Blanco a Granel (kg)", catGranel.id, 48.00, 37.00, 0.00, 45.0, "KG", true, now),

                // Limpieza y Hogar
                ProductDto("p-lim-1", "LIM-001", "7501025401112", "Cloralex Blanqueador 950ml", catLimpieza.id, 21.00, 15.00, 0.16, 30.0, "PZA", true, now),
                ProductDto("p-lim-2", "LIM-002", "7501035902221", "Limpiador Fabuloso Lavanda 1L", catLimpieza.id, 26.50, 19.00, 0.16, 35.0, "PZA", true, now),
                ProductDto("p-lim-3", "LIM-003", "7501018503334", "Jabón Zote Rosa 400g", catLimpieza.id, 24.00, 17.00, 0.16, 40.0, "PZA", true, now),
                ProductDto("p-lim-4", "LIM-004", "7501025404441", "Papel Higiénico Pétalo 4 rollos", catLimpieza.id, 36.00, 26.50, 0.16, 28.0, "PZA", true, now)
            )

            for (p in products) {
                queries.upsertProduct(
                    p.id, p.sku, p.barcode, p.name, p.categoryId,
                    p.price, p.costPrice, p.taxRate, p.stockQuantity, p.unit,
                    if (p.isActive) 1L else 0L, p.updatedAt
                )
            }
        }
    }

    fun getProducts(): List<ProductDto> {
        return queries.selectAllProducts().executeAsList().map {
            ProductDto(
                id = it.id,
                sku = it.sku,
                barcode = it.barcode,
                name = it.name,
                categoryId = it.category_id,
                price = it.price,
                costPrice = it.cost_price,
                taxRate = it.tax_rate,
                stockQuantity = it.stock_quantity,
                unit = it.unit,
                isActive = it.is_active == 1L,
                updatedAt = it.updated_at
            )
        }
    }

    fun searchProducts(query: String): List<ProductDto> {
        return queries.searchProducts(query).executeAsList().map {
            ProductDto(
                id = it.id,
                sku = it.sku,
                barcode = it.barcode,
                name = it.name,
                categoryId = it.category_id,
                price = it.price,
                costPrice = it.cost_price,
                taxRate = it.tax_rate,
                stockQuantity = it.stock_quantity,
                unit = it.unit,
                isActive = it.is_active == 1L,
                updatedAt = it.updated_at
            )
        }
    }

    fun getProductByBarcodeOrSku(code: String): ProductDto? {
        val row = queries.selectProductByBarcodeOrSku(code, code).executeAsOneOrNull() ?: return null
        return ProductDto(
            id = row.id,
            sku = row.sku,
            barcode = row.barcode,
            name = row.name,
            categoryId = row.category_id,
            price = row.price,
            costPrice = row.cost_price,
            taxRate = row.tax_rate,
            stockQuantity = row.stock_quantity,
            unit = row.unit,
            isActive = row.is_active == 1L,
            updatedAt = row.updated_at
        )
    }

    fun getCategories(): List<CategoryDto> {
        return queries.selectAllCategories().executeAsList().map {
            CategoryDto(
                id = it.id,
                name = it.name,
                colorHex = it.color_hex,
                icon = it.icon
            )
        }
    }

    fun getCurrentShift(): CashShiftDto? {
        val row = queries.selectCurrentOpenShift().executeAsOneOrNull() ?: return null
        return CashShiftDto(
            id = row.id,
            registerId = row.register_id,
            cashierName = row.cashier_name,
            openedAt = row.opened_at,
            closedAt = row.closed_at,
            initialCash = row.initial_cash,
            cashSales = row.cash_sales,
            cardSales = row.card_sales,
            transferSales = row.transfer_sales,
            cashIn = row.cash_in,
            cashOut = row.cash_out,
            expectedCash = row.expected_cash,
            actualCash = row.actual_cash,
            notes = row.notes,
            status = row.status
        )
    }

    fun openShift(id: String, registerId: String, cashierName: String, initialCash: Double): CashShiftDto {
        val now = currentEpochMillis()
        queries.insertShift(
            id = id,
            register_id = registerId,
            cashier_name = cashierName,
            opened_at = now,
            initial_cash = initialCash,
            expected_cash = initialCash
        )

        val shiftDto = CashShiftDto(
            id = id,
            registerId = registerId,
            cashierName = cashierName,
            openedAt = now,
            initialCash = initialCash,
            expectedCash = initialCash,
            status = "OPEN"
        )

        // Registrar evento Outbox para sincronización
        queries.insertSyncEvent(
            id = "evt-$id",
            aggregate_type = "SHIFT",
            aggregate_id = id,
            action = "CREATE",
            payload_json = json.encodeToString(shiftDto),
            status = "PENDING",
            retry_count = 0L,
            created_at = now
        )

        return shiftDto
    }

    fun closeShift(shiftId: String, actualCash: Double, notes: String?): Boolean {
        val now = currentEpochMillis()
        queries.closeShift(
            closedAt = now,
            actualCash = actualCash,
            notes = notes,
            id = shiftId
        )

        val updatedShift = queries.selectShiftById(shiftId).executeAsOneOrNull() ?: return false
        val shiftDto = CashShiftDto(
            id = updatedShift.id,
            registerId = updatedShift.register_id,
            cashierName = updatedShift.cashier_name,
            openedAt = updatedShift.opened_at,
            closedAt = updatedShift.closed_at,
            initialCash = updatedShift.initial_cash,
            cashSales = updatedShift.cash_sales,
            cardSales = updatedShift.card_sales,
            transferSales = updatedShift.transfer_sales,
            cashIn = updatedShift.cash_in,
            cashOut = updatedShift.cash_out,
            expectedCash = updatedShift.expected_cash,
            actualCash = updatedShift.actual_cash,
            notes = updatedShift.notes,
            status = updatedShift.status
        )

        // Registrar evento Outbox para sincronización del cierre de turno
        queries.insertSyncEvent(
            id = "evt-close-$shiftId",
            aggregate_type = "SHIFT",
            aggregate_id = shiftId,
            action = "UPDATE",
            payload_json = json.encodeToString(shiftDto),
            status = "PENDING",
            retry_count = 0L,
            created_at = now
        )

        return true
    }

    /**
     * Registra una venta completa de forma atómica:
     * 1. Inserta registro de Sale
     * 2. Inserta partidas en SaleItem
     * 3. Descuenta stock local de productos
     * 4. Actualiza totales de la caja/turno en curso
     * 5. Encola evento de sincronización en la tabla SyncEvent (Outbox)
     */
    fun createSale(sale: SaleDto): SaleDto {
        val now = currentEpochMillis()
        queries.transaction {
            // 1. Guardar Sale
            queries.insertSale(
                id = sale.id,
                shift_id = sale.shiftId,
                register_id = sale.registerId,
                cashier_name = sale.cashierName,
                customer_name = sale.customerName,
                subtotal = sale.subtotal,
                tax_total = sale.taxTotal,
                discount_total = sale.discountTotal,
                total = sale.total,
                payment_method = sale.paymentMethod,
                amount_paid = sale.amountPaid,
                change_given = sale.changeGiven,
                status = sale.status,
                created_at = sale.createdAt,
                synced = 0L
            )

            // 2. Guardar SaleItems y 3. Descontar Stock
            for (item in sale.items) {
                queries.insertSaleItem(
                    id = item.id,
                    sale_id = sale.id,
                    product_id = item.productId,
                    product_name = item.productName,
                    unit_price = item.unitPrice,
                    quantity = item.quantity,
                    unit = item.unit,
                    subtotal = item.subtotal,
                    tax_rate = item.taxRate,
                    tax_amount = item.taxAmount,
                    discount_amount = item.discountAmount,
                    total = item.total
                )

                queries.decreaseProductStock(
                    qty = item.quantity,
                    updatedAt = now,
                    productId = item.productId
                )
            }

            // 4. Actualizar totales del turno
            val cashAmount = if (sale.paymentMethod == "CASH") sale.total else 0.0
            val cardAmount = if (sale.paymentMethod == "CARD") sale.total else 0.0
            val transferAmount = if (sale.paymentMethod == "TRANSFER") sale.total else 0.0

            queries.updateShiftSalesTotals(
                cashAmount = cashAmount,
                cardAmount = cardAmount,
                transferAmount = transferAmount,
                shiftId = sale.shiftId
            )

            // 5. Encolar evento outbox
            queries.insertSyncEvent(
                id = "evt-sale-${sale.id}",
                aggregate_type = "SALE",
                aggregate_id = sale.id,
                action = "CREATE",
                payload_json = json.encodeToString(sale),
                status = "PENDING",
                retry_count = 0L,
                created_at = now
            )
        }

        return sale
    }

    fun getPendingSyncEvents(limit: Long = 50): List<SyncEventDto> {
        return queries.selectPendingSyncEvents(limit).executeAsList().map {
            SyncEventDto(
                id = it.id,
                aggregateType = it.aggregate_type,
                aggregateId = it.aggregate_id,
                action = it.action,
                payloadJson = it.payload_json,
                createdAt = it.created_at
            )
        }
    }

    fun markSyncEventSuccess(id: String, timestamp: Long) {
        queries.transaction {
            queries.markSyncEventSuccess(syncedAt = timestamp, id = id)
            // Si el evento era de una venta, marcar la venta como sincronizada
            if (id.startsWith("evt-sale-")) {
                val saleId = id.removePrefix("evt-sale-")
                queries.markSaleSynced(saleId)
            }
        }
    }

    fun markSyncEventFailed(id: String, errorMessage: String) {
        queries.markSyncEventFailed(errorMessage = errorMessage, id = id)
    }

    fun upsertSyncedProducts(products: List<ProductDto>, categories: List<CategoryDto>) {
        queries.transaction {
            for (cat in categories) {
                queries.insertCategory(cat.id, cat.name, cat.colorHex, cat.icon)
            }
            for (p in products) {
                queries.upsertProduct(
                    p.id, p.sku, p.barcode, p.name, p.categoryId,
                    p.price, p.costPrice, p.taxRate, p.stockQuantity, p.unit,
                    if (p.isActive) 1L else 0L, p.updatedAt
                )
            }
        }
    }
}

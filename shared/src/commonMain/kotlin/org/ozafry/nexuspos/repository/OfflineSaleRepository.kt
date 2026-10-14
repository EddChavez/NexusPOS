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
        if (existingProducts.isEmpty()) {
            val now = currentEpochMillis()
            val catBebidas = CategoryDto("cat-1", "Bebidas", "#1E88E5", "coffee")
            val catAlimentos = CategoryDto("cat-2", "Alimentos", "#43A047", "fastfood")
            val catSnacks = CategoryDto("cat-3", "Snacks", "#FB8C00", "cookie")

            queries.insertCategory(catBebidas.id, catBebidas.name, catBebidas.colorHex, catBebidas.icon)
            queries.insertCategory(catAlimentos.id, catAlimentos.name, catAlimentos.colorHex, catAlimentos.icon)
            queries.insertCategory(catSnacks.id, catSnacks.name, catSnacks.colorHex, catSnacks.icon)

            val products = listOf(
                ProductDto("prod-1", "BEB-001", "7501001", "Café Americano 12oz", catBebidas.id, 35.0, 10.0, 0.16, 100.0, true, now),
                ProductDto("prod-2", "BEB-002", "7501002", "Capuchino 12oz", catBebidas.id, 45.0, 15.0, 0.16, 80.0, true, now),
                ProductDto("prod-3", "BEB-003", "7501003", "Refresco Cola 600ml", catBebidas.id, 22.0, 12.0, 0.16, 150.0, true, now),
                ProductDto("prod-4", "ALI-001", "7502001", "Sandwich Jamón y Queso", catAlimentos.id, 65.0, 25.0, 0.16, 50.0, true, now),
                ProductDto("prod-5", "ALI-002", "7502002", "Croissant de Mantequilla", catAlimentos.id, 40.0, 18.0, 0.16, 40.0, true, now),
                ProductDto("prod-6", "SNK-001", "7503001", "Papas Fritas Saladas", catSnacks.id, 25.0, 12.0, 0.16, 120.0, true, now),
                ProductDto("prod-7", "SNK-002", "7503002", "Galletas de Avena", catSnacks.id, 20.0, 8.0, 0.16, 90.0, true, now)
            )

            for (p in products) {
                queries.upsertProduct(
                    p.id, p.sku, p.barcode, p.name, p.categoryId,
                    p.price, p.costPrice, p.taxRate, p.stockQuantity,
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
                    p.price, p.costPrice, p.taxRate, p.stockQuantity,
                    if (p.isActive) 1L else 0L, p.updatedAt
                )
            }
        }
    }
}

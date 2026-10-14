package org.ozafry.nexuspos

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.ozafry.nexuspos.db.NexusDatabase
import org.ozafry.nexuspos.models.SaleDto
import org.ozafry.nexuspos.models.SaleItemDto
import org.ozafry.nexuspos.repository.OfflineSaleRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedLogicDesktopTest {

    private fun createInMemoryRepo(): OfflineSaleRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NexusDatabase.Schema.create(driver)
        val database = NexusDatabase(driver)
        val repo = OfflineSaleRepository(database)
        repo.seedInitialDataIfEmpty()
        return repo
    }

    @Test
    fun testSeedDataAndProductQueries() {
        val repo = createInMemoryRepo()
        val products = repo.getProducts()
        assertTrue(products.isNotEmpty(), "Los productos iniciales deben cargarse")

        val found = repo.getProductByBarcodeOrSku("7501001")
        assertNotNull(found)
        assertEquals("Café Americano 12oz", found.name)

        val searchResult = repo.searchProducts("Sandwich")
        assertEquals(1, searchResult.size)
        assertEquals("Sandwich Jamón y Queso", searchResult.first().name)
    }

    @Test
    fun testOfflineSaleAtomicTransactionAndOutbox() {
        val repo = createInMemoryRepo()

        // 1. Abrir turno
        val shift = repo.openShift(
            id = "shift-001",
            registerId = "REG-01",
            cashierName = "Carlos",
            initialCash = 500.0
        )
        assertEquals("OPEN", shift.status)

        // 2. Realizar venta offline
        val coffee = repo.getProductByBarcodeOrSku("7501001")!!
        val initialStock = coffee.stockQuantity

        val sale = SaleDto(
            id = "sale-101",
            shiftId = shift.id,
            registerId = "REG-01",
            cashierName = "Carlos",
            customerName = "Cliente Mostrador",
            subtotal = 70.0,
            taxTotal = 11.20,
            discountTotal = 0.0,
            total = 81.20,
            paymentMethod = "CASH",
            amountPaid = 100.0,
            changeGiven = 18.80,
            status = "COMPLETED",
            createdAt = System.currentTimeMillis(),
            items = listOf(
                SaleItemDto(
                    id = "item-1",
                    saleId = "sale-101",
                    productId = coffee.id,
                    productName = coffee.name,
                    unitPrice = coffee.price,
                    quantity = 2.0,
                    subtotal = 70.0,
                    taxRate = coffee.taxRate,
                    taxAmount = 11.20,
                    discountAmount = 0.0,
                    total = 81.20
                )
            )
        )

        repo.createSale(sale)

        // Verificar descuento de stock local
        val updatedCoffee = repo.getProductByBarcodeOrSku("7501001")!!
        assertEquals(initialStock - 2.0, updatedCoffee.stockQuantity)

        // Verificar eventos pendientes en el Outbox (apertura de turno + venta)
        val pendingEvents = repo.getPendingSyncEvents()
        assertEquals(2, pendingEvents.size, "Debe haber 2 eventos outbox pendientes")

        val saleEvent = pendingEvents.find { it.aggregateType == "SALE" }
        assertNotNull(saleEvent)
        assertEquals("sale-101", saleEvent.aggregateId)

        // Simular éxito de sincronización
        repo.markSyncEventSuccess(saleEvent.id, System.currentTimeMillis())

        val remainingPending = repo.getPendingSyncEvents()
        assertEquals(1, remainingPending.size)
        assertEquals("SHIFT", remainingPending.first().aggregateType)
    }
}
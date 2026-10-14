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
        assertTrue(products.isNotEmpty(), "Los productos iniciales de abarrotes deben cargarse")

        val found = repo.getProductByBarcodeOrSku("7501000111204")
        assertNotNull(found)
        assertEquals("Arroz Morelos 1kg", found.name)
        assertEquals("PZA", found.unit)

        val searchResult = repo.searchProducts("Frijol")
        assertEquals(1, searchResult.size)
        assertEquals("Frijol Negro Verde Valle 1kg", searchResult.first().name)

        val granel = repo.getProductByBarcodeOrSku("2000001000001")
        assertNotNull(granel)
        assertEquals("Jitomate Saladet (kg)", granel.name)
        assertEquals("KG", granel.unit)
    }

    @Test
    fun testOfflineSaleAtomicTransactionAndOutbox() {
        val repo = createInMemoryRepo()

        // 1. Abrir turno en tienda
        val shift = repo.openShift(
            id = "shift-001",
            registerId = "REG-01",
            cashierName = "Don Pepe",
            initialCash = 500.0
        )
        assertEquals("OPEN", shift.status)

        // 2. Realizar venta offline de abarrotes (Coca-Cola + Jitomate a granel)
        val coca = repo.getProductByBarcodeOrSku("7501055310884")!!
        val initialStockCoca = coca.stockQuantity

        val sale = SaleDto(
            id = "sale-101",
            shiftId = shift.id,
            registerId = "REG-01",
            cashierName = "Don Pepe",
            customerName = "Cliente Vecino",
            subtotal = 38.0,
            taxTotal = 6.08,
            discountTotal = 0.0,
            total = 44.08,
            paymentMethod = "CASH",
            amountPaid = 50.0,
            changeGiven = 5.92,
            status = "COMPLETED",
            createdAt = currentEpochMillis(),
            items = listOf(
                SaleItemDto(
                    id = "item-1",
                    saleId = "sale-101",
                    productId = coca.id,
                    productName = coca.name,
                    unitPrice = coca.price,
                    quantity = 2.0,
                    unit = coca.unit,
                    subtotal = 38.0,
                    taxRate = coca.taxRate,
                    taxAmount = 6.08,
                    discountAmount = 0.0,
                    total = 44.08
                )
            )
        )

        repo.createSale(sale)

        // Verificar descuento de stock local
        val updatedCoca = repo.getProductByBarcodeOrSku("7501055310884")!!
        assertEquals(initialStockCoca - 2.0, updatedCoca.stockQuantity)

        // Verificar eventos pendientes en el Outbox (apertura de turno + venta)
        val pendingEvents = repo.getPendingSyncEvents()
        assertEquals(2, pendingEvents.size, "Debe haber 2 eventos outbox pendientes")

        val saleEvent = pendingEvents.find { it.aggregateType == "SALE" }
        assertNotNull(saleEvent)
        assertEquals("sale-101", saleEvent.aggregateId)

        // Simular éxito de sincronización
        repo.markSyncEventSuccess(saleEvent.id, currentEpochMillis())

        val remainingPending = repo.getPendingSyncEvents()
        assertEquals(1, remainingPending.size)
        assertEquals("SHIFT", remainingPending.first().aggregateType)
    }
}
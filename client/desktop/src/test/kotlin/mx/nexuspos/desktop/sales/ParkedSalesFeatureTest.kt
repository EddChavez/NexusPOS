package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.ParkedSaleItemDto
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.core.ProductUnit
import java.util.UUID
import kotlin.test.*

class ParkedSalesFeatureTest {

    private class MockParkedSalesApiClient : ApiClient() {
        val storedParkedSales = mutableListOf<ParkedSaleResponse>()
        var lastParkSaleRequest: ParkSaleRequest? = null
        var lastRetrieveRequest: Pair<String, RetrieveParkedSaleRequest>? = null
        var lastDiscardId: String? = null

        override suspend fun checkHealth(): Boolean = true

        override suspend fun parkSale(req: ParkSaleRequest): Result<ParkedSaleResponse> {
            lastParkSaleRequest = req
            val parked = ParkedSaleResponse(
                id = "parked-${UUID.randomUUID().toString().take(6)}",
                customLabel = req.customLabel,
                cashierUserId = req.cashierUserId,
                cashierName = req.cashierName,
                registerId = req.registerId,
                customerId = req.customerId,
                customerName = req.customerName,
                totalCents = req.totalCents,
                itemsCount = req.items.size,
                items = req.items,
                parkedAt = System.currentTimeMillis(),
                status = "PARKED"
            )
            storedParkedSales.add(parked)
            return Result.success(parked)
        }

        override suspend fun getParkedSales(registerId: String?): Result<List<ParkedSaleResponse>> {
            val active = storedParkedSales.filter { it.status == "PARKED" }
            return Result.success(active)
        }

        override suspend fun retrieveParkedSale(id: String, req: RetrieveParkedSaleRequest): Result<ParkedSaleResponse> {
            lastRetrieveRequest = Pair(id, req)
            val idx = storedParkedSales.indexOfFirst { it.id == id }
            if (idx == -1) return Result.failure(Exception("Not found"))
            val updated = storedParkedSales[idx].copy(status = "RETRIEVED", retrievedAt = System.currentTimeMillis(), retrievedByUserId = req.retrievedByUserId)
            storedParkedSales[idx] = updated
            return Result.success(updated)
        }

        override suspend fun discardParkedSale(id: String, userId: String): Result<ParkedSaleResponse> {
            lastDiscardId = id
            val idx = storedParkedSales.indexOfFirst { it.id == id }
            if (idx == -1) return Result.failure(Exception("Not found"))
            val updated = storedParkedSales[idx].copy(status = "DISCARDED")
            storedParkedSales[idx] = updated
            return Result.success(updated)
        }
    }

    private fun createOpenSessionSummary(): CashSessionSummary {
        return CashSessionSummary(
            sessionId = "sess-100",
            registerId = "caja-1",
            registerName = "Caja Principal",
            cashierId = "user-1",
            cashierName = "Cajero Don Pepe",
            openedAt = System.currentTimeMillis(),
            closedAt = null,
            initialFundCents = 50000L,
            salesCashCents = 0L,
            creditPaymentsCashCents = 0L,
            cashInCents = 0L,
            cashOutCents = 0L,
            refundsCashCents = 0L,
            expectedCashCents = 50000L,
            countedCashCents = null,
            differenceCents = null,
            status = "OPEN"
        )
    }

    @Test
    fun testParkCurrentSaleSuccess() = runBlocking {
        val mockApi = MockParkedSalesApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.setActiveCashier("user-1", "Don Pepe", "CASHIER")

        // Add 2 products to cart
        viewModel.addGenericProduct("Aceite 1L", 4000L) // $40.00
        viewModel.addGenericProduct("Arroz 1kg", 2500L)  // $25.00
        assertEquals(2, viewModel.uiState.value.cartLines.size)
        assertEquals(6500L, viewModel.uiState.value.total.cents)

        // Park sale with label
        viewModel.confirmParkCurrentSale("Doña Rosa")

        // Verify API was called
        val capturedReq = mockApi.lastParkSaleRequest
        assertNotNull(capturedReq)
        assertEquals("Doña Rosa", capturedReq.customLabel)
        assertEquals(2, capturedReq.items.size)
        assertEquals(6500L, capturedReq.totalCents)
        assertEquals("user-1", capturedReq.cashierUserId)

        // Verify cart and ticket reset
        val state = viewModel.uiState.value
        assertEquals(0, state.cartLines.size)
        assertEquals(0L, state.total.cents)
        assertFalse(state.isShowingParkSaleDialog)
        assertTrue(state.successMessage?.contains("Doña Rosa") == true)
        assertEquals(1, state.parkedSalesCount)
    }

    @Test
    fun testRetrieveParkedSaleIntoEmptyCart() = runBlocking {
        val mockApi = MockParkedSalesApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.setActiveCashier("user-1", "Don Pepe", "CASHIER")

        // Populate a parked sale in mock API
        val parkedItem1 = ParkedSaleItemDto(
            id = "item-1",
            name = "Leche Entera 1L",
            unitCode = "PZA",
            quantityMillis = 2000L,
            unitPriceCents = 2700L,
            totalCents = 5400L
        )
        val parkedItem2 = ParkedSaleItemDto(
            id = "item-2",
            name = "Huevo Granel",
            unitCode = "KG",
            quantityMillis = 500L,
            unitPriceCents = 4000L,
            totalCents = 2000L,
            isWeighed = true
        )
        val parkRes = mockApi.parkSale(
            ParkSaleRequest(
                customLabel = "Don Manuel",
                cashierUserId = "user-1",
                cashierName = "Don Pepe",
                items = listOf(parkedItem1, parkedItem2),
                totalCents = 7400L
            )
        ).getOrThrow()

        viewModel.loadParkedSales()
        assertEquals(1, viewModel.uiState.value.parkedSalesCount)

        // Cart is currently empty; retrieve the parked sale
        viewModel.retrieveParkedSale(parkRes)

        val state = viewModel.uiState.value
        assertEquals(2, state.cartLines.size)
        assertEquals("Leche Entera 1L", state.cartLines[0].name)
        assertEquals(2000L, state.cartLines[0].quantity.millis)
        assertEquals("Huevo Granel", state.cartLines[1].name)
        assertEquals(ProductUnit.KILOGRAM, state.cartLines[1].unit)
        assertEquals(7400L, state.total.cents)
        assertTrue(state.successMessage?.contains("Don Manuel") == true)

        // Parked sales count is now 0 in active list
        assertEquals(0, state.parkedSalesCount)
    }

    @Test
    fun testRetrieveParkedSaleWhenCartHasItemsCreatesNewTicket() = runBlocking {
        val mockApi = MockParkedSalesApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        viewModel.setActiveCashier("user-1", "Don Pepe", "CASHIER")

        // Active ticket already has items
        viewModel.addGenericProduct("Refresco 600ml", 1800L)
        val originalTicketId = viewModel.uiState.value.activeTicketId

        // Parked sale to retrieve
        val parkedRes = mockApi.parkSale(
            ParkSaleRequest(
                customLabel = "Cliente Mostrador",
                cashierUserId = "user-1",
                cashierName = "Don Pepe",
                items = listOf(
                    ParkedSaleItemDto(
                        id = "p-1",
                        name = "Pan de Caja",
                        quantityMillis = 1000L,
                        unitPriceCents = 4500L,
                        totalCents = 4500L
                    )
                ),
                totalCents = 4500L
            )
        ).getOrThrow()

        // Retrieve while active ticket has items
        viewModel.retrieveParkedSale(parkedRes)

        val state = viewModel.uiState.value
        // Must have created a new ticket so the previous one isn't overwritten
        assertEquals(2, state.tickets.size)
        assertNotEquals(originalTicketId, state.activeTicketId)
        assertEquals("Pan de Caja", state.cartLines[0].name)
        assertEquals(4500L, state.total.cents)

        // Switch back to original ticket
        viewModel.selectTicket(originalTicketId)
        val switchedState = viewModel.uiState.value
        assertEquals(1, switchedState.cartLines.size)
        assertEquals("Refresco 600ml", switchedState.cartLines[0].name)
        assertEquals(1800L, switchedState.total.cents)
    }

    @Test
    fun testDiscardParkedSale() = runBlocking {
        val mockApi = MockParkedSalesApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val parkRes = mockApi.parkSale(
            ParkSaleRequest(
                customLabel = "Venta a descartar",
                cashierUserId = "user-1",
                cashierName = "Don Pepe",
                items = listOf(
                    ParkedSaleItemDto(id = "x", name = "Articulo", quantityMillis = 1000L, unitPriceCents = 100L, totalCents = 100L)
                ),
                totalCents = 100L
            )
        ).getOrThrow()

        viewModel.loadParkedSales()
        assertEquals(1, viewModel.uiState.value.parkedSalesCount)

        viewModel.discardParkedSale(parkRes.id)

        assertEquals("DISCARDED", mockApi.storedParkedSales.first().status)
        assertEquals(0, viewModel.uiState.value.parkedSalesCount)
        assertTrue(viewModel.uiState.value.successMessage?.contains("descartado") == true)
    }

    @Test
    fun testCannotParkEmptyCart() = runBlocking {
        val mockApi = MockParkedSalesApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        viewModel.confirmParkCurrentSale("Venta Vacia")

        assertNull(mockApi.lastParkSaleRequest)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("El carrito está vacío") == true)
    }
}

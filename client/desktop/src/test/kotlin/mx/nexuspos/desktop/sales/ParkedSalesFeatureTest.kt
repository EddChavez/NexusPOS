package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.credit.CustomerDto
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
        var parkGate: CompletableDeferred<Unit>? = null
        var parkAttemptCount = 0
        var parkError: Exception? = null
        var savesNotes = true
        val customers = mutableMapOf<String, CustomerDto>()

        override suspend fun checkHealth(): Boolean = true

        override suspend fun getCustomer(id: String): Result<CustomerDto> =
            customers[id]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException(id))

        override suspend fun parkSale(req: ParkSaleRequest): Result<ParkedSaleResponse> {
            lastParkSaleRequest = req
            parkAttemptCount++
            parkGate?.await()
            parkError?.let { return Result.failure(it) }
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
                status = "PARKED",
                note = if (savesNotes) req.note else ""
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

        // Park with a reference and an independent, multiline note.
        val note = "Entregar por la tarde\nAvisar al llegar"
        viewModel.setTicketNote(note)
        viewModel.renameTicket(viewModel.uiState.value.activeTicketId, "Pedido especial")
        viewModel.confirmParkCurrentSale("Doña Rosa")

        // Verify API was called
        val capturedReq = mockApi.lastParkSaleRequest
        assertNotNull(capturedReq)
        assertEquals("Doña Rosa", capturedReq.customLabel)
        assertEquals(note, capturedReq.note)
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
        assertEquals("", state.activeTicketNote)
        assertEquals("", state.tickets.single().note)
        assertEquals("Ticket 1", state.tickets.single().title)

        viewModel.retrieveParkedSale(mockApi.storedParkedSales.single())
        assertEquals(note, viewModel.uiState.value.activeTicketNote)
        assertEquals(note, viewModel.uiState.value.tickets.single().note)
        assertEquals("Doña Rosa", viewModel.uiState.value.tickets.single().title)
    }

    @Test
    fun parkingTheActiveTabReturnsToTheOtherTicketsNote() {
        val api = MockParkedSalesApiClient()
        val vm = SalesViewModel(api, scope = CoroutineScope(Dispatchers.Unconfined))
        val firstId = vm.uiState.value.activeTicketId
        vm.setTicketNote("Entregar mañana")
        vm.newTicket()
        vm.addGenericProduct("Café", 6000)
        vm.setTicketNote("Entregar hoy")
        vm.confirmParkCurrentSale("")

        val state = vm.uiState.value
        assertEquals(firstId, state.tickets.single().id)
        assertEquals(firstId, state.activeTicketId)
        assertEquals("Entregar mañana", state.activeTicketNote)
        assertEquals("Ticket 2", api.storedParkedSales.single().customLabel)
        assertEquals("Entregar hoy", api.storedParkedSales.single().note)
    }

    @Test
    fun olderServerThatOmitsTheNoteTriggersAWarning() {
        val api = MockParkedSalesApiClient().apply { savesNotes = false }
        val vm = SalesViewModel(api, scope = CoroutineScope(Dispatchers.Unconfined))
        vm.addGenericProduct("Café", 6000)
        vm.setTicketNote("Entregar mañana")
        vm.confirmParkCurrentSale("Pedido 1")

        val state = vm.uiState.value
        assertEquals("", state.activeTicketNote)
        assertTrue(state.errorMessage.orEmpty().contains("El servidor no guardó la nota"))
        assertTrue(state.errorMessage.orEmpty().contains("Entregar mañana"))
    }

    @Test
    fun parkedTicketIsRemovedEvenIfAnotherTabBecomesActiveBeforeConfirmation() {
        val gate = CompletableDeferred<Unit>()
        val api = MockParkedSalesApiClient().apply { parkGate = gate }
        val vm = SalesViewModel(api, scope = CoroutineScope(Dispatchers.Unconfined))
        val otherId = vm.uiState.value.activeTicketId
        vm.addGenericProduct("Arroz", 2500)
        vm.setTicketNote("Nota de arroz")
        vm.newTicket()
        val parkedId = vm.uiState.value.activeTicketId
        vm.addGenericProduct("Frijol", 2000)
        vm.setTicketNote("Nota de frijol")
        vm.confirmParkCurrentSale("Pedido 2")
        vm.confirmParkCurrentSale("Pedido 2")
        vm.selectTicket(otherId)
        gate.complete(Unit)

        val state = vm.uiState.value
        assertEquals(1, api.parkAttemptCount)
        assertEquals("Nota de frijol", api.storedParkedSales.single().note)
        assertEquals(listOf(otherId), state.tickets.map { it.id })
        assertFalse(state.tickets.any { it.id == parkedId })
        assertEquals(otherId, state.activeTicketId)
        assertEquals("Nota de arroz", state.activeTicketNote)
        assertEquals("Arroz", state.cartLines.single().name)
    }

    @Test
    fun failedParkRetainsTheActiveTicketForRetry() {
        val api = MockParkedSalesApiClient().apply { parkError = IllegalStateException("Sin conexión") }
        val vm = SalesViewModel(api, scope = CoroutineScope(Dispatchers.Unconfined))
        vm.addGenericProduct("Café", 6000)
        vm.setTicketNote("Entregar el lunes")
        vm.openParkSaleDialog()
        vm.confirmParkCurrentSale("Café")

        val state = vm.uiState.value
        assertTrue(state.isShowingParkSaleDialog)
        assertFalse(state.isParkingSale)
        assertEquals("Entregar el lunes", state.activeTicketNote)
        assertEquals("Café", state.cartLines.single().name)
        assertTrue(state.errorMessage.orEmpty().contains("Sin conexión"))
    }

    @Test
    fun retrievingKeepsANoteOnlyDraftAndRestoresTheCustomerAndDiscount() {
        val customer = CustomerDto(id = "customer-1", name = "Doña Rosa", creditEnabled = true)
        val api = MockParkedSalesApiClient().apply { customers[customer.id] = customer }
        val vm = SalesViewModel(api, scope = CoroutineScope(Dispatchers.Unconfined))
        vm.addGenericProduct("Arroz", 2500)
        vm.selectCustomer(customer)
        vm.setTicketNote("Recoge el lunes")
        vm.applyDiscount(null, 10.0, 0)
        vm.confirmParkCurrentSale("")

        val parked = api.storedParkedSales.single()
        assertEquals(customer.name, parked.customLabel)
        val draftId = vm.uiState.value.activeTicketId
        vm.setTicketNote("Otra venta")
        vm.retrieveParkedSale(parked)

        val state = vm.uiState.value
        assertEquals("Recoge el lunes", state.activeTicketNote)
        assertEquals(customer, state.selectedCustomer)
        assertEquals(250L, state.discount.cents)
        assertEquals(10.0, state.cartLines.single().discountPercent)
        assertEquals("Otra venta", state.tickets.single { it.id == draftId }.note)
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

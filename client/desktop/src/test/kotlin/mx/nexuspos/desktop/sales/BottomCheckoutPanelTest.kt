package mx.nexuspos.desktop.sales

import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.sales.*
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.test.*

class BottomCheckoutPanelTest {

    @Test
    fun testDefaultBottomCheckoutPanelState() {
        val state = BottomCheckoutPanelState()
        assertEquals("Ticket 1", state.activeTicketLabel)
        assertEquals("En Curso", state.statusBadgeText)
        assertFalse(state.hasMultipleTickets)
        assertFalse(state.canDelete)
        assertEquals("Ticket #---", state.lastSaleFolio)
        assertEquals("$0.00", state.lastSaleTotalFormatted)
        assertEquals("$0.00", state.lastSaleReceivedFormatted)
        assertEquals("$0.00", state.lastSaleChangeFormatted)
        assertFalse(state.hasLastSale)
        assertEquals("$0.00", state.currentTotalFormatted)
        assertEquals("Moneda: MXN ($)", state.currencyLabel)
        assertFalse(state.canCheckout)
        assertTrue(state.isServerConnected)
    }

    @Test
    fun testMappingFromSalesUiStateEmpty() {
        val uiState = SalesUiState()
        val panelState = uiState.toBottomCheckoutPanelState()

        assertEquals("Ticket 1", panelState.activeTicketLabel)
        assertEquals("En Curso", panelState.statusBadgeText)
        assertFalse(panelState.hasMultipleTickets)
        assertFalse(panelState.canDelete)
        assertEquals("Ticket #---", panelState.lastSaleFolio)
        assertEquals("$0.00", panelState.lastSaleTotalFormatted)
        assertEquals("$0.00", panelState.lastSaleReceivedFormatted)
        assertEquals("$0.00", panelState.lastSaleChangeFormatted)
        assertFalse(panelState.hasLastSale)
        assertEquals("$0.00", panelState.currentTotalFormatted)
        assertFalse(panelState.canCheckout)
    }

    @Test
    fun testMappingFromSalesUiStateWithCompletedSale() {
        val mockCompletedSale = SaleResponse(
            id = "sale-1042",
            folio = 1042L,
            cashSessionId = "sess-1",
            cashierName = "Cajero Don Pepe",
            status = "COMPLETED",
            subtotalCents = 6285L,
            taxCents = 1006L,
            totalCents = 7291L,
            lines = emptyList(),
            payments = listOf(
                SalePaymentResponse(
                    id = "pay-1",
                    method = "CASH",
                    amountCents = 7291L,
                    receivedCents = 10000L,
                    changeCents = 2709L
                )
            ),
            createdAt = 1760000000L
        )

        val cartLine = CartLine(
            id = "c1",
            productId = "p1",
            presentationId = "pr1",
            barcode = "7501055300076",
            name = "Coca Cola 600ml",
            category = "Refrescos",
            unit = ProductUnit.PIECE,
            quantity = Quantity.pieces(2),
            unitPrice = Money.fromCents(1850),
            taxRate = 0.16,
            total = Money.fromCents(3700)
        )

        val uiState = SalesUiState(
            cartLines = listOf(cartLine),
            total = Money.fromCents(3700),
            activeSession = CashSessionSummary(
                sessionId = "sess-1",
                registerId = "caja-1",
                registerName = "Caja Principal",
                cashierId = "usr-1",
                cashierName = "Don Pepe",
                openedAt = 1760000000L,
                initialFundCents = 50000L,
                salesCashCents = 7291L,
                creditPaymentsCashCents = 0L,
                cashInCents = 0L,
                cashOutCents = 0L,
                refundsCashCents = 0L,
                expectedCashCents = 57291L,
                status = "OPEN"
            ),
            connectionState = ServerConnectionState.CONNECTED,
            lastCompletedSale = mockCompletedSale
        )

        val panelState = uiState.toBottomCheckoutPanelState()

        assertEquals("Ticket #1042", panelState.lastSaleFolio)
        assertEquals("$72.91", panelState.lastSaleTotalFormatted)
        assertEquals("$100.00", panelState.lastSaleReceivedFormatted)
        assertEquals("$27.09", panelState.lastSaleChangeFormatted)
        assertTrue(panelState.hasLastSale)
        assertEquals("$37.00", panelState.currentTotalFormatted)
        assertTrue(panelState.canCheckout)
        assertTrue(panelState.canDelete)
    }

    @Test
    fun testCycleNextTicketInViewModel() {
        val viewModel = SalesViewModel()
        assertEquals("ticket-1", viewModel.uiState.value.activeTicketId)
        assertEquals(1, viewModel.uiState.value.tickets.size)

        // Crear segundo ticket
        viewModel.newTicket()
        assertEquals(2, viewModel.uiState.value.tickets.size)
        val secondTicketId = viewModel.uiState.value.activeTicketId
        assertNotEquals("ticket-1", secondTicketId)

        // Ciclar con F5: debe regresar al primer ticket
        viewModel.cycleNextTicket()
        assertEquals("ticket-1", viewModel.uiState.value.activeTicketId)

        // Ciclar nuevamente: debe regresar al segundo ticket
        viewModel.cycleNextTicket()
        assertEquals(secondTicketId, viewModel.uiState.value.activeTicketId)
    }

    @Test
    fun testHoldTicketAndNew() {
        val viewModel = SalesViewModel()
        assertEquals(1, viewModel.uiState.value.tickets.size)

        viewModel.holdTicketAndNew()
        assertEquals(2, viewModel.uiState.value.tickets.size)
        assertTrue(viewModel.uiState.value.successMessage?.contains("en espera") == true)
    }

    @Test
    fun testOpenCashDrawerAndReprintMessages() {
        val viewModel = SalesViewModel()

        viewModel.openCashDrawer()
        assertTrue(viewModel.uiState.value.successMessage?.contains("cajón de dinero") == true)

        viewModel.reprintLastTicket()
        assertTrue(viewModel.uiState.value.errorMessage?.contains("No hay ninguna venta") == true)
    }

    @Test
    fun testCancelCurrentTicketDestroysActiveTicket() {
        val viewModel = SalesViewModel()
        // Crear un segundo ticket
        viewModel.newTicket()
        assertEquals(2, viewModel.uiState.value.tickets.size)
        val activeId = viewModel.uiState.value.activeTicketId

        // Simular onCancelCurrentTicket: elimina por completo el ticket en foco
        viewModel.closeTicket(activeId)

        // Debe quedar 1 solo ticket y el ticket cancelado ya no debe existir en memoria
        assertEquals(1, viewModel.uiState.value.tickets.size)
        assertFalse(viewModel.uiState.value.tickets.any { it.id == activeId })
    }

    @Test
    fun testOpenParkSaleDialogState() {
        val viewModel = SalesViewModel()
        assertFalse(viewModel.uiState.value.isShowingParkedSalesListDialog)

        // When cart is empty, openParkSaleDialog opens the list of parked sales
        viewModel.openParkSaleDialog()
        assertTrue(viewModel.uiState.value.isShowingParkedSalesListDialog)

        viewModel.closeParkedSalesListDialog()
        assertFalse(viewModel.uiState.value.isShowingParkedSalesListDialog)
    }
}

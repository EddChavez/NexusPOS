package mx.nexuspos.desktop.sales

import mx.nexuspos.client.features.sales.*
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.test.*

class CaptureBarStateTest {

    @Test
    fun testToCaptureBarStateDefaultProjection() {
        val uiState = SalesUiState()
        val captureBarState = uiState.toCaptureBarState()

        assertEquals("ticket-1", captureBarState.activeTicketId)
        assertEquals(1, captureBarState.tickets.size)
        assertTrue(captureBarState.tickets.first().isActive)
        assertEquals(0, captureBarState.pendingTicketsCount)
        assertEquals("Público en General", captureBarState.currentCustomerName)
        assertEquals(ScannerStatus.READY, captureBarState.scannerStatus)
        assertFalse(captureBarState.isDeleteEnabled)
    }

    @Test
    fun testPendingTicketsCalculation() {
        val sampleLine = CartLine(
            id = "c1",
            productId = "p1",
            presentationId = "pr1",
            barcode = "75010001",
            name = "Coca Cola 600ml",
            unit = ProductUnit.PIECE,
            quantity = Quantity.ONE,
            unitPrice = Money.fromCents(1800),
            taxRate = 0.16,
            total = Money.fromCents(1800)
        )

        val ticket1 = TicketState(
            id = "t1",
            title = "Ticket 1",
            cartLines = listOf(sampleLine),
            total = Money.fromCents(1800)
        )

        val ticket2 = TicketState(
            id = "t2",
            title = "Ticket 2",
            cartLines = emptyList(),
            total = Money.ZERO
        )

        // When ticket 2 is active, ticket 1 has items and is pending
        val uiState = SalesUiState(
            tickets = listOf(ticket1, ticket2),
            activeTicketId = "t2",
            cartLines = emptyList(),
            total = Money.ZERO
        )

        val captureBarState = uiState.toCaptureBarState()
        assertEquals(1, captureBarState.pendingTicketsCount)
        assertEquals(2, captureBarState.tickets.size)
        assertFalse(captureBarState.tickets[0].isActive)
        assertTrue(captureBarState.tickets[1].isActive)
        assertEquals("$18.00", captureBarState.tickets[0].totalFormatted)
    }

    @Test
    fun testCustomerNameProjection() {
        val cust = CustomerDto(
            id = "cust-042",
            name = "Don Ramón Valdés",
            creditLimitCents = 100000L,
            currentBalanceCents = 25000L,
            availableCreditCents = 75000L
        )

        val uiState = SalesUiState(selectedCustomer = cust)
        val captureBarState = uiState.toCaptureBarState()

        assertEquals("Don Ramón Valdés", captureBarState.currentCustomerName)
        assertEquals("0042", captureBarState.currentCustomerCode)
    }

    @Test
    fun testMultiTicketCreationAndSwitchingInViewModel() {
        val vm = SalesViewModel()

        // Initially 1 ticket
        assertEquals(1, vm.uiState.value.tickets.size)
        assertEquals("ticket-1", vm.uiState.value.activeTicketId)

        // Add generic product to ticket 1
        vm.addGenericProduct("Refresco", 2000L)
        assertEquals(1, vm.uiState.value.cartLines.size)
        assertEquals(Money.fromCents(2000), vm.uiState.value.total)

        // Create Ticket 2
        vm.newTicket()
        assertEquals(2, vm.uiState.value.tickets.size)
        assertNotEquals("ticket-1", vm.uiState.value.activeTicketId)
        assertTrue(vm.uiState.value.cartLines.isEmpty())
        assertEquals(Money.ZERO, vm.uiState.value.total)

        // Ticket 1 in list still has the 2000 cents
        val t1 = vm.uiState.value.tickets.first { it.id == "ticket-1" }
        assertEquals(1, t1.cartLines.size)
        assertEquals(Money.fromCents(2000), t1.total)

        // Switch back to ticket 1
        vm.selectTicket("ticket-1")
        assertEquals("ticket-1", vm.uiState.value.activeTicketId)
        assertEquals(1, vm.uiState.value.cartLines.size)
        assertEquals(Money.fromCents(2000), vm.uiState.value.total)
    }

    @Test
    fun testCloseTicketBehavior() {
        val vm = SalesViewModel()
        vm.newTicket() // now 2 tickets
        assertEquals(2, vm.uiState.value.tickets.size)

        val ticket2Id = vm.uiState.value.activeTicketId
        vm.closeTicket(ticket2Id)

        // Now 1 ticket left, which is ticket 1
        assertEquals(1, vm.uiState.value.tickets.size)
        assertEquals("ticket-1", vm.uiState.value.activeTicketId)

        // Closing the only remaining ticket clears it without deleting it
        vm.addGenericProduct("Chicle", 200L)
        assertEquals(1, vm.uiState.value.cartLines.size)

        vm.closeTicket("ticket-1")
        assertEquals(1, vm.uiState.value.tickets.size)
        assertTrue(vm.uiState.value.cartLines.isEmpty())
    }

    @Test
    fun testDeleteCartLineAndWholesale() {
        val vm = SalesViewModel()
        vm.addGenericProduct("Sabritas", 1800L)
        assertEquals(1, vm.uiState.value.cartLines.size)

        // Apply wholesale: 10% discount on 1800 -> 1620
        vm.onApplyWholesalePrice()
        val line = vm.uiState.value.cartLines.first()
        assertEquals(Money.fromCents(1620), line.total)

        // Delete line
        vm.deleteActiveCartLine()
        assertTrue(vm.uiState.value.cartLines.isEmpty())
        assertEquals(Money.ZERO, vm.uiState.value.total)
    }
}

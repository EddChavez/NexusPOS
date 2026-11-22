package mx.nexuspos.desktop.sales

import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOs
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import kotlin.test.*

class SalesV2FeaturesTest {

    @Test
    fun testMultiTicketNoteAndRename() {
        val vm = SalesViewModel()

        // 1. Verificar ticket inicial
        val initialTicket = vm.uiState.value.tickets.first()
        assertEquals("Ticket 1", initialTicket.title)

        // 2. Renombrar ticket a "Pedido María"
        vm.renameTicket(initialTicket.id, "Pedido María")
        assertEquals("Pedido María", vm.uiState.value.tickets.first().title)

        // 3. Establecer nota en ticket
        vm.setTicketNote("Entrega a domicilio 3:00 PM")
        assertEquals("Entrega a domicilio 3:00 PM", vm.uiState.value.activeTicketNote)
        assertEquals("Entrega a domicilio 3:00 PM", vm.uiState.value.tickets.first().note)

        // 4. Crear nuevo ticket
        vm.newTicket()
        assertEquals(2, vm.uiState.value.tickets.size)
        assertEquals("", vm.uiState.value.activeTicketNote)

        // 5. Regresar al primer ticket y verificar que la nota y título persisten
        vm.selectTicket(initialTicket.id)
        assertEquals("Pedido María", vm.uiState.value.tickets.first { it.id == initialTicket.id }.title)
        assertEquals("Entrega a domicilio 3:00 PM", vm.uiState.value.activeTicketNote)
    }

    @Test
    fun testUpdateCartLinePriceTier() {
        val vm = SalesViewModel()
        val prod = ProductDto(
            id = "prod-res",
            sku = "CARNE-01",
            name = "Carne Molida De Res",
            departmentName = "Carnicería",
            baseUnit = "KG"
        )
        val pres = ProductPresentationDto(
            id = "pres-res",
            productId = "prod-res",
            barcode = "75012345",
            name = "Kg",
            factorMillis = 1000L,
            priceCents = 16000L // $160.00 / Kg
        )

        // Agregar 1.5 Kg -> $240.00
        vm.addProductPresentation(prod, pres, Quantity.fromDouble(1.5))
        val line = vm.uiState.value.cartLines.first()
        assertEquals(Money.fromCents(24000L), line.total)

        // Conmutar a Precio 2 (Mayoreo - $144.00)
        val wholesalePrice = Money.fromCents(14400L)
        vm.updateCartLinePrice(line.id, wholesalePrice)

        val updatedLine = vm.uiState.value.cartLines.first()
        assertEquals(wholesalePrice, updatedLine.unitPrice)
        // 1.5 * 144.00 = 216.00
        assertEquals(Money.fromCents(21600L), updatedLine.total)
        assertEquals(Money.fromCents(21600L), vm.uiState.value.total)
    }

    @Test
    fun testRecordManualDrawerOpenGeneratesAuditToast() {
        val vm = SalesViewModel()
        assertNull(vm.uiState.value.auditToastMessage)

        vm.recordManualDrawerOpen("Don Pepe")
        val msg = vm.uiState.value.auditToastMessage
        assertNotNull(msg)
        assertTrue(msg.contains("Cajón de dinero abierto"))
        assertTrue(msg.contains("Don Pepe"))

        vm.clearAuditToast()
        assertNull(vm.uiState.value.auditToastMessage)
    }

    @Test
    fun testPosActionShortcutLabelsByOs() {
        // En Windows
        assertEquals("F12", PosAction.CHECKOUT.getLabel(HostOs.WINDOWS_LINUX))
        assertEquals("F5", PosAction.SWITCH_TICKET.getLabel(HostOs.WINDOWS_LINUX))
        assertEquals("Ctrl+K / Alt+C", PosAction.OPEN_DRAWER.getLabel(HostOs.WINDOWS_LINUX))

        // En macOS
        assertEquals("⌘↵", PosAction.CHECKOUT.getLabel(HostOs.MACOS))
        assertEquals("⌘5", PosAction.SWITCH_TICKET.getLabel(HostOs.MACOS))
        assertEquals("⌘K", PosAction.OPEN_DRAWER.getLabel(HostOs.MACOS))
        assertEquals("⌘R", PosAction.FAST_SALE.getLabel(HostOs.MACOS))
    }

    @Test
    fun testTicketCancellationFlowWithAudit() {
        val vm = SalesViewModel()
        val prod = ProductDto(
            id = "prod-coca",
            sku = "COCA-600",
            name = "Coca Cola 600ml",
            departmentName = "Bebidas",
            baseUnit = "PZA"
        )
        val pres = ProductPresentationDto(
            id = "pres-coca",
            productId = "prod-coca",
            barcode = "75010553",
            name = "Pieza",
            factorMillis = 1000L,
            priceCents = 1800L
        )

        // 1. Ticket vacío: cerrarlo no requiere confirmación ni motivo
        val ticket1Id = vm.uiState.value.activeTicketId
        vm.requestCloseTicket(ticket1Id)
        assertFalse(vm.uiState.value.isShowingCancelTicketDialog)

        // 2. Agregar productos a una nueva venta
        vm.newTicket()
        val ticket2Id = vm.uiState.value.activeTicketId
        vm.addProductPresentation(prod, pres, Quantity.ONE)
        assertEquals(1, vm.uiState.value.cartLines.size)

        // 3. Intentar cerrar ticket con productos: debe solicitar confirmación (Requisito 4.5)
        vm.requestCloseTicket(ticket2Id)
        assertTrue(vm.uiState.value.isShowingCancelTicketDialog)
        assertEquals(ticket2Id, vm.uiState.value.ticketPendingCancelId)

        // 4. Confirmar cancelación con motivo
        vm.confirmCancelTicket(ticket2Id, "Cliente desistió de la compra")
        assertFalse(vm.uiState.value.isShowingCancelTicketDialog)
        assertNull(vm.uiState.value.ticketPendingCancelId)
        val toast = vm.uiState.value.auditToastMessage
        assertNotNull(toast)
        assertTrue(toast.contains("Venta cancelada"))
        assertTrue(toast.contains("Cliente desistió"))
    }

    @Test
    fun testReweighCartLineWithScaleReading() {
        val vm = SalesViewModel()

        val prod = ProductDto(
            id = "prod-manzana",
            sku = "MANZANA",
            name = "Manzana Red Delicious",
            departmentName = "Frutas",
            baseUnit = "KG"
        )
        val pres = ProductPresentationDto(
            id = "pres-manzana",
            productId = "prod-manzana",
            barcode = "7509999",
            name = "Kg",
            factorMillis = 1000L,
            priceCents = 4500L
        )

        // Agregar inicialmente 1.0 Kg
        vm.addProductPresentation(prod, pres, Quantity.fromDouble(1.0))
        val initialLine = vm.uiState.value.cartLines.first()
        assertEquals(Quantity.fromDouble(1.0), initialLine.quantity)

        // Simular que el cliente coloca más manzanas en la báscula (2.350 Kg)
        vm.scaleReadingProvider = { ScaleReading("2.350 kg", ScaleStatus.STABLE) }
        vm.reweighCartLine(initialLine)

        // Verificar que la cantidad en el carrito se actualizó desde la báscula RS-232 (Requisito 4.3)
        val reweighedLine = vm.uiState.value.cartLines.first()
        assertEquals(Quantity.fromDouble(2.350), reweighedLine.quantity)
        assertNotNull(vm.uiState.value.successMessage)
        assertTrue(vm.uiState.value.successMessage!!.contains("RS-232"))
    }

    @Test
    fun testCustomerSelectionWithLoyaltyBalance() {
        val vm = SalesViewModel()
        val customer = CustomerDto(
            id = "cust-1",
            folio = "CTE-001",
            name = "Juan Pérez",
            phone = "5512345678",
            creditLimitCents = 500000L, // $5,000 crédito
            currentBalanceCents = 100000L, // $1,000 saldo usado
            availableCreditCents = 400000L,
            loyaltyBalanceCents = 35000L  // $350.00 monedero
        )

        vm.selectCustomer(customer)
        assertEquals("Juan Pérez", vm.uiState.value.selectedCustomer?.name)
        assertEquals(35000L, vm.uiState.value.selectedCustomer?.loyaltyBalanceCents)
        assertEquals(400000L, vm.uiState.value.selectedCustomer?.availableCreditCents)
    }
}

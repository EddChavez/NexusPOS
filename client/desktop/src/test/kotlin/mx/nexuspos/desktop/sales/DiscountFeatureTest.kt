package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import kotlin.test.*

class DiscountFeatureTest {

    private fun createCartLine(id: String, name: String, priceCents: Long, qtyPieces: Long = 1): CartLine {
        val qty = Quantity.pieces(qtyPieces)
        val unitPrice = Money.fromCents(priceCents)
        val total = unitPrice * qtyPieces
        return CartLine(
            id = id,
            productId = "prod-$id",
            presentationId = null,
            barcode = "123456",
            name = name,
            unit = ProductUnit.PIECE,
            quantity = qty,
            unitPrice = unitPrice,
            taxRate = 0.0,
            total = total
        )
    }

    @Test
    fun testApplyLineDiscountDirectlyWhenUnderThreshold() {
        val vm = SalesViewModel(scope = CoroutineScope(Dispatchers.Unconfined))
        val line1 = createCartLine("1", "Galletas Oreo", 2000L) // $20.00
        vm.addGenericProduct("Galletas Oreo", 2000L) // Line added

        val lineId = vm.uiState.value.cartLines.first().id

        // Apply 10% discount (under or equal to 10% threshold)
        vm.applyDiscount(targetLineId = lineId, discountPercent = 10.0, discountCents = 0L)

        val updatedLine = vm.uiState.value.cartLines.first()
        assertEquals(1800L, updatedLine.total.cents) // $18.00
        assertEquals(200L, updatedLine.discountCents) // $2.00
        assertEquals(10.0, updatedLine.discountPercent)
        assertEquals(1800L, vm.uiState.value.total.cents)
        assertEquals(200L, vm.uiState.value.discount.cents)
    }

    @Test
    fun testApplyDiscountExceedingThresholdRequiresSupervisorForCashier() {
        val vm = SalesViewModel(scope = CoroutineScope(Dispatchers.Unconfined))
        vm.addGenericProduct("Aceite Nutrioli", 5000L) // $50.00
        val lineId = vm.uiState.value.cartLines.first().id

        var elevationAction: SupervisorAction.ApplyDiscount? = null
        vm.onDiscountSupervisorRequired = { action ->
            elevationAction = action
        }

        // Apply 20% discount as CASHIER -> should trigger supervisor elevation
        vm.applyDiscount(targetLineId = lineId, discountPercent = 20.0, discountCents = 0L, isSupervisorApproved = false)

        assertNotNull(elevationAction)
        assertEquals(lineId, elevationAction?.lineId)
        assertEquals(20.0, elevationAction?.discountPercent)

        // Line should NOT be discounted yet
        assertEquals(5000L, vm.uiState.value.cartLines.first().total.cents)

        // Simulate supervisor PIN entered successfully
        vm.applyDiscount(targetLineId = lineId, discountPercent = 20.0, discountCents = 0L, isSupervisorApproved = true)

        // Now line is discounted
        val approvedLine = vm.uiState.value.cartLines.first()
        assertEquals(4000L, approvedLine.total.cents) // $40.00
        assertEquals(1000L, approvedLine.discountCents) // $10.00
    }

    @Test
    fun testApplyGlobalDiscountToAllLines() {
        val vm = SalesViewModel(scope = CoroutineScope(Dispatchers.Unconfined))
        vm.addGenericProduct("Articulo A", 10000L) // $100.00
        vm.addGenericProduct("Articulo B", 20000L) // $200.00
        assertEquals(30000L, vm.uiState.value.total.cents) // $300.00

        // Apply 10% global discount
        vm.applyDiscount(targetLineId = null, discountPercent = 10.0, discountCents = 0L)

        val lines = vm.uiState.value.cartLines
        assertEquals(9000L, lines[0].total.cents)  // $90.00
        assertEquals(18000L, lines[1].total.cents) // $180.00
        assertEquals(27000L, vm.uiState.value.total.cents) // $270.00
        assertEquals(3000L, vm.uiState.value.discount.cents) // $30.00
    }

    @Test
    fun testEscPosReceiptPrintingWithDiscount() {
        val printer = EscPosReceiptPrinter(columnWidth = 40)
        val mockSale = SaleResponse(
            id = "sale-999",
            folio = 999L,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            status = "COMPLETED",
            subtotalCents = 18000L,
            taxCents = 0L,
            totalCents = 18000L,
            discountCents = 2000L, // $20.00 discount
            lines = listOf(
                SaleLineItemResponse(
                    id = "l1",
                    productId = "p1",
                    presentationId = null,
                    description = "Cereal Chocozucaradas",
                    quantityMillis = 1000L,
                    unitPriceCents = 20000L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 18000L,
                    discountCents = 2000L,
                    discountPercent = 10.0
                )
            ),
            payments = listOf(
                SalePaymentResponse(
                    id = "pay1",
                    method = "CASH",
                    amountCents = 18000L,
                    receivedCents = 20000L,
                    changeCents = 2000L
                )
            ),
            createdAt = 1760000000L
        )

        val receiptText = printer.formatSaleReceiptText(mockSale, SettingsDto(storeName = "Mi Tiendita"))
        assertTrue(receiptText.contains("Cereal Chocozucaradas"))
        assertTrue(receiptText.contains("Desc. 10%: -$20.00"))
        assertTrue(receiptText.contains("SUBTOTAL:"))
        assertTrue(receiptText.contains("DESCUENTO:"))
        assertTrue(receiptText.contains("-$20.00"))
        assertTrue(receiptText.contains("TOTAL:"))
        assertTrue(receiptText.contains("$180.00"))
    }
}

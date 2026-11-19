package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import mx.nexuspos.desktop.hardware.PaperWidth
import kotlin.test.*

class SaleRefundFeatureTest {

    private fun createSampleSale(): SaleResponse {
        return SaleResponse(
            id = "sale-100",
            folio = 1050L,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            customerName = "Doña María",
            status = "COMPLETED",
            subtotalCents = 3190L,
            taxCents = 510L,
            totalCents = 3700L,
            discountCents = 0L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "line-1",
                    productId = "prod-1",
                    presentationId = "pres-1",
                    description = "Coca-Cola 600ml",
                    quantityMillis = 2000L,
                    unitPriceCents = 1850L,
                    taxRate = 0.16,
                    taxCents = 510L,
                    totalCents = 3700L,
                    refundedQuantityMillis = 0L
                )
            ),
            payments = listOf(
                SalePaymentResponse(
                    id = "pay-1",
                    method = "CASH",
                    amountCents = 3700L,
                    receivedCents = 4000L,
                    changeCents = 300L
                )
            ),
            createdAt = 1700000000000L
        )
    }

    private fun createSampleRefund(): SaleRefundResponse {
        return SaleRefundResponse(
            id = "ref-1",
            folio = 12L,
            saleId = "sale-100",
            saleFolio = 1050L,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            authorizedByName = "Encargado Carlos",
            reason = "DEFECTO",
            notes = "Botella con fuga de gas",
            refundMethod = "CASH",
            subtotalCents = 1595L,
            taxCents = 255L,
            totalCents = 1850L,
            lines = listOf(
                SaleRefundLineResponse(
                    id = "refl-1",
                    saleLineId = "line-1",
                    productId = "prod-1",
                    presentationId = "pres-1",
                    description = "Coca-Cola 600ml",
                    quantityMillis = 1000L,
                    unitPriceCents = 1850L,
                    taxRate = 0.16,
                    taxCents = 255L,
                    totalCents = 1850L,
                    reenterStock = true
                )
            ),
            createdAt = 1700000010000L
        )
    }

    @Test
    fun testPromptRefundSaleAndDismiss() {
        val vm = SalesViewModel(scope = CoroutineScope(Dispatchers.Unconfined))
        val sale = createSampleSale()

        assertFalse(vm.uiState.value.isShowingRefundDialog)
        assertNull(vm.uiState.value.salePendingRefund)

        vm.promptRefundSale(sale)

        assertTrue(vm.uiState.value.isShowingRefundDialog)
        assertEquals("sale-100", vm.uiState.value.salePendingRefund?.id)

        vm.dismissRefundDialog()

        assertFalse(vm.uiState.value.isShowingRefundDialog)
        assertNull(vm.uiState.value.salePendingRefund)
    }

    @Test
    fun testCashierRoleRequiresSupervisorElevationForRefund() {
        val vm = SalesViewModel(scope = CoroutineScope(Dispatchers.Unconfined))
        vm.setActiveCashier(userId = "usr-cajero-1", name = "Juan Pérez", role = "CASHIER", permissions = emptyList())

        var elevatedAction: SupervisorAction.RefundSale? = null
        vm.onRefundSupervisorRequired = { action ->
            elevatedAction = action
        }

        val request = CreateSaleRefundRequest(
            cashSessionId = "sess-1",
            cashierUserId = "usr-cajero-1",
            reason = "DEFECTO",
            lines = listOf(
                RefundLineItemRequest(saleLineId = "line-1", quantityMillis = 1000L, reenterStock = true)
            )
        )

        // Attempt refund without supervisor approval
        vm.executeRefundSale(saleId = "sale-100", request = request, isSupervisorApproved = false)

        assertNotNull(elevatedAction)
        assertEquals("sale-100", elevatedAction?.saleId)
        assertEquals("DEFECTO", elevatedAction?.request?.reason)
    }

    @Test
    fun testEscPosReceiptPrinterSaleRefundFormat() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_80)
        val refund = createSampleRefund()
        val settings = SettingsDto(storeName = "Abarrotes Don Pepe", storeAddress = "Av. Central #123")

        val text = printer.formatSaleRefundText(refund, settings)

        assertTrue(text.contains("NOTA DE DEVOLUCIÓN"))
        assertTrue(text.contains("Nota Devolución: #12"))
        assertTrue(text.contains("Ticket Original: #1050"))
        assertTrue(text.contains("Coca-Cola 600ml"))
        assertTrue(text.contains("TOTAL REEMBOLSADO:"))
        assertTrue(text.contains("$18.50"))
        assertTrue(text.contains("Motivo: DEFECTO"))
        assertTrue(text.contains("Autorizó (Sup): Encargado Carlos"))

        val bytes = printer.generateSaleRefundBytes(refund, settings)
        assertTrue(bytes.isNotEmpty())
        // ESC @ initialization check
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }
}

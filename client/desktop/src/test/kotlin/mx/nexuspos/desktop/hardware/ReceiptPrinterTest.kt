package mx.nexuspos.desktop.hardware

import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import kotlin.test.Test
import kotlin.test.assertTrue

class ReceiptPrinterTest {

    private val printer = EscPosReceiptPrinter(columnWidth = 40)

    @Test
    fun testFormatCashSaleReceipt() {
        val sale = SaleResponse(
            id = "sale-101",
            folio = 101,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            customerName = null,
            status = "COMPLETED",
            subtotalCents = 3500L,
            taxCents = 0L,
            totalCents = 3500L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "line-1",
                    productId = "prod-coca",
                    presentationId = "pres-1",
                    description = "Coca-Cola 600ml",
                    quantityMillis = 1000L,
                    unitPriceCents = 1800L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 1800L
                ),
                SaleLineItemResponse(
                    id = "line-2",
                    productId = "prod-huevo",
                    presentationId = "pres-2",
                    description = "Huevo Blanco a Granel",
                    quantityMillis = 500L,
                    unitPriceCents = 3400L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 1700L
                )
            ),
            payments = listOf(
                SalePaymentResponse(
                    id = "pay-1",
                    method = "CASH",
                    amountCents = 3500L,
                    receivedCents = 5000L,
                    changeCents = 1500L
                )
            ),
            createdAt = 1713139200000L
        )

        val settings = SettingsDto(
            storeName = "Abarrotes Don Pepe",
            storeAddress = "Av. Hidalgo 123",
            storePhone = "555-987-6543"
        )

        val text = printer.formatSaleReceiptText(sale, settings, isReprint = false)

        assertTrue(text.contains("Abarrotes Don Pepe"))
        assertTrue(text.contains("Folio: #101"))
        assertTrue(text.contains("Coca-Cola 600ml"))
        assertTrue(text.contains("Huevo Blanco a Granel"))
        assertTrue(text.contains("TOTAL:"))
        assertTrue(text.contains("$35.00"))
        assertTrue(text.contains("RECIBIDO:"))
        assertTrue(text.contains("$50.00"))
        assertTrue(text.contains("CAMBIO:"))
        assertTrue(text.contains("$15.00"))

        val bytes = printer.generateEscPosBytes(text)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size > 50)
    }

    @Test
    fun testFormatReprintReceiptHasBanner() {
        val sale = SaleResponse(
            id = "sale-102",
            folio = 102,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            status = "COMPLETED",
            subtotalCents = 2000L,
            taxCents = 0L,
            totalCents = 2000L,
            lines = emptyList(),
            payments = listOf(SalePaymentResponse("pay-1", "CASH", 2000L, 2000L, 0L)),
            createdAt = 1713139200000L
        )

        val text = printer.formatSaleReceiptText(sale, isReprint = true)
        assertTrue(text.contains("*** COPIA DE REIMPRESIÓN ***"))
    }

    @Test
    fun testFormatCreditSaleReceipt() {
        val sale = SaleResponse(
            id = "sale-103",
            folio = 103,
            cashSessionId = "sess-1",
            cashierName = "Don Pepe",
            customerName = "Doña Rosa",
            customerRemainingCreditCents = 35000L,
            status = "COMPLETED",
            subtotalCents = 15000L,
            taxCents = 0L,
            totalCents = 15000L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "line-1",
                    productId = null,
                    presentationId = null,
                    description = "Despensa Semanal",
                    quantityMillis = 1000L,
                    unitPriceCents = 15000L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 15000L
                )
            ),
            payments = listOf(
                SalePaymentResponse(
                    id = "pay-credit",
                    method = "CREDIT",
                    amountCents = 15000L,
                    receivedCents = 0L,
                    changeCents = 0L
                )
            ),
            createdAt = 1713139200000L
        )

        val text = printer.formatSaleReceiptText(sale)

        assertTrue(text.contains("VENTA A CRÉDITO / FIADO"))
        assertTrue(text.contains("Doña Rosa"))
        assertTrue(text.contains("Cargo a Cuenta:"))
        assertTrue(text.contains("$150.00"))
        assertTrue(text.contains("Crédito Disponible:"))
        assertTrue(text.contains("$350.00"))
        assertTrue(text.contains("Firma del Cliente:"))
    }

    @Test
    fun testFormatCashCutSummary() {
        val cut = CashSessionSummary(
            sessionId = "sess-1",
            registerId = "caja-1",
            registerName = "Caja Principal",
            cashierId = "user-1",
            cashierName = "Don Pepe",
            openedAt = 1713135600000L,
            closedAt = 1713164400000L,
            initialFundCents = 50000L,     // $500.00
            salesCashCents = 345000L,       // $3,450.00
            creditPaymentsCashCents = 20000L, // $200.00
            cashInCents = 0L,
            cashOutCents = 15000L,          // $150.00 (retiro proveedor)
            refundsCashCents = 0L,
            expectedCashCents = 400000L,    // $4,000.00
            countedCashCents = 400500L,     // $4,005.00
            differenceCents = 500L,         // +$5.00
            status = "CLOSED"
        )

        val text = printer.formatCashCutText(cut, isFinalZCut = true)

        assertTrue(text.contains("CORTE Z (CIERRE OFICIAL)"))
        assertTrue(text.contains("Fondo Inicial:"))
        assertTrue(text.contains("$500.00"))
        assertTrue(text.contains("Ventas en Efectivo:"))
        assertTrue(text.contains("$3450.00"))
        assertTrue(text.contains("Abonos de Crédito:"))
        assertTrue(text.contains("$200.00"))
        assertTrue(text.contains("Retiros / Salidas:"))
        assertTrue(text.contains("$150.00"))
        assertTrue(text.contains("EFECTIVO ESPERADO:"))
        assertTrue(text.contains("$4000.00"))
        assertTrue(text.contains("Efectivo Contado:"))
        assertTrue(text.contains("$4005.00"))
        assertTrue(text.contains("DIFERENCIA (Arqueo):"))
        assertTrue(text.contains("+$5.00"))
        assertTrue(text.contains("Firma del Cajero:"))
    }
}

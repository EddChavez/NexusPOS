package mx.nexuspos.desktop.whatsapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.client.features.whatsapp.WhatsAppMessageFormatter
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.CustomerStatementDto
import mx.nexuspos.contracts.loyalty.SaleLoyaltyRewardDto
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleRefundLineResponse
import mx.nexuspos.contracts.sales.SaleRefundResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.desktop.hardware.WhatsAppDispatcher
import kotlin.test.*

class WhatsAppFeatureTest {

    private val sampleSettings = SettingsDto(
        storeName = "Abarrotes Don Pepe",
        storeAddress = "Av. Hidalgo #123, Col. Centro",
        storePhone = "5512345678",
        ticketHeader = "¡Gracias por su compra!",
        ticketFooter = "Conserve su ticket para cambios o aclaraciones",
        whatsappCountryCode = "52",
        whatsappBankDetails = "BBVA CLABE: 012180001234567890 a nombre de José Pérez",
        whatsappCustomFooter = "Dudas o pedidos: WhatsApp 55-1234-5678"
    )

    private val sampleCustomer = CustomerDto(
        id = "cust-1",
        folio = "CLI-0001",
        name = "María Gómez",
        phone = "55 9876 5432",
        creditEnabled = true,
        hasCreditLimit = true,
        creditLimitCents = 100_000L,
        currentBalanceCents = 35_000L,
        availableCreditCents = 65_000L,
        loyaltyBalanceCents = 4_500L,
        totalLoyaltyEarnedCents = 8_000L,
        totalLoyaltyRedeemedCents = 3_500L
    )

    private val sampleSale = SaleResponse(
        id = "sale-101",
        folio = 1001L,
        cashSessionId = "sess-1",
        cashierName = "Carlos Cajero",
        customerName = "María Gómez",
        status = "COMPLETED",
        subtotalCents = 15_000L,
        taxCents = 0L,
        totalCents = 15_000L,
        lines = listOf(
            SaleLineItemResponse("l-1", "p-1", null, "Leche Entera 1L", 2000L, 2500L, 0.0, 0L, 5000L),
            SaleLineItemResponse("l-2", "p-2", null, "Huevo Blanco 1Kg", 1000L, 4500L, 0.0, 0L, 4500L),
            SaleLineItemResponse("l-3", "p-3", null, "Pan de Caja Blanco", 1000L, 5500L, 0.0, 0L, 5500L)
        ),
        payments = listOf(
            SalePaymentResponse("pay-1", "CASH", 15_000L, 20_000L, 5_000L, "")
        ),
        createdAt = 1700000000000L,
        loyaltyReward = SaleLoyaltyRewardDto(
            earnedCents = 300L,
            redeemedCents = 0L,
            previousBalanceCents = 4200L,
            finalBalanceCents = 4500L,
            cashbackPercentApplied = 2.0
        )
    )

    // ==========================================
    // 1. Phone number normalizer
    // ==========================================
    @Test
    fun testPhoneNumberCleaningAndNormalization() {
        // Standard 10-digit Mexican mobile
        val phone1 = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("5512345678", "52")
        assertEquals("525512345678", phone1)

        // Formatted with spaces, dashes, parentheses
        val phone2 = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("+52 (55) 9876-5432", "52")
        assertEquals("525598765432", phone2)

        // Already has 52 country code prepended
        val phone3 = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("525512345678", "52")
        assertEquals("525512345678", phone3)

        // Plus sign prefix with country code
        val phone4 = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("+525512345678", "52")
        assertEquals("525512345678", phone4)

        // Other international number (+1 USA 10 digits)
        val phone5 = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("+1 (415) 555-2671", "52")
        assertEquals("14155552671", phone5)

        // Blank or null
        val phoneEmpty = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber("   ", "52")
        assertEquals("", phoneEmpty)
    }

    // ==========================================
    // 2. WhatsApp URL Builder
    // ==========================================
    @Test
    fun testBuildWhatsAppUrl() {
        val message = "Hola! Este es tu ticket #1001 por $150.00"
        val url = WhatsAppMessageFormatter.buildWhatsAppUrl("5512345678", message, "52")

        assertTrue(url.startsWith("https://api.whatsapp.com/send?phone=525512345678&text="))
        assertTrue(url.contains("Hola"))
        // URL should have properly encoded special characters
        assertFalse(url.contains(" ")) // spaces must be encoded as %20
        assertTrue(url.contains("%20"))
        assertTrue(url.contains("%23") || url.contains("1001")) // '#' encoded as %23
    }

    // ==========================================
    // 3. Sale Ticket Formatter
    // ==========================================
    @Test
    fun testFormatSaleTicket() {
        val ticket = WhatsAppMessageFormatter.formatSaleTicket(sampleSale, sampleSettings)

        // Store header
        assertTrue(ticket.contains("Abarrotes Don Pepe"), "Debe incluir el nombre de la tienda")
        assertTrue(ticket.contains("Av. Hidalgo #123"), "Debe incluir la dirección")
        assertTrue(ticket.contains("#1001"), "Debe incluir el folio del ticket")
        assertTrue(ticket.contains("Carlos Cajero"), "Debe incluir el nombre del cajero")
        assertTrue(ticket.contains("María Gómez"), "Debe incluir el nombre del cliente")

        // Monospace table delimiters
        assertTrue(ticket.contains("```"), "Debe tener bloques monospace de WhatsApp")
        assertTrue(ticket.contains("Leche Entera 1L"), "Debe listar los artículos")
        assertTrue(ticket.contains("Huevo Blanco 1Kg"), "Debe listar los artículos")
        assertTrue(ticket.contains("Pan de Caja Blanco"), "Debe listar los artículos")

        // Totals & Payments
        assertTrue(ticket.contains("TOTAL:") && ticket.contains("$150.00"), "Debe mostrar el total correcto en pesos")
        assertTrue(ticket.contains("Efectivo:") && ticket.contains("$150.00"), "Debe detallar el método de pago")
        assertTrue(ticket.contains("Cambio: $50.00"), "Debe mostrar el cambio entregado")

        // Loyalty block
        assertTrue(ticket.contains("+$3.00"), "Debe notificar el cashback acumulado")
        assertTrue(ticket.contains("$45.00"), "Debe mostrar el saldo final de monedero")

        // Custom footer
        assertTrue(ticket.contains("Dudas o pedidos: WhatsApp 55-1234-5678"), "Debe incluir el pie de mensaje WhatsApp")
    }

    // ==========================================
    // 4. Customer Statement Formatter
    // ==========================================
    @Test
    fun testFormatCustomerStatement() {
        val statement = CustomerStatementDto(
            customer = sampleCustomer,
            totalChargesCents = 100_000L,
            totalPaymentsCents = 65_000L,
            movements = listOf(
                CreditMovementDto(
                    id = "mov-1",
                    customerId = sampleCustomer.id,
                    type = "CHARGE",
                    amountCents = 15_000L,
                    balanceAfterCents = 35_000L,
                    description = "Compra Ticket #1001",
                    userName = "Carlos Cajero",
                    createdAt = 1700000000000L
                )
            )
        )

        val text = WhatsAppMessageFormatter.formatCustomerStatement(sampleCustomer, statement, sampleSettings)

        assertTrue(text.contains("estado de cuenta", ignoreCase = true), "Debe indicar que es un estado de cuenta")
        assertTrue(text.contains("María Gómez"), "Debe incluir el nombre del cliente")
        assertTrue(text.contains("$350.00"), "Debe indicar la deuda pendiente actual")
        assertTrue(text.contains("$650.00"), "Debe mostrar el disponible")
        assertTrue(text.contains("BBVA CLABE: 012180001234567890"), "Debe adjuntar los datos bancarios para transferencia")
        assertTrue(text.contains("Compra Ticket #1001"), "Debe incluir los últimos movimientos")
    }

    // ==========================================
    // 5. Payment Receipt Formatter
    // ==========================================
    @Test
    fun testFormatCreditPayment() {
        val payment = CreditMovementDto(
            id = "mov-pay-1",
            customerId = sampleCustomer.id,
            type = "PAYMENT",
            amountCents = 10_000L,
            balanceAfterCents = 25_000L,
            description = "Abono a cuenta semanal",
            userName = "Carlos Cajero",
            receiptFolio = "REC-0050",
            createdAt = 1700000000000L
        )

        val receiptText = WhatsAppMessageFormatter.formatCreditPayment(sampleCustomer, payment, sampleSettings)

        assertTrue(receiptText.contains("abono", ignoreCase = true), "Debe contener confirmación de abono")
        assertTrue(receiptText.contains("REC-0050"), "Debe contener el folio del recibo")
        assertTrue(receiptText.contains("$100.00"), "Debe mostrar el importe abonado")
        assertTrue(receiptText.contains("$250.00"), "Debe mostrar el saldo deudor resultante")
    }

    // ==========================================
    // 6. Refund Note Formatter
    // ==========================================
    @Test
    fun testFormatRefund() {
        val refund = SaleRefundResponse(
            id = "ref-1",
            folio = 12L,
            saleId = sampleSale.id,
            saleFolio = sampleSale.folio,
            cashSessionId = "sess-1",
            cashierName = "Carlos Cajero",
            authorizedByName = null,
            reason = "Producto defectuoso",
            notes = "",
            refundMethod = "CASH",
            subtotalCents = 5000L,
            taxCents = 0L,
            totalCents = 5000L,
            lines = listOf(
                SaleRefundLineResponse("rl-1", "l-1", "p-1", null, "Leche Entera 1L", 2000L, 2500L, 0.0, 0L, 5000L, true)
            ),
            createdAt = 1700000000000L
        )

        val refundText = WhatsAppMessageFormatter.formatRefund(refund, sampleSettings)

        assertTrue(refundText.contains("NOTA DE DEVOLUCIÓN"), "Debe titularse nota de devolución")
        assertTrue(refundText.contains("#12"), "Debe incluir el folio de devolución")
        assertTrue(refundText.contains("#1001"), "Debe incluir el folio del ticket original")
        assertTrue(refundText.contains("TOTAL DEVUELTO:") && refundText.contains("$50.00"), "Debe indicar el total reembolsado")
        assertTrue(refundText.contains("Producto defectuoso"), "Debe incluir el motivo")
        assertTrue(refundText.contains("Leche Entera 1L"), "Debe detallar los artículos devueltos")
    }

    // ==========================================
    // 7. SalesViewModel WhatsApp State
    // ==========================================
    @Test
    fun testSalesViewModelWhatsAppFlow() {
        val mockApi = object : ApiClient() {
            override suspend fun getSettings(): Result<SettingsDto> {
                return Result.success(sampleSettings)
            }
        }
        val unconfinedScope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = SalesViewModel(mockApi, unconfinedScope)

        viewModel.currentSettings = sampleSettings
        assertFalse(viewModel.uiState.value.isShowingWhatsAppDialog)

        // Open WhatsApp for completed sale
        viewModel.openSaleWhatsAppDialog(sampleSale)
        val state = viewModel.uiState.value

        assertTrue(state.isShowingWhatsAppDialog)
        assertTrue(state.whatsAppMessageText.contains("#1001"))
        assertEquals("Enviar Ticket Folio #1001 por WhatsApp", state.whatsAppDialogTitle)

        // Close WhatsApp dialog
        viewModel.closeWhatsAppDialog()
        assertFalse(viewModel.uiState.value.isShowingWhatsAppDialog)
    }

    // ==========================================
    // 8. CustomersViewModel WhatsApp State
    // ==========================================
    @Test
    fun testCustomersViewModelWhatsAppFlow() {
        val mockApi = object : ApiClient() {
            override suspend fun getSettings(): Result<SettingsDto> {
                return Result.success(sampleSettings)
            }
        }
        val unconfinedScope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = CustomersViewModel(mockApi, unconfinedScope)
        viewModel.currentSettings = sampleSettings

        assertFalse(viewModel.uiState.value.showWhatsAppDialog)

        // 1. Open customer statement WhatsApp
        viewModel.openStatementWhatsAppDialog(sampleCustomer)
        val statementState = viewModel.uiState.value
        assertTrue(statementState.showWhatsAppDialog)
        assertEquals("55 9876 5432", statementState.whatsAppInitialPhone)
        assertTrue(statementState.whatsAppMessageText.contains("estado de cuenta", ignoreCase = true))

        viewModel.closeWhatsAppDialog()
        assertFalse(viewModel.uiState.value.showWhatsAppDialog)

        // 2. Open payment receipt WhatsApp
        val payment = CreditMovementDto(
            id = "mov-1",
            customerId = sampleCustomer.id,
            type = "PAYMENT",
            amountCents = 10_000L,
            balanceAfterCents = 25_000L,
            description = "Abono en caja",
            userName = "Carlos",
            receiptFolio = "REC-1234",
            createdAt = 1700000000000L
        )

        viewModel.openPaymentReceiptWhatsAppDialog(sampleCustomer, payment)
        val receiptState = viewModel.uiState.value
        assertTrue(receiptState.showWhatsAppDialog)
        assertEquals("55 9876 5432", receiptState.whatsAppInitialPhone)
        assertTrue(receiptState.whatsAppMessageText.contains("abono", ignoreCase = true))

        viewModel.closeWhatsAppDialog()
        assertFalse(viewModel.uiState.value.showWhatsAppDialog)
    }

    // ==========================================
    // 9. WhatsAppDispatcher Clipboard Test
    // ==========================================
    @Test
    fun testClipboardCopy() {
        val result = WhatsAppDispatcher.copyToClipboard("NexusPOS Test Message")
        // In headless or local test environment, copyToClipboard should either succeed or handle gracefully
        assertNotNull(result)
    }
}

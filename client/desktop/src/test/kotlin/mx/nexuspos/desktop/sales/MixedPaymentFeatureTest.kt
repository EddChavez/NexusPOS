package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import kotlin.test.*

class MixedPaymentFeatureTest {

    private class MockMixedPaymentApiClient : ApiClient() {
        var lastCreateSaleRequest: CreateSaleRequest? = null
        var nextFolio = 501L

        override suspend fun completeSale(request: CreateSaleRequest): Result<SaleResponse> {
            lastCreateSaleRequest = request

            var totalChangeCents = 0L
            val paymentResponses = request.payments.mapIndexed { idx, p ->
                val change = if (p.method == "CASH" && p.receivedCents > p.amountCents) {
                    p.receivedCents - p.amountCents
                } else 0L
                totalChangeCents += change
                SalePaymentResponse(
                    id = "pay-$idx",
                    method = p.method,
                    amountCents = p.amountCents,
                    receivedCents = p.receivedCents,
                    changeCents = change,
                    reference = p.reference
                )
            }

            val saleResponse = SaleResponse(
                id = "sale-test-501",
                folio = nextFolio++,
                cashSessionId = request.cashSessionId,
                cashierName = "Cajero Prueba",
                customerName = if (request.customerId != null) "Cliente Frecuente" else null,
                customerRemainingCreditCents = if (request.customerId != null) 25000L else null,
                status = "COMPLETED",
                subtotalCents = request.expectedTotalCents,
                taxCents = 0L,
                totalCents = request.expectedTotalCents,
                lines = request.lines.mapIndexed { idx, l ->
                    SaleLineItemResponse(
                        id = "line-$idx",
                        productId = l.productId,
                        presentationId = l.presentationId,
                        description = l.description,
                        quantityMillis = l.quantityMillis,
                        unitPriceCents = l.expectedUnitPriceCents,
                        taxRate = l.taxRate,
                        taxCents = 0L,
                        totalCents = l.expectedTotalCents
                    )
                },
                payments = paymentResponses,
                createdAt = System.currentTimeMillis()
            )
            return Result.success(saleResponse)
        }
    }

    private fun createOpenSessionSummary(): CashSessionSummary {
        return CashSessionSummary(
            sessionId = "sess-100",
            registerId = "caja-1",
            registerName = "Caja Principal",
            cashierId = "user-1",
            cashierName = "Cajero Prueba",
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
    fun testSplitPaymentCashAndCard() = runBlocking {
        val mockApi = MockMixedPaymentApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Set active cashier
        viewModel.setActiveCashier("user-1", "Cajero Prueba", "CASHIER")
        // Inject session directly via reflection or state manipulation
        viewModel.showSessionDialog(false)
        viewModel.addGenericProduct("Aceite Cocina 1L", 4500L) // $45.00
        viewModel.addGenericProduct("Jabón Barra", 1500L)       // $15.00
        assertEquals(6000L, viewModel.uiState.value.total.cents) // Total = $60.00

        // Set active session in state
        val openSession = createOpenSessionSummary()
        val privateStateField = SalesViewModel::class.java.getDeclaredField("_uiState")
        privateStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = privateStateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<mx.nexuspos.client.features.sales.SalesUiState>
        stateFlow.value = stateFlow.value.copy(activeSession = openSession)

        // Perform split payment: $20.00 CASH + $40.00 CARD (with reference)
        val splitPayments = listOf(
            SalePaymentRequest(
                method = "CASH",
                amountCents = 2000L,
                receivedCents = 5000L // Paid with $50 bill, $30 change expected on the cash portion
            ),
            SalePaymentRequest(
                method = "CARD",
                amountCents = 4000L,
                receivedCents = 4000L,
                reference = "AUTH-44321"
            )
        )

        viewModel.checkoutMultiPayment(splitPayments)

        // Verify request received by mock API
        val capturedReq = mockApi.lastCreateSaleRequest
        assertNotNull(capturedReq)
        assertEquals(6000L, capturedReq.expectedTotalCents)
        assertEquals(2, capturedReq.payments.size)
        assertEquals("CASH", capturedReq.payments[0].method)
        assertEquals(2000L, capturedReq.payments[0].amountCents)
        assertEquals(5000L, capturedReq.payments[0].receivedCents)
        assertEquals("CARD", capturedReq.payments[1].method)
        assertEquals(4000L, capturedReq.payments[1].amountCents)
        assertEquals("AUTH-44321", capturedReq.payments[1].reference)

        // Verify UI state reset and success message
        val finalState = viewModel.uiState.value
        assertEquals(0, finalState.cartLines.size)
        assertEquals(0L, finalState.total.cents)
        assertFalse(finalState.isShowingCashPayment)
        assertNotNull(finalState.lastCompletedSale)
        assertEquals(501L, finalState.lastCompletedSale?.folio)
        assertTrue(finalState.successMessage?.contains("Cambio: $30") == true)
    }

    @Test
    fun testSplitPaymentWithCreditRequiresCustomer() = runBlocking {
        val mockApi = MockMixedPaymentApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val openSession = createOpenSessionSummary()
        val privateStateField = SalesViewModel::class.java.getDeclaredField("_uiState")
        privateStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = privateStateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<mx.nexuspos.client.features.sales.SalesUiState>
        stateFlow.value = stateFlow.value.copy(activeSession = openSession)

        viewModel.addGenericProduct("Bulto Harina", 35000L) // $350.00

        // Attempt CREDIT payment without selecting customer
        val creditPayment = listOf(
            SalePaymentRequest(
                method = "CREDIT",
                amountCents = 35000L
            )
        )

        viewModel.checkoutMultiPayment(creditPayment)

        // Must reject without calling API
        assertNull(mockApi.lastCreateSaleRequest)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Debe seleccionar un cliente") == true)

        // Now select customer and retry
        val customer = CustomerDto(
            id = "cust-1",
            name = "Don Roberto",
            phone = "555-1234",
            creditLimitCents = 50000L,
            currentBalanceCents = 0L,
            creditEnabled = true
        )
        viewModel.selectCustomer(customer)
        viewModel.clearMessages()

        // Split: $100 CASH + $250 CREDIT
        val splitWithCredit = listOf(
            SalePaymentRequest(
                method = "CASH",
                amountCents = 10000L,
                receivedCents = 10000L
            ),
            SalePaymentRequest(
                method = "CREDIT",
                amountCents = 25000L
            )
        )

        viewModel.checkoutMultiPayment(splitWithCredit)

        assertNotNull(mockApi.lastCreateSaleRequest)
        assertEquals("cust-1", mockApi.lastCreateSaleRequest?.customerId)
        assertEquals(2, mockApi.lastCreateSaleRequest?.payments?.size)
        assertTrue(viewModel.uiState.value.successMessage?.contains("Crédito restante: $250") == true)
    }

    @Test
    fun testEmptyPaymentsRejected() = runBlocking {
        val mockApi = MockMixedPaymentApiClient()
        val viewModel = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val openSession = createOpenSessionSummary()
        val privateStateField = SalesViewModel::class.java.getDeclaredField("_uiState")
        privateStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = privateStateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<mx.nexuspos.client.features.sales.SalesUiState>
        stateFlow.value = stateFlow.value.copy(activeSession = openSession)

        viewModel.addGenericProduct("Golosina", 500L)
        viewModel.checkoutMultiPayment(emptyList())

        assertNull(mockApi.lastCreateSaleRequest)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Debe especificar al menos una forma de pago") == true)
    }
}

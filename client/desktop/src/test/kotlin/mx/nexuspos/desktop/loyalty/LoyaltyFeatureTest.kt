package mx.nexuspos.desktop.loyalty

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.client.features.sales.SalesUiState
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto
import mx.nexuspos.contracts.loyalty.SaleLoyaltyRewardDto
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import kotlin.test.*

class LoyaltyFeatureTest {

    private val printer = EscPosReceiptPrinter(columnWidth = 40)

    private class MockLoyaltyApiClient : ApiClient() {
        var lastSaleRequest: CreateSaleRequest? = null
        var lastAdjustRequest: AdjustLoyaltyBalanceRequest? = null
        val movements = mutableListOf<LoyaltyMovementDto>()
        var customer = CustomerDto(
            id = "cust-1",
            folio = "CLI-0001",
            name = "Juan Pérez",
            phone = "555-1234",
            creditEnabled = true,
            hasCreditLimit = true,
            creditLimitCents = 50_000L,
            currentBalanceCents = 0L,
            availableCreditCents = 50_000L,
            loyaltyBalanceCents = 2500L,
            totalLoyaltyEarnedCents = 5000L,
            totalLoyaltyRedeemedCents = 2500L
        )

        override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
            lastSaleRequest = req
            val loyaltyPay = req.payments.firstOrNull { it.method == "LOYALTY" }
            val redeemed = loyaltyPay?.amountCents ?: 0L
            val realMoney = req.payments.filter { it.method != "LOYALTY" }.sumOf { it.amountCents }
            val earned = (realMoney * 0.02).toLong()
            val newBal = customer.loyaltyBalanceCents - redeemed + earned

            val sale = SaleResponse(
                id = "sale-101",
                folio = 101L,
                cashSessionId = req.cashSessionId,
                cashierName = "Cajero 1",
                customerName = customer.name,
                status = "COMPLETED",
                subtotalCents = req.expectedTotalCents,
                taxCents = 0L,
                totalCents = req.expectedTotalCents,
                lines = emptyList(),
                payments = req.payments.mapIndexed { idx, p ->
                    SalePaymentResponse("pay-$idx", p.method, p.amountCents, p.receivedCents, 0L, p.reference)
                },
                createdAt = System.currentTimeMillis(),
                loyaltyReward = SaleLoyaltyRewardDto(
                    earnedCents = earned,
                    redeemedCents = redeemed,
                    previousBalanceCents = customer.loyaltyBalanceCents,
                    finalBalanceCents = newBal,
                    cashbackPercentApplied = 2.0
                )
            )
            return Result.success(sale)
        }

        override suspend fun getCustomerLoyaltyMovements(customerId: String): Result<List<LoyaltyMovementDto>> {
            return Result.success(movements)
        }

        override suspend fun adjustCustomerLoyalty(customerId: String, req: AdjustLoyaltyBalanceRequest): Result<CustomerDto> {
            lastAdjustRequest = req
            customer = customer.copy(
                loyaltyBalanceCents = customer.loyaltyBalanceCents + req.amountCents,
                totalLoyaltyEarnedCents = if (req.amountCents > 0) customer.totalLoyaltyEarnedCents + req.amountCents else customer.totalLoyaltyEarnedCents
            )
            movements.add(
                LoyaltyMovementDto(
                    id = "mov-adj",
                    customerId = customerId,
                    type = "ADJUSTMENT",
                    amountCents = req.amountCents,
                    balanceAfterCents = customer.loyaltyBalanceCents,
                    description = req.reason,
                    createdAt = System.currentTimeMillis()
                )
            )
            return Result.success(customer)
        }

        override suspend fun searchCustomers(query: String?, includeInactive: Boolean): Result<List<CustomerDto>> {
            return Result.success(listOf(customer))
        }
    }

    @Test
    fun testEscPosReceiptPrintingWithLoyalty() {
        val sale = SaleResponse(
            id = "sale-loy-1",
            folio = 205,
            cashSessionId = "sess-1",
            cashierName = "Pedro Cajero",
            customerName = "Laura Pausini",
            status = "COMPLETED",
            subtotalCents = 25000L,
            taxCents = 0L,
            totalCents = 25000L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "line-1",
                    productId = "prod-1",
                    presentationId = "pres-1",
                    description = "Perfume Elegance",
                    quantityMillis = 1000L,
                    unitPriceCents = 25000L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 25000L
                )
            ),
            payments = listOf(
                SalePaymentResponse("p-1", "LOYALTY", 5000L, 5000L, 0L),
                SalePaymentResponse("p-2", "CASH", 20000L, 20000L, 0L)
            ),
            createdAt = System.currentTimeMillis(),
            loyaltyReward = SaleLoyaltyRewardDto(
                earnedCents = 400L, // 2% de 20000
                redeemedCents = 5000L,
                previousBalanceCents = 8000L,
                finalBalanceCents = 3400L,
                cashbackPercentApplied = 2.0
            )
        )

        val text = printer.formatSaleReceiptText(sale)

        // Verificar renglón de pago con monedero
        assertTrue(text.contains("MONEDERO ELECTRÓNICO:"))
        assertTrue(text.contains("$50.00"))

        // Verificar sección de lealtad al calce
        assertTrue(text.contains("** MONEDERO ELECTRÓNICO **"))
        assertTrue(text.contains("Saldo Redimido:"))
        assertTrue(text.contains("$50.00"))
        assertTrue(text.contains("Puntos Ganados (2.0%):"))
        assertTrue(text.contains("+$4.00"))
        assertTrue(text.contains("Nuevo Saldo Disponible:"))
        assertTrue(text.contains("$34.00"))

        val bytes = printer.generateEscPosBytes(text)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testEscPosRefundReceiptWithLoyaltyMethod() {
        val refund = SaleRefundResponse(
            id = "ref-1",
            folio = 12,
            saleId = "sale-1",
            saleFolio = 100L,
            cashSessionId = "sess-1",
            cashierName = "Pedro Cajero",
            authorizedByName = "Super Admin",
            refundMethod = "LOYALTY",
            reason = "DEFECTIVE",
            notes = "Abonado a monedero",
            subtotalCents = 1500L,
            taxCents = 0L,
            totalCents = 1500L,
            lines = emptyList(),
            createdAt = System.currentTimeMillis()
        )

        val text = printer.formatSaleRefundText(refund)
        assertTrue(text.contains("MÉTODO REEMBOLSO:"))
        assertTrue(text.contains("MONEDERO ELECTRÓNICO"))
        assertTrue(text.contains("$15.00"))
    }

    @Test
    fun testSalesViewModelLoyaltyNotification() = runBlocking {
        val mockApi = MockLoyaltyApiClient()
        val vm = SalesViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val openSession = CashSessionSummary(
            sessionId = "sess-1",
            registerId = "caja-1",
            registerName = "Caja 1",
            cashierId = "usr-1",
            cashierName = "Cajero 1",
            openedAt = System.currentTimeMillis(),
            closedAt = null,
            initialFundCents = 10000L,
            salesCashCents = 0L,
            creditPaymentsCashCents = 0L,
            cashInCents = 0L,
            cashOutCents = 0L,
            refundsCashCents = 0L,
            expectedCashCents = 10000L,
            countedCashCents = null,
            differenceCents = null,
            status = "OPEN"
        )

        val privateStateField = SalesViewModel::class.java.getDeclaredField("_uiState")
        privateStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = privateStateField.get(vm) as MutableStateFlow<SalesUiState>
        stateFlow.value = stateFlow.value.copy(activeSession = openSession, selectedCustomer = mockApi.customer)

        vm.setActiveCashier("usr-1", "Cajero 1", "CASHIER")
        vm.addGenericProduct("Leche Entera", 2600L) // $26.00

        // Pagar $10 con LOYALTY y $16 con CASH
        val payments = listOf(
            SalePaymentRequest("LOYALTY", 1000L),
            SalePaymentRequest("CASH", 1600L, 1600L)
        )
        vm.checkoutMultiPayment(payments)

        val state = vm.uiState.value
        assertNotNull(state.lastCompletedSale)
        assertNotNull(state.lastCompletedSale!!.loyaltyReward)
        assertEquals(1000L, state.lastCompletedSale!!.loyaltyReward!!.redeemedCents)
        assertEquals(32L, state.lastCompletedSale!!.loyaltyReward!!.earnedCents) // 2% de 1600 = 32
        assertTrue(state.successMessage?.contains("monedero") == true)
    }

    @Test
    fun testCustomersViewModelLoyaltyAdjustment() = runBlocking {
        val mockApi = MockLoyaltyApiClient()
        val vm = CustomersViewModel(
            apiClient = mockApi,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        vm.selectCustomer(mockApi.customer)
        vm.openLoyaltyDialog(mockApi.customer)

        val stateAfterOpen = vm.uiState.value
        assertTrue(stateAfterOpen.showLoyaltyDialog)

        // Ajustar +$15.00
        vm.adjustLoyaltyBalance(1500L, "Bono regalo aniversario", "usr-admin")

        val stateAfterAdjust = vm.uiState.value
        assertEquals(4000L, stateAfterAdjust.selectedCustomer?.loyaltyBalanceCents) // 2500 + 1500
        assertEquals(1, stateAfterAdjust.loyaltyMovements.size)
        assertEquals(1500L, stateAfterAdjust.loyaltyMovements.first().amountCents)
    }
}

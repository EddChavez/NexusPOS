package mx.nexuspos.desktop.customers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.CustomerStatementDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.system.SettingsDto
import kotlin.test.*

class CustomersWorkspaceTest {
    private val first = CustomerDto("A", folio = "1", name = "Alicia", currentBalanceCents = 2500)
    private val second = CustomerDto("B", folio = "2", name = "Bruno", currentBalanceCents = 4900)

    @Test
    fun lateStatementCannotReplaceAnotherCustomerOrLeakTheirMovements() {
        val firstStatement = CompletableDeferred<Result<CustomerStatementDto>>()
        val api = object : ApiClient() {
            override suspend fun getSettings() = Result.success(SettingsDto())
            override suspend fun searchCustomers(query: String?, includeInactive: Boolean) =
                Result.success(listOf(first, second))

            override suspend fun getCustomerStatement(id: String): Result<CustomerStatementDto> =
                if (id == first.id) firstStatement.await()
                else Result.success(CustomerStatementDto(second, emptyList()))
        }
        val vm = CustomersViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        assertTrue(vm.uiState.value.isStatementLoading)
        vm.selectCustomer(second)
        firstStatement.complete(Result.success(CustomerStatementDto(first, emptyList(), totalChargesCents = 2500)))

        val state = vm.uiState.value
        assertEquals(second.id, state.selectedCustomer?.id)
        assertEquals(second.id, state.statement?.customer?.id)
        assertFalse(state.isStatementLoading)
        vm.openPaymentReceiptWhatsAppDialog(second, payment(first.id, 500, 2000))
        assertFalse(vm.uiState.value.showWhatsAppDialog)
    }

    @Test
    fun paymentUsesItsSelectedCustomerAndIgnoresRepeatedConfirmation() {
        val response = CompletableDeferred<Result<CreditMovementDto>>()
        val api = PaymentApi(response)
        val vm = CustomersViewModel(api, CoroutineScope(Dispatchers.Unconfined))

        vm.openPaymentDialog(isLiquidation = false)
        vm.submitPayment("shift", "cashier", 500)
        vm.submitPayment("shift", "cashier", 500)
        vm.selectCustomer(second)
        vm.closePaymentDialog()

        assertEquals(first.id, vm.uiState.value.selectedCustomer?.id)
        assertTrue(vm.uiState.value.showPaymentDialog)
        assertTrue(vm.uiState.value.isSubmitting)
        assertEquals(1, api.requests.size)
        assertEquals(first.id, api.requests.single().customerId)
        assertEquals("shift", api.requests.single().cashSessionId)

        api.customer = first.copy(currentBalanceCents = 2000)
        response.complete(Result.success(payment(first.id, 500, 2000)))
        assertFalse(vm.uiState.value.showPaymentDialog)
        assertEquals(2000L, vm.uiState.value.selectedCustomer?.currentBalanceCents)
        assertContains(vm.uiState.value.successMessage.orEmpty(), "$5.00")
    }

    @Test
    fun liquidationNeedsAnOpenShiftAndTheFullBalanceAndPreservesFormOnFailure() {
        val response = CompletableDeferred<Result<CreditMovementDto>>()
        val api = PaymentApi(response)
        val vm = CustomersViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        vm.openPaymentDialog(isLiquidation = true)

        vm.submitPayment("", "cashier", first.currentBalanceCents)
        assertContains(vm.uiState.value.errorMessage.orEmpty(), "turno de caja")
        vm.submitPayment("shift", "cashier", 500)
        assertTrue(api.requests.isEmpty())

        vm.submitPayment("shift", "cashier", first.currentBalanceCents, "CARD")
        assertEquals("CARD", api.requests.single().paymentMethod)
        response.complete(Result.failure(IllegalStateException("El servidor rechazó el abono")))

        assertTrue(vm.uiState.value.showPaymentDialog)
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(first.id, vm.uiState.value.selectedCustomer?.id)
        assertEquals("El servidor rechazó el abono", vm.uiState.value.errorMessage)
    }

    private inner class PaymentApi(private val response: CompletableDeferred<Result<CreditMovementDto>>) : ApiClient() {
        var customer = first
        val requests = mutableListOf<RegisterCreditPaymentRequest>()

        override suspend fun getSettings() = Result.success(SettingsDto())
        override suspend fun searchCustomers(query: String?, includeInactive: Boolean) =
            Result.success(listOf(customer, second))

        override suspend fun getCustomerStatement(id: String) =
            Result.success(CustomerStatementDto(if (id == first.id) customer else second, emptyList()))

        override suspend fun registerCreditPayment(req: RegisterCreditPaymentRequest): Result<CreditMovementDto> {
            requests += req
            return response.await()
        }
    }

    private fun payment(customerId: String, amount: Long, balance: Long) = CreditMovementDto(
        id = "movement", customerId = customerId, type = "PAYMENT", amountCents = amount,
        balanceAfterCents = balance, receiptFolio = "AB-1", description = "Abono en caja", createdAt = 0L
    )
}

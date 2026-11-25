package mx.nexuspos.client.features.customers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.whatsapp.WhatsAppMessageFormatter
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money

class CustomersViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    var currentSettings: SettingsDto? = null
    private var searchJob: Job? = null
    private val customerRequests = MutableStateFlow(0L)

    init {
        loadCustomers()
        loadSettings()
    }

    fun loadSettings() {
        scope.launch {
            apiClient.getSettings().onSuccess { s -> currentSettings = s }
        }
    }

    fun openStatementWhatsAppDialog(customer: CustomerDto) {
        val statement = _uiState.value.statement?.takeIf { it.customer.id == customer.id }
        val text = WhatsAppMessageFormatter.formatCustomerStatement(customer, statement, currentSettings)
        _uiState.update {
            it.copy(
                showWhatsAppDialog = true,
                whatsAppMessageText = text,
                whatsAppInitialPhone = customer.phone,
                whatsAppDialogTitle = "Enviar Estado de Cuenta a ${customer.name}"
            )
        }
    }

    fun openPaymentReceiptWhatsAppDialog(customer: CustomerDto, payment: CreditMovementDto) {
        if (payment.customerId != customer.id) return
        val text = WhatsAppMessageFormatter.formatCreditPayment(customer, payment, currentSettings)
        _uiState.update {
            it.copy(
                showWhatsAppDialog = true,
                whatsAppMessageText = text,
                whatsAppInitialPhone = customer.phone,
                whatsAppDialogTitle = "Enviar Comprobante de Abono a ${customer.name}"
            )
        }
    }

    fun closeWhatsAppDialog() {
        _uiState.update { it.copy(showWhatsAppDialog = false) }
    }

    fun loadCustomers(query: String? = _uiState.value.searchQuery) {
        val current = _uiState.value
        if (current.searchQuery != query.orEmpty()) return
        val includeInactive = current.includeInactive
        val request = customerRequests.updateAndGet { it + 1L }
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
            val result = apiClient.searchCustomers(query, includeInactive)
            ensureActive()
            _uiState.update { state ->
                if (customerRequests.value != request || state.searchQuery != query.orEmpty() ||
                    state.includeInactive != includeInactive
                ) return@update state
                result.fold(
                    onSuccess = { list -> state.copy(
                        customers = list,
                        selectedCustomer = state.selectedCustomer ?: list.firstOrNull(),
                        isLoading = false
                    ) },
                    onFailure = { state.copy(isLoading = false, errorMessage = it.message ?: "No se pudo cargar la lista de clientes.") }
                )
            }
            val state = _uiState.value
            if (customerRequests.value == request && state.statement == null &&
                !state.isStatementLoading && state.statementErrorMessage == null
            ) state.selectedCustomer?.let { loadCustomerStatement(it.id) }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery, isLoading = true) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250)
            loadCustomers(newQuery)
        }
    }

    fun onToggleIncludeInactive() {
        _uiState.update { it.copy(includeInactive = !it.includeInactive) }
        loadCustomers()
    }

    fun selectCustomer(customer: CustomerDto?) {
        val previous = _uiState.getAndUpdate { state ->
            if (state.showPaymentDialog || state.isSubmitting || state.selectedCustomer?.id == customer?.id) state
            else state.copy(
                selectedCustomer = customer,
                statement = null,
                isStatementLoading = customer != null,
                statementErrorMessage = null,
                loyaltyMovements = emptyList(),
                isLoyaltyLoading = false,
                loyaltyErrorMessage = null,
                showLoyaltyDialog = false,
                showAdjustLoyaltyDialog = false,
                statementRequestId = state.statementRequestId + 1L,
                loyaltyRequestId = state.loyaltyRequestId + 1L
            )
        }
        if (!previous.showPaymentDialog && !previous.isSubmitting && previous.selectedCustomer?.id != customer?.id) {
            customer?.let { loadCustomerStatement(it.id) }
        }
    }

    fun loadCustomerStatement(customerId: String) {
        val started = _uiState.updateAndGet { state ->
            if (state.selectedCustomer?.id == customerId) state.copy(
                isStatementLoading = true,
                statementErrorMessage = null,
                statementRequestId = state.statementRequestId + 1L
            ) else state
        }
        if (started.selectedCustomer?.id != customerId) return
        val request = started.statementRequestId
        scope.launch {
            val result = apiClient.getCustomerStatement(customerId)
            ensureActive()
            _uiState.update { state ->
                if (state.selectedCustomer?.id != customerId || state.statementRequestId != request) return@update state
                result.fold(
                    onSuccess = { statement ->
                        if (statement.customer.id == customerId) state.copy(
                            statement = statement,
                            selectedCustomer = statement.customer,
                            isStatementLoading = false
                        ) else state.copy(
                            isStatementLoading = false,
                            statementErrorMessage = "No se pudo vincular el estado de cuenta con este cliente."
                        )
                    },
                    onFailure = {
                        val message = it.message ?: "No se pudo consultar el estado de cuenta."
                        state.copy(isStatementLoading = false, statementErrorMessage = message, errorMessage = message)
                    }
                )
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update { state ->
            if (state.isSubmitting || state.showFormDialog || state.showPaymentDialog ||
                state.showWhatsAppDialog || state.showAdjustLoyaltyDialog
            ) state
            else state.copy(showFormDialog = true, customerToEdit = null, errorMessage = null)
        }
    }

    fun openEditDialog(customer: CustomerDto) {
        _uiState.update { it.copy(showFormDialog = true, customerToEdit = customer, errorMessage = null) }
    }

    fun closeFormDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showFormDialog = false, customerToEdit = null) }
    }

    fun saveCustomer(
        name: String,
        phone: String,
        email: String,
        rfc: String,
        address: String,
        notes: String,
        creditEnabled: Boolean,
        hasCreditLimit: Boolean,
        creditLimitCents: Long
    ) {
        val snapshot = _uiState.getAndUpdate {
            if (!it.showFormDialog || it.isSubmitting) it else it.copy(isSubmitting = true, errorMessage = null)
        }
        if (!snapshot.showFormDialog || snapshot.isSubmitting) return
        val editing = snapshot.customerToEdit
        scope.launch {
            if (editing == null) {
                val req = CreateCustomerRequest(
                    name = name,
                    phone = phone,
                    email = email,
                    rfc = rfc,
                    address = address,
                    notes = notes,
                    creditEnabled = creditEnabled,
                    hasCreditLimit = hasCreditLimit,
                    creditLimitCents = creditLimitCents
                )
                val res = apiClient.createCustomer(req)
                res.onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showFormDialog = false,
                            successMessage = "Cliente '${created.name}' (${created.folio}) registrado correctamente"
                        )
                    }
                    loadCustomers()
                    selectCustomer(created)
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
            } else {
                val req = UpdateCustomerRequest(
                    name = name,
                    phone = phone,
                    email = email,
                    rfc = rfc,
                    address = address,
                    notes = notes,
                    creditEnabled = creditEnabled,
                    hasCreditLimit = hasCreditLimit,
                    creditLimitCents = creditLimitCents,
                    isActive = editing.isActive
                )
                val res = apiClient.updateCustomer(editing.id, req)
                res.onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showFormDialog = false,
                            successMessage = "Cliente '${updated.name}' actualizado correctamente"
                        )
                    }
                    if (_uiState.value.selectedCustomer?.id == updated.id) {
                        _uiState.update {
                            it.copy(selectedCustomer = updated, statementRequestId = it.statementRequestId + 1L)
                        }
                        loadCustomerStatement(updated.id)
                    } else {
                        selectCustomer(updated)
                    }
                    loadCustomers()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
            }
        }
    }

    fun deactivateCustomer(customer: CustomerDto) =
        updateCustomerStatus(customer, active = false)

    fun reactivateCustomer(customer: CustomerDto) =
        updateCustomerStatus(customer, active = true)

    private fun updateCustomerStatus(customer: CustomerDto, active: Boolean) {
        val previous = _uiState.getAndUpdate {
            if (it.isSubmitting) it else it.copy(isSubmitting = true, errorMessage = null)
        }
        if (previous.isSubmitting) return
        scope.launch {
            val result = if (active) apiClient.reactivateCustomer(customer.id) else apiClient.deactivateCustomer(customer.id)
            result.onSuccess { updated ->
                val status = if (updated.isActive) "reactivado" else "desactivado"
                _uiState.update { state ->
                    val isSelected = state.selectedCustomer?.id == updated.id
                    state.copy(
                        isSubmitting = false,
                        customers = state.customers.mapNotNull {
                            when {
                                it.id != updated.id -> it
                                updated.isActive || state.includeInactive -> updated
                                else -> null
                            }
                        },
                        selectedCustomer = if (isSelected) updated else state.selectedCustomer,
                        statementRequestId = if (isSelected) state.statementRequestId + 1L else state.statementRequestId,
                        successMessage = "Cliente '${updated.name}' $status"
                    )
                }
                loadCustomers()
                loadCustomerStatement(updated.id)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = error.message ?: "No se pudo cambiar el estado del cliente.")
                }
            }
        }
    }

    fun openPaymentDialog(isLiquidation: Boolean) {
        val selected = _uiState.value.selectedCustomer
        if (selected == null || selected.currentBalanceCents <= 0L) return
        _uiState.update { it.copy(showPaymentDialog = true, isFullLiquidation = isLiquidation, errorMessage = null) }
    }

    fun closePaymentDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showPaymentDialog = false) }
    }

    fun submitPayment(
        cashSessionId: String,
        cashierUserId: String,
        amountCents: Long,
        paymentMethod: String = "CASH",
        notes: String = ""
    ) {
        val attempt = _uiState.getAndUpdate { state ->
            val debt = state.selectedCustomer?.currentBalanceCents ?: 0L
            when {
                state.isSubmitting || !state.showPaymentDialog -> state
                cashSessionId.isBlank() -> state.copy(errorMessage = "Abre un turno de caja antes de registrar el abono.")
                amountCents !in 1L..debt || (state.isFullLiquidation && amountCents != debt) ->
                    state.copy(errorMessage = "El importe no coincide con el saldo pendiente.")
                else -> state.copy(isSubmitting = true, errorMessage = null)
            }
        }
        val customer = attempt.selectedCustomer ?: return
        if (attempt.isSubmitting || !attempt.showPaymentDialog || cashSessionId.isBlank() ||
            amountCents !in 1L..customer.currentBalanceCents ||
            (attempt.isFullLiquidation && amountCents != customer.currentBalanceCents)
        ) return
        scope.launch {
            val req = RegisterCreditPaymentRequest(
                customerId = customer.id,
                cashSessionId = cashSessionId,
                cashierUserId = cashierUserId,
                amountCents = amountCents,
                paymentMethod = paymentMethod,
                notes = notes
            )
            val res = apiClient.registerCreditPayment(req)
            res.onSuccess { mov ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        showPaymentDialog = false,
                        statementRequestId = it.statementRequestId + 1L,
                        selectedCustomer = it.selectedCustomer?.copy(currentBalanceCents = mov.balanceAfterCents),
                        successMessage = "Abono de ${Money(amountCents).format()} registrado. Recibo ${mov.receiptFolio ?: mov.id}."
                    )
                }
                loadCustomers()
                loadCustomerStatement(customer.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun openLoyaltyDialog(customer: CustomerDto) {
        if (_uiState.value.selectedCustomer?.id != customer.id) selectCustomer(customer)
        if (_uiState.value.selectedCustomer?.id != customer.id) return
        _uiState.update { it.copy(showLoyaltyDialog = true) }
        loadLoyaltyMovements(customer.id)
    }

    fun loadLoyaltyMovements(customerId: String) {
        val started = _uiState.updateAndGet { state ->
            if (state.selectedCustomer?.id == customerId) state.copy(
                isLoyaltyLoading = true,
                loyaltyErrorMessage = null,
                loyaltyRequestId = state.loyaltyRequestId + 1L
            ) else state
        }
        if (started.selectedCustomer?.id != customerId) return
        val request = started.loyaltyRequestId
        scope.launch {
            val result = apiClient.getCustomerLoyaltyMovements(customerId)
            ensureActive()
            _uiState.update { state ->
                if (state.selectedCustomer?.id != customerId || state.loyaltyRequestId != request) return@update state
                result.fold(
                    onSuccess = { movements -> state.copy(
                        loyaltyMovements = movements.filter { it.customerId == customerId },
                        isLoyaltyLoading = false
                    ) },
                    onFailure = {
                        val message = it.message ?: "No se pudieron consultar los movimientos del monedero."
                        state.copy(isLoyaltyLoading = false, loyaltyErrorMessage = message, errorMessage = message)
                    }
                )
            }
        }
    }

    fun closeLoyaltyDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showLoyaltyDialog = false) }
    }

    fun openAdjustLoyaltyDialog() {
        _uiState.update {
            if (it.isSubmitting) it else it.copy(showAdjustLoyaltyDialog = true, errorMessage = null)
        }
    }

    fun closeAdjustLoyaltyDialog() {
        _uiState.update { if (it.isSubmitting) it else it.copy(showAdjustLoyaltyDialog = false) }
    }

    fun adjustLoyaltyBalance(amountCents: Long, reason: String, userId: String = "usr-cajero") {
        val attempt = _uiState.getAndUpdate {
            if (it.isSubmitting || it.selectedCustomer == null || amountCents == 0L) it
            else it.copy(isSubmitting = true, errorMessage = null)
        }
        val customer = attempt.selectedCustomer ?: return
        if (attempt.isSubmitting || amountCents == 0L) return
        scope.launch {
            val req = AdjustLoyaltyBalanceRequest(customer.id, amountCents, reason, userId)
            apiClient.adjustCustomerLoyalty(customer.id, req).onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        selectedCustomer = updated,
                        isSubmitting = false,
                        showAdjustLoyaltyDialog = false,
                        statementRequestId = it.statementRequestId + 1L,
                        successMessage = "Ajuste de ${Money(amountCents).format()} registrado en el monedero."
                    )
                }
                loadCustomers()
                loadLoyaltyMovements(customer.id)
                loadCustomerStatement(customer.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message ?: "No se pudo ajustar el monedero.") }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

package mx.nexuspos.client.features.customers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.credit.UpdateCustomerRequest

class CustomersViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadCustomers()
    }

    fun loadCustomers(query: String? = _uiState.value.searchQuery) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = apiClient.searchCustomers(query, _uiState.value.includeInactive)
            res.onSuccess { list ->
                _uiState.update { current ->
                    val selected = current.selectedCustomer?.let { sel -> list.firstOrNull { it.id == sel.id } }
                        ?: list.firstOrNull()
                    current.copy(
                        customers = list,
                        selectedCustomer = selected,
                        isLoading = false
                    )
                }
                _uiState.value.selectedCustomer?.let { sel ->
                    loadCustomerStatement(sel.id)
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250) // Debounce
            loadCustomers(newQuery)
        }
    }

    fun onToggleIncludeInactive() {
        _uiState.update { it.copy(includeInactive = !it.includeInactive) }
        loadCustomers()
    }

    fun selectCustomer(customer: CustomerDto?) {
        _uiState.update { it.copy(selectedCustomer = customer, statement = null) }
        if (customer != null) {
            loadCustomerStatement(customer.id)
        }
    }

    fun loadCustomerStatement(customerId: String) {
        scope.launch {
            _uiState.update { it.copy(isStatementLoading = true) }
            val res = apiClient.getCustomerStatement(customerId)
            res.onSuccess { st ->
                _uiState.update { it.copy(statement = st, selectedCustomer = st.customer, isStatementLoading = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isStatementLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showFormDialog = true, customerToEdit = null, errorMessage = null) }
    }

    fun openEditDialog(customer: CustomerDto) {
        _uiState.update { it.copy(showFormDialog = true, customerToEdit = customer, errorMessage = null) }
    }

    fun closeFormDialog() {
        _uiState.update { it.copy(showFormDialog = false, customerToEdit = null) }
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
        val editing = _uiState.value.customerToEdit
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
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
                    loadCustomers()
                    selectCustomer(updated)
                }.onFailure { err ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
                }
            }
        }
    }

    fun deactivateCustomer(customer: CustomerDto) {
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val res = apiClient.deactivateCustomer(customer.id)
            res.onSuccess { deactivated ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        successMessage = "Cliente '${deactivated.name}' desactivado"
                    )
                }
                loadCustomers()
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun openPaymentDialog(isLiquidation: Boolean) {
        val selected = _uiState.value.selectedCustomer
        if (selected == null || selected.currentBalanceCents <= 0L) return
        _uiState.update { it.copy(showPaymentDialog = true, isFullLiquidation = isLiquidation, errorMessage = null) }
    }

    fun closePaymentDialog() {
        _uiState.update { it.copy(showPaymentDialog = false) }
    }

    fun submitPayment(
        cashSessionId: String,
        cashierUserId: String,
        amountCents: Long,
        paymentMethod: String = "CASH",
        notes: String = ""
    ) {
        val customer = _uiState.value.selectedCustomer ?: return
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
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
                        successMessage = "Abono de $${amountCents / 100.0} registrado exitosamente (${mov.receiptFolio ?: mov.id})"
                    )
                }
                loadCustomers()
                loadCustomerStatement(customer.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isSubmitting = false, errorMessage = err.message) }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

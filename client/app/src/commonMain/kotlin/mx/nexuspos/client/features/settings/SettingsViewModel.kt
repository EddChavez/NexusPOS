package mx.nexuspos.client.features.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.identity.CreateUserRequest
import mx.nexuspos.contracts.identity.UpdateUserRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.system.SettingsDto

class SettingsViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    private fun loadAll() {
        loadSettings()
        loadUsers()
    }

    fun loadSettings() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            val res = apiClient.getSettings()
            res.onSuccess { dto ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        storeName = dto.storeName,
                        storeAddress = dto.storeAddress,
                        storePhone = dto.storePhone,
                        storeTaxId = dto.storeTaxId,
                        ticketHeader = dto.ticketHeader,
                        ticketFooter = dto.ticketFooter,
                        currencySymbol = dto.currencySymbol,
                        allowNegativeStock = dto.allowNegativeStock
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorBanner = err.message) }
            }
        }
    }

    fun loadUsers(includeInactive: Boolean = _uiState.value.includeInactiveUsers) {
        scope.launch {
            _uiState.update { it.copy(isLoadingUsers = true) }
            val res = apiClient.getUsers(includeInactive)
            res.onSuccess { list ->
                _uiState.update { it.copy(users = list, isLoadingUsers = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingUsers = false, errorBanner = err.message) }
            }
        }
    }

    fun selectTab(tab: SettingsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // --- Store / Ticket field updates ---

    fun onStoreNameChanged(v: String) = _uiState.update { it.copy(storeName = v) }
    fun onStoreAddressChanged(v: String) = _uiState.update { it.copy(storeAddress = v) }
    fun onStorePhoneChanged(v: String) = _uiState.update { it.copy(storePhone = v) }
    fun onStoreTaxIdChanged(v: String) = _uiState.update { it.copy(storeTaxId = v) }
    fun onTicketHeaderChanged(v: String) = _uiState.update { it.copy(ticketHeader = v) }
    fun onTicketFooterChanged(v: String) = _uiState.update { it.copy(ticketFooter = v) }
    fun onCurrencySymbolChanged(v: String) = _uiState.update { it.copy(currencySymbol = v) }

    fun saveStoreSettings() {
        val s = _uiState.value
        scope.launch {
            _uiState.update { it.copy(isSaving = true, errorBanner = null) }
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = s.allowNegativeStock
            )
            val res = apiClient.updateSettings(dto)
            res.onSuccess {
                _uiState.update { it.copy(isSaving = false, successBanner = "Configuración de tienda guardada correctamente") }
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorBanner = err.message) }
            }
        }
    }

    // --- Policies ---

    fun toggleNegativeStock() {
        val newValue = !_uiState.value.allowNegativeStock
        _uiState.update { it.copy(allowNegativeStock = newValue) }
        // Persist immediately
        val s = _uiState.value
        scope.launch {
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = newValue
            )
            apiClient.updateSettings(dto).onSuccess {
                _uiState.update { it.copy(successBanner = if (newValue) "Política cambiada a Flexible (stock negativo permitido)" else "Política cambiada a Estricto (stock negativo bloqueado)") }
                autoDismissBanner()
            }.onFailure { err ->
                // Revert on failure
                _uiState.update { it.copy(allowNegativeStock = !newValue, errorBanner = err.message) }
            }
        }
    }

    // --- User Filter ---

    fun onUserFilterChanged(v: String) = _uiState.update { it.copy(userFilter = v) }

    fun onToggleIncludeInactiveUsers() {
        val newVal = !_uiState.value.includeInactiveUsers
        _uiState.update { it.copy(includeInactiveUsers = newVal) }
        loadUsers(newVal)
    }

    // --- User Form Dialog ---

    fun openCreateUserDialog() {
        _uiState.update {
            it.copy(
                showUserDialog = true,
                editingUser = null,
                userFormName = "",
                userFormRole = "CASHIER",
                userFormPin = "",
                userFormError = null
            )
        }
    }

    fun openEditUserDialog(user: UserDto) {
        _uiState.update {
            it.copy(
                showUserDialog = true,
                editingUser = user,
                userFormName = user.name,
                userFormRole = user.role,
                userFormPin = "",
                userFormError = null
            )
        }
    }

    fun closeUserDialog() {
        _uiState.update { it.copy(showUserDialog = false, editingUser = null, userFormError = null) }
    }

    fun onUserFormNameChanged(v: String) = _uiState.update { it.copy(userFormName = v) }
    fun onUserFormRoleChanged(v: String) = _uiState.update { it.copy(userFormRole = v) }
    fun onUserFormPinChanged(v: String) {
        if (v.length <= 4 && v.all { it.isDigit() }) {
            _uiState.update { it.copy(userFormPin = v) }
        }
    }

    fun saveUser() {
        val s = _uiState.value
        val name = s.userFormName.trim()
        val role = s.userFormRole
        val pin = s.userFormPin

        // Validate
        if (name.isBlank()) {
            _uiState.update { it.copy(userFormError = "El nombre es obligatorio") }
            return
        }
        val isCreating = s.editingUser == null
        if (isCreating && pin.length != 4) {
            _uiState.update { it.copy(userFormError = "El PIN debe ser exactamente 4 dígitos") }
            return
        }
        if (pin.isNotBlank() && pin.length != 4) {
            _uiState.update { it.copy(userFormError = "El PIN debe ser exactamente 4 dígitos") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSaving = true, userFormError = null) }

            if (isCreating) {
                val req = CreateUserRequest(name = name, role = role, pin = pin)
                val res = apiClient.createUser(req)
                res.onSuccess { created ->
                    _uiState.update {
                        it.copy(isSaving = false, showUserDialog = false, editingUser = null,
                            successBanner = "Usuario '${created.name}' creado correctamente")
                    }
                    loadUsers()
                    autoDismissBanner()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, userFormError = err.message) }
                }
            } else {
                val editing = s.editingUser!!
                val req = UpdateUserRequest(name = name, role = role, pin = pin.ifBlank { null })
                val res = apiClient.updateUser(editing.id, req)
                res.onSuccess { updated ->
                    _uiState.update {
                        it.copy(isSaving = false, showUserDialog = false, editingUser = null,
                            successBanner = "Usuario '${updated.name}' actualizado correctamente")
                    }
                    loadUsers()
                    autoDismissBanner()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, userFormError = err.message) }
                }
            }
        }
    }

    fun deactivateUser(user: UserDto) {
        scope.launch {
            val res = apiClient.deactivateUser(user.id)
            res.onSuccess {
                _uiState.update { it.copy(successBanner = "Usuario '${user.name}' desactivado") }
                loadUsers()
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    fun reactivateUser(user: UserDto) {
        scope.launch {
            val res = apiClient.reactivateUser(user.id)
            res.onSuccess {
                _uiState.update { it.copy(successBanner = "Usuario '${user.name}' reactivado") }
                loadUsers()
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    fun clearBanners() {
        _uiState.update { it.copy(successBanner = null, errorBanner = null) }
    }

    private fun autoDismissBanner() {
        scope.launch {
            delay(4000)
            _uiState.update { it.copy(successBanner = null) }
        }
    }
}

package mx.nexuspos.client.features.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.identity.LoginResponse

class AuthViewModel(
    val apiClient: ApiClient = ApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _supervisorState = MutableStateFlow(SupervisorAuthState())
    val supervisorState: StateFlow<SupervisorAuthState> = _supervisorState.asStateFlow()

    // Callback invoked upon successful cashier login
    var onLoginSuccess: ((LoginResponse) -> Unit)? = null

    // Callback invoked upon successful supervisor elevation
    var onSupervisorSuccess: ((SupervisorAction) -> Unit)? = null

    // ==========================================
    // LOGIN & LOCK SCREEN
    // ==========================================

    fun onDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _uiState.value.pinInput
        if (current.length < 4) {
            val updated = current + digit
            _uiState.update { it.copy(pinInput = updated, errorMessage = null) }
            if (updated.length == 4) {
                submitLogin()
            }
        }
    }

    fun onBackspace() {
        val current = _uiState.value.pinInput
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(pinInput = current.dropLast(1), errorMessage = null) }
        }
    }

    fun onClear() {
        _uiState.update { it.copy(pinInput = "", errorMessage = null) }
    }

    fun submitLogin() {
        val pin = _uiState.value.pinInput
        if (pin.length < 4 || _uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            val res = apiClient.login(pin)
            res.onSuccess { loginResponse ->
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        currentUser = loginResponse,
                        pinInput = "",
                        isLoading = false,
                        errorMessage = null
                    )
                }
                onLoginSuccess?.invoke(loginResponse)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pinInput = "",
                        errorMessage = err.message ?: "PIN incorrecto o usuario inactivo"
                    )
                }
            }
        }
    }

    fun lockTerminal() {
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                pinInput = "",
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun unlockWithUser(user: LoginResponse) {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                currentUser = user,
                pinInput = "",
                isLoading = false,
                errorMessage = null
            )
        }
        onLoginSuccess?.invoke(user)
    }

    // ==========================================
    // SUPERVISOR ELEVATION MODAL
    // ==========================================

    fun requestSupervisorElevation(action: SupervisorAction) {
        _supervisorState.update {
            SupervisorAuthState(
                isVisible = true,
                targetAction = action,
                pinInput = "",
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun onSupervisorDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _supervisorState.value.pinInput
        if (current.length < 4) {
            val updated = current + digit
            _supervisorState.update { it.copy(pinInput = updated, errorMessage = null) }
            if (updated.length == 4) {
                submitSupervisorElevation()
            }
        }
    }

    fun onSupervisorBackspace() {
        val current = _supervisorState.value.pinInput
        if (current.isNotEmpty()) {
            _supervisorState.update { it.copy(pinInput = current.dropLast(1), errorMessage = null) }
        }
    }

    fun onSupervisorClear() {
        _supervisorState.update { it.copy(pinInput = "", errorMessage = null) }
    }

    fun cancelSupervisorElevation() {
        _supervisorState.update { SupervisorAuthState(isVisible = false) }
    }

    fun submitSupervisorElevation() {
        val state = _supervisorState.value
        val pin = state.pinInput
        val target = state.targetAction ?: return
        if (pin.length < 4 || state.isLoading) return

        _supervisorState.update { it.copy(isLoading = true, errorMessage = null) }

        val actionName = when (target) {
            is SupervisorAction.AccessSettings -> "SETTINGS"
            is SupervisorAction.AdjustInventory -> "ADJUST_INVENTORY"
            is SupervisorAction.CancelSale -> "CANCEL_SALE"
            is SupervisorAction.ApplyDiscount -> "APPLY_DISCOUNT"
        }

        scope.launch {
            val res = apiClient.authorizeSupervisor(pin, action = actionName)
            res.onSuccess { authResponse ->
                if (authResponse.authorized) {
                    _supervisorState.update { SupervisorAuthState(isVisible = false) }
                    onSupervisorSuccess?.invoke(target)
                } else {
                    _supervisorState.update {
                        it.copy(
                            isLoading = false,
                            pinInput = "",
                            errorMessage = "PIN incorrecto o sin privilegios de Encargado/Administrador"
                        )
                    }
                }
            }.onFailure { err ->
                _supervisorState.update {
                    it.copy(
                        isLoading = false,
                        pinInput = "",
                        errorMessage = err.message ?: "Error al verificar autorización de supervisor"
                    )
                }
            }
        }
    }
}

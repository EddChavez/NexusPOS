package mx.nexuspos.client.features.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.identity.LoginResponse

class AuthViewModel(
    val apiClient: ApiClient = ApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val loginRegisterId: (() -> String)? = null
) {
    private data class PendingLogin(val user: LoginResponse, val registerId: String, val serverUrl: String)

    private var pendingLogin: PendingLogin? = null
    private var loginAttempt = 0L
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _supervisorState = MutableStateFlow(SupervisorAuthState())
    val supervisorState: StateFlow<SupervisorAuthState> = _supervisorState.asStateFlow()

    // Callback invoked upon successful cashier login
    var onLoginSuccess: ((LoginResponse, CashSessionSummary?) -> Unit)? = null
    var onTerminalLocked: (() -> Unit)? = null

    // Callback invoked upon successful supervisor elevation
    var onSupervisorSuccess: ((SupervisorAction) -> Unit)? = null

    init {
        apiClient.onDesktopSessionInvalid = { token, message ->
            scope.launch {
                if (token == apiClient.sessionToken) lockTerminal(message)
            }
        }
    }

    // ==========================================
    // LOGIN & LOCK SCREEN
    // ==========================================

    fun onDigit(digit: Char) {
        if (digit !in '0'..'9') return
        val state = _uiState.value
        if (state.isLoading || state.shift != null) return
        val current = state.pinInput
        val max = if (state.useRootPin) 6 else 4
        if (current.length < max) {
            val updated = current + digit
            _uiState.update { it.copy(pinInput = updated, errorMessage = null) }
            if (updated.length == max) submitLogin()
        }
    }

    fun toggleRootPin() {
        _uiState.update {
            if (it.isLoading || it.shift != null) it
            else it.copy(useRootPin = !it.useRootPin, pinInput = "", errorMessage = null)
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
        val state = _uiState.value
        val pin = state.pinInput
        if (pin.length !in setOf(4, 6) || state.isAuthenticated || state.isLoading || state.shift != null) return

        val registerId = loginRegisterId?.invoke()
        val serverUrl = apiClient.baseUrl
        val attempt = ++loginAttempt
        _uiState.update { it.copy(isLoading = true, pinInput = "", errorMessage = null) }

        scope.launch {
            if (attempt != loginAttempt) return@launch
            if (serverUrl != apiClient.baseUrl || registerId != loginRegisterId?.invoke()) {
                lockTerminal()
                return@launch
            }
            val res = apiClient.login(pin)
            if (attempt != loginAttempt || serverUrl != apiClient.baseUrl || registerId != loginRegisterId?.invoke()) {
                if (attempt == loginAttempt) lockTerminal()
                res.getOrNull()?.let { apiClient.closeDesktopSession(it.token, serverUrl) }
                return@launch
            }
            res.onSuccess { user ->
                apiClient.sessionToken = user.token
                if (registerId != null && "cash.open" in user.permissions && "cash.view" in user.permissions) {
                    val pending = PendingLogin(user, registerId, serverUrl)
                    pendingLogin = pending
                    prepareShift(pending)
                } else {
                    completeLogin(user)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "PIN incorrecto o usuario inactivo") }
            }
        }
    }

    fun confirmLoginShift(amount: String) {
        val pending = pendingLogin ?: return
        val state = _uiState.value
        val prompt = state.shift ?: return
        if (state.isLoading || prompt.currencySymbol == null || !isCurrent(pending)) return
        val cents = parseCheckoutCents(amount) ?: return
        _uiState.update { it.copy(isLoading = true, shift = prompt.copy(errorMessage = null)) }
        scope.launch { openShift(pending, cents) }
    }

    fun retryLoginShift() {
        val pending = pendingLogin ?: return
        val state = _uiState.value
        if (state.isLoading || state.shift?.errorMessage == null || !isCurrent(pending)) return
        _uiState.update { it.copy(isLoading = true, shift = state.shift.copy(errorMessage = null)) }
        scope.launch { prepareShift(pending) }
    }

    fun cancelLoginShift() {
        if (_uiState.value.shift != null && !_uiState.value.isLoading) lockTerminal()
    }

    private fun isCurrent(pending: PendingLogin): Boolean {
        if (pendingLogin !== pending) return false
        if (pending.serverUrl == apiClient.baseUrl && pending.registerId == loginRegisterId?.invoke()) return true
        lockTerminal()
        return false
    }

    private suspend fun prepareShift(pending: PendingLogin) {
        if (!isCurrent(pending)) return
        val session = apiClient.getCurrentSession(pending.registerId)
        if (!isCurrent(pending)) return
        if (session.isFailure) {
            showShiftPrompt(pending, error = session.exceptionOrNull()?.message ?: "No se pudo consultar el turno de la caja.")
            return
        }
        val current = session.getOrNull()
        if (current != null) {
            completeLogin(pending.user, current)
            return
        }
        val settings = apiClient.getSettings()
        if (!isCurrent(pending)) return
        settings.onSuccess {
            if (it.commerce.requireOpeningFund) showShiftPrompt(pending, currency = it.currencySymbol)
            else openShift(pending, 0)
        }.onFailure {
            showShiftPrompt(pending, error = it.message ?: "No se pudo consultar la configuración de turnos.")
        }
    }

    private suspend fun openShift(pending: PendingLogin, cents: Long) {
        if (!isCurrent(pending)) return
        val result = apiClient.openSession(OpenSessionRequest(pending.registerId, pending.user.userId, cents))
        if (!isCurrent(pending)) return
        val session = result.getOrNull() ?: run {
            // Another terminal may have opened the shift, or the response may have been lost.
            val current = apiClient.getCurrentSession(pending.registerId).getOrNull()
            if (!isCurrent(pending)) return
            if (current == null) {
                showShiftPrompt(
                    pending,
                    currency = _uiState.value.shift?.currencySymbol,
                    error = result.exceptionOrNull()?.message ?: "No se pudo abrir el turno."
                )
                return
            }
            current
        }
        completeLogin(pending.user, session)
    }

    private fun showShiftPrompt(pending: PendingLogin, currency: String? = null, error: String? = null) {
        _uiState.update { it.copy(isLoading = false, shift = LoginShiftPrompt(pending.registerId, currency, error)) }
    }

    fun lockTerminal() = lockTerminal(null)

    private fun lockTerminal(message: String?) {
        loginAttempt++
        val token = apiClient.sessionToken
        val serverUrl = pendingLogin?.serverUrl ?: apiClient.baseUrl
        pendingLogin = null
        apiClient.sessionToken = null
        _supervisorState.value = SupervisorAuthState()
        if (token != null) scope.launch { apiClient.closeDesktopSession(token, serverUrl) }
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                currentUser = null,
                pinInput = "",
                isLoading = false,
                errorMessage = message,
                shift = null
            )
        }
        onTerminalLocked?.invoke()
    }

    fun unlockWithUser(user: LoginResponse) {
        if (pendingLogin != null) lockTerminal()
        loginAttempt++
        apiClient.sessionToken = user.token
        completeLogin(user)
    }

    private fun completeLogin(user: LoginResponse, session: CashSessionSummary? = null) {
        pendingLogin = null
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                currentUser = user,
                pinInput = "",
                isLoading = false,
                errorMessage = null,
                shift = null
            )
        }
        onLoginSuccess?.invoke(user, session)
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
        if (digit !in '0'..'9') return
        val state = _supervisorState.value
        if (state.isLoading) return
        val current = state.pinInput
        val max = if (state.useRootPin) 6 else 4
        if (current.length < max) {
            val updated = current + digit
            _supervisorState.update { it.copy(pinInput = updated, errorMessage = null) }
            if (updated.length == max) submitSupervisorElevation()
        }
    }

    fun toggleSupervisorRootPin() {
        _supervisorState.update { if (it.isLoading) it else it.copy(useRootPin = !it.useRootPin, pinInput = "", errorMessage = null) }
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
        if (pin.length !in setOf(4, 6) || state.isLoading) return

        _supervisorState.update { it.copy(isLoading = true, errorMessage = null) }

        val actionName = when (target) {
            is SupervisorAction.AccessSettings -> "SETTINGS"
            is SupervisorAction.AdjustInventory -> "ADJUST_INVENTORY"
            is SupervisorAction.CancelSale -> "CANCEL_SALE"
            is SupervisorAction.ApplyDiscount -> "APPLY_DISCOUNT"
            is SupervisorAction.RefundSale -> "REFUND_SALE"
        }

        val attempt = loginAttempt
        scope.launch {
            val res = apiClient.authorizeSupervisor(pin, action = actionName)
            if (attempt != loginAttempt) return@launch
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

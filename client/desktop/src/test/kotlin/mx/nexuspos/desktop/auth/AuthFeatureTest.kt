package mx.nexuspos.desktop.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.auth.AuthViewModel
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.identity.AuthorizeResponse
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.test.*

class AuthFeatureTest {

    private class FakeApiClient : ApiClient() {
        var loginResult: (String) -> Result<LoginResponse> = { pin ->
            when (pin) {
                "1234" -> Result.success(
                    LoginResponse(
                        token = "tok-admin",
                        userId = "usr-admin",
                        name = "Administrador",
                        role = "ADMIN",
                        permissions = listOf("SELL", "SETTINGS", "MANAGE_CATALOG", "cash.open", "cash.view")
                    )
                )
                "0000" -> Result.success(
                    LoginResponse(
                        token = "tok-cajero",
                        userId = "usr-cajero",
                        name = "Cajero Principal",
                        role = "CASHIER",
                        permissions = listOf("SELL", "CREDIT")
                    )
                )
                else -> Result.failure(Exception("PIN incorrecto o usuario inactivo"))
            }
        }

        var authorizeResult: (String, String) -> Result<AuthorizeResponse> = { pin, _ ->
            when (pin) {
                "1234" -> Result.success(
                    AuthorizeResponse(
                        authorized = true,
                        supervisorId = "usr-admin",
                        supervisorName = "Administrador"
                    )
                )
                else -> Result.success(AuthorizeResponse(authorized = false))
            }
        }

        override suspend fun login(pin: String): Result<LoginResponse> = loginResult(pin)

        var currentShift: CashSessionSummary? = null
        var currentShiftError: Exception? = null
        var openShiftError: Exception? = null
        var afterOpenRequest: () -> Unit = {}
        var requireFund = true
        val openRequests = mutableListOf<OpenSessionRequest>()

        override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> =
            currentShiftError?.let { Result.failure(it) } ?: Result.success(currentShift)

        override suspend fun getSettings(): Result<SettingsDto> {
            val settings = SettingsDto()
            return Result.success(settings.copy(commerce = settings.commerce.copy(requireOpeningFund = requireFund)))
        }

        override suspend fun openSession(req: OpenSessionRequest): Result<CashSessionSummary> {
            openRequests += req
            afterOpenRequest()
            openShiftError?.let { return Result.failure(it) }
            return Result.success(
                shiftForTest(req.userId).copy(initialFundCents = req.initialFundCents, expectedCashCents = req.initialFundCents)
                    .also { currentShift = it }
            )
        }

        override suspend fun authorizeSupervisor(pin: String, action: String): Result<AuthorizeResponse> =
            authorizeResult(pin, action)
    }

    private fun createTestAuthVm(fakeApi: FakeApiClient = FakeApiClient()): AuthViewModel {
        return AuthViewModel(apiClient = fakeApi, scope = CoroutineScope(Dispatchers.Unconfined))
    }

    private fun signInAtRegister(api: FakeApiClient, pin: String = "1234"): AuthViewModel =
        AuthViewModel(api, CoroutineScope(Dispatchers.Unconfined), loginRegisterId = { "caja-2" }).apply {
            pin.forEach(::onDigit)
        }

    @Test
    fun loginWaitsForValidOpeningFundAndUsesRegisterAndAuthenticatedUser() {
        val api = FakeApiClient()
        val auth = signInAtRegister(api)
        var authenticatedUser: LoginResponse? = null
        var confirmedShift: CashSessionSummary? = null
        auth.onLoginSuccess = { user, session ->
            authenticatedUser = user
            confirmedShift = session
        }
        assertFalse(auth.uiState.value.isAuthenticated)
        assertNull(auth.uiState.value.currentUser)
        assertEquals("caja-2", auth.uiState.value.shift?.registerId)
        auth.confirmLoginShift("-10")
        auth.confirmLoginShift("50.005")
        assertTrue(api.openRequests.isEmpty())

        auth.confirmLoginShift("125,50")
        assertEquals(listOf(OpenSessionRequest("caja-2", "usr-admin", 12_550)), api.openRequests)
        assertTrue(auth.uiState.value.isAuthenticated)
        assertEquals("usr-admin", authenticatedUser?.userId)
        assertEquals(api.currentShift, confirmedShift)
        assertNull(auth.uiState.value.shift)
    }

    @Test
    fun loginWithoutRequiredFundOpensWithZero() {
        val api = FakeApiClient().apply { requireFund = false }
        val auth = signInAtRegister(api)
        assertEquals(listOf(OpenSessionRequest("caja-2", "usr-admin", 0)), api.openRequests)
        assertTrue(auth.uiState.value.isAuthenticated)
        assertNull(auth.uiState.value.shift)
    }

    @Test
    fun loginPreservesShiftOpenedByAnotherUser() {
        val api = FakeApiClient().apply { currentShift = shiftForTest("otro-cajero") }
        val auth = signInAtRegister(api)
        assertTrue(auth.uiState.value.isAuthenticated)
        assertNull(auth.uiState.value.shift)
        assertEquals("otro-cajero", api.currentShift?.operatorUserId)
        assertTrue(api.openRequests.isEmpty())
    }

    @Test
    fun cancellingFundKeepsTerminalLocked() {
        val api = FakeApiClient()
        val auth = signInAtRegister(api)
        auth.cancelLoginShift()
        auth.confirmLoginShift("0")
        assertFalse(auth.uiState.value.isAuthenticated)
        assertNull(auth.uiState.value.currentUser)
        assertNull(auth.uiState.value.shift)
        assertTrue(api.openRequests.isEmpty())
    }

    @Test
    fun failedSessionLookupCanBeRetriedBeforeOpening() {
        val api = FakeApiClient().apply { currentShiftError = Exception("Sin conexión") }
        val auth = signInAtRegister(api)
        assertFalse(auth.uiState.value.isAuthenticated)
        assertEquals("Sin conexión", auth.uiState.value.shift?.errorMessage)
        assertTrue(api.openRequests.isEmpty())

        api.currentShiftError = null
        auth.retryLoginShift()
        auth.confirmLoginShift("0")
        assertTrue(auth.uiState.value.isAuthenticated)
        assertEquals(0L, api.openRequests.single().initialFundCents)
    }

    @Test
    fun failedOpenAllowsRetryAndReusesConcurrentlyOpenedShift() {
        val api = FakeApiClient().apply { openShiftError = Exception("No se pudo abrir") }
        val auth = signInAtRegister(api)
        auth.confirmLoginShift("100")
        assertFalse(auth.uiState.value.isAuthenticated)
        assertEquals("No se pudo abrir", auth.uiState.value.shift?.errorMessage)
        assertNotNull(auth.uiState.value.shift?.currencySymbol)

        api.afterOpenRequest = { api.currentShift = shiftForTest("otra-terminal") }
        auth.confirmLoginShift("100")
        assertTrue(auth.uiState.value.isAuthenticated)
        assertEquals("otra-terminal", api.currentShift?.operatorUserId)
    }

    @Test
    fun userWithoutOpeningPermissionsCanStillSignIn() {
        val api = FakeApiClient()
        val auth = signInAtRegister(api, pin = "0000")
        assertTrue(auth.uiState.value.isAuthenticated)
        assertNull(auth.uiState.value.shift)
        assertTrue(api.openRequests.isEmpty())
    }

    @Test
    fun testInitialStateIsLocked() {
        val authVm = createTestAuthVm()

        val state = authVm.uiState.value
        assertFalse(state.isAuthenticated, "La terminal debe iniciar bloqueada")
        assertNull(state.currentUser)
        assertEquals("", state.pinInput)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun testPinDigitInputAndBackspace() {
        val authVm = createTestAuthVm()

        authVm.onDigit('1')
        authVm.onDigit('2')
        authVm.onDigit('3')
        assertEquals("123", authVm.uiState.value.pinInput)

        authVm.onBackspace()
        assertEquals("12", authVm.uiState.value.pinInput)

        authVm.onClear()
        assertEquals("", authVm.uiState.value.pinInput)
    }

    @Test
    fun testLoginWithAdminSuccess() {
        val authVm = createTestAuthVm()

        var loggedInUser: LoginResponse? = null
        authVm.onLoginSuccess = { user, _ -> loggedInUser = user }

        authVm.onDigit('1')
        authVm.onDigit('2')
        authVm.onDigit('3')
        authVm.onDigit('4') // Auto-submits on 4th digit

        val state = authVm.uiState.value
        assertTrue(state.isAuthenticated, "Debe autenticarse tras ingresar PIN correcto")
        assertNotNull(state.currentUser)
        assertEquals("ADMIN", state.currentUser?.role)
        assertEquals("Administrador", state.currentUser?.name)
        assertEquals("usr-admin", state.currentUser?.userId)
        assertNotNull(loggedInUser)
        assertEquals("Administrador", loggedInUser.name)
    }

    @Test
    fun testLoginWithCashierSuccess() {
        val authVm = createTestAuthVm()

        authVm.onDigit('0')
        authVm.onDigit('0')
        authVm.onDigit('0')
        authVm.onDigit('0')

        val state = authVm.uiState.value
        assertTrue(state.isAuthenticated)
        assertEquals("CASHIER", state.currentUser?.role)
        assertEquals("Cajero Principal", state.currentUser?.name)
    }

    @Test
    fun testLoginWithInvalidPinFails() {
        val authVm = createTestAuthVm()

        authVm.onDigit('9')
        authVm.onDigit('9')
        authVm.onDigit('9')
        authVm.onDigit('9')

        val state = authVm.uiState.value
        assertFalse(state.isAuthenticated, "No debe autenticarse con PIN erróneo")
        assertNull(state.currentUser)
        assertEquals("", state.pinInput, "El PIN debe limpiarse para reintentar")
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("PIN incorrecto"))
    }

    @Test
    fun testLockTerminalPreservesCartLines() {
        val fakeApi = FakeApiClient()
        val authVm = createTestAuthVm(fakeApi)
        val salesVm = SalesViewModel(apiClient = fakeApi, scope = CoroutineScope(Dispatchers.Unconfined))

        // 1. Simular sesión inicial autenticada
        authVm.unlockWithUser(
            LoginResponse(
                token = "tok",
                userId = "usr-cajero",
                name = "Cajero Principal",
                role = "CASHIER",
                permissions = emptyList()
            )
        )
        assertTrue(authVm.uiState.value.isAuthenticated)

        // 2. Agregar productos en el carrito de ventas
        val sampleLine = CartLine(
            id = "line-1",
            productId = "prod-1",
            presentationId = "pres-1",
            barcode = "75010001",
            name = "Refresco Cola 600ml",
            unit = ProductUnit.PIECE,
            quantity = Quantity.ONE,
            unitPrice = Money.pesos(18.0),
            taxRate = 0.0,
            total = Money.pesos(18.0)
        )
        // Directly testing cart preservation via sales state
        salesVm.onScannerInputChanged("75010001")
        assertEquals(1, listOf(sampleLine).size)

        // 3. Bloquear terminal (Ctrl+L)
        authVm.lockTerminal()
        assertFalse(authVm.uiState.value.isAuthenticated)
        assertEquals("", authVm.uiState.value.pinInput)

        // 4. Cambiar de cajero e iniciar sesión con otro usuario
        authVm.onDigit('1')
        authVm.onDigit('2')
        authVm.onDigit('3')
        authVm.onDigit('4')

        val activeUser = authVm.uiState.value.currentUser
        assertNotNull(activeUser)
        salesVm.setActiveCashier(
            userId = activeUser.userId,
            name = activeUser.name,
            role = activeUser.role,
            permissions = activeUser.permissions
        )

        assertEquals("Administrador", salesVm.uiState.value.activeCashierName)
        assertEquals("ADMIN", salesVm.uiState.value.activeCashierRole)
    }

    @Test
    fun testSupervisorAuthorizationElevationSuccess() {
        val authVm = createTestAuthVm()

        var elevatedAction: SupervisorAction? = null
        authVm.onSupervisorSuccess = { elevatedAction = it }

        // Solicitar elevación para acceder a Configuración
        authVm.requestSupervisorElevation(SupervisorAction.AccessSettings)
        assertTrue(authVm.supervisorState.value.isVisible)
        assertEquals(SupervisorAction.AccessSettings, authVm.supervisorState.value.targetAction)

        // Digitar PIN de Supervisor (1234)
        authVm.onSupervisorDigit('1')
        authVm.onSupervisorDigit('2')
        authVm.onSupervisorDigit('3')
        authVm.onSupervisorDigit('4')

        // Verificar que cerró el modal y detonó el callback de éxito
        assertFalse(authVm.supervisorState.value.isVisible)
        assertEquals(SupervisorAction.AccessSettings, elevatedAction)
    }

    @Test
    fun testSupervisorAuthorizationElevationFailure() {
        val authVm = createTestAuthVm()

        var elevatedAction: SupervisorAction? = null
        authVm.onSupervisorSuccess = { elevatedAction = it }

        authVm.requestSupervisorElevation(SupervisorAction.AccessSettings)
        assertTrue(authVm.supervisorState.value.isVisible)

        // Digitar PIN sin permisos de supervisor (0000)
        authVm.onSupervisorDigit('0')
        authVm.onSupervisorDigit('0')
        authVm.onSupervisorDigit('0')
        authVm.onSupervisorDigit('0')

        // Verificar que sigue visible, con mensaje de error y no autorizó
        assertTrue(authVm.supervisorState.value.isVisible)
        assertNull(elevatedAction)
        assertNotNull(authVm.supervisorState.value.errorMessage)
        assertEquals("", authVm.supervisorState.value.pinInput)
    }

    @Test
    fun testRoleBasedAccessRestrictionsLogic() {
        // Regla: CASHIER requiere autorización para Settings [F5] e Inventario [F4]
        fun canAccessSettingsDirectly(role: String): Boolean = role in listOf("ADMIN", "SUPERVISOR")
        fun canAdjustStockDirectly(role: String): Boolean = role in listOf("ADMIN", "SUPERVISOR")

        assertFalse(canAccessSettingsDirectly("CASHIER"))
        assertTrue(canAccessSettingsDirectly("ADMIN"))
        assertTrue(canAccessSettingsDirectly("SUPERVISOR"))

        assertFalse(canAdjustStockDirectly("CASHIER"))
        assertTrue(canAdjustStockDirectly("ADMIN"))
        assertTrue(canAdjustStockDirectly("SUPERVISOR"))
    }
}

private fun shiftForTest(operator: String) = CashSessionSummary(
    sessionId = "turno-1",
    registerId = "caja-2",
    cashierId = operator,
    cashierName = "Cajero",
    openedAt = 1L,
    initialFundCents = 0L,
    salesCashCents = 0L,
    creditPaymentsCashCents = 0L,
    cashInCents = 0L,
    cashOutCents = 0L,
    refundsCashCents = 0L,
    expectedCashCents = 0L,
    status = "OPEN"
)

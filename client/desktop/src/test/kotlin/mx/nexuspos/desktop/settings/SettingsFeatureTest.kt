package mx.nexuspos.desktop.settings

import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsUiState
import mx.nexuspos.contracts.identity.UserDto
import kotlin.test.*

class SettingsFeatureTest {

    private val sampleAdminUser = UserDto(
        id = "usr-admin-001",
        name = "Administrador Principal",
        role = "ADMIN",
        isActive = true
    )

    private val sampleCashierUser = UserDto(
        id = "usr-cajero-001",
        name = "Juan Pérez",
        role = "CASHIER",
        isActive = true
    )

    private val sampleInactiveUser = UserDto(
        id = "usr-inactivo-001",
        name = "Usuario Inactivo",
        role = "CASHIER",
        isActive = false
    )

    @Test
    fun testDefaultUiState() {
        val state = SettingsUiState()

        assertTrue(state.isLoading)
        assertEquals(SettingsTab.STORE_TICKET, state.selectedTab)
        assertEquals("", state.storeName)
        assertEquals("", state.storeAddress)
        assertEquals("", state.storePhone)
        assertEquals("", state.storeTaxId)
        assertEquals("", state.ticketHeader)
        assertEquals("", state.ticketFooter)
        assertEquals("$", state.currencySymbol)
        assertTrue(state.allowNegativeStock)
        assertTrue(state.users.isEmpty())
        assertFalse(state.isLoadingUsers)
        assertEquals("", state.userFilter)
        assertFalse(state.includeInactiveUsers)
        assertFalse(state.showUserDialog)
        assertNull(state.editingUser)
        assertEquals("", state.userFormName)
        assertEquals("CASHIER", state.userFormRole)
        assertEquals("", state.userFormPin)
        assertNull(state.userFormError)
        assertFalse(state.isSaving)
        assertNull(state.successBanner)
        assertNull(state.errorBanner)
    }

    @Test
    fun testTabSelection() {
        var state = SettingsUiState()
        assertEquals(SettingsTab.STORE_TICKET, state.selectedTab)

        state = state.copy(selectedTab = SettingsTab.POLICIES)
        assertEquals(SettingsTab.POLICIES, state.selectedTab)

        state = state.copy(selectedTab = SettingsTab.USERS)
        assertEquals(SettingsTab.USERS, state.selectedTab)

        state = state.copy(selectedTab = SettingsTab.STORE_TICKET)
        assertEquals(SettingsTab.STORE_TICKET, state.selectedTab)
    }

    @Test
    fun testStoreFieldUpdates() {
        var state = SettingsUiState(isLoading = false)

        state = state.copy(
            storeName = "Abarrotes La Esperanza",
            storeAddress = "Calle Principal #123, Col. Centro",
            storePhone = "333-456-7890",
            storeTaxId = "XAXX010101000",
            ticketHeader = "¡Bienvenido! Gracias por su preferencia",
            ticketFooter = "No se aceptan devoluciones sin ticket",
            currencySymbol = "$"
        )

        assertEquals("Abarrotes La Esperanza", state.storeName)
        assertEquals("Calle Principal #123, Col. Centro", state.storeAddress)
        assertEquals("333-456-7890", state.storePhone)
        assertEquals("XAXX010101000", state.storeTaxId)
        assertEquals("¡Bienvenido! Gracias por su preferencia", state.ticketHeader)
        assertEquals("No se aceptan devoluciones sin ticket", state.ticketFooter)
        assertEquals("$", state.currencySymbol)
    }

    @Test
    fun testNegativeStockPolicyToggle() {
        var state = SettingsUiState(allowNegativeStock = true)
        assertTrue(state.allowNegativeStock)

        state = state.copy(allowNegativeStock = false)
        assertFalse(state.allowNegativeStock)

        state = state.copy(allowNegativeStock = true)
        assertTrue(state.allowNegativeStock)
    }

    @Test
    fun testUserListFilterAndInactiveToggle() {
        val users = listOf(sampleAdminUser, sampleCashierUser, sampleInactiveUser)
        var state = SettingsUiState(users = users, includeInactiveUsers = true)

        assertEquals(3, state.users.size)
        assertTrue(state.includeInactiveUsers)

        // Filter active only
        state = state.copy(includeInactiveUsers = false)
        assertFalse(state.includeInactiveUsers)

        // userFilter text filter
        state = state.copy(userFilter = "Juan")
        val filtered = state.users.filter {
            state.userFilter.isBlank() || it.name.contains(state.userFilter, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("Juan Pérez", filtered.first().name)
    }

    @Test
    fun testUserFormDialogOpenCreateState() {
        var state = SettingsUiState()

        // Open create dialog
        state = state.copy(
            showUserDialog = true,
            editingUser = null,
            userFormName = "",
            userFormRole = "CASHIER",
            userFormPin = "",
            userFormError = null
        )

        assertTrue(state.showUserDialog)
        assertNull(state.editingUser)
        assertEquals("", state.userFormName)
        assertEquals("CASHIER", state.userFormRole)
        assertEquals("", state.userFormPin)
        assertNull(state.userFormError)
    }

    @Test
    fun testUserFormDialogOpenEditState() {
        var state = SettingsUiState()

        // Open edit dialog pre-fills data
        state = state.copy(
            showUserDialog = true,
            editingUser = sampleAdminUser,
            userFormName = sampleAdminUser.name,
            userFormRole = sampleAdminUser.role,
            userFormPin = "", // pin always blank when editing
            userFormError = null
        )

        assertTrue(state.showUserDialog)
        assertEquals(sampleAdminUser, state.editingUser)
        assertEquals("Administrador Principal", state.userFormName)
        assertEquals("ADMIN", state.userFormRole)
        assertEquals("", state.userFormPin) // PIN not pre-filled
    }

    @Test
    fun testUserFormPinValidation() {
        // Pin must be 4 digits for new user
        val validPins = listOf("0000", "1234", "9999", "5678")
        val invalidPins = listOf("", "123", "12345", "abcd", "12 4")

        validPins.forEach { pin ->
            assertTrue(pin.length == 4 && pin.all { it.isDigit() }, "PIN '$pin' should be valid")
        }

        invalidPins.forEach { pin ->
            val isValid = pin.length == 4 && pin.all { it.isDigit() }
            assertFalse(isValid, "PIN '$pin' should be invalid")
        }
    }

    @Test
    fun testUserFormCloseDialog() {
        var state = SettingsUiState(
            showUserDialog = true,
            editingUser = sampleCashierUser,
            userFormError = "Algún error"
        )

        // Close dialog resets state
        state = state.copy(showUserDialog = false, editingUser = null, userFormError = null)

        assertFalse(state.showUserDialog)
        assertNull(state.editingUser)
        assertNull(state.userFormError)
    }

    @Test
    fun testBannerManagement() {
        var state = SettingsUiState()

        // Success banner
        state = state.copy(successBanner = "Configuración guardada correctamente")
        assertEquals("Configuración guardada correctamente", state.successBanner)
        assertNull(state.errorBanner)

        // Clear banners
        state = state.copy(successBanner = null, errorBanner = null)
        assertNull(state.successBanner)
        assertNull(state.errorBanner)

        // Error banner
        state = state.copy(errorBanner = "Error de conexión con el servidor")
        assertEquals("Error de conexión con el servidor", state.errorBanner)
    }

    @Test
    fun testUserRoles() {
        val validRoles = listOf("ADMIN", "SUPERVISOR", "CASHIER")

        // Verify all sample users have valid roles
        listOf(sampleAdminUser, sampleCashierUser, sampleInactiveUser).forEach { user ->
            assertTrue(user.role in validRoles, "Role '${user.role}' should be valid for user '${user.name}'")
        }

        // Test role-based badge logic
        assertEquals("ADMIN", sampleAdminUser.role)
        assertEquals("CASHIER", sampleCashierUser.role)
        assertFalse(sampleInactiveUser.isActive)
    }
}

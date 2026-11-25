package mx.nexuspos.desktop.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.system.OperatingPolicies
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SettingsPreferencesPatch
import kotlin.test.*

class SettingsWorkspaceTest {
    @Test
    fun lateResponsesFromThePreviousServerDoNotReplaceNewSettingsOrUsers() {
        val oldSettings = CompletableDeferred<Result<SettingsDto>>()
        val oldUsers = CompletableDeferred<Result<List<UserDto>>>()
        val api = object : ApiClient(baseUrl = "http://old-box") {
            override suspend fun getSettings() =
                if (baseUrl.endsWith("old-box")) oldSettings.await()
                else Result.success(SettingsDto(storeName = "Nueva sucursal"))

            override suspend fun getUsers(includeInactive: Boolean) =
                if (baseUrl.endsWith("old-box")) oldUsers.await()
                else Result.success(listOf(UserDto("new", "Nuevo administrador", "ADMIN", true)))
        }
        val workspace = SettingsViewModel(api, CoroutineScope(Dispatchers.Unconfined))

        api.baseUrl = "http://new-box"
        workspace.loadSettings()
        workspace.loadUsers()
        oldSettings.complete(Result.success(SettingsDto(storeName = "Sucursal anterior")))
        oldUsers.complete(Result.success(listOf(UserDto("old", "Administrador anterior", "ADMIN", true))))

        val state = workspace.uiState.value
        assertTrue(state.settingsAvailable)
        assertEquals("Nueva sucursal", state.storeName)
        assertEquals(listOf("new"), state.users.map { it.id })
    }

    @Test
    fun aFailedPolicySaveKeepsTheLastAppliedValuesAndTheStoreDraft() {
        val response = CompletableDeferred<Result<SettingsDto>>()
        val requests = mutableListOf<SettingsPreferencesPatch>()
        val api = object : ApiClient() {
            override suspend fun getSettings() = Result.success(SettingsDto())
            override suspend fun getUsers(includeInactive: Boolean) = Result.success(emptyList<UserDto>())
            override suspend fun updateSettingsPreferences(patch: SettingsPreferencesPatch): Result<SettingsDto> {
                requests.add(patch)
                return response.await()
            }
        }
        val workspace = SettingsViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        val policies = OperatingPolicies(false, true, 15.0, false, 0.0, 0)
        workspace.onStoreNameChanged("Borrador de tienda")

        workspace.saveOperatingPolicies(policies)
        workspace.saveOperatingPolicies(policies)
        assertTrue(workspace.uiState.value.isSaving)
        assertEquals(listOf(SettingsPreferencesPatch(policies = policies)), requests)
        response.complete(Result.failure(IllegalStateException("Servidor inaccesible")))

        val state = workspace.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.allowNegativeStock)
        assertEquals("Borrador de tienda", state.storeName)
        assertEquals("Servidor inaccesible", state.errorBanner)
    }
}

package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.settings.SettingsUiState
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.contracts.identity.UserDto

private val userRoles = listOf("CASHIER" to "Cajero", "SUPERVISOR" to "Supervisor", "ADMIN" to "Administrador")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserSettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.showUserDialog) {
        UserEditor(state, viewModel)
        return
    }

    var pendingDeactivation by remember { mutableStateOf<UserDto?>(null) }
    pendingDeactivation?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDeactivation = null },
            title = { Text("Desactivar usuario") },
            text = { Text("${user.name} perderá el acceso a caja. Su historial se conserva.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deactivateUser(user)
                    pendingDeactivation = null
                }) { Text("Desactivar") }
            },
            dismissButton = { TextButton(onClick = { pendingDeactivation = null }) { Text("Cancelar") } }
        )
    }
    val q = state.userFilter.trim()
    val filtered = state.users.filter { it.name.contains(q, ignoreCase = true) }
    val busy = state.userStatusChangeId != null
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsField("Buscar usuario", state.userFilter, viewModel::onUserFilterChanged)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.includeInactiveUsers, onClick = viewModel::onToggleIncludeInactiveUsers,
                label = { Text("Incluir inactivos") }
            )
            Button(viewModel::openCreateUserDialog, enabled = !busy) { Text("Nuevo usuario [Ins]") }
            TextButton({ viewModel.loadUsers() }, enabled = !busy && !state.isLoadingUsers) { Text("Actualizar") }
        }
        if (state.isLoadingUsers || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (filtered.isEmpty() && !state.isLoadingUsers) {
            Text(if (q.isBlank()) "No hay usuarios para mostrar." else "No encontramos usuarios con ese nombre.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.id }) { user ->
                val role = userRoles.firstOrNull { it.first == user.role }?.second ?: user.role
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.name, style = MaterialTheme.typography.titleSmall)
                        Text("$role · ${if (user.isActive) "Activo" else "Inactivo"}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (user.isActive) {
                                TextButton({ viewModel.openEditUserDialog(user) }, enabled = !busy) { Text("Editar") }
                                TextButton({ pendingDeactivation = user }, enabled = !busy) { Text("Desactivar") }
                            } else {
                                TextButton({ viewModel.reactivateUser(user) }, enabled = !busy) { Text("Reactivar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserEditor(state: SettingsUiState, viewModel: SettingsViewModel) {
    val editing = state.editingUser != null
    val enabled = !state.isSaving
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(if (editing) "Editar usuario" else "Nuevo usuario") {
                SettingsField(
                    "Nombre completo", state.userFormName, viewModel::onUserFormNameChanged,
                    modifier = Modifier.focusRequester(nameFocus), enabled = enabled
                )
                SettingsChoice("Rol", state.userFormRole, userRoles, viewModel::onUserFormRoleChanged, enabled = enabled)
                OutlinedTextField(
                    value = state.userFormPin,
                    onValueChange = viewModel::onUserFormPinChanged,
                    label = { Text("PIN de 4 dígitos") },
                    supportingText = if (editing) ({ Text("Deja el campo vacío para conservar el PIN actual.") }) else null,
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                state.userFormError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(viewModel::closeUserDialog, enabled = enabled) { Text("Cancelar") }
            Button(viewModel::saveUser, enabled = enabled) {
                Text(if (!enabled) "Guardando…" else if (editing) "Guardar usuario" else "Crear usuario")
            }
        }
    }
}

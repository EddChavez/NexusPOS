package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.identity.PermissionDto
import mx.nexuspos.contracts.identity.RoleDto
import mx.nexuspos.contracts.identity.SaveRoleRequest

@Composable
internal fun RolesSettingsV2(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var roles by remember { mutableStateOf<List<RoleDto>>(emptyList()) }
    var definitions by remember { mutableStateOf<List<PermissionDto>>(emptyList()) }
    var selected by remember { mutableStateOf<RoleDto?>(null) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var checks by remember { mutableStateOf(emptySet<String>()) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        api.listRoles().onSuccess { roles = it }.onFailure { error = it.message }
        api.permissionCatalog().onSuccess { definitions = it }.onFailure { error = it.message }
    }
    fun open(role: RoleDto?, duplicate: Boolean = false) {
        selected = role.takeUnless { duplicate }
        name = if (duplicate) "" else role?.name.orEmpty()
        checks = role?.permissions.orEmpty()
        error = null
        editing = true
    }
    LaunchedEffect(api) { load() }
    val role = selected
    val readOnly = role?.builtIn == true
    if (confirmingDelete && role != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Eliminar rol") },
            text = { Text("Se eliminará el rol ${role.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    busy = true
                    scope.launch {
                        api.deleteRole(role.id).onSuccess { editing = false; selected = null; load() }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Volver") } }
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(null) }, enabled = !busy && definitions.isNotEmpty()) { Text("Nuevo rol") }
                LazyColumn {
                    items(roles, key = { it.id }) { item ->
                        TextButton(onClick = { open(item) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text("${item.assignedUsers} asignados", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (!editing) {
                Text("Selecciona un rol para consultar sus permisos.")
            } else Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre del rol") },
                    enabled = !readOnly && !busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, label = { Text("Buscar permiso") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val groups = definitions.groupBy(PermissionDto::group)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((group, all) in groups) {
                        val matches = all.filter { it.label.contains(query.trim(), ignoreCase = true) || group.contains(query.trim(), ignoreCase = true) }
                        if (matches.isEmpty()) continue
                        item(key = group) {
                            OutlinedButton(onClick = {
                                expanded = if (group in expanded) expanded - group else expanded + group
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("$group · ${all.count { it.key in checks }}/${all.size}")
                            }
                        }
                        if (group in expanded || query.isNotBlank()) items(matches, key = { it.key }) { permission ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (permission.parent == null) 0.dp else 16.dp)) {
                                Checkbox(
                                    checked = permission.key in checks,
                                    onCheckedChange = { checked ->
                                        checks = if (checked) checks + permission.key else checks - permission.key -
                                            definitions.filter { it.parent == permission.key }.map(PermissionDto::key).toSet()
                                    },
                                    enabled = !readOnly && !busy && (permission.parent == null || permission.parent in checks)
                                )
                                Text(permission.label)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (readOnly) {
                        OutlinedButton(onClick = { open(role, duplicate = true) }, enabled = !busy) { Text("Duplicar rol") }
                    } else {
                        if (role != null) TextButton(onClick = { confirmingDelete = true }, enabled = !busy && role.assignedUsers == 0) { Text("Eliminar") }
                        Button(onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                api.saveRole(role?.id, SaveRoleRequest(name, checks)).onSuccess {
                                    open(it)
                                    load()
                                }.onFailure { error = it.message }
                                busy = false
                            }
                        }, enabled = !busy && name.isNotBlank()) { Text("Guardar rol") }
                    }
                }
            }
        }
    }
}

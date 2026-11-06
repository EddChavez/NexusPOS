package mx.nexuspos.client.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.identity.UserDto

@Composable
fun UserFormDialog(
    editingUser: UserDto?,
    name: String,
    role: String,
    pin: String,
    errorMessage: String?,
    isSaving: Boolean,
    onNameChanged: (String) -> Unit,
    onRoleChanged: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = editingUser != null

    val roleOptions = listOf(
        "CASHIER" to "Cajero",
        "SUPERVISOR" to "Supervisor",
        "ADMIN" to "Administrador"
    )

    var roleExpanded by remember { mutableStateOf(false) }
    val roleLabel = roleOptions.firstOrNull { it.first == role }?.second ?: role

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = NexusTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isEditing) "Editar Usuario" else "Nuevo Usuario",
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = errorMessage != null && name.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Rol
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = roleLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = {
                            IconButton(onClick = { roleExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar rol")
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roleOptions.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onRoleChanged(code)
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                // PIN
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChanged,
                    label = { Text("PIN (4 dígitos)") },
                    placeholder = {
                        Text(
                            if (isEditing) "Dejar en blanco para conservar PIN actual"
                            else "Ingrese 4 dígitos"
                        )
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = errorMessage != null && errorMessage.contains("PIN", ignoreCase = true),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                // Error message
                if (errorMessage != null) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NexusTheme.colors.bgCanvas
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isEditing) "Guardar Cambios" else "Crear Usuario", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                border = androidx.compose.foundation.BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    )
}

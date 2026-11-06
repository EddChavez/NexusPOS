package mx.nexuspos.client.features.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.DepartmentDto

@Composable
fun DepartmentsManagementDialog(
    departments: List<DepartmentDto>,
    isSubmitting: Boolean,
    onCreateDepartment: (String) -> Unit,
    onUpdateDepartment: (String, String) -> Unit,
    onDeleteDepartment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newDeptName by remember { mutableStateOf("") }
    var editingDeptId by remember { mutableStateOf<String?>(null) }
    var editingDeptName by remember { mutableStateOf("") }
    var deptToDelete by remember { mutableStateOf<DepartmentDto?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Departamentos y Categorías",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                Surface(
                    color = NexusTheme.colors.accentLight,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.accent)
                ) {
                    Text(
                        text = "${departments.size} registrados",
                        color = NexusTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 480.dp, max = 560.dp)
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Diálogo de confirmación de eliminación
                if (deptToDelete != null) {
                    Surface(
                        color = NexusTheme.colors.warningBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.warningBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "¿Eliminar departamento '${deptToDelete?.name}'?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.warningText
                            )
                            Text(
                                text = "Los productos asociados a este departamento no se borrarán; se reasignarán automáticamente a la categoría 'General'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { deptToDelete = null }) {
                                    Text("Cancelar")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        deptToDelete?.let { onDeleteDepartment(it.id) }
                                        deptToDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.dangerText)
                                ) {
                                    Text("Sí, Eliminar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 1. Alta de nuevo departamento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newDeptName,
                        onValueChange = { newDeptName = it },
                        placeholder = { Text("Nuevo departamento (ej. Panadería, Farmacia)...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (newDeptName.isNotBlank()) {
                                onCreateDepartment(newDeptName.trim())
                                newDeptName = ""
                            }
                        },
                        enabled = newDeptName.isNotBlank() && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // 2. Lista de departamentos existentes
                if (departments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No hay departamentos registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(departments, key = { it.id }) { dept ->
                            val isEditingThis = editingDeptId == dept.id

                            Surface(
                                color = NexusTheme.colors.bgSurfaceSubtle,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isEditingThis) {
                                        OutlinedTextField(
                                            value = editingDeptName,
                                            onValueChange = { editingDeptName = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    if (editingDeptName.isNotBlank()) {
                                                        onUpdateDepartment(dept.id, editingDeptName.trim())
                                                        editingDeptId = null
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Guardar",
                                                    tint = NexusTheme.colors.textGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { editingDeptId = null },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Cancelar",
                                                    tint = NexusTheme.colors.dangerText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = dept.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NexusTheme.colors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editingDeptId = dept.id
                                                    editingDeptName = dept.name
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Editar",
                                                    tint = NexusTheme.colors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { deptToDelete = dept },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Eliminar",
                                                    tint = NexusTheme.colors.dangerText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

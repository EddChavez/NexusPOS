package mx.nexuspos.client.features.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.credit.CustomerDto

@Composable
fun CustomerFormDialog(
    customerToEdit: CustomerDto?,
    onSave: (
        name: String,
        phone: String,
        email: String,
        rfc: String,
        address: String,
        notes: String,
        creditEnabled: Boolean,
        hasCreditLimit: Boolean,
        creditLimitCents: Long
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = customerToEdit != null
    var name by remember { mutableStateOf(customerToEdit?.name ?: "") }
    var phone by remember { mutableStateOf(customerToEdit?.phone ?: "") }
    var email by remember { mutableStateOf(customerToEdit?.email ?: "") }
    var rfc by remember { mutableStateOf(customerToEdit?.rfc ?: "") }
    var address by remember { mutableStateOf(customerToEdit?.address ?: "") }
    var notes by remember { mutableStateOf(customerToEdit?.notes ?: "") }
    var creditEnabled by remember { mutableStateOf(customerToEdit?.creditEnabled ?: true) }
    var hasCreditLimit by remember { mutableStateOf(customerToEdit?.hasCreditLimit ?: true) }
    var creditLimitPesos by remember {
        mutableStateOf(
            if (customerToEdit != null && customerToEdit.creditLimitCents > 0) {
                (customerToEdit.creditLimitCents / 100.0).toString()
            } else {
                "1000.00"
            }
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdit) "Editar Cliente (${customerToEdit?.folio})" else "Registrar Nuevo Cliente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                if (isEdit && !customerToEdit?.folio.isNullOrBlank()) {
                    Surface(
                        color = NexusTheme.colors.accentLight,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.accent)
                    ) {
                        Text(
                            text = customerToEdit?.folio ?: "",
                            color = NexusTheme.colors.accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 520.dp, max = 600.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Datos Generales
                Text(
                    text = "1. Datos de Contacto e Identidad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NexusTheme.colors.accent
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Nombre Completo *") },
                    placeholder = { Text("Ej. Don Pancho o María Elena Ramos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono / Celular") },
                        placeholder = { Text("555-123-4567") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo Electrónico (Opcional)") },
                        placeholder = { Text("cliente@email.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rfc,
                        onValueChange = { rfc = it.uppercase() },
                        label = { Text("RFC / Datos Fiscales") },
                        placeholder = { Text("XAXX010101000") },
                        singleLine = true,
                        modifier = Modifier.weight(0.45f)
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Domicilio / Referencias") },
                        placeholder = { Text("Calle, número, colonia o señas particulares") },
                        singleLine = true,
                        modifier = Modifier.weight(0.55f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas internas (Observaciones)") },
                    placeholder = { Text("Ej. Paga todos los viernes, familiar de Don Pepe...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Divider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 4.dp))

                // 2. Configuración de Crédito / Fiado
                Text(
                    text = "2. Configuración de Fiado / Crédito",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NexusTheme.colors.accent
                )

                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Autorizar Fiado a este Cliente",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Permite cobrar ventas con método [Fiado / Crédito]",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = creditEnabled,
                                onCheckedChange = { creditEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NexusTheme.colors.actionPrimary)
                            )
                        }

                        if (creditEnabled) {
                            Divider(color = NexusTheme.colors.borderSubtle)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = hasCreditLimit,
                                    onClick = { hasCreditLimit = true }
                                )
                                Text(
                                    text = "Con Límite Máximo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusTheme.colors.textPrimary,
                                    modifier = Modifier.padding(end = 16.dp)
                                )

                                RadioButton(
                                    selected = !hasCreditLimit,
                                    onClick = { hasCreditLimit = false }
                                )
                                Text(
                                    text = "Sin Límite (Confianza Total)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusTheme.colors.textPrimary
                                )
                            }

                            if (hasCreditLimit) {
                                OutlinedTextField(
                                    value = creditLimitPesos,
                                    onValueChange = { creditLimitPesos = it },
                                    label = { Text("Límite Autorizado ($ MXN)") },
                                    prefix = { Text("$ ") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationError = "El nombre completo del cliente es obligatorio"
                        return@Button
                    }
                    val limitCents = if (creditEnabled && hasCreditLimit) {
                        val parsed = creditLimitPesos.toDoubleOrNull()
                        if (parsed == null || parsed < 0) {
                            validationError = "Ingrese un límite de crédito válido mayor o igual a $0.00"
                            return@Button
                        }
                        (parsed * 100).toLong()
                    } else {
                        0L
                    }

                    onSave(
                        name.trim(),
                        phone.trim(),
                        email.trim(),
                        rfc.trim(),
                        address.trim(),
                        notes.trim(),
                        creditEnabled,
                        hasCreditLimit,
                        limitCents
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
            ) {
                Text(if (isEdit) "Guardar Cambios" else "Registrar Cliente")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder)
            ) {
                Text("Cancelar", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    )
}

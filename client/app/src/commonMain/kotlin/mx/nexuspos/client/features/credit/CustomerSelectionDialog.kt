package mx.nexuspos.client.features.credit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.credit.CustomerDto

@Composable
fun CustomerSelectionDialog(
    apiClient: ApiClient,
    currentCustomer: CustomerDto?,
    onSelectCustomer: (CustomerDto?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<CustomerDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        apiClient.searchCustomers(searchQuery).onSuccess {
            customers = it
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Seleccionar Cliente (Fiado / Crédito)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar por nombre, folio, teléfono o RFC") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexusTheme.colors.accent)
                    }
                } else if (customers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron clientes", color = NexusTheme.colors.textSecondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customers, key = { it.id }) { cust ->
                            val isSelected = currentCustomer?.id == cust.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) NexusTheme.colors.accentLight else NexusTheme.colors.bgSurfaceSubtle,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) NexusTheme.colors.accent else NexusTheme.colors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth().clickable { onSelectCustomer(cust) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (cust.folio.isNotBlank()) {
                                                Surface(
                                                    color = NexusTheme.colors.accentLight,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, NexusTheme.colors.accent)
                                                ) {
                                                    Text(
                                                        text = cust.folio,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = NexusTheme.colors.accent,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = cust.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = NexusTheme.colors.textPrimary
                                            )
                                        }

                                        if (cust.phone.isNotBlank()) {
                                            Text(
                                                text = "Tel: ${cust.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NexusTheme.colors.textSecondary
                                            )
                                        }
                                        val limitStr = if (!cust.hasCreditLimit) "Sin Límite" else "$${"%.2f".format(cust.creditLimitCents / 100.0)}"
                                        Text(
                                            text = "Límite: $limitStr | Saldo: $${"%.2f".format(cust.currentBalanceCents / 100.0)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NexusTheme.colors.textSecondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Disponible", style = MaterialTheme.typography.labelSmall, color = NexusTheme.colors.textSecondary)
                                        val dispStr = if (!cust.hasCreditLimit) "ILIMITADO" else "$${"%.2f".format(cust.availableCreditCents / 100.0)}"
                                        Text(
                                            text = dispStr,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!cust.hasCreditLimit || cust.availableCreditCents > 0) NexusTheme.colors.actionPrimary else NexusTheme.colors.dangerText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentCustomer != null) {
                OutlinedButton(
                    onClick = { onSelectCustomer(null) },
                    border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder)
                ) {
                    Text("Quitar Cliente Asignado", color = NexusTheme.colors.dangerText)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder)
            ) {
                Text("Cerrar", color = NexusTheme.colors.btnSecondaryText)
            }
        }
    )
}

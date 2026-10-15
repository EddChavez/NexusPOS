package mx.nexuspos.client.features.credit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.credit.CustomerDto

@Composable
fun CustomerSelectionDialog(
    apiClient: ApiClient,
    currentCustomer: CustomerDto?,
    onSelectCustomer: (CustomerDto?) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
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
            Text("Seleccionar Cliente (Fiado / Crédito)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar por nombre o teléfono") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (customers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No se encontraron clientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customers) { cust ->
                            val isSelected = currentCustomer?.id == cust.id
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable { onSelectCustomer(cust) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cust.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        if (cust.phone.isNotBlank()) {
                                            Text("Tel: ${cust.phone}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            "Límite: $${cust.creditLimitCents / 100.0} | Saldo: $${cust.currentBalanceCents / 100.0}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Disponible", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "$${cust.availableCreditCents / 100.0}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (cust.availableCreditCents > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
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
                OutlinedButton(onClick = { onSelectCustomer(null) }) {
                    Text("Quitar Cliente Asignado")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

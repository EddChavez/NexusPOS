package mx.nexuspos.client.features.customers

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto
import mx.nexuspos.core.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerLoyaltyDialog(
    isOpen: Boolean,
    customer: CustomerDto?,
    movements: List<LoyaltyMovementDto>,
    isLoading: Boolean,
    onAdjustBalance: (amountCents: Long, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen || customer == null) return

    var isAdjusting by remember { mutableStateOf(false) }
    var adjustType by remember { mutableStateOf("ADD") } // "ADD" or "SUBTRACT"
    var adjustAmountPesos by remember { mutableStateOf("") }
    var adjustReason by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(760.dp)
                .height(640.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Monedero Electrónico: ${customer.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Folio: ${customer.folio} • Historial y Saldo Acumulado",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // KPI Balances Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SALDO DISPONIBLE EN MONEDERO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(2.dp))
                            Text(Money(customer.loyaltyBalanceCents).format(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgCanvas),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TOTAL ACUMULADO HISTÓRICO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text(Money(customer.totalLoyaltyEarnedCents).format(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgCanvas),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TOTAL CANJEADO EN VENTAS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text(Money(customer.totalLoyaltyRedeemedCents).format(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.accent)
                        }
                    }
                }

                // Adjustment Form / Action
                if (!isAdjusting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kardex de Movimientos (${movements.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                        OutlinedButton(
                            onClick = { isAdjusting = true },
                            border = BorderStroke(1.dp, NexusTheme.colors.accent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexusTheme.colors.accent)
                            Spacer(Modifier.width(6.dp))
                            Text("Ajustar Saldo Manualmente", color = NexusTheme.colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Surface(
                        color = NexusTheme.colors.bgCanvas,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.accent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ajuste Manual de Saldo (Cortesía / Corrección)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = adjustType == "ADD",
                                        onClick = { adjustType = "ADD" },
                                        label = { Text("+ Abonar Saldo", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = adjustType == "SUBTRACT",
                                        onClick = { adjustType = "SUBTRACT" },
                                        label = { Text("- Restar Saldo", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = adjustAmountPesos,
                                    onValueChange = { adjustAmountPesos = it },
                                    label = { Text("Monto ($)") },
                                    singleLine = true,
                                    modifier = Modifier.width(140.dp)
                                )
                                OutlinedTextField(
                                    value = adjustReason,
                                    onValueChange = { adjustReason = it },
                                    label = { Text("Motivo / Justificación") },
                                    placeholder = { Text("ej. Cortesía por fidelidad, Corrección de saldo") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { isAdjusting = false }) {
                                    Text("Cancelar")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val amt = ((adjustAmountPesos.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
                                        if (amt > 0L) {
                                            val finalCents = if (adjustType == "ADD") amt else -amt
                                            onAdjustBalance(finalCents, adjustReason)
                                            isAdjusting = false
                                            adjustAmountPesos = ""
                                            adjustReason = ""
                                        }
                                    },
                                    enabled = (adjustAmountPesos.toDoubleOrNull() ?: 0.0) > 0.0
                                ) {
                                    Text("Confirmar Ajuste")
                                }
                            }
                        }
                    }
                }

                // Kardex Table
                Surface(
                    color = NexusTheme.colors.bgCanvas,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (movements.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No hay movimientos registrados en el monedero", color = NexusTheme.colors.textSecondary)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(movements) { mov ->
                                Surface(
                                    color = NexusTheme.colors.bgSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Badge
                                            val (badgeText, badgeBg, badgeFg) = when (mov.type) {
                                                "EARN" -> Triple("ACUMULACIÓN", Color(0xFF059669).copy(alpha = 0.15f), Color(0xFF059669))
                                                "REDEEM" -> Triple("CANJE VENTA", NexusTheme.colors.accent.copy(alpha = 0.15f), NexusTheme.colors.accent)
                                                "ADJUSTMENT" -> Triple("AJUSTE", Color(0xFFD97706).copy(alpha = 0.15f), Color(0xFFD97706))
                                                "REFUND_REVERSAL" -> Triple("REVERSIÓN", Color(0xFFDC2626).copy(alpha = 0.15f), Color(0xFFDC2626))
                                                "REFUND_CREDIT" -> Triple("REEMBOLSO", Color(0xFF2563EB).copy(alpha = 0.15f), Color(0xFF2563EB))
                                                else -> Triple(mov.type, NexusTheme.colors.bgSurfaceSubtle, NexusTheme.colors.textSecondary)
                                            }
                                            Surface(
                                                color = badgeBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeFg,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = mov.description.ifBlank { "Movimiento de monedero" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = NexusTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = dateFormat.format(Date(mov.createdAt)),
                                                    fontSize = 10.sp,
                                                    color = NexusTheme.colors.textSecondary
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            val isPositive = mov.amountCents >= 0L
                                            Text(
                                                text = (if (isPositive) "+" else "") + Money(mov.amountCents).format(),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp,
                                                color = if (isPositive) Color(0xFF059669) else Color(0xFFDC2626)
                                            )
                                            Text(
                                                text = "Saldo: ${Money(mov.balanceAfterCents).format()}",
                                                fontSize = 10.sp,
                                                color = NexusTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Close Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

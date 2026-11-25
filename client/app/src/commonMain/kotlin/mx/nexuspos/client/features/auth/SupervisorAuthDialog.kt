package mx.nexuspos.client.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme

@Composable
fun SupervisorAuthDialog(
    state: SupervisorAuthState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onToggleRootPin: () -> Unit = {}
) {
    if (!state.isVisible) return
    val pinLength = if (state.useRootPin) 6 else 4

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon and dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = "Autorización de Encargado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action description card
                val (actionTitle, actionSubtitle) = when (val target = state.targetAction) {
                    is SupervisorAction.AccessSettings -> Pair(
                        "Acceso a Configuración del Sistema [F5]",
                        "Se requiere rol de Administrador para modificar parámetros del sistema y usuarios."
                    )
                    is SupervisorAction.AdjustInventory -> Pair(
                        "Ajuste Manual de Inventario",
                        "Producto: ${target.product.name} (Stock: ${(target.product.currentStockMillis / 1000.0)})"
                    )
                    is SupervisorAction.CancelSale -> Pair(
                        "Cancelación de Venta Folio #${target.folio}",
                        "Motivo: ${target.reason}. Esta acción revertirá inventario, caja y crédito de forma atómica."
                    )
                    is SupervisorAction.ApplyDiscount -> Pair(
                        "Autorización de Descuento Especial",
                        if (target.discountPercent > 0.0) {
                            "Aplicar descuento del ${target.discountPercent}% que excede el límite permitido sin supervisor."
                        } else {
                            "Aplicar descuento de $${target.discountCents / 100.0} pesos que requiere autorización."
                        }
                    )
                    is SupervisorAction.RefundSale -> Pair(
                        "Devolución de Mercancía Ticket #${target.folio}",
                        "Motivo: ${target.request.reason}. Se autorizará la devolución de ${target.request.lines.size} partida(s)."
                    )
                    null -> Pair("Acción Protegida", "Requiere PIN de Encargado o Administrador.")
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = actionTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = actionSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onToggleRootPin, enabled = !state.isLoading) {
                    Text(if (state.useRootPin) "Usar PIN de 4 dígitos" else "Usar PIN raíz")
                }

                // PIN display dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    repeat(pinLength) { index ->
                        val isFilled = index < state.pinInput.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) Color(0xFFD97706)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) Color(0xFFD97706)
                                    else NexusTheme.colors.borderPrimary,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error Banner
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Numeric Keypad
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    rows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { digit ->
                                CompactKeypadButton(
                                    text = digit,
                                    enabled = !state.isLoading,
                                    onClick = { onDigit(digit[0]) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Bottom Row: Backspace, 0, Enter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBackspace,
                            enabled = !state.isLoading && state.pinInput.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusTheme.colors.dangerBg,
                                contentColor = NexusTheme.colors.dangerText
                            ),
                            border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Backspace, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                        }

                        CompactKeypadButton(
                            text = "0",
                            enabled = !state.isLoading,
                            onClick = { onDigit('0') },
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = onSubmit,
                            enabled = !state.isLoading && state.pinInput.length == pinLength,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("OK", fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancelar [Esc]", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactKeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

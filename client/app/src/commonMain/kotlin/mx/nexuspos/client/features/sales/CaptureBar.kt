package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente integral de la "Barra de Control de Venta y Captura Rápida".
 *
 * Contiene 3 filas en un contenedor [Column]:
 * 1. Fila Superior: Gestor multi-ticket y selector de cliente.
 * 2. Fila Central: Input de escaneo continuo con auto-enfoque y botón Enter.
 * 3. Fila Inferior: Cinta de operaciones auxiliares con atajos de teclado visibles.
 */
@Composable
fun CaptureBar(
    state: CaptureBarState,
    onEvent: (CaptureBarEvent) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    // Garantizar que el foco regrese al campo de escaneo al conmutar tickets
    LaunchedEffect(state.activeTicketId) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // Ignorar si el componente aún no está montado en el frame inicial
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // =========================================================================
            // FILA 1: GESTOR DE CUENTAS (MULTI-TICKET) Y CLIENTE
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: Tabs de tickets, badge de pendientes y botón '+'
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    state.tickets.forEach { tab ->
                        val isSelected = tab.isActive
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val borderColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }

                        Surface(
                            color = containerColor,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier
                                .clickable { onEvent(CaptureBarEvent.SelectTicket(tab.id)) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    tab.totalFormatted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (tab.canClose && (isSelected || state.tickets.size > 1)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar ${tab.label}",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onEvent(CaptureBarEvent.CloseTicket(tab.id)) }
                                    )
                                }
                            }
                        }
                    }

                    // Indicador de tickets pendientes
                    if (state.pendingTicketsCount > 0) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFE65100), CircleShape)
                                )
                                Text(
                                    "${state.pendingTicketsCount} ${if (state.pendingTicketsCount == 1) "pendiente" else "pendientes"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }

                    // Botón para crear nueva venta / ticket
                    OutlinedIconButton(
                        onClick = { onEvent(CaptureBarEvent.NewTicket) },
                        modifier = Modifier.size(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nuevo Ticket",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Derecha: Selector rápido de cliente actual (Chip interactivo)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onEvent(CaptureBarEvent.OpenCustomerSelector) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Cliente",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Cliente:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            state.currentCustomerName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Cambiar",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // FILA 2: LÍNEA DE COMANDO / INPUT DE ESCANEO
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TextField Principal de Captura Rápida
                OutlinedTextField(
                    value = state.scannerInput,
                    onValueChange = { onEvent(CaptureBarEvent.ScannerInputChanged(it)) },
                    placeholder = {
                        Text(
                            "Escanear código de barras o teclear SKU / nombre...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Lector de Código de Barras",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        // Badge visual de estado del escáner en el interior
                        Surface(
                            color = when (state.scannerStatus) {
                                ScannerStatus.READY -> Color(0xFFE8F5E9)
                                ScannerStatus.READING -> Color(0xFFFFF3E0)
                                ScannerStatus.PROCESSING -> Color(0xFFE3F2FD)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            when (state.scannerStatus) {
                                                ScannerStatus.READY -> Color(0xFF2E7D32)
                                                ScannerStatus.READING -> Color(0xFFEF6C00)
                                                ScannerStatus.PROCESSING -> Color(0xFF1976D2)
                                            },
                                            CircleShape
                                        )
                                )
                                Text(
                                    state.scannerStatus.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (state.scannerStatus) {
                                        ScannerStatus.READY -> Color(0xFF2E7D32)
                                        ScannerStatus.READING -> Color(0xFFEF6C00)
                                        ScannerStatus.PROCESSING -> Color(0xFF1976D2)
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEvent(CaptureBarEvent.SubmitScanner) }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                // Botón ENTER Añadir
                Button(
                    onClick = { onEvent(CaptureBarEvent.SubmitScanner) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = "Añadir",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ENTER Añadir",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // =========================================================================
            // FILA 3: CINTA DE OPERACIONES AUXILIARES (ATAJOS)
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grupo Izquierdo de Operaciones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    ShortcutActionChip(
                        shortcut = "INS",
                        label = "Varios",
                        onClick = { onEvent(CaptureBarEvent.InsertGeneric) }
                    )
                    ShortcutActionChip(
                        shortcut = "Ctrl+P",
                        label = "Art. Común",
                        onClick = { onEvent(CaptureBarEvent.CommonArticles) }
                    )
                    ShortcutActionChip(
                        shortcut = "F10",
                        label = "Catálogo",
                        onClick = { onEvent(CaptureBarEvent.SearchCatalog) }
                    )
                    ShortcutActionChip(
                        shortcut = "F11",
                        label = "Mayoreo",
                        onClick = { onEvent(CaptureBarEvent.ApplyWholesale) }
                    )
                    ShortcutActionChip(
                        shortcut = "F7",
                        label = "Entradas",
                        onClick = { onEvent(CaptureBarEvent.CashIn) }
                    )
                    ShortcutActionChip(
                        shortcut = "F8",
                        label = "Salidas",
                        onClick = { onEvent(CaptureBarEvent.CashOut) }
                    )
                    ShortcutActionChip(
                        shortcut = "F9",
                        label = "Granel",
                        onClick = { onEvent(CaptureBarEvent.WeighBulk) }
                    )
                }

                // Extremo Derecho: [DEL] Borrar Art. (Acción destructiva)
                Surface(
                    color = if (state.isDeleteEnabled) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.isDeleteEnabled) Color(0xFFEF9A9A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .clickable(enabled = state.isDeleteEnabled) { onEvent(CaptureBarEvent.DeleteItem) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = if (state.isDeleteEnabled) Color(0xFFC62828) else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                "DEL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            "Borrar Art.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isDeleteEnabled) Color(0xFFC62828) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chip compacto que resalta la tecla de atajo en una cápsula y muestra su etiqueta de acción.
 */
@Composable
private fun ShortcutActionChip(
    shortcut: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(3.dp)
            ) {
                Text(
                    shortcut,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

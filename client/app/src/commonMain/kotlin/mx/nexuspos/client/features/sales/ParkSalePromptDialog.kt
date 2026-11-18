package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.core.Money

@Composable
fun ParkSalePromptDialog(
    isOpen: Boolean,
    itemsCount: Int,
    total: Money,
    defaultLabel: String = "",
    onConfirm: (label: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var labelText by remember(isOpen, defaultLabel) { mutableStateOf(defaultLabel) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isOpen) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.5.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
                .padding(16.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter -> {
                                onConfirm(labelText)
                                true
                            }
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PauseCircle,
                        contentDescription = "Poner en Espera",
                        tint = NexusTheme.colors.warningText,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Poner Ticket en Espera [F4]",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                        Text(
                            text = "Se guardará en la base de datos central para recuperarlo en cualquier caja.",
                            fontSize = 12.sp,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Resumen del ticket actual
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Artículos en venta",
                                fontSize = 11.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = "$itemsCount productos",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total acumulado",
                                fontSize = 11.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = total.format(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NexusTheme.colors.textGreen
                            )
                        }
                    }
                }

                // Campo de captura de referencia / nota
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Referencia / Nombre del Cliente (Opcional):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NexusTheme.colors.textPrimary
                    )
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                "Ej. Doña Carmen, Joven sudadera azul, Mesa 4",
                                fontSize = 13.sp,
                                color = NexusTheme.colors.textMuted
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirm(labelText) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexusTheme.colors.borderPrimary,
                            unfocusedBorderColor = NexusTheme.colors.borderSubtle,
                            focusedTextColor = NexusTheme.colors.textPrimary,
                            unfocusedTextColor = NexusTheme.colors.textPrimary,
                            focusedContainerColor = NexusTheme.colors.bgSurfaceSubtle,
                            unfocusedContainerColor = NexusTheme.colors.bgSurfaceSubtle
                        )
                    )
                }

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NexusTheme.colors.textSecondary
                        )
                    ) {
                        Text("Cancelar [Esc]")
                    }

                    Button(
                        onClick = { onConfirm(labelText) },
                        modifier = Modifier.weight(1.5f).height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusTheme.colors.warningText,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            "Poner en Espera [Enter]",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.client.theme.NexusTheme

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    connectionState: ServerConnectionState = ServerConnectionState.CONNECTED,
    registerId: String = "01",
    onConfigureServerConnection: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onRecoverRoot: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val pinLength = if (state.useRootPin) 6 else 4

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.bgCanvas),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.bgSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. BRAND HEADER & NETWORK STATUS
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "NexusPOS",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Terminal de Punto de Venta • Caja $registerId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    // Network Indicator
                    val isConnected = connectionState == ServerConnectionState.CONNECTED
                    Surface(
                        color = if (isConnected) Color(0xFF059669).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (onConfigureServerConnection != null) {
                                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onConfigureServerConnection() }
                                } else Modifier
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isConnected) Color(0xFF059669) else Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isConnected) {
                                    "Servidor Central Conectado"
                                } else {
                                    "Sin Conexión al Servidor (clic para configurar)"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Divider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)

                // 2. PIN DISPLAY (4 MASKED DOTS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ingresa tu PIN de $pinLength dígitos",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pinLength) { index ->
                            val isFilled = index < state.pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isFilled) MaterialTheme.colorScheme.primary
                                        else NexusTheme.colors.borderPrimary,
                                        shape = CircleShape
                                    )
                            )
                        }
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // 3. ON-SCREEN NUMERIC KEYPAD
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    rows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowKeys.forEach { digit ->
                                KeypadButton(
                                    text = digit,
                                    enabled = !state.isLoading,
                                    onClick = { viewModel.onDigit(digit[0]) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Bottom Row: Clear (C), 0, Submit (↵)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Clear / Backspace
                        Button(
                            onClick = { viewModel.onBackspace() },
                            enabled = !state.isLoading && state.pinInput.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusTheme.colors.dangerBg,
                                contentColor = NexusTheme.colors.dangerText,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Borrar",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 0 Button
                        KeypadButton(
                            text = "0",
                            enabled = !state.isLoading,
                            onClick = { viewModel.onDigit('0') },
                            modifier = Modifier.weight(1f)
                        )

                        // Enter Button
                        Button(
                            onClick = { viewModel.submitLogin() },
                            enabled = !state.isLoading && state.pinInput.length == pinLength,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Entrar",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                TextButton(viewModel::toggleRootPin, enabled = !state.isLoading) {
                    Text(if (state.useRootPin) "Usar PIN de 4 dígitos" else "Usar PIN raíz")
                }
                if (onRecoverRoot != null) {
                    TextButton(onRecoverRoot, enabled = !state.isLoading) { Text("Recuperar PIN raíz") }
                }

                // Keyboard shortcuts
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⌨️ Teclado físico numérico [0-9, Enter, Backspace] activo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    state.shift?.let { prompt ->
        LoginShiftDialog(
            prompt = prompt,
            busy = state.isLoading,
            onConfirm = viewModel::confirmLoginShift,
            onRetry = viewModel::retryLoginShift,
            onCancel = viewModel::cancelLoginShift
        )
    }
}

@Composable
private fun LoginShiftDialog(
    prompt: LoginShiftPrompt,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    var amount by remember(prompt.registerId) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val requiresFund = prompt.currencySymbol != null
    val validAmount = parseCheckoutCents(amount) != null

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (requiresFund) "Fondo de caja" else "Abrir turno") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (requiresFund) {
                    Text("Ingresa el efectivo inicial para abrir el turno en la caja ${prompt.registerId}.")
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Fondo inicial") },
                        prefix = { Text(prompt.currencySymbol.orEmpty()) },
                        singleLine = true,
                        readOnly = busy,
                        isError = amount.isNotBlank() && !validAmount,
                        supportingText = {
                            Text(
                                if (amount.isNotBlank() && !validAmount) "Ingresa un monto desde cero con hasta dos decimales."
                                else "Si la caja inicia sin efectivo, ingresa 0."
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                            if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyDown && validAmount && !busy) onConfirm(amount)
                            true
                        }
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else {
                    Text("Caja ${prompt.registerId}")
                }
                prompt.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { if (requiresFund) onConfirm(amount) else onRetry() },
                enabled = !busy && (!requiresFund || validAmount)
            ) { Text(if (requiresFund) "Abrir turno" else "Reintentar") }
        },
        dismissButton = { TextButton(onCancel, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun KeypadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

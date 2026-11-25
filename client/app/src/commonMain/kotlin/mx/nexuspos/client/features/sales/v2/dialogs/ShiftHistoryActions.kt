package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.whatsapp.WhatsAppMessageFormatter
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money

internal enum class ShiftHistoryAction(val title: String) {
    CANCEL("Cancelar venta"),
    REFUND("Registrar devolución"),
    WHATSAPP("Compartir por WhatsApp"),
    REPRINT("Reimprimir ticket")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShiftActionPane(
    error: String?,
    busy: Boolean,
    footer: @Composable FlowRowScope.() -> Unit,
    summary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        error?.let { ShiftActionMessage(it, isError = true) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider()
        summary?.invoke()
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = footer
        )
    }
}

@Composable
internal fun ShiftActionMessage(text: String, isError: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (isError) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onSecondaryContainer
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ShiftSupervisorPane(
    summary: String,
    confirmLabel: String,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
    destructive: Boolean = false
) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = {
        if (pin.length == 4 && !busy) {
            val enteredPin = pin
            pin = ""
            onConfirm(enteredPin)
        }
    }
    ShiftActionPane(
        error = error,
        busy = busy,
        footer = {
            TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
            Button(
                onClick = submit,
                enabled = pin.length == 4 && !busy,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        }
    ) {
        Text("Autorización de encargado", style = MaterialTheme.typography.titleLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pin,
            onValueChange = { value -> if (value.length <= 4 && value.all { it in '0'..'9' }) pin = value },
            label = { Text("PIN de encargado") },
            supportingText = { Text("4 dígitos") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown ||
                        (event.key != Key.Enter && event.key != Key.NumPadEnter)
                    ) return@onPreviewKeyEvent false
                    submit()
                    true
                }
        )
        LaunchedEffect(busy) { if (!busy) focus.requestFocus() }
    }
}

private val cancellationReasons = listOf(
    "Error al capturar productos",
    "Cliente desistió de la compra",
    "Producto defectuoso / devuelto",
    "Cobro duplicado",
    "Otro motivo"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShiftCancelPane(
    sale: SaleResponse,
    requiresSupervisor: Boolean,
    authorizing: Boolean,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onAuthorize: () -> Unit,
    onConfirm: (reason: String, pin: String?) -> Unit
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val other = reason == cancellationReasons.last()
    val valid = reason != null && (!other || notes.isNotBlank())
    val finalReason = when {
        other -> notes.trim()
        notes.isNotBlank() -> "${reason.orEmpty()} · ${notes.trim()}"
        else -> reason.orEmpty()
    }
    val amount = Money(sale.totalCents).format()

    if (authorizing) {
        ShiftSupervisorPane(
            summary = "Cancelar el folio #${sale.folio} por $amount. Motivo: $finalReason",
            confirmLabel = "Autorizar cancelación",
            error = error,
            busy = busy,
            onBack = onBack,
            onConfirm = { onConfirm(finalReason, it) },
            destructive = true
        )
    } else {
        ShiftActionPane(error, busy, footer = {
            TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
            Button(
                onClick = { if (requiresSupervisor) onAuthorize() else onConfirm(finalReason, null) },
                enabled = valid && !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(if (requiresSupervisor) "Continuar a autorización" else "Confirmar cancelación") }
        }) {
            ShiftActionMessage(
                "Se cancelará la venta completa por $amount y se revertirán los movimientos de inventario, caja y crédito que correspondan.",
                isError = true
            )
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!busy) expanded = it }) {
                OutlinedTextField(
                    value = reason.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !busy,
                    label = { Text("Motivo de cancelación") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    cancellationReasons.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { reason = option; expanded = false })
                    }
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                enabled = !busy,
                label = { Text(if (other) "Describe el motivo" else "Observaciones (opcional)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun ShiftWhatsAppPane(
    message: String,
    countryCode: String,
    error: String?,
    onBack: () -> Unit,
    onCopy: (String) -> Result<Unit>,
    onOpen: (String) -> Result<Unit>,
    onOpened: () -> Unit,
    onError: (String?) -> Unit,
    initialPhone: String = ""
) {
    var phone by remember(message, initialPhone) { mutableStateOf(initialPhone) }
    var copied by remember { mutableStateOf(false) }
    val normalized = WhatsAppMessageFormatter.cleanAndFormatPhoneNumber(phone, countryCode)
    val valid = phone.isBlank() || (phone.all { it in "0123456789+()- .	" } &&
        normalized.length in 10..15 && normalized.first() != '0')
    ShiftActionPane(error, busy = false, footer = {
        TextButton(onClick = onBack) { Text("Volver") }
        TextButton(onClick = {
            onCopy(message).fold(
                onSuccess = { copied = true; onError(null) },
                onFailure = { onError(it.message ?: "No se pudo copiar el mensaje.") }
            )
        }) { Text(if (copied) "Copiado" else "Copiar") }
        Button(
            onClick = {
                onOpen(WhatsAppMessageFormatter.buildWhatsAppUrl(phone, message, countryCode)).fold(
                    onSuccess = { onOpened() },
                    onFailure = { onError(it.message ?: "No se pudo abrir WhatsApp.") }
                )
            },
            enabled = valid
        ) { Text("Abrir WhatsApp") }
    }) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Teléfono (opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            isError = !valid,
            supportingText = {
                Text(when {
                    !valid -> "Escribe un número válido, con lada si corresponde."
                    phone.isBlank() -> "Si lo dejas vacío, podrás elegir el contacto en WhatsApp."
                    else -> "Número de destino: +$normalized"
                })
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Vista previa del mensaje", style = MaterialTheme.typography.titleSmall)
        ShiftReceiptText(message)
    }
}

@Composable
internal fun ShiftReprintPane(
    receipt: String,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onPrint: () -> Unit
) {
    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(onClick = onPrint, enabled = !busy) { Text("Imprimir copia") }
    }) {
        Text("Vista previa de la copia", style = MaterialTheme.typography.titleSmall)
        ShiftReceiptText(receipt)
    }
}

@Composable
private fun ShiftReceiptText(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(value, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

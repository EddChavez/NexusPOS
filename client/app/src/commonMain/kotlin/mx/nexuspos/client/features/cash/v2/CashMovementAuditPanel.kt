package mx.nexuspos.client.features.cash.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.CashCutHistoryAccessDenied
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.cash.AuditedCashMovement
import mx.nexuspos.contracts.cash.CashMovementCursor
import mx.nexuspos.contracts.cash.CashMovementHistoryPage
import mx.nexuspos.contracts.cash.CashMovementHistoryRequest
import mx.nexuspos.contracts.cash.ReverseCashMovementRequest
import mx.nexuspos.core.Money

@Composable
internal fun CashMovementAuditPanel(
    api: ApiClient,
    sessionId: String,
    registerName: String,
    cutPin: String? = null,
    onBackToCut: (() -> Unit)? = null,
    onAccessDenied: ((CashCutHistoryAccessDenied) -> Unit)? = null,
    onReversed: (AuditedCashMovement) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    var pin by remember { mutableStateOf(cutPin.orEmpty()) }
    var page by remember { mutableStateOf<CashMovementHistoryPage?>(null) }
    var trail by remember { mutableStateOf(listOf<CashMovementCursor?>(null)) }
    var selected by remember { mutableStateOf<AuditedCashMovement?>(null) }
    var reason by remember { mutableStateOf("") }
    var reversalPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    fun lock() {
        page = null
        selected = null
        trail = listOf(null)
        pin = ""
        reason = ""
        reversalPin = ""
        success = null
        error = null
    }

    fun showError(failure: Throwable) {
        if (failure is CashCutHistoryAccessDenied) {
            lock()
            onAccessDenied?.invoke(failure)
        }
        error = failure.message ?: "No se pudo completar la operación."
    }

    suspend fun fetch(destination: List<CashMovementCursor?>) {
        api.getCashMovements(sessionId, CashMovementHistoryRequest(pin, destination.last())).onSuccess {
            trail = destination
            page = it
            scroll.requestScrollToItem(0)
        }.onFailure(::showError)
    }

    fun load(destination: List<CashMovementCursor?>) {
        if (busy || pin.length != 4) return
        busy = true
        error = null
        scope.launch {
            fetch(destination)
            busy = false
        }
    }

    fun reverse(movement: AuditedCashMovement) {
        if (busy || reason.trim().length !in 1..256 || reversalPin.length != 4) return
        val request = ReverseCashMovementRequest(reversalPin, reason.trim())
        busy = true
        error = null
        success = null
        scope.launch {
            api.reverseCashMovement(sessionId, movement.id, request).onSuccess { saved ->
                page = page?.let { previous ->
                    previous.copy(movements = previous.movements.map {
                        if (it.id == movement.id) it.copy(reversedByMovementId = saved.id) else it
                    })
                }
                selected = null
                reason = ""
                reversalPin = ""
                success = "Reversión registrada · ${saved.id}"
                onReversed(saved)
                fetch(listOf(null))
            }.onFailure {
                if (it is CashCutHistoryAccessDenied) reversalPin = ""
                error = it.message ?: "No se pudo registrar la reversión."
            }
            busy = false
        }
    }

    LaunchedEffect(sessionId) { if (cutPin != null) load(listOf(null)) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBackToCut != null || selected != null) {
            TextButton(onClick = {
                if (selected == null) onBackToCut?.invoke() else {
                    selected = null
                    reason = ""
                    reversalPin = ""
                    error = null
                }
            }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (selected == null) "Volver al corte" else "Volver a movimientos")
            }
        }
        val activePage = page
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected == null) "Movimientos de caja" else "Revertir movimiento",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (activePage != null && selected == null) {
                IconButton(onClick = { load(listOf(null)) }, enabled = !busy) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar movimientos")
                }
            }
            if (cutPin == null && activePage != null && selected == null) {
                TextButton(onClick = { lock() }, enabled = !busy) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bloquear")
                }
            }
        }
        Text("$registerName · $sessionId", style = MaterialTheme.typography.bodySmall)
        FeedbackMessageHost(error, success, onDismissError = { error = null }, onDismissSuccess = { success = null })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        val original = selected
        when {
            original != null -> CashMovementReversalForm(
                original, reason, { reason = it }, reversalPin,
                { value -> if (value.length <= 4 && value.all { it in '0'..'9' }) reversalPin = value },
                busy, { reverse(original) }
            )
            activePage == null -> {
                if (cutPin == null) {
                    Text("Ingresa el PIN de un encargado para consultar los movimientos.")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input -> if (input.length <= 4 && input.all { it in '0'..'9' }) pin = input },
                        label = { Text("PIN de encargado") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth()
                    )
                }
                Button(onClick = { load(listOf(null)) }, enabled = pin.length == 4 && !busy) {
                    Text(if (cutPin == null) "Consultar movimientos" else "Reintentar consulta")
                }
            }
            else -> {
                if (!activePage.isOpen || cutPin != null) {
                    Text("Turno cerrado · Consulta de solo lectura", style = MaterialTheme.typography.bodySmall)
                }
                if (!busy && activePage.movements.isEmpty()) Text("Este turno no tiene entradas ni salidas registradas.")
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activePage.movements, key = { it.id }) { movement ->
                        CashMovementRow(
                            movement = movement,
                            canReverse = cutPin == null && activePage.isOpen && movement.isReversible && !busy,
                            onReverse = {
                                reason = ""
                                reversalPin = ""
                                error = null
                                success = null
                                selected = movement
                            }
                        )
                    }
                }
                if (trail.size > 1 || activePage.next != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { load(trail.dropLast(1)) }, enabled = !busy && trail.size > 1) {
                            Text("Anterior")
                        }
                        OutlinedButton(
                            onClick = { activePage.next?.let { load(trail + it) } },
                            enabled = !busy && activePage.next != null
                        ) { Text("Siguiente") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashMovementRow(movement: AuditedCashMovement, canReverse: Boolean, onReverse: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val label = movement.type.movementLabel()
                Text(if (movement.reversesId == null) label else "$label compensatoria", style = MaterialTheme.typography.titleMedium)
                Text(movement.concept)
                Text(
                    "${formatShiftHistoryTimestamp(movement.createdAt, true)} · ${movement.id}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (movement.reversesId == null) Text("Registró · ${movement.cashierName}", style = MaterialTheme.typography.bodySmall)
                movement.reversedByMovementId?.let { Text("Revertido por $it", style = MaterialTheme.typography.bodySmall) }
                movement.reversesId?.let { Text("Movimiento original · $it", style = MaterialTheme.typography.bodySmall) }
                movement.authorizedByName?.let { Text("Autorizó · $it", style = MaterialTheme.typography.bodySmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money(movement.amountCents).format(), style = MaterialTheme.typography.titleMedium)
                if (canReverse) TextButton(onReverse) { Text("Revertir") }
            }
        }
    }
}

@Composable
private fun CashMovementReversalForm(
    movement: AuditedCashMovement,
    reason: String,
    onReason: (String) -> Unit,
    pin: String,
    onPin: (String) -> Unit,
    busy: Boolean,
    onConfirm: () -> Unit
) {
    val valid = reason.trim().length in 1..256
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CashMovementRow(movement, canReverse = false, onReverse = {})
            val operation = if (movement.type == "CASH_IN") "una salida" else "una entrada"
            Text("Se registrará $operation compensatoria por ${Money(movement.amountCents).format()}.")
            Text("Registra la corrección que hiciste en el efectivo. El movimiento original quedará en la bitácora.")
            OutlinedTextField(
                value = reason, onValueChange = onReason, enabled = !busy,
                label = { Text("Motivo de la reversión") },
                supportingText = { Text("${reason.trim().length}/256") },
                isError = reason.trim().length > 256,
                minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pin, onValueChange = onPin, enabled = !busy,
                label = { Text("PIN de encargado") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.weight(1f, fill = false).widthIn(max = 220.dp)
            )
            Button(onConfirm, enabled = valid && pin.length == 4 && !busy) { Text("Confirmar reversión") }
        }
    }
}

private fun String.movementLabel(): String = when (this) {
    "CASH_IN" -> "Entrada"
    "CASH_OUT" -> "Salida"
    else -> this
}

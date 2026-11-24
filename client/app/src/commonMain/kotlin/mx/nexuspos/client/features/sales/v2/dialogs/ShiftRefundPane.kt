package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.RefundLineItemRequest
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import kotlin.math.roundToLong

private val refundReasons = listOf(
    "DEFECTO" to "Defecto o daño",
    "CADUCADO" to "Producto vencido",
    "CAMBIO" to "Cambio",
    "INCONFORMIDAD" to "Inconformidad",
    "OTRO" to "Otro motivo"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ShiftRefundPane(
    sale: SaleResponse,
    sessionId: String?,
    cashierId: String,
    requiresSupervisor: Boolean,
    authorizing: Boolean,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onAuthorize: () -> Unit,
    onConfirm: (CreateSaleRefundRequest) -> Unit
) {
    val quantities = remember { mutableStateMapOf<String, String>() }
    val stockChoices = remember { mutableStateMapOf<String, Boolean>() }
    var reason by remember { mutableStateOf<Pair<String, String>?>(null) }
    var notes by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var expanded by remember { mutableStateOf(false) }
    val lines = sale.lines.filter { it.returnableQuantityMillis > 0 }
    val chosen = lines.mapNotNull { line ->
        val quantity = parseSalesQuantity(quantities[line.id].orEmpty())?.millis ?: return@mapNotNull null
        if (quantity > line.returnableQuantityMillis) return@mapNotNull null
        val request = RefundLineItemRequest(line.id, quantity, stockChoices[line.id] ?: true)
        request to ((line.totalCents.toDouble() * quantity) / line.quantityMillis).roundToLong()
    }
    val total = Money(chosen.sumOf { it.second }).format()
    val hasInvalidQuantity = lines.any { line ->
        val input = quantities[line.id].orEmpty()
        input.isNotBlank() && !isValidRefundQuantity(input, line)
    }
    val valid = chosen.isNotEmpty() && !hasInvalidQuantity && reason != null && !sessionId.isNullOrBlank()

    fun confirm(pin: String?) {
        if (!valid || busy) return
        onConfirm(
            CreateSaleRefundRequest(
                cashSessionId = sessionId.orEmpty(),
                cashierUserId = cashierId,
                reason = reason?.first.orEmpty(),
                notes = notes.trim(),
                refundMethod = method,
                supervisorPin = pin,
                lines = chosen.map { it.first }
            )
        )
    }

    if (authorizing) {
        val methodLabel = if (method == "CASH") "efectivo" else "saldo a favor"
        ShiftSupervisorPane(
            summary = "Folio #${sale.folio} · ${chosen.size} partida(s) · $total en $methodLabel. Motivo: ${reason?.second.orEmpty()}",
            confirmLabel = "Autorizar devolución",
            error = error,
            busy = busy,
            onBack = onBack,
            onConfirm = ::confirm
        )
    } else {
        ShiftActionPane(error, busy, footer = {
            TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
            Button(
                onClick = { if (requiresSupervisor) onAuthorize() else confirm(null) },
                enabled = valid && !busy
            ) { Text(if (requiresSupervisor) "Continuar" else "Confirmar devolución") }
        }, summary = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("A reembolsar", style = MaterialTheme.typography.titleSmall)
                Text(total, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            }
        }) {
            if (sessionId.isNullOrBlank()) ShiftActionMessage("Abre una sesión de caja para registrar una devolución.", isError = true)
            Text("Partidas disponibles", style = MaterialTheme.typography.titleMedium)
            for (line in lines) {
                key(line.id) {
                    RefundQuantityCard(
                        line = line,
                        input = quantities[line.id].orEmpty(),
                        onInput = { quantities[line.id] = it },
                        reenterStock = stockChoices[line.id] ?: true,
                        onReenterStock = { stockChoices[line.id] = it },
                        enabled = !busy
                    )
                }
            }
            HorizontalDivider()
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!busy) expanded = it }) {
                OutlinedTextField(
                    value = reason?.second.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !busy,
                    label = { Text("Motivo de devolución") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    refundReasons.forEach { option ->
                        DropdownMenuItem(text = { Text(option.second) }, onClick = { reason = option; expanded = false })
                    }
                }
            }
            Column {
                Text("Método de reembolso", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = method == "CASH",
                        onClick = { method = "CASH" },
                        enabled = !busy,
                        label = { Text("Efectivo") }
                    )
                    FilterChip(
                        selected = method == "CREDIT",
                        onClick = { method = "CREDIT" },
                        enabled = !busy && !sale.customerName.isNullOrBlank(),
                        label = { Text("Saldo a favor") }
                    )
                }
                if (sale.customerName.isNullOrBlank()) {
                    Text(
                        "El saldo a favor requiere un cliente asociado al folio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                enabled = !busy,
                label = { Text("Observaciones (opcional)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            if (requiresSupervisor) {
                Text("Necesitarás el PIN de un encargado para confirmar.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun isValidRefundQuantity(input: String, line: SaleLineItemResponse): Boolean {
    if (input.isBlank()) return true
    val quantity = parseSalesQuantity(input) ?: return false
    return quantity.millis <= line.returnableQuantityMillis
}

@Composable
private fun RefundQuantityCard(
    line: SaleLineItemResponse,
    input: String,
    onInput: (String) -> Unit,
    reenterStock: Boolean,
    onReenterStock: (Boolean) -> Unit,
    enabled: Boolean
) {
    val available = Quantity(line.returnableQuantityMillis).format().substringBefore(' ')
    val selected = input.isNotBlank() && isValidRefundQuantity(input, line)

    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.description, style = MaterialTheme.typography.titleSmall)
            Text(
                "Cantidad disponible: $available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> if (value.all { it in '0'..'9' || it == '.' || it == ',' }) onInput(value) },
                    label = { Text("Cantidad a devolver") },
                    singleLine = true,
                    enabled = enabled,
                    isError = !isValidRefundQuantity(input, line),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onInput(available) }, enabled = enabled) { Text("Todo") }
                if (input.isNotEmpty()) TextButton(onClick = { onInput("") }, enabled = enabled) { Text("Quitar") }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = reenterStock,
                    enabled = enabled && selected,
                    role = Role.Checkbox,
                    onValueChange = onReenterStock
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = reenterStock, onCheckedChange = null, enabled = enabled && selected)
                Text("Reingresar al inventario", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

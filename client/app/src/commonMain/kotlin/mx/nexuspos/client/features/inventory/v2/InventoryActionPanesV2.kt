package mx.nexuspos.client.features.inventory.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDate
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.client.features.sales.v2.components.symbol
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftActionMessage
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftActionPane
import mx.nexuspos.client.features.sales.v2.dialogs.checkoutAmountText
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

private enum class MovementMode(val code: String, val label: String, val defaultReason: String) {
    ENTRY("ENTRY", "Entrada", "Entrada de mercancía"),
    LOSS("LOSS", "Merma", "Merma de inventario"),
    COUNT("ADJUSTMENT", "Conteo físico", "Ajuste por conteo físico")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockMovementPaneV2(
    product: ProductDto,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: (String, Long, String, String, Long?) -> Unit
) {
    var mode by remember { mutableStateOf(MovementMode.ENTRY) }
    var input by remember(mode) { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    val unit = ProductUnit.fromCode(product.baseUnit)
    val current = product.currentStockMillis
    val quantity = parseInventoryQuantity(input, allowZero = mode == MovementMode.COUNT)
    val change = quantity?.let {
        when (mode) {
            MovementMode.ENTRY -> it.takeIf { current <= Long.MAX_VALUE - it }
            MovementMode.LOSS -> (-it).takeIf { current >= Long.MIN_VALUE + it }
            MovementMode.COUNT -> (it - current).takeIf { current >= 0L || it <= Long.MAX_VALUE + current }
        }
    }
    val valid = change != null && change != 0L
    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            enabled = valid && !busy,
            onClick = {
                if (change != null) onConfirm(
                    mode.code, change, reason.trim().ifBlank { mode.defaultReason }, reference.trim(),
                    current.takeIf { mode == MovementMode.COUNT }
                )
            }
        ) { Text("Registrar movimiento") }
    }) {
        Text(product.name, style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option, onClick = { mode = option }, enabled = !busy,
                    label = { Text(option.label) }
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InventoryMetric("Existencia actual", Quantity(current).format(unit))
                InventoryMetric("Existencia resultante", change?.let { Quantity(current + it).format(unit) } ?: "—")
            }
        }
        InventoryQuantityField(
            input, { input = it },
            if (mode == MovementMode.COUNT) "Cantidad contada (${unit.symbol})" else "Cantidad (${unit.symbol})",
            busy, isError = input.isNotBlank() && !valid
        )
        if (change == 0L) Text("El conteo coincide con la existencia registrada.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            reason, { reason = it }, label = { Text("Motivo (opcional)") },
            enabled = !busy, singleLine = true, modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
        OutlinedTextField(
            reference, { reference = it }, label = { Text("Referencia (opcional)") },
            enabled = !busy, singleLine = true, modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
    }
}

@Composable
internal fun InventoryLotEntryV2(
    initialProduct: ProductDto?,
    busy: Boolean,
    error: String?,
    onSearch: suspend (String) -> Result<List<ProductDto>>,
    onBack: () -> Unit,
    onConfirm: (CreateProductLotRequest) -> Unit
) {
    val default = initialProduct?.takeIf { it.isActive && it.kitComponents.isEmpty() }
    var product by remember { mutableStateOf(default) }
    var productSearch by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var expiration by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf(default?.costCents?.let(::checkoutAmountText).orEmpty()) }
    var addStock by remember { mutableStateOf(default?.usesInventory ?: true) }
    var notes by remember { mutableStateOf("") }
    val term = productSearch.trim()
    var results by remember(term) { mutableStateOf<Result<List<ProductDto>>?>(null) }
    LaunchedEffect(term) {
        if (term.length >= 2) {
            delay(200)
            val result = onSearch(term)
            ensureActive()
            results = result
        }
    }
    val expirationDate = runCatching { LocalDate.parse(expiration.trim()) }.getOrNull()
    val quantity = parseInventoryQuantity(amount)
    val costCents = if (cost.isBlank()) 0L else parseCheckoutCents(cost)
    val selected = product

    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            enabled = selected != null && expirationDate != null && quantity != null && costCents != null && !busy,
            onClick = {
                if (selected != null && expirationDate != null && quantity != null && costCents != null) {
                    onConfirm(CreateProductLotRequest(
                        selected.id, lot.trim().ifBlank { "S/L" }, expirationDate.toString(), quantity, costCents,
                        notes.trim(), selected.usesInventory && addStock
                    ))
                }
            }
        ) { Text("Guardar lote") }
    }) {
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.name, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { product = null }, enabled = !busy) { Text("Cambiar producto") }
            }
        } else {
            OutlinedTextField(
                productSearch, { productSearch = it }, label = { Text("Buscar producto por nombre, SKU o código") },
                enabled = !busy, singleLine = true, modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
            )
            if (term.length >= 2) {
                val response = results
                when {
                    response == null -> LinearProgressIndicator(Modifier.widthIn(max = 620.dp).fillMaxWidth())
                    response.isFailure -> Text("No se pudo consultar el catálogo.", color = MaterialTheme.colorScheme.error)
                    response.getOrNull().isNullOrEmpty() -> Text("Ningún producto coincide con la búsqueda.")
                    else -> response.getOrNull().orEmpty().take(8).forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                product = candidate
                                cost = checkoutAmountText(candidate.costCents)
                                addStock = candidate.usesInventory
                                productSearch = ""
                            },
                            enabled = !busy
                        ) { Text("${candidate.name} · ${candidate.sku}") }
                    }
                }
            }
        }
        val unit = selected?.let { ProductUnit.fromCode(it.baseUnit).symbol }
        OutlinedTextField(
            lot, { lot = it }, label = { Text("Número de lote") },
            enabled = !busy, singleLine = true, modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
        OutlinedTextField(
            expiration, { expiration = it }, label = { Text("Fecha de vencimiento") },
            placeholder = { Text("AAAA-MM-DD") }, enabled = !busy, singleLine = true,
            isError = expiration.isNotBlank() && expirationDate == null,
            modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
        InventoryQuantityField(amount, { amount = it }, unit?.let { "Cantidad ($it)" } ?: "Cantidad", busy, amount.isNotBlank() && quantity == null)
        OutlinedTextField(
            cost, { cost = it }, label = { Text("Costo unitario") },
            prefix = { Text("$") }, enabled = !busy, singleLine = true,
            isError = costCents == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
        if (selected?.usesInventory == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = addStock, onCheckedChange = { addStock = it }, enabled = !busy)
                Text("Sumar el lote a las existencias generales")
            }
        }
        OutlinedTextField(
            notes, { notes = it }, label = { Text("Observaciones (opcional)") },
            enabled = !busy, minLines = 2, maxLines = 4, modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
    }
}

@Composable
internal fun InventoryLotWriteOffV2(
    lot: ProductLotDto,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val unit = ProductUnit.fromCode(lot.baseUnit)
    var input by remember { mutableStateOf(Quantity(lot.quantityMillis).format(unit).substringBefore(' ')) }
    var reason by remember { mutableStateOf(if (lot.daysUntilExpiration <= 0) "Merma por caducidad" else "Retiro preventivo") }
    val quantity = parseInventoryQuantity(input)
    val valid = quantity != null && quantity <= lot.quantityMillis
    ShiftActionPane(error, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(
            enabled = valid && !busy,
            onClick = { if (quantity != null) onConfirm(quantity, reason.trim().ifBlank { "Merma de lote" }) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("Confirmar merma") }
    }) {
        Text(lot.productName, style = MaterialTheme.typography.titleLarge)
        Text("Lote ${lot.lotNumber} · Vence ${lot.expirationDate}", style = MaterialTheme.typography.bodyMedium)
        ShiftActionMessage("La merma reduce el saldo del lote y las existencias del producto.")
        InventoryMetric("Cantidad disponible", Quantity(lot.quantityMillis).format(unit))
        InventoryQuantityField(input, { input = it }, "Cantidad a retirar (${unit.symbol})", busy, input.isNotBlank() && !valid)
        if (quantity != null && quantity > lot.quantityMillis) {
            Text("El retiro supera la cantidad disponible.", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            reason, { reason = it }, label = { Text("Motivo") },
            enabled = !busy, singleLine = true, modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun InventoryQuantityField(input: String, onChange: (String) -> Unit, label: String, busy: Boolean, isError: Boolean) {
    OutlinedTextField(
        input, onChange, label = { Text(label) }, enabled = !busy, singleLine = true, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
    )
}

private val zeroQuantity = Regex("0+([.,]0{0,3})?|[.,]0{1,3}")

internal fun parseInventoryQuantity(text: String, allowZero: Boolean = false): Long? {
    val input = text.trim()
    if (allowZero && zeroQuantity.matches(input)) return 0
    return parseSalesQuantity(input)?.millis
}

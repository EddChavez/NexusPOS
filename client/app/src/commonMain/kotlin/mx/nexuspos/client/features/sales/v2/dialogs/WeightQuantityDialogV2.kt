package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

private enum class BulkEntryMode(val label: String) { WEIGHT("Por peso"), AMOUNT("Por importe") }

private val weightPresets = listOf(250L, 500L, 750L, 1000L, 1500L, 2000L).map(::Quantity)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightQuantityDialogV2(
    product: ProductDto,
    presentation: ProductPresentationDto,
    scaleReading: ScaleReading,
    currentLine: CartLine?,
    onConfirm: (Quantity) -> Unit,
    onDismiss: () -> Unit
) {
    val scaleWeight = scaleReading.takeIf { it.isStableWeight && it.weightKg.isFinite() }
        ?.let { Quantity.fromDouble(it.weightKg) }
    val initialValue = remember(product.id, presentation.id, currentLine?.id) {
        (currentLine?.quantity ?: scaleWeight)?.weightValue().orEmpty()
    }
    var mode by remember(product.id, presentation.id, currentLine?.id) { mutableStateOf(BulkEntryMode.WEIGHT) }
    var weightInput by remember(product.id, presentation.id, currentLine?.id) {
        mutableStateOf(TextFieldValue(initialValue))
    }
    var amountInput by remember(product.id, presentation.id, currentLine?.id) {
        mutableStateOf(TextFieldValue())
    }
    var dispatchInput by remember(product.id, presentation.id, currentLine?.id) {
        mutableStateOf<TextFieldValue?>(null)
    }
    val byAmount = mode == BulkEntryMode.AMOUNT
    val input = if (byAmount) amountInput else weightInput
    val focus = remember { FocusRequester() }
    val dispatchFocus = remember { FocusRequester() }
    val budget = if (byAmount) parseCheckoutCents(input.text)?.takeIf { it > 0L } else null
    val suggestedWeight = budget?.let { weightForBudget(presentation, Money(it)) }
    val suggestedText = suggestedWeight?.millis?.toString().orEmpty()
    val dispatch = dispatchInput ?: TextFieldValue(suggestedText)
    val weight = when {
        !byAmount -> parseSalesQuantity(input.text.trim())
        suggestedWeight == null -> null
        dispatchInput == null -> suggestedWeight
        else -> dispatch.text.trim().toLongOrNull()?.takeIf { it > 0L }?.let(::Quantity)
    }
    val price = Money(weight?.let { presentation.priceFor(it.millis) } ?: presentation.priceCents)
    val exceedsPricingRange = weight != null && price.cents > 0L &&
        weight.millis > (Long.MAX_VALUE - 500L) / price.cents
    val total = weight?.takeUnless { exceedsPricingRange }?.let { PricingRules.calculateLineTotal(price, it) }
    val overBudget = byAmount && budget != null && total != null && total.cents > budget
    val validWeight = weight?.takeUnless { exceedsPricingRange || overBudget }
    val error = when {
        !byAmount && exceedsPricingRange -> "El peso excede el importe que se puede calcular."
        !byAmount && input.text.isNotBlank() && weight == null ->
            "Captura un peso mayor que cero con hasta tres decimales."
        byAmount && price.cents <= 0L -> "Se necesita un precio por kilo mayor que cero."
        byAmount && input.text.isNotBlank() && budget == null ->
            "Captura un importe mayor que cero con hasta dos decimales."
        byAmount && budget != null && budget > MAX_BULK_BUDGET_CENTS -> "El importe excede el rango de cálculo."
        byAmount && budget != null && suggestedWeight == null -> "Este importe no alcanza para un gramo."
        else -> null
    }
    val dispatchError = when {
        suggestedWeight == null -> null
        weight == null -> "Captura el peso en gramos enteros, mayor que cero."
        exceedsPricingRange -> "El peso excede el importe que se puede calcular."
        overBudget -> "Este peso supera el importe máximo."
        else -> null
    }

    val window = LocalWindowInfo.current.containerDpSize
    val width = if (window.width > 0.dp) (window.width - 32.dp).coerceIn(1.dp, 644.dp) else 644.dp
    val maxHeight = if (window.height > 0.dp) (window.height - 32.dp).coerceIn(1.dp, 660.dp) else 660.dp
    val contentGap = if (width < 520.dp) 8.dp else 12.dp
    val contentPadding = if (width < 520.dp) 16.dp else 20.dp
    val isReweigh = currentLine != null
    val confirm: () -> Unit = { if (validWeight != null) onConfirm(validWeight) }

    fun updateInput(value: TextFieldValue) {
        if (mode == BulkEntryMode.AMOUNT) {
            if (amountInput.text != value.text) dispatchInput = null
            amountInput = value
        } else {
            weightInput = value
        }
    }

    fun setWeight(quantity: Quantity) {
        val value = if (byAmount) quantity.millis.toString() else quantity.weightValue()
        val selected = TextFieldValue(value, selection = TextRange(0, value.length))
        if (byAmount) {
            dispatchInput = selected
            dispatchFocus.requestFocus()
        } else {
            weightInput = selected
            focus.requestFocus()
        }
    }

    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(width).heightIn(max = maxHeight).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else false
            },
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(contentGap)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isReweigh) "Volver a pesar" else "Venta a granel",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar pesaje") }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(contentGap)
                ) {
                    ProductWeightHeading(product, presentation, price)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        BulkEntryMode.entries.forEachIndexed { index, entryMode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, BulkEntryMode.entries.size),
                                selected = mode == entryMode,
                                onClick = {
                                    mode = entryMode
                                    val next = if (entryMode == BulkEntryMode.AMOUNT) amountInput else weightInput
                                    val selected = next.copy(selection = TextRange(0, next.text.length))
                                    if (entryMode == BulkEntryMode.AMOUNT) amountInput = selected else weightInput = selected
                                    focus.requestFocus()
                                },
                                label = { Text(entryMode.label) }
                            )
                        }
                    }

                    ScaleWeightPanel(scaleReading, scaleWeight, !byAmount || suggestedWeight != null, ::setWeight)
                    OutlinedTextField(
                        value = input,
                        onValueChange = ::updateInput,
                        label = { Text(if (byAmount) "Importe máximo" else "Peso a aplicar") },
                        placeholder = { Text(if (byAmount) "20.00" else "0.000") },
                        prefix = if (byAmount) ({ Text("$") }) else null,
                        suffix = if (byAmount) null else ({ Text("kg") }),
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    val value = if (mode == BulkEntryMode.AMOUNT) amountInput else weightInput
                                    updateInput(value.copy(selection = TextRange(0, value.text.length)))
                                }
                            }
                            .onPreviewKeyEvent { confirmWeightOnEnter(it, confirm) }
                    )
                    LaunchedEffect(focus) { focus.requestFocus() }

                    if (byAmount) {
                        OutlinedTextField(
                            value = dispatch,
                            onValueChange = { dispatchInput = it },
                            label = { Text("Peso a despachar") },
                            suffix = { Text("g") },
                            enabled = suggestedWeight != null,
                            singleLine = true,
                            isError = dispatchError != null,
                            supportingText = dispatchError?.let { message -> { Text(message) } },
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { confirm() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(dispatchFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        val value = dispatchInput ?: TextFieldValue(suggestedText)
                                        dispatchInput = value.copy(selection = TextRange(0, value.text.length))
                                    }
                                }
                                .onPreviewKeyEvent { confirmWeightOnEnter(it, confirm) }
                        )
                    }

                    if (!byAmount) {
                        Column {
                            Text("Pesos rápidos", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (preset in weightPresets) {
                                    SuggestionChip(onClick = { setWeight(preset) }, label = { Text(preset.format(ProductUnit.KILOGRAM)) })
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                if (byAmount) {
                    WeightTotal("Peso sugerido", suggestedWeight?.let { "${it.format(ProductUnit.KILOGRAM)} · ${it.millis} g" })
                }
                val hasDiscount = currentLine?.let { it.discountCents > 0 || it.discountPercent > 0 } == true
                WeightTotal(if (hasDiscount) "Importe antes de descuentos" else "Importe", total?.format())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = confirm, enabled = validWeight != null) {
                        Text(if (isReweigh) "Actualizar peso" else "Agregar al carrito")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductWeightHeading(product: ProductDto, presentation: ProductPresentationDto, price: Money) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            if (presentation.name.isNotBlank() && presentation.name != product.name) {
                Text(
                    presentation.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "${price.format()} / kg",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleWeightPanel(
    reading: ScaleReading,
    stableWeight: Quantity?,
    canApplyWeight: Boolean,
    onTakeWeight: (Quantity) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (stableWeight != null) colors.secondaryContainer else colors.surfaceVariant
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Scale, null)
                Column {
                    Text("Báscula", style = MaterialTheme.typography.labelLarge)
                    val status = when (reading.status) {
                        ScaleStatus.DISCONNECTED -> "Desconectada · captura manual"
                        ScaleStatus.READING -> "Estabilizando lectura"
                        ScaleStatus.ZERO -> "En cero"
                        ScaleStatus.STABLE -> if (stableWeight != null) "Lectura estable" else "Sin peso utilizable"
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reading.status != ScaleStatus.DISCONNECTED) {
                    Text(
                        reading.weightText,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    )
                }
                OutlinedButton(
                    onClick = { stableWeight?.let(onTakeWeight) },
                    enabled = stableWeight != null && canApplyWeight
                ) { Text("Tomar peso") }
            }
        }
    }
}

@Composable
private fun WeightTotal(label: String, value: String?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold
        )
    }
}

private fun confirmWeightOnEnter(event: KeyEvent, confirm: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || (event.key != Key.Enter && event.key != Key.NumPadEnter) ||
        event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
    ) return false

    confirm()
    return true
}

private fun Quantity.weightValue(): String = format(ProductUnit.KILOGRAM).substringBefore(' ')


private const val MAX_BULK_BUDGET_CENTS = (Long.MAX_VALUE - 500L) / 1000L

internal fun weightForBudget(pricePerKilogram: Money, budget: Money): Quantity? {
    val price = pricePerKilogram.cents
    if (price <= 0L || budget.cents !in 1L..MAX_BULK_BUDGET_CENTS) return null

    val scaledBudget = budget.cents * 1000L
    val wholeGrams = scaledBudget / price
    val remainder = scaledBudget % price

    // Try the nearest gram when it rounds up, using the same centavo rounding as the cart.
    val nextGram = wholeGrams + 1L
    if (remainder >= price - remainder && nextGram <= (Long.MAX_VALUE - 500L) / price) {
        val candidate = Quantity(nextGram)
        if (PricingRules.calculateLineTotal(pricePerKilogram, candidate) <= budget) return candidate
    }
    return Quantity(wholeGrams).takeIf { it.isPositive() }
}


internal fun weightForBudget(presentation: ProductPresentationDto, budget: Money): Quantity? {
    val ranges = listOf(1L) + presentation.priceTiers
        .map { it.minimumQuantityMillis }
        .filter { it > 1L }
        .sorted()
    for (index in ranges.indices.reversed()) {
        val lower = ranges[index]
        val upper = ranges.getOrNull(index + 1)?.minus(1) ?: Long.MAX_VALUE
        val price = Money(presentation.priceFor(lower))
        val affordable = weightForBudget(price, budget)?.millis ?: continue
        val weight = minOf(affordable, upper)
        if (weight >= lower) return Quantity(weight)
    }
    return null
}

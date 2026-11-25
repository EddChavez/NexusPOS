package mx.nexuspos.client.features.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.client.features.sales.v2.components.symbol
import mx.nexuspos.client.features.sales.v2.dialogs.checkoutAmountText
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.contracts.catalog.PresentationPriceTier
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.CatalogPriceList
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

private data class TierDraft(val id: Int, val quantity: String, val price: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PresentationPricingDialogV2(
    product: ProductDto,
    loadLists: suspend () -> Result<List<CatalogPriceList>> = { Result.success(emptyList()) },
    onSaveListPrice: suspend (String, Int, Long?) -> Result<ProductDto> = { _, _, _ ->
        Result.failure(IllegalStateException("Las listas de precio se editan en Productos V2."))
    },
    onSave: suspend (String, List<PresentationPriceTier>) -> Result<ProductDto>,
    onDismiss: () -> Unit
) {
    var presentationId by remember(product.id) { mutableStateOf(product.presentations.first().id) }
    val presentation = product.presentations.firstOrNull { it.id == presentationId } ?: product.presentations.first()
    val unit = ProductUnit.fromCode(product.baseUnit)
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember(presentation.id) { mutableStateOf<String?>(null) }
    var saved by remember(presentation.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var priceLists by remember { mutableStateOf<List<CatalogPriceList>>(emptyList()) }
    LaunchedEffect(product.id) { loadLists().onSuccess { priceLists = it } }
    val drafts = remember(presentation.id, presentation.priceTiers) {
        mutableStateListOf<TierDraft>().apply {
            presentation.priceTiers.forEachIndexed { index, tier ->
                add(TierDraft(index, Quantity(tier.minimumQuantityMillis).format(unit).substringBefore(' '), checkoutAmountText(tier.unitPriceCents)))
            }
        }
    }
    var nextId by remember(drafts) { mutableIntStateOf(drafts.size) }
    val window = LocalWindowInfo.current.containerDpSize
    val width = if (window.width > 0.dp) (window.width - 32.dp).coerceIn(1.dp, 600.dp) else 600.dp
    val height = if (window.height > 0.dp) (window.height - 32.dp).coerceIn(1.dp, 620.dp) else 620.dp

    fun save() {
        val tiers = drafts.map { row ->
            val quantity = parseSalesQuantity(row.quantity.trim())?.millis
            val price = parseCheckoutCents(row.price)
            if (quantity == null || price == null || price <= 0 ||
                (unit == ProductUnit.PIECE && (quantity < 2000 || quantity % 1000 != 0L))
            ) {
                error = if (unit == ProductUnit.PIECE) "Captura cantidades enteras desde 2 y precios positivos."
                else "Captura cantidades mínimas y precios positivos."
                return
            }
            PresentationPriceTier(quantity, price)
        }
        saving = true
        saved = false
        error = null
        scope.launch {
            try {
                onSave(presentation.id, tiers).fold(
                    onSuccess = { saved = true },
                    onFailure = { error = it.message ?: "No se pudieron guardar las tarifas." }
                )
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            Modifier.width(width).heightIn(max = height),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Precios de presentaciones", style = MaterialTheme.typography.headlineSmall)
                        Text(product.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!saving) expanded = it }) {
                        OutlinedTextField(
                            value = "${presentation.name} · ${presentation.barcode}",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            enabled = !saving,
                            label = { Text("Presentación") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            product.presentations.forEach {
                                DropdownMenuItem(
                                    text = { Text("${it.name} · ${it.barcode}") },
                                    enabled = !saving,
                                    onClick = { presentationId = it.id; expanded = false }
                                )
                            }
                        }
                    }
                    Text("Precio público · ${Money(presentation.priceCents).format()}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Al alcanzar un mínimo, la tarifa se aplica a toda la cantidad de la partida.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    drafts.forEachIndexed { index, row ->
                        key(row.id) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = row.quantity,
                                    onValueChange = { drafts[index] = row.copy(quantity = it); saved = false },
                                    label = { Text(if (unit == ProductUnit.PIECE) "Desde (cantidad)" else "Desde (${unit.symbol})") },
                                    enabled = !saving,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = row.price,
                                    onValueChange = { drafts[index] = row.copy(price = it); saved = false },
                                    label = { Text("Precio unitario") },
                                    enabled = !saving,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { drafts.removeAt(index); saved = false },
                                    enabled = !saving
                                ) { Icon(Icons.Default.RemoveCircleOutline, "Quitar tarifa") }
                            }
                        }
                    }
                    TextButton(
                        onClick = { drafts.add(TierDraft(nextId++, "", "")); saved = false },
                        enabled = !saving
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar tarifa")
                    }
                    if (priceLists.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Listas de precio", style = MaterialTheme.typography.titleMedium)
                        priceLists.forEach { list ->
                            val savedPrice = presentation.listPrices[list.level]
                            var price by remember(presentation.id, list.level, savedPrice) { mutableStateOf(savedPrice?.let(::checkoutAmountText).orEmpty()) }
                            val cents = parseCheckoutCents(price)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(price, { price = it }, label = { Text(list.name + if (list.enabled) "" else " (inactiva)") },
                                    singleLine = true, enabled = !saving, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                TextButton(onClick = {
                                    saving = true
                                    error = null
                                    scope.launch {
                                        onSaveListPrice(presentation.id, list.level, cents).onFailure { error = it.message }
                                        saving = false
                                    }
                                }, enabled = !saving && (price.isBlank() || cents != null) && cents != savedPrice) { Text("Guardar precio") }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (saved) Text("Tarifas guardadas.", color = MaterialTheme.colorScheme.primary)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cerrar") }
                    Button(onClick = ::save, enabled = !saving) { Text("Guardar tarifas") }
                }
            }
        }
    }
}

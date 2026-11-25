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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.client.features.sales.v2.components.symbol
import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@Composable
internal fun KitEditorDialogV2(
    product: ProductDto,
    onLoadComponent: suspend (String) -> Result<ProductDto>,
    onSearch: suspend (String) -> Result<List<ProductDto>>,
    onSave: suspend (List<KitComponent>) -> Result<ProductDto>,
    onDismiss: () -> Unit
) {
    val quantities = remember(product.id) {
        mutableStateMapOf<String, String>().apply {
            product.kitComponents.forEach {
                put(it.productId, Quantity(it.quantityMillis).format().substringBefore(' '))
            }
        }
    }
    val components = remember(product.id) { mutableStateMapOf<String, ProductDto>() }
    var search by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val term = search.trim()
    var matches by remember(term) { mutableStateOf<Result<List<ProductDto>>?>(null) }
    val scope = rememberCoroutineScope()
    val window = LocalWindowInfo.current.containerDpSize
    val width = if (window.width > 0.dp) (window.width - 32.dp).coerceIn(1.dp, 660.dp) else 660.dp
    val height = if (window.height > 0.dp) (window.height - 32.dp).coerceIn(1.dp, 650.dp) else 650.dp

    LaunchedEffect(product.id) {
        product.kitComponents.forEach { item ->
            val result = onLoadComponent(item.productId)
            ensureActive()
            result.onSuccess { components[item.productId] = it }
        }
    }
    LaunchedEffect(term) {
        if (term.length < 2) return@LaunchedEffect
        delay(200)
        val result = onSearch(term)
        ensureActive()
        matches = result
    }

    fun save() {
        val request = quantities.entries.sortedBy { it.key }.map { (id, input) ->
            val quantity = parseSalesQuantity(input.trim())?.millis
            val unit = components[id]?.baseUnit?.let(ProductUnit::fromCode)
            if (quantity == null || (unit == ProductUnit.PIECE && quantity % 1000 != 0L)) {
                error = "Usa cantidades positivas y piezas enteras para cada componente."
                return
            }
            KitComponent(id, quantity)
        }
        saving = true
        error = null
        scope.launch {
            try {
                onSave(request).onFailure { error = it.message ?: "No se pudo guardar el kit." }
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
                        Text("Composición del kit", style = MaterialTheme.typography.headlineSmall)
                        Text(product.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Indica cuánto se descuenta de cada producto al vender un kit.", style = MaterialTheme.typography.bodyMedium)
                    quantities.keys.sorted().forEach { id ->
                        key(id) {
                            val component = components[id]
                            val unit = component?.baseUnit?.let(ProductUnit::fromCode)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1.4f)) {
                                    Text(component?.name ?: id, style = MaterialTheme.typography.titleSmall)
                                    component?.let { Text(it.sku, style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedTextField(
                                    value = quantities[id].orEmpty(),
                                    onValueChange = { quantities[id] = it },
                                    modifier = Modifier.weight(1f),
                                    enabled = !saving,
                                    singleLine = true,
                                    label = { Text(unit?.let { "Cantidad (${it.symbol})" } ?: "Cantidad") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (unit == ProductUnit.PIECE) KeyboardType.Number else KeyboardType.Decimal
                                    )
                                )
                                IconButton(onClick = { quantities.remove(id) }, enabled = !saving) {
                                    Icon(Icons.Default.RemoveCircleOutline, "Quitar ${component?.name ?: id}")
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Buscar componente por nombre o SKU") },
                        enabled = !saving,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (term.length >= 2) {
                        val result = matches
                        if (result == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            val candidates = result.getOrNull()?.filter { it.id != product.id && it.id !in quantities }
                            when {
                                candidates == null -> Text("No se pudo buscar en el catálogo.", color = MaterialTheme.colorScheme.error)
                                candidates.isEmpty() -> Text("No hay componentes disponibles para esta búsqueda.")
                                else -> candidates.take(6).forEach { candidate ->
                                    OutlinedButton(
                                        onClick = {
                                            components[candidate.id] = candidate
                                            quantities[candidate.id] = "1"
                                            search = ""
                                        },
                                        enabled = !saving,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(candidate.name, Modifier.weight(1f))
                                        Text(candidate.sku)
                                    }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cerrar") }
                    Button(
                        onClick = ::save,
                        enabled = !saving && (quantities.isNotEmpty() || product.kitComponents.isNotEmpty())
                    ) { Text(if (quantities.isEmpty()) "Quitar kit" else "Guardar kit") }
                }
            }
        }
    }
}

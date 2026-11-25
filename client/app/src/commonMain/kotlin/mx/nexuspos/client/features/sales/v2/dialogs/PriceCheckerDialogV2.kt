package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.v2.components.ProductThumbnail
import mx.nexuspos.client.features.sales.v2.components.symbol
import mx.nexuspos.client.features.sales.v2.components.volumeQuantityLabel
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

private sealed interface PriceCheckResult {
    data object Ready : PriceCheckResult
    data object Loading : PriceCheckResult
    data class Found(val product: ProductDto, val query: String) : PriceCheckResult
    data class NotFound(val query: String) : PriceCheckResult
    data class Failed(val query: String) : PriceCheckResult
}

@Composable
fun PriceCheckerDialogV2(
    apiClient: ApiClient,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PriceCheckResult>(PriceCheckResult.Ready) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val width = if (windowSize.width > 0.dp) (windowSize.width - 32.dp).coerceIn(1.dp, 540.dp) else 540.dp
    val height = if (windowSize.height > 0.dp) (windowSize.height - 32.dp).coerceIn(1.dp, 620.dp) else 620.dp

    fun search(value: String) {
        val query = value.trim()
        if (query.isEmpty()) return

        lookupJob?.cancel()
        input = ""
        result = PriceCheckResult.Loading
        searchFocus.requestFocus()
        lookupJob = scope.launch {
            val byBarcode = apiClient.searchProducts(barcode = query)
            ensureActive()
            val response = if (byBarcode.getOrNull()?.isEmpty() == true) {
                apiClient.searchProducts(query = query)
            } else {
                byBarcode
            }
            ensureActive()
            result = response.fold(
                onSuccess = { products ->
                    val product = products.firstOrNull { item -> item.presentations.any { it.barcode == query } }
                        ?: products.firstOrNull()
                    if (product == null) PriceCheckResult.NotFound(query) else PriceCheckResult.Found(product, query)
                },
                onFailure = { PriceCheckResult.Failed(query) }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.width(width).heightIn(max = height),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Verificador de precios", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar verificador de precios") }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Código de barras o nombre") },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                        trailingIcon = if (input.isEmpty()) null else {
                            {
                                IconButton(onClick = {
                                    input = ""
                                    searchFocus.requestFocus()
                                }) {
                                    Icon(Icons.Default.Clear, "Limpiar búsqueda")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(input) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.isAltPressed ||
                                event.isCtrlPressed || event.isMetaPressed ||
                                (event.key != Key.Enter && event.key != Key.NumPadEnter)
                            ) return@onPreviewKeyEvent false

                            search(input)
                            true
                        }
                    )

                    Box(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        when (val current = result) {
                            PriceCheckResult.Ready -> PriceCheckMessage(
                                "Escanea un código o escribe el nombre de un producto para consultar su precio."
                            )
                            PriceCheckResult.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            is PriceCheckResult.Found -> CheckedProduct(current.product, current.query)
                            is PriceCheckResult.NotFound -> PriceCheckMessage(
                                "Ningún producto coincide con «${current.query}»."
                            )
                            is PriceCheckResult.Failed -> PriceCheckMessage(
                                message = "No se pudo completar la consulta de «${current.query}».",
                                onRetry = { search(current.query) }
                            )
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    Button(onClick = { search(input) }, enabled = input.isNotBlank()) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buscar")
                    }
                }
            }
        }

        LaunchedEffect(Unit) { searchFocus.requestFocus() }
    }
}

@Composable
private fun PriceCheckMessage(message: String, onRetry: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (onRetry == null) colors.surfaceContainerLow else colors.errorContainer,
        contentColor = if (onRetry == null) colors.onSurfaceVariant else colors.onErrorContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun CheckedProduct(product: ProductDto, query: String) {
    val presentation = product.presentationForPriceCheck(query)
    val unit = ProductUnit.fromCode(product.baseUnit)
    val price = presentation?.let { Money.fromCents(it.priceCents) }
    val unitLabel = when {
        presentation == null -> null
        presentation.factorMillis == Quantity.ONE.millis -> unit.symbol
        else -> Quantity(presentation.factorMillis).format(unit)
    }
    val colors = MaterialTheme.colorScheme

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductThumbnail(product.name, product.departmentName.orEmpty())
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
                        presentation?.name?.takeIf { it.isNotBlank() && it != product.name },
                        presentation?.barcode?.ifBlank { product.sku } ?: product.sku,
                        product.departmentName?.takeIf { it.isNotBlank() }
                    ).filter { it.isNotBlank() }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            if (presentation != null && price != null && unitLabel != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Precio público", style = MaterialTheme.typography.labelLarge)
                        Text(
                            price.format(),
                            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold
                        )
                        Text("Por $unitLabel", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                presentation.priceTiers.sortedBy { it.minimumQuantityMillis }.forEach { tier ->
                    Text(
                        "Desde ${volumeQuantityLabel(tier.minimumQuantityMillis, unit)} · " +
                            "${Money(presentation.priceFor(tier.minimumQuantityMillis)).format()} por $unitLabel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text("Sin precio disponible", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text(
                if (product.usesInventory) "Existencia registrada · ${Quantity(product.currentStockMillis).format(unit)}"
                else "Producto sin control de inventario",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

internal fun ProductDto.presentationForPriceCheck(query: String) =
    presentations.firstOrNull { it.barcode == query }
        ?: presentations.firstOrNull { it.name.equals(query, ignoreCase = true) }
        ?: presentations.firstOrNull()

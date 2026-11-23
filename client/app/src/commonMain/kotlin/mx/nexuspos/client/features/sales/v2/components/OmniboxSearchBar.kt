package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

/**
 * Barra superior estilo Omnibox con autocompletado flotante Material Design 3.
 * Captura entrada directa de escáner láser de código de barras o búsqueda manual
 * por nombre o categoría, navegable por teclado (↑, ↓, Enter).
 */
@Composable
fun OmniboxSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    matches: List<ProductDto>,
    onSelectMatch: (ProductDto) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    isFocused: MutableState<Boolean> = remember { mutableStateOf(false) }
) {
    val input = query.trim()
    var highlightedIndex by remember(matches) { mutableStateOf(0) }
    var fieldHeight by remember { mutableStateOf(0) }
    val showDropdown = isFocused.value && input.length >= 2 && matches.isNotEmpty()

    fun submit() {
        val looksLikeBarcode = input.isNotEmpty() && input.all { it in '0'..'9' }
        val exactBarcode = matches.any { product ->
            product.sku.equals(input, ignoreCase = true) ||
                product.presentations.any { it.barcode.equals(input, ignoreCase = true) }
        }
        if (showDropdown && !looksLikeBarcode && !exactBarcode) {
            onSelectMatch(matches[highlightedIndex])
        } else {
            onSubmit()
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val searchWidth = maxWidth
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Buscar o escanear productos") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            ),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "Limpiar búsqueda")
                    }
                } else {
                    Icon(Icons.Default.QrCodeScanner, "Captura por escáner")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { fieldHeight = it.height }
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused.value = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed ||
                        event.isMetaPressed || event.isAltPressed
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            submit()
                            true
                        }
                        Key.DirectionDown, Key.DirectionUp -> {
                            if (!showDropdown) return@onPreviewKeyEvent false
                            val direction = if (event.key == Key.DirectionDown) 1 else -1
                            highlightedIndex = (highlightedIndex + direction).coerceIn(matches.indices)
                            true
                        }
                        else -> false
                    }
                }
        )

        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

        // =====================================================================
        // LISTA FLOTANTE DE RESULTADOS PREDICTIVOS (M3 ListItem Overlay)
        // =====================================================================
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, fieldHeight),
                properties = PopupProperties(focusable = false)
            ) {
                ElevatedCard(
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .width(searchWidth)
                        .heightIn(max = 260.dp)
                ) {
                    val resultsState = rememberLazyListState()
                    LaunchedEffect(highlightedIndex) { resultsState.scrollToItem(highlightedIndex) }
                    LazyColumn(modifier = Modifier.fillMaxWidth(), state = resultsState) {
                        itemsIndexed(matches) { index, prod ->
                            val isHighlighted = index == highlightedIndex
                            val pres = prod.presentations.firstOrNull()
                            val priceText = pres?.let { Money.fromCents(it.priceCents).format() }
                            val stock = Quantity(prod.currentStockMillis).format(ProductUnit.fromCode(prod.baseUnit))

                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = prod.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${pres?.barcode ?: prod.sku} • ${prod.departmentName ?: "General"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    ProductThumbnail(
                                        name = prod.name,
                                        category = prod.departmentName ?: "Abarrotes",
                                        size = 36.dp
                                    )
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (priceText != null) {
                                            Text(priceText, style = MaterialTheme.typography.titleSmall)
                                        }
                                        if (prod.usesInventory) {
                                            Text(stock, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isHighlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                ),
                                modifier = Modifier.clickable { onSelectMatch(prod) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

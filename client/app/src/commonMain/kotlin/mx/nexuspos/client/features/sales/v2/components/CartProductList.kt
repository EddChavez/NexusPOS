package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@Composable
fun CartProductList(
    items: List<CartLine>,
    selectedLineId: String?,
    onSelectLine: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onUpdateQuantity: (String, Quantity) -> Unit,
    onReweighLine: (CartLine) -> Unit,
    onDiscountLine: (CartLine) -> Unit,
    onQuantityFocusChanged: (Boolean) -> Unit,
    onFinishQuantityEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("El ticket está vacío", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Busca o escanea un producto para agregarlo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items, key = { it.id }) { line ->
                CartProductRow(
                    line = line,
                    isSelected = line.id == selectedLineId,
                    onSelect = { onSelectLine(line.id) },
                    onRemove = { onRemoveLine(line.id) },
                    onUpdateQuantity = { onUpdateQuantity(line.id, it) },
                    onReweigh = { onReweighLine(line) },
                    onDiscount = { onDiscountLine(line) },
                    onQuantityFocusChanged = onQuantityFocusChanged,
                    onFinishQuantityEdit = onFinishQuantityEdit
                )
            }
        }
    }
}

@Composable
private fun CartProductRow(
    line: CartLine,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onUpdateQuantity: (Quantity) -> Unit,
    onReweigh: () -> Unit,
    onDiscount: () -> Unit,
    onQuantityFocusChanged: (Boolean) -> Unit,
    onFinishQuantityEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val canReweigh = line.isWeighed || line.unit == ProductUnit.KILOGRAM

    OutlinedCard(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) colors.secondaryContainer else colors.surface,
            contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            val compact = maxWidth < 530.dp
            Column {
                if (compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductDetails(line, isSelected, Modifier.weight(1f))
                        if (canReweigh) ReweighButton(onReweigh)
                        ProductMenu(onRemove, onDiscount)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (compact) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        ProductDetails(line, isSelected, Modifier.weight(1f))
                        if (canReweigh) ReweighButton(onReweigh)
                    }
                    QuantityStepper(line, onUpdateQuantity, onQuantityFocusChanged, onFinishQuantityEdit)
                    ProductPrices(line, isSelected, Modifier.widthIn(min = 100.dp))
                    if (!compact) ProductMenu(onRemove, onDiscount)
                }
            }
        }
    }
}

@Composable
private fun ProductDetails(line: CartLine, isSelected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductThumbnail(line.name, line.category, size = 36.dp, imageUrl = line.imageUrl)
        Column {
            Text(
                line.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (line.barcode.isBlank()) line.unit.label else "${line.barcode} · ${line.unit.label}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (line.promotionDiscountCents > 0) {
                Text("Promoción aplicada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProductPrices(line: CartLine, isSelected: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            line.total.format(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
        Text(
            "${line.unitPrice.format()} / ${line.unit.symbol}",
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun QuantityStepper(
    line: CartLine,
    onUpdateQuantity: (Quantity) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onFinishEditing: () -> Unit
) {
    val displayQuantity = line.quantity.format(line.unit).substringBefore(' ')
    var draft by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val parsed = draft?.let(::parseSalesQuantity)
    val current = parsed ?: line.quantity
    val hasInvalidDraft = focused && draft != null && parsed == null
    val step = if (line.unit == ProductUnit.KILOGRAM) 100L else 1000L
    val stepperColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    DisposableEffect(Unit) {
        onDispose {
            if (focused) onFocusChanged(false)
        }
    }

    fun commit(quantity: Quantity?) {
        draft = null
        if (quantity != null && quantity != line.quantity) onUpdateQuantity(quantity)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { commit(Quantity(current.millis - step)) },
            enabled = current.millis > step,
            colors = stepperColors
        ) {
            Icon(Icons.Default.Remove, "Disminuir cantidad")
        }
        OutlinedTextField(
            value = draft ?: displayQuantity,
            onValueChange = { draft = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            isError = hasInvalidDraft,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFinishEditing() }),
            modifier = Modifier
                .width(94.dp)
                .semantics {
                    contentDescription = "Cantidad de ${line.name} (${line.unit.symbol})"
                    if (hasInvalidDraft) error("Ingresa una cantidad positiva con hasta tres decimales")
                }
                .onFocusChanged { state ->
                    if (state.isFocused != focused) {
                        if (!state.isFocused) commit(draft?.let(::parseSalesQuantity))
                        focused = state.isFocused
                        onFocusChanged(focused)
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> {
                            draft = null
                            onFinishEditing()
                            true
                        }
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                            !event.isCtrlPressed && !event.isMetaPressed && !event.isAltPressed -> {
                            onFinishEditing()
                            true
                        }
                        else -> false
                    }
                }
        )
        FilledTonalIconButton(
            onClick = { commit(Quantity(current.millis + step)) },
            enabled = current.millis <= Long.MAX_VALUE - step,
            colors = stepperColors
        ) {
            Icon(Icons.Default.Add, "Aumentar cantidad")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReweighButton(onReweigh: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text("Re-pesar en báscula RS-232") } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onReweigh) {
            Icon(Icons.Default.Scale, "Re-pesar en báscula RS-232")
        }
    }
}

@Composable
private fun ProductMenu(onRemove: () -> Unit, onDiscount: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "Opciones de artículo")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Eliminar artículo") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
            DropdownMenuItem(
                text = { Text("Aplicar descuento a línea") },
                leadingIcon = { Icon(Icons.Default.Percent, null) },
                onClick = {
                    expanded = false
                    onDiscount()
                }
            )
        }
    }
}

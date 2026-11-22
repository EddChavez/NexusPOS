package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

/**
 * Lista virtualizada de partidas en el ticket activo con componentes oficiales Material Design 3.
 * Incluye miniatura de producto, stepper de cantidad interactivo con FilledTonalIconButton M3,
 * acceso directo a re-pesaje con báscula RS-232 y menú contextual [⋮].
 */
@Composable
fun CartProductList(
    items: List<CartLine>,
    selectedLineId: String?,
    onSelectLine: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onUpdateQuantity: (String, Quantity) -> Unit,
    onReweighLine: (CartLine) -> Unit,
    onDiscountLine: (CartLine) -> Unit,
    onChangePriceLine: (CartLine) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "El ticket está vacío",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Usa el buscador Omnibox o el escáner láser para agregar productos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(items, key = { _, line -> line.id }) { index, line ->
                CartProductRow(
                    index = index + 1,
                    line = line,
                    isSelected = line.id == selectedLineId,
                    onSelect = { onSelectLine(line.id) },
                    onRemove = { onRemoveLine(line.id) },
                    onUpdateQuantity = { newQty -> onUpdateQuantity(line.id, newQty) },
                    onReweigh = { onReweighLine(line) },
                    onDiscount = { onDiscountLine(line) },
                    onChangePrice = { onChangePriceLine(line) }
                )
            }
        }
    }
}

@Composable
private fun CartProductRow(
    index: Int,
    line: CartLine,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onUpdateQuantity: (Quantity) -> Unit,
    onReweigh: () -> Unit,
    onDiscount: () -> Unit,
    onChangePrice: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    var rawQuantityText by remember(line.quantity) {
        val qty = line.quantity.millis / 1000.0
        mutableStateOf(if (line.unit == ProductUnit.KILOGRAM) String.format("%.3f", qty) else "${qty.toLong()}")
    }

    val cardBorder = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    val cardBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        border = cardBorder,
        colors = CardDefaults.outlinedCardColors(containerColor = cardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. ÍNDICE Y MINIATURA
            Text(
                text = "$index",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(18.dp)
            )

            ProductThumbnail(
                name = line.name,
                category = line.category,
                size = 42.dp,
                imageUrl = line.imageUrl
            )

            // 2. DETALLE DE PRODUCTO
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = line.barcode,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Báscula RS-232",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 3. STEPPER DE CANTIDAD CON BOTÓN DE BÁSCULA INTEGRADO
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                    IconButton(
                        onClick = onReweigh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Scale,
                            contentDescription = "Re-pesar en báscula RS-232",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        val current = line.quantity.millis
                        val step = if (line.unit == ProductUnit.KILOGRAM) 100L else 1000L
                        if (current > step) {
                            onUpdateQuantity(Quantity(current - step))
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Disminuir cantidad",
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Input de cantidad editable directo con OutlinedTextField compacto M3
                OutlinedTextField(
                    value = rawQuantityText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        rawQuantityText = filtered
                        val parsed = filtered.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            onUpdateQuantity(Quantity.fromDouble(parsed))
                        }
                    },
                    textStyle = TextStyle(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = rawQuantityText.toDoubleOrNull()
                            if (parsed != null && parsed > 0.0) {
                                onUpdateQuantity(Quantity.fromDouble(parsed))
                            }
                        }
                    ),
                    modifier = Modifier.width(58.dp).height(38.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                FilledTonalIconButton(
                    onClick = {
                        val current = line.quantity.millis
                        val step = if (line.unit == ProductUnit.KILOGRAM) 100L else 1000L
                        onUpdateQuantity(Quantity(current + step))
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar cantidad",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 4. PRECIOS E IMPORTE TOTAL DE PARTIDA
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(90.dp)
            ) {
                Text(
                    text = line.total.format(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${line.unitPrice.format()} c/u",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 5. ACCIONES CONTEXTUALES [⋮]
            Box {
                IconButton(
                    onClick = { isMenuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones de artículo",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Eliminar artículo (Supr / Del)") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            isMenuExpanded = false
                            onRemove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Aplicar descuento a línea") },
                        leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onDiscount()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cambiar precio") },
                        leadingIcon = { Icon(Icons.Default.PriceChange, contentDescription = null) },
                        onClick = {
                            isMenuExpanded = false
                            onChangePrice()
                        }
                    )
                }
            }
        }
    }
}

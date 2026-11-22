package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

/**
 * Componente de "Parrilla de Productos en Venta" (Data Grid Central / Carrito).
 *
 * Presenta una tabla contable de alto rendimiento con encabezado fijo, 8 columnas
 * proporcionales, soporte para productos pesados a granel con badge RS-232,
 * control de cantidad interactivo con stepper (+ / -), edición directa por teclado,
 * y resaltado visual de fila activa.
 */
@Composable
fun CartGrid(
    items: List<CartLine>,
    selectedLineId: String?,
    onSelectItem: (lineId: String) -> Unit,
    onRemoveItem: (lineId: String) -> Unit,
    onUpdateQuantity: ((lineId: String, newQuantity: Quantity) -> Unit)? = null,
    onEditQuantity: ((line: CartLine) -> Unit)? = null,
    onSearchCatalog: (() -> Unit)? = null,
    onCommonArticles: (() -> Unit)? = null,
    onInsertGeneric: (() -> Unit)? = null,
    onWeighBulk: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // ENCABEZADO FIJO DE LA PARRILLA
            // =========================================================================
            CartGridHeader()

            HorizontalDivider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)

            // =========================================================================
            // CUERPO SCROLLABLE VIRTUALIZADO (LazyColumn)
            // =========================================================================
            if (items.isEmpty()) {
                CartGridEmptyState(
                    onSearchCatalog = onSearchCatalog,
                    onCommonArticles = onCommonArticles,
                    onInsertGeneric = onInsertGeneric,
                    onWeighBulk = onWeighBulk,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, line ->
                        val isSelected = line.id == selectedLineId
                        CartGridRow(
                            index = index,
                            line = line,
                            isSelected = isSelected,
                            onSelect = { onSelectItem(line.id) },
                            onRemove = { onRemoveItem(line.id) },
                            onUpdateQuantity = onUpdateQuantity?.let { callback -> { newQty -> callback(line.id, newQty) } },
                            onEditQuantity = onEditQuantity?.let { { it(line) } }
                        )
                        HorizontalDivider(
                            color = NexusTheme.colors.borderSubtle.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            // =========================================================================
            // PIE DE TABLA / RESUMEN (FOOTER INTEGRADO)
            // =========================================================================
            if (footer != null) {
                HorizontalDivider(color = NexusTheme.colors.borderSubtle, thickness = 1.dp)
                footer()
            }
        }
    }
}

/**
 * Encabezado estático de la tabla con las 8 columnas requeridas.
 */
@Composable
private fun CartGridHeader() {
    val headerTextStyle = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. [#]
            Text(
                text = "#",
                style = headerTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp)
            )

            // 2. [CÓDIGO BARRAS]
            Text(
                text = "CÓDIGO BARRAS",
                style = headerTextStyle,
                modifier = Modifier.width(130.dp).padding(horizontal = 4.dp)
            )

            // 3. [DESCRIPCIÓN DEL PRODUCTO]
            Text(
                text = "DESCRIPCIÓN DEL PRODUCTO",
                style = headerTextStyle,
                modifier = Modifier.weight(0.36f).padding(horizontal = 4.dp)
            )

            // 4. [PRECIO VENTA]
            Text(
                text = "PRECIO VENTA",
                style = headerTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.12f).padding(horizontal = 4.dp)
            )

            // 5. [CANTIDAD]
            Text(
                text = "CANTIDAD",
                style = headerTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.15f).padding(horizontal = 4.dp)
            )

            // 6. [IMPORTE]
            Text(
                text = "IMPORTE",
                style = headerTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.12f).padding(horizontal = 4.dp)
            )

            // 7. [EXISTENCIA]
            Text(
                text = "EXISTENCIA",
                style = headerTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.11f).padding(horizontal = 4.dp)
            )

            // 8. [ACCIÓN]
            Text(
                text = "ACCIÓN",
                style = headerTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(42.dp)
            )
        }
    }
}

/**
 * Fila individual del data grid.
 */
@Composable
private fun CartGridRow(
    index: Int,
    line: CartLine,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onUpdateQuantity: ((Quantity) -> Unit)?,
    onEditQuantity: (() -> Unit)?
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
    } else if (index % 2 == 1) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable { onSelect() }
    ) {
        // Indicador lateral de selección activa en Azul/Cian (Focus)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. [#] Partida (01, 02...)
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.secondary else NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp)
            )

            // 2. [CÓDIGO BARRAS]
            Text(
                text = line.barcode,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = NexusTheme.colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(130.dp).padding(horizontal = 4.dp)
            )

            // 3. [DESCRIPCIÓN DEL PRODUCTO]
            Column(
                modifier = Modifier.weight(0.36f).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = line.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = line.category,
                        fontSize = 11.sp,
                        color = NexusTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Badge condicional: "Báscula RS-232" para artículos a granel
                    if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        Surface(
                            color = NexusTheme.colors.surfaceGreen,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, NexusTheme.colors.borderGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "⚖",
                                    fontSize = 10.sp
                                )
                                Text(
                                    "Báscula RS-232",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NexusTheme.colors.textGreen
                                )
                            }
                        }
                    }
                }
            }

            // 4. [PRECIO VENTA]
            Column(
                modifier = Modifier.weight(0.12f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = line.unitPrice.format(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                    Text(
                        text = "/kg",
                        fontSize = 10.5.sp,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            // 5. [CANTIDAD] (Contenedor interactivo con Stepper + / - e Input numérico directo)
            Box(
                modifier = Modifier.weight(0.15f).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (onUpdateQuantity != null) {
                    CartQuantityStepper(
                        line = line,
                        onUpdateQuantity = onUpdateQuantity,
                        onEditQuantity = onEditQuantity
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                        modifier = Modifier
                            .clickable(enabled = onEditQuantity != null) { onEditQuantity?.invoke() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val qtyText = if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                                line.quantity.format(line.unit)
                            } else {
                                (line.quantity.millis / 1000).toString()
                            }

                            Text(
                                text = qtyText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Cantidad",
                                tint = NexusTheme.colors.textSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // 6. [IMPORTE]
            Column(
                modifier = Modifier.weight(0.12f).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = line.total.format(),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = NexusTheme.colors.actionPrimary,
                    textAlign = TextAlign.End
                )
                if (line.discountCents > 0L) {
                    Text(
                        text = if (line.discountPercent > 0.0) "-${line.discountPercent.toInt()}%" else "-$${line.discountCents / 100.0}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.dangerText
                    )
                }
            }

            // 7. [EXISTENCIA]
            Box(
                modifier = Modifier.weight(0.11f).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val stockMillis = line.currentStockMillis
                if (stockMillis == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                    ) {
                        Text(
                            text = "Ilim",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    val stockValue = stockMillis / 1000.0
                    val isLowStock = stockValue <= 5.0
                    val isOutOfStock = stockValue <= 0.0

                    val badgeColor = when {
                        isOutOfStock -> NexusTheme.colors.dangerBg
                        isLowStock -> NexusTheme.colors.warningBg
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val badgeBorder = when {
                        isOutOfStock -> NexusTheme.colors.dangerBorder
                        isLowStock -> NexusTheme.colors.warningBorder
                        else -> NexusTheme.colors.borderSubtle
                    }
                    val textColor = when {
                        isOutOfStock -> NexusTheme.colors.dangerText
                        isLowStock -> NexusTheme.colors.warningText
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val stockFormatted = if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        "${(stockMillis / 1000.0).toString()} kg"
                    } else {
                        "${stockMillis / 1000} pza"
                    }

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, badgeBorder)
                    ) {
                        Text(
                            text = stockFormatted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 8. [ACCIÓN]
            Box(
                modifier = Modifier.width(42.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar artículo",
                        tint = NexusTheme.colors.dangerText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Componente interactivo de ajuste de cantidad en parrilla de ventas.
 *
 * Incluye:
 * - Botones táctiles y ágiles [-] y [+] para incremento/decremento inmediato.
 * - Input numérico directo con BasicTextField, selección total automática al enfocar
 *   para sobreescritura rápida con teclado físico/numpad, confirmación con [Enter] y cancelación con [Esc].
 * - Soporte adaptativo para productos pesables a granel (decimales y botón de re-pesaje RS-232 ⚖).
 * - Protección contra cantidades en cero o negativas con retorno automático al valor previo válido.
 */
@Composable
fun CartQuantityStepper(
    line: CartLine,
    onUpdateQuantity: (Quantity) -> Unit,
    onEditQuantity: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isWeighed = line.isWeighed || line.unit == ProductUnit.KILOGRAM
    val focusManager = LocalFocusManager.current

    val formattedQty = remember(line.quantity, isWeighed) {
        if (isWeighed) {
            val doubleVal = line.quantity.millis / 1000.0
            if (line.quantity.millis % 1000L == 0L) {
                (line.quantity.millis / 1000L).toString()
            } else {
                val whole = line.quantity.millis / 1000L
                val decimals = (line.quantity.millis % 1000L).toString().padStart(3, '0').trimEnd('0')
                "$whole.$decimals"
            }
        } else {
            (line.quantity.millis / 1000L).toString()
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = formattedQty)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(formattedQty) {
        if (!isFocused) {
            textFieldValue = TextFieldValue(text = formattedQty)
        }
    }

    fun commitValue(rawText: String) {
        val clean = rawText.trim().replace(',', '.')
        val parsed = clean.toDoubleOrNull()
        if (parsed != null && parsed > 0.0) {
            val newQty = if (isWeighed) {
                Quantity.fromDouble(parsed)
            } else {
                Quantity.pieces(parsed.toLong())
            }
            if (newQty.millis > 0L) {
                if (newQty != line.quantity) {
                    onUpdateQuantity(newQty)
                }
            } else {
                textFieldValue = TextFieldValue(text = formattedQty)
            }
        } else {
            textFieldValue = TextFieldValue(text = formattedQty)
        }
    }

    val stepMillis = if (isWeighed) 100L else 1000L
    val minMillis = if (isWeighed) 100L else 1000L
    val canDecrease = line.quantity.millis > minMillis

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Surface(
            color = if (isFocused) NexusTheme.colors.bgSurfaceSubtle else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) NexusTheme.colors.accent else NexusTheme.colors.borderSubtle
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(29.dp)
            ) {
                // 1. Botón Restar [-]
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                        .background(
                            if (canDecrease) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .clickable(enabled = canDecrease) {
                            val newMillis = line.quantity.millis - stepMillis
                            if (newMillis >= minMillis) {
                                onUpdateQuantity(Quantity(newMillis))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Disminuir cantidad",
                        tint = if (canDecrease) NexusTheme.colors.textPrimary else NexusTheme.colors.textMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Separador vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(NexusTheme.colors.borderSubtle.copy(alpha = 0.5f))
                )

                // 2. Input numérico editable directo
                Box(
                    modifier = Modifier
                        .widthIn(min = 34.dp, max = 56.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newVal ->
                            val txt = newVal.text
                            val isValid = if (isWeighed) {
                                txt.isEmpty() || txt.matches(Regex("""^\d*([.,]\d{0,3})?$"""))
                            } else {
                                txt.isEmpty() || txt.matches(Regex("""^\d{0,5}$"""))
                            }
                            if (isValid) {
                                textFieldValue = newVal
                            }
                        },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(NexusTheme.colors.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isWeighed) KeyboardType.Decimal else KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitValue(textFieldValue.text)
                                focusManager.clearFocus()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && !isFocused) {
                                    isFocused = true
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(0, textFieldValue.text.length)
                                    )
                                } else if (!focusState.isFocused && isFocused) {
                                    isFocused = false
                                    commitValue(textFieldValue.text)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.Enter, Key.NumPadEnter -> {
                                            commitValue(textFieldValue.text)
                                            focusManager.clearFocus()
                                            true
                                        }
                                        Key.Escape -> {
                                            textFieldValue = TextFieldValue(text = formattedQty)
                                            focusManager.clearFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                }

                // Separador vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(NexusTheme.colors.borderSubtle.copy(alpha = 0.5f))
                )

                // 3. Botón Sumar [+]
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            val newMillis = line.quantity.millis + stepMillis
                            onUpdateQuantity(Quantity(newMillis))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar cantidad",
                        tint = NexusTheme.colors.actionPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // 4. Botón opcional de Báscula para artículos a granel
        if (isWeighed && onEditQuantity != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = NexusTheme.colors.surfaceGreen.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderGreen.copy(alpha = 0.7f)),
                modifier = Modifier
                    .size(29.dp)
                    .clickable { onEditQuantity() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⚖",
                        fontSize = 12.sp,
                        color = NexusTheme.colors.textGreen
                    )
                }
            }
        }
    }
}

/**
 * Vista interactiva y operativa de alto impacto para cuando el ticket no tiene partidas agregadas.
 *
 * Incluye:
 * 1. Indicador de estado de terminal lista para captura de artículos.
 * 2. Composición Hero con icono de carrito de compras y badge de escáner USB activo.
 * 3. Instrucciones visuales claras de escaneo continuo.
 * 4. Tarjetas táctiles de accesos rápidos ([F10] Catálogo, [Ctrl+P] Comunes, [INS] Varios, [F9] Báscula).
 * 5. Barra de recomendación operativa para cajeros (multiplicación por asterisco).
 */
@Composable
private fun CartGridEmptyState(
    onSearchCatalog: (() -> Unit)? = null,
    onCommonArticles: (() -> Unit)? = null,
    onInsertGeneric: (() -> Unit)? = null,
    onWeighBulk: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Badge de Estado Operativo: Terminal Lista
            Surface(
                color = NexusTheme.colors.surfaceGreen.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderGreen.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(NexusTheme.colors.actionPrimary, CircleShape)
                    )
                    Text(
                        text = "TERMINAL LISTA PARA CAPTURA",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = NexusTheme.colors.textGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Composición Hero: Carrito de compras con badge de escáner
            Box(
                modifier = Modifier.size(86.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.5.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = NexusTheme.colors.accent,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Insignia flotante del escáner en la esquina inferior derecha
                Surface(
                    color = NexusTheme.colors.actionPrimary,
                    shape = CircleShape,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Lector Activo",
                            tint = NexusTheme.colors.actionPrimaryFg,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Título y Subtítulo descriptivo
            Text(
                text = "Parrilla de Venta Vacía",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NexusTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Pase el producto frente al lector óptico USB, teclee el SKU en la barra de captura\no utilice los accesos rápidos de mostrador:",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.5.sp,
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 580.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Tarjetas de Acciones Rápidas (Accesos directos interactivos)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmptyStateActionCard(
                    shortcut = "F10",
                    title = "Catálogo",
                    description = "Buscar por nombre / SKU",
                    icon = Icons.Default.Search,
                    accentColor = NexusTheme.colors.accent,
                    onClick = onSearchCatalog
                )

                EmptyStateActionCard(
                    shortcut = "Ctrl+P",
                    title = "Art. Comunes",
                    description = "Bolsas, recargas y varios",
                    icon = Icons.Default.Star,
                    accentColor = NexusTheme.colors.warningText,
                    onClick = onCommonArticles
                )

                EmptyStateActionCard(
                    shortcut = "INS",
                    title = "Varios / Libre",
                    description = "Precio manual al instante",
                    icon = Icons.Default.AddCircleOutline,
                    accentColor = NexusTheme.colors.actionPrimary,
                    onClick = onInsertGeneric
                )

                EmptyStateActionCard(
                    shortcut = "F9",
                    title = "Báscula RS-232",
                    description = "Frutas, verduras y granel",
                    icon = Icons.Default.Scale,
                    accentColor = NexusTheme.colors.totalDisplay,
                    onClick = onWeighBulk
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Nota / Tip de operación para el cajero
            Surface(
                color = NexusTheme.colors.bgSurfaceSubtle,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Tip",
                        tint = NexusTheme.colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tip de cajero:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                        Text(
                            text = "Para multiplicar piezas antes de escanear, escriba el multiplicador con asterisco (ej. 5*75010553) en la barra de captura.",
                            fontSize = 11.5.sp,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta interactiva de acceso rápido en el Empty State del carrito.
 */
@Composable
private fun EmptyStateActionCard(
    shortcut: String,
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = Modifier
            .width(170.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    color = NexusTheme.colors.bgCanvas,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                ) {
                    Text(
                        text = shortcut,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = description,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = NexusTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

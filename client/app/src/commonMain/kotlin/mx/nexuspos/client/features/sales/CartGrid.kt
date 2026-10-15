package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.core.ProductUnit

/**
 * Componente de "Parrilla de Productos en Venta" (Data Grid Central / Carrito).
 *
 * Presenta una tabla contable de alto rendimiento con encabezado fijo, 8 columnas
 * proporcionales, soporte para productos pesados a granel con badge RS-232,
 * contenedor editable para cantidades y resaltado visual de fila activa.
 */
@Composable
fun CartGrid(
    items: List<CartLine>,
    selectedLineId: String?,
    onSelectItem: (lineId: String) -> Unit,
    onRemoveItem: (lineId: String) -> Unit,
    onEditQuantity: ((line: CartLine) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // =========================================================================
            // ENCABEZADO FIJO DE LA PARRILLA
            // =========================================================================
            CartGridHeader()

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), thickness = 1.dp)

            // =========================================================================
            // CUERPO SCROLLABLE VIRTUALIZADO (LazyColumn)
            // =========================================================================
            if (items.isEmpty()) {
                CartGridEmptyState(modifier = Modifier.weight(1f))
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
                            onEditQuantity = onEditQuantity?.let { { it(line) } }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Encabezado estático de la tabla con las 8 columnas requeridas.
 */
@Composable
private fun CartGridHeader() {
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
                style = HeaderTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp)
            )

            // 2. [CÓDIGO BARRAS]
            Text(
                text = "CÓDIGO BARRAS",
                style = HeaderTextStyle,
                modifier = Modifier.width(130.dp).padding(horizontal = 4.dp)
            )

            // 3. [DESCRIPCIÓN DEL PRODUCTO]
            Text(
                text = "DESCRIPCIÓN DEL PRODUCTO",
                style = HeaderTextStyle,
                modifier = Modifier.weight(0.36f).padding(horizontal = 4.dp)
            )

            // 4. [PRECIO VENTA]
            Text(
                text = "PRECIO VENTA",
                style = HeaderTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.12f).padding(horizontal = 4.dp)
            )

            // 5. [CANT.]
            Text(
                text = "CANTIDAD",
                style = HeaderTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.14f).padding(horizontal = 4.dp)
            )

            // 6. [IMPORTE]
            Text(
                text = "IMPORTE",
                style = HeaderTextStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.13f).padding(horizontal = 4.dp)
            )

            // 7. [EXISTENCIA]
            Text(
                text = "EXISTENCIA",
                style = HeaderTextStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.11f).padding(horizontal = 4.dp)
            )

            // 8. [ACCIÓN]
            Text(
                text = "ACCIÓN",
                style = HeaderTextStyle,
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
    onEditQuantity: (() -> Unit)?
) {
    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else if (index % 2 == 1) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable { onSelect() }
    ) {
        // Indicador lateral de selección activa
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.primary)
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(36.dp)
            )

            // 2. [CÓDIGO BARRAS]
            Text(
                text = line.barcode,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Badge condicional: "Báscula RS-232" para artículos a granel
                    if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFA5D6A7))
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
                                    color = Color(0xFF2E7D32)
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
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 5. [CANTIDAD] (Contenedor editable)
            Box(
                modifier = Modifier.weight(0.14f).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // 6. [IMPORTE]
            Text(
                text = line.total.format(),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.13f).padding(horizontal = 4.dp)
            )

            // 7. [EXISTENCIA]
            Box(
                modifier = Modifier.weight(0.11f).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val stockMillis = line.currentStockMillis
                if (stockMillis == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
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
                        isOutOfStock -> Color(0xFFFFEBEE)
                        isLowStock -> Color(0xFFFFF3E0)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val textColor = when {
                        isOutOfStock -> Color(0xFFC62828)
                        isLowStock -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    val stockFormatted = if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        "${(stockMillis / 1000.0).toString()} kg"
                    } else {
                        "${stockMillis / 1000} pza"
                    }

                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(4.dp)
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
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Vista sobria para cuando el ticket no tiene partidas agregadas.
 */
@Composable
private fun CartGridEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = "Parrilla de Venta Vacía",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )

            Text(
                text = "Escanee códigos de barras con el lector USB, teclee un SKU en la barra de captura\no utilice los atajos [INS] Varios y [Ctrl+P] Artículos Comunes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val HeaderTextStyle = androidx.compose.ui.text.TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color(0xFF49454F),
    letterSpacing = 0.5.sp
)

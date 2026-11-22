package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.Money

/**
 * Tarjeta superior del panel derecho: Producto Seleccionado en Contexto.
 * Implementada con el componente oficial ElevatedCard de Material Design 3.
 * Muestra el último artículo escaneado o seleccionado en el carrito, su unidad de medida,
 * selector interactivo de escalas de precio [Precio ▾] y botón de consulta de ficha.
 */
@Composable
fun SelectedProductCard(
    selectedLine: CartLine?,
    onApplyPriceTier: (Money) -> Unit,
    onInspectCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPriceMenuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "PRODUCTO EN CONTEXTO",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.primary
            )

            if (selectedLine == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Escanea o selecciona un producto para ver detalles y precios",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProductThumbnail(
                        name = selectedLine.name,
                        category = selectedLine.category,
                        size = 46.dp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedLine.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        Text(
                            text = "Código: ${selectedLine.barcode} • ${selectedLine.unit.code}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.8.dp)

                // Fila de Precio y Dropdown de Escalas [Precio ▾]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PRECIO ACTIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedLine.unitPrice.format()} / ${selectedLine.unit.code}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Selector de Escala de Precios con DropdownMenu oficial M3
                    Box {
                        OutlinedButton(
                            onClick = { isPriceMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Precio ▾", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = isPriceMenuExpanded,
                            onDismissRequest = { isPriceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Precio 1 (Público): ${selectedLine.unitPrice.format()}") },
                                onClick = {
                                    isPriceMenuExpanded = false
                                    onApplyPriceTier(selectedLine.unitPrice)
                                }
                            )
                            val wholesalePrice = Money((selectedLine.unitPrice.cents * 0.90).toLong())
                            DropdownMenuItem(
                                text = { Text("Precio 2 (Mayoreo -10%): ${wholesalePrice.format()}") },
                                onClick = {
                                    isPriceMenuExpanded = false
                                    onApplyPriceTier(wholesalePrice)
                                }
                            )
                            val specialPrice = Money((selectedLine.unitPrice.cents * 0.80).toLong())
                            DropdownMenuItem(
                                text = { Text("Precio 3 (Especial -20%): ${specialPrice.format()}") },
                                onClick = {
                                    isPriceMenuExpanded = false
                                    onApplyPriceTier(specialPrice)
                                }
                            )
                        }
                    }
                }

                // Botón de consulta de ficha de producto en catálogo
                OutlinedButton(
                    onClick = onInspectCatalog,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Consultar Ficha en Catálogo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

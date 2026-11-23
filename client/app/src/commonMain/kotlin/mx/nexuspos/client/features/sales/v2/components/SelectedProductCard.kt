package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.nexuspos.core.Quantity
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.Money

/** Precio aplicado, existencia registrada y acceso al catálogo del producto seleccionado. */
@Composable
fun SelectedProductCard(
    selectedLine: CartLine?,
    onApplyPriceTier: (Money) -> Unit,
    onInspectCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPriceMenuExpanded by remember(selectedLine?.id) { mutableStateOf(false) }

    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Producto seleccionado",
                style = MaterialTheme.typography.titleSmall
            )

            if (selectedLine == null) {
                Text(
                    "Escanea o selecciona un producto para consultar sus datos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProductThumbnail(
                        name = selectedLine.name,
                        category = selectedLine.category,
                        size = 40.dp,
                        imageUrl = selectedLine.imageUrl
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedLine.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedLine.barcode.isNotBlank()) {
                            Text(
                                selectedLine.barcode,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Fila de Precio y Dropdown de Escalas [Precio ▾]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Precio aplicado",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedLine.unitPrice.format()} / ${selectedLine.unit.symbol}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Selector de Escala de Precios con DropdownMenu oficial M3
                    Box {
                        TextButton(
                            onClick = { isPriceMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Precio")
                            Icon(Icons.Default.ArrowDropDown, null)
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

                selectedLine.currentStockMillis?.let { stock ->
                    Text(
                        "Existencia registrada: ${Quantity(stock).format(selectedLine.unit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(onClick = onInspectCatalog, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir catálogo")
                }
            }
        }
    }
}

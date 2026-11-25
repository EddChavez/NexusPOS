package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
    onInspectCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
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

                Text("Precio aplicado", style = MaterialTheme.typography.labelMedium)
                Text(selectedLine.unitPrice.format(), style = MaterialTheme.typography.titleMedium)
                if (selectedLine.priceTiers.isNotEmpty()) {
                    val tier = selectedLine.priceTiers.filter { it.minimumQuantityMillis <= selectedLine.quantity.millis }
                        .maxByOrNull { it.minimumQuantityMillis }
                    val nextTier = selectedLine.priceTiers.filter { it.minimumQuantityMillis > selectedLine.quantity.millis }
                        .minByOrNull { it.minimumQuantityMillis }
                    if (tier != null && selectedLine.unitPrice.cents < (selectedLine.publicPriceCents ?: 0)) {
                        Text("Mayoreo aplicado", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    nextTier?.let {
                        Text(
                            "Desde ${volumeQuantityLabel(it.minimumQuantityMillis, selectedLine.unit)} · ${Money(minOf(it.unitPriceCents, selectedLine.publicPriceCents ?: it.unitPriceCents)).format()}",
                            style = MaterialTheme.typography.bodySmall
                        )
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

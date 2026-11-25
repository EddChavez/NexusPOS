package mx.nexuspos.client.features.sales

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto

@Composable
internal fun BarcodeChoicesDialog(
    matches: List<Pair<ProductDto, ProductPresentationDto>>,
    select: (Pair<ProductDto, ProductPresentationDto>?) -> Unit
) {
    AlertDialog(
        onDismissRequest = { select(null) },
        title = { Text("Elige una presentación") },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(matches, key = { it.second.id }) { item ->
                    TextButton(onClick = { select(item) }) { Text("${item.first.name} · ${item.second.name} · ${item.second.barcode}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { select(null) }) { Text("Cancelar") } }
    )
}

package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.core.Money

private data class DemoItem(val name: String, val detail: String)
private val demoProducts = listOf(
    DemoItem("Arroz 1 kg", "Pieza · ${Money(3200).format()}"),
    DemoItem("Manzana roja", "Por kg · ${Money(4680).format()}"),
    DemoItem("Agua natural 600 ml", "Pieza · ${Money(1250).format()}")
)
private val demoCustomers = listOf(
    DemoItem("Cliente de contado", "Sin adeudo"),
    DemoItem("Cliente con fiado", "Saldo de ejemplo · ${Money(18550).format()}")
)

@Composable
internal fun GeneralSettingsWithDemo(preferences: @Composable () -> Unit) {
    var showDemo by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showDemo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Datos de demostración", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showDemo = false; search = "" }) { Text("Volver a General") }
            }
            Text("Consulta productos y clientes de ejemplo. Los datos de la tienda quedan separados.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(products, { products = true; search = "" }, { Text("Productos") })
                FilterChip(!products, { products = false; search = "" }, { Text("Clientes") })
            }
            OutlinedTextField(search, { search = it.take(80) }, label = { Text("Buscar datos de ejemplo") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = (if (products) demoProducts else demoCustomers).filter { it.name.contains(search.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) Text("Sin resultados.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered) { item -> ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text(item.detail) }) }
            }
        } else {
            preferences()
            HorizontalDivider()
            OutlinedButton(onClick = { showDemo = true }) { Text("Ver datos de demostración") }
        }
    }
}

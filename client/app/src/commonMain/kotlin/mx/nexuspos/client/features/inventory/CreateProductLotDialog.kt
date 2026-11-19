package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.ProductDto
import java.time.LocalDate

@Composable
fun CreateProductLotDialog(
    products: List<ProductDto>,
    initialProduct: ProductDto? = null,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (
        productId: String,
        lotNumber: String,
        expirationDate: String,
        quantityMillis: Long,
        costCents: Long,
        addToGeneralStock: Boolean,
        notes: String
    ) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(initialProduct ?: products.firstOrNull()) }
    var productSearchQuery by remember { mutableStateOf("") }
    var isSelectingProduct by remember { mutableStateOf(initialProduct == null) }

    var lotNumber by remember { mutableStateOf("L-" + System.currentTimeMillis().toString().takeLast(6)) }
    var expirationDate by remember {
        val defaultExp = LocalDate.now().plusDays(30).toString()
        mutableStateOf(defaultExp)
    }
    var quantityText by remember { mutableStateOf("10") }
    var costText by remember {
        val initialCost = (selectedProduct?.costCents ?: 0L) / 100.0
        mutableStateOf(if (initialCost > 0) initialCost.toString() else "0.00")
    }
    var addToGeneralStock by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(productSearchQuery, products) {
        if (productSearchQuery.isBlank()) products.take(20)
        else {
            val q = productSearchQuery.lowercase().trim()
            products.filter {
                it.name.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                it.presentations.any { pres -> pres.barcode.contains(q) }
            }.take(20)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier.width(620.dp).wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = NexusTheme.colors.actionPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Registrar Lote de Producto Perecedero",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = NexusTheme.colors.textSecondary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = NexusTheme.colors.borderPrimary)

                if (isSelectingProduct || selectedProduct == null) {
                    Text("Selecciona el producto:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary)
                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = productSearchQuery,
                        onValueChange = { productSearchQuery = it },
                        placeholder = { Text("Buscar por nombre, SKU o código...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NexusTheme.colors.textSecondary) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(filteredProducts) { prod ->
                            Surface(
                                color = if (selectedProduct?.id == prod.id) NexusTheme.colors.actionPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProduct = prod
                                        costText = ((prod.costCents) / 100.0).toString()
                                        isSelectingProduct = false
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NexusTheme.colors.textPrimary)
                                        Text("SKU: ${prod.sku} | Unidad: ${prod.baseUnit}", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                    }
                                    Text("Stock: ${prod.currentStockMillis / 1000.0}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                                }
                            }
                        }
                    }
                } else {
                    // Producto seleccionado
                    Surface(
                        color = NexusTheme.colors.bgSurfaceSubtle,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(selectedProduct!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NexusTheme.colors.textPrimary)
                                Text("SKU: ${selectedProduct!!.sku} | Umbral Alerta: ${selectedProduct!!.shelfLifeAlertDays} días", fontSize = 11.5.sp, color = NexusTheme.colors.textSecondary)
                            }
                            TextButton(onClick = { isSelectingProduct = true }) {
                                Text("Cambiar Producto", fontSize = 12.sp, color = NexusTheme.colors.actionPrimary)
                            }
                        }
                    }

                    // Campos: Lote y Fecha de Caducidad
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Número de Lote:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = lotNumber,
                                onValueChange = { lotNumber = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fecha de Caducidad (YYYY-MM-DD):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = expirationDate,
                                onValueChange = { expirationDate = it },
                                singleLine = true,
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Botones rápidos de fecha
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val today = remember { LocalDate.now() }
                        listOf(7, 15, 30, 60, 90).forEach { days ->
                            SuggestionChip(
                                onClick = { expirationDate = today.plusDays(days.toLong()).toString() },
                                label = { Text("+$days d", fontSize = 11.sp) }
                            )
                        }
                    }

                    // Cantidad y Costo
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cantidad recibida:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { quantityText = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Costo Unitario ($):", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = costText,
                                onValueChange = { costText = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Checkbox para sumar al inventario general
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = addToGeneralStock,
                            onCheckedChange = { addToGeneralStock = it }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sumar esta cantidad a existencias físicas y registrar movimiento en Kardex", fontSize = 12.sp, color = NexusTheme.colors.textPrimary)
                    }

                    Spacer(Modifier.height(6.dp))

                    // Notas
                    Text("Notas u observaciones:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NexusTheme.colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Ej. Proveedor Carnes del Norte, tarima 4", fontSize = 12.sp) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                validationError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = NexusTheme.colors.dangerText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                // Footer Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val prod = selectedProduct
                            if (prod == null) {
                                validationError = "Debe seleccionar un producto"
                                return@Button
                            }
                            val cleanDate = expirationDate.trim()
                            try {
                                LocalDate.parse(cleanDate)
                            } catch (e: Exception) {
                                validationError = "Fecha inválida. Use formato YYYY-MM-DD"
                                return@Button
                            }

                            val qty = quantityText.toDoubleOrNull() ?: 0.0
                            if (qty <= 0.0) {
                                validationError = "La cantidad debe ser mayor a 0"
                                return@Button
                            }

                            val cost = (costText.toDoubleOrNull() ?: 0.0) * 100.0

                            onConfirm(
                                prod.id,
                                lotNumber.trim().ifBlank { "S/L" },
                                cleanDate,
                                (qty * 1000.0).toLong(),
                                cost.toLong(),
                                addToGeneralStock,
                                notes.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                        enabled = !isSubmitting && selectedProduct != null
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Guardar Lote")
                    }
                }
            }
        }
    }
}

package mx.nexuspos.client.features.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.Money
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ShelfLabelsDialog(
    isOpen: Boolean,
    products: List<ProductDto>,
    selectedProduct: ProductDto? = null,
    onSelectProduct: (ProductDto) -> Unit,
    onPrintLabel: (product: ProductDto, copies: Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val currentProd = selectedProduct ?: products.firstOrNull()
    var copies by remember { mutableStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.sku.contains(searchQuery, ignoreCase = true) ||
            it.presentations.any { p -> p.barcode.contains(searchQuery) }
        }
    }

    val todayFormatted = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.5.dp, NexusTheme.colors.borderSubtle),
            modifier = Modifier
                .width(820.dp)
                .height(580.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Etiquetas de Anaquel",
                            tint = NexusTheme.colors.actionPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Etiquetas de Anaquel (Shelf Labels)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Diseño optimizado para rollos térmicos de 50x30mm o 58mm y estantes de exhibición.",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NexusTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Split Layout: Selector a la izquierda, Preview de Etiqueta a la derecha
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Selector de productos (Columna Izquierda)
                    Column(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Buscar producto...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NexusTheme.colors.bgSurfaceSubtle,
                                unfocusedContainerColor = NexusTheme.colors.bgSurfaceSubtle
                            )
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            color = NexusTheme.colors.bgSurfaceSubtle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filteredProducts, key = { it.id }) { prod ->
                                    val isSelected = currentProd?.id == prod.id
                                    val price = prod.presentations.firstOrNull()?.priceCents ?: 0L
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) NexusTheme.colors.bgSurface else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.5.dp, NexusTheme.colors.borderPrimary) else null,
                                        modifier = Modifier.fillMaxWidth().clickable { onSelectProduct(prod) }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary, maxLines = 1)
                                                Text("SKU: ${prod.sku} | ${prod.presentations.firstOrNull()?.barcode ?: "Sin código"}", fontSize = 10.sp, color = NexusTheme.colors.textSecondary)
                                            }
                                            Text(Money(price).format(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = NexusTheme.colors.textGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Previsualización de Etiqueta de Anaquel (Columna Derecha)
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Previsualización de Etiqueta Térmica",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )

                        // Tarjeta de la etiqueta física simulada (blanca con borde negro como en estante)
                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(2.dp, Color.Black)
                        ) {
                            if (currentProd != null) {
                                val presentation = currentProd.presentations.firstOrNull()
                                val priceCents = presentation?.priceCents ?: 0L
                                val barcode = presentation?.barcode ?: currentProd.sku
                                val presName = presentation?.name ?: if (currentProd.baseUnit == "KG") "Kilo" else "Pieza"

                                Column(
                                    modifier = Modifier.fillMaxSize().padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Encabezado
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "NEXUS POS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )
                                        HorizontalDivider(color = Color.Black, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                        Text(
                                            text = currentProd.name.uppercase(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = "Presentación: $presName | ${currentProd.departmentName ?: "General"}",
                                            fontSize = 10.sp,
                                            color = Color.DarkGray
                                        )
                                    }

                                    // Precio Gigante
                                    Surface(
                                        color = Color.Black,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "  ${Money(priceCents).format()}  ",
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Código de barras simulado (rayas verticales con tipografía monospace)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "||| || |||| | ||||| || ||| ||||| |||",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = barcode,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "PRECIO VIGENTE: $todayFormatted",
                                            fontSize = 8.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Seleccione un producto para previsualizar", color = Color.Gray)
                                }
                            }
                        }

                        // Configuración de copias
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cantidad de copias:", fontSize = 12.sp, color = NexusTheme.colors.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 2, 5, 10).forEach { num ->
                                    val isSelected = copies == num
                                    OutlinedButton(
                                        onClick = { copies = num },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) NexusTheme.colors.actionPrimary else Color.Transparent,
                                            contentColor = if (isSelected) NexusTheme.colors.actionPrimaryFg else NexusTheme.colors.textPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("$num", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                    ) {
                        Text("Cerrar [Esc]")
                    }

                    Button(
                        onClick = {
                            currentProd?.let { onPrintLabel(it, copies) }
                        },
                        enabled = currentProd != null,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusTheme.colors.actionPrimary,
                            contentColor = NexusTheme.colors.actionPrimaryFg
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Imprimir", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Imprimir Etiqueta ($copies ${if (copies == 1) "copia" else "copias"})")
                    }
                }
            }
        }
    }
}

package mx.nexuspos.client.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.ProductDto
import kotlin.math.abs

enum class MovementMode(val label: String) {
    ENTRY("📥 Entrada"),
    LOSS("📉 Merma / Pérdida"),
    COUNT_ADJUSTMENT("⚖️ Conteo Físico")
}

@Composable
fun StockMovementDialog(
    product: ProductDto,
    onConfirm: (type: String, quantityMillis: Long, reason: String, reference: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(MovementMode.ENTRY) }
    var quantityText by remember { mutableStateOf("") }
    var countedStockText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val currentStock = product.currentStockMillis / 1000.0
    val baseUnit = product.baseUnit

    // Cálculo dinámico del nuevo stock según el modo
    val (deltaUnits, targetStock) = remember(mode, quantityText, countedStockText, currentStock) {
        when (mode) {
            MovementMode.ENTRY -> {
                val q = quantityText.toDoubleOrNull() ?: 0.0
                Pair(q, currentStock + q)
            }
            MovementMode.LOSS -> {
                val q = quantityText.toDoubleOrNull() ?: 0.0
                Pair(-q, currentStock - q)
            }
            MovementMode.COUNT_ADJUSTMENT -> {
                val target = countedStockText.toDoubleOrNull()
                if (target != null) {
                    val delta = target - currentStock
                    Pair(delta, target)
                } else {
                    Pair(0.0, currentStock)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Ajustar Inventario",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                Text(
                    text = "${product.sku} • ${product.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 500.dp, max = 560.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Selector de Tipo de Movimiento
                Text(
                    text = "Tipo de Operación",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.accent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovementMode.values().forEach { m ->
                        val isSelected = mode == m
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                mode = m
                                validationError = null
                            },
                            label = { Text(m.label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                // 2. Panel informativo de Stock Actual y Proyección
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Existencia Actual",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = "$currentStock $baseUnit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        Text(
                            text = "➔",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexusTheme.colors.textSecondary
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Nueva Existencia Proyectada",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Text(
                                text = "$targetStock $baseUnit (${if (deltaUnits >= 0) "+$deltaUnits" else "$deltaUnits"})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (targetStock < 0) NexusTheme.colors.dangerText else NexusTheme.colors.actionPrimary
                            )
                        }
                    }
                }

                // 3. Inputs de Captura
                when (mode) {
                    MovementMode.ENTRY -> {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it
                                validationError = null
                            },
                            label = { Text("Cantidad a Ingresar ($baseUnit) *") },
                            placeholder = { Text("Ej. 10") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MovementMode.LOSS -> {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = {
                                quantityText = it
                                validationError = null
                            },
                            label = { Text("Cantidad de Merma o Pérdida ($baseUnit) *") },
                            placeholder = { Text("Ej. 2") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MovementMode.COUNT_ADJUSTMENT -> {
                        OutlinedTextField(
                            value = countedStockText,
                            onValueChange = {
                                countedStockText = it
                                validationError = null
                            },
                            label = { Text("Existencia Real Contada en Anaquel ($baseUnit) *") },
                            placeholder = { Text("Ej. 18") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Motivo / Concepto del Movimiento") },
                    placeholder = {
                        Text(
                            when (mode) {
                                MovementMode.ENTRY -> "Ej. Factura proveedor F-1234 / Recepción de surtido"
                                MovementMode.LOSS -> "Ej. Empaque roto, caducado, rotura en traslado"
                                MovementMode.COUNT_ADJUSTMENT -> "Ej. Conteo físico de inventario / Auditoría mensual"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = referenceText,
                    onValueChange = { referenceText = it },
                    label = { Text("Documento o Folio de Referencia (Opcional)") },
                    placeholder = { Text("Ej. REM-4091 o FAC-889") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (type, deltaMillis) = when (mode) {
                        MovementMode.ENTRY -> {
                            val q = quantityText.toDoubleOrNull()
                            if (q == null || q <= 0.0) {
                                validationError = "Ingresa una cantidad de entrada válida mayor a 0"
                                return@Button
                            }
                            Pair("ENTRY", (q * 1000.0).toLong())
                        }
                        MovementMode.LOSS -> {
                            val q = quantityText.toDoubleOrNull()
                            if (q == null || q <= 0.0) {
                                validationError = "Ingresa una cantidad de merma válida mayor a 0"
                                return@Button
                            }
                            Pair("LOSS", (q * 1000.0).toLong())
                        }
                        MovementMode.COUNT_ADJUSTMENT -> {
                            val target = countedStockText.toDoubleOrNull()
                            if (target == null || target < 0.0) {
                                validationError = "Ingresa una existencia contada válida mayor o igual a 0"
                                return@Button
                            }
                            val delta = target - currentStock
                            if (abs(delta) < 0.0001) {
                                validationError = "La existencia contada es idéntica al stock actual (no hay ajuste)"
                                return@Button
                            }
                            Pair("ADJUSTMENT", (delta * 1000.0).toLong())
                        }
                    }

                    val finalReason = reasonText.trim().ifBlank {
                        when (mode) {
                            MovementMode.ENTRY -> "Entrada de mercancía"
                            MovementMode.LOSS -> "Merma o merma operativa"
                            MovementMode.COUNT_ADJUSTMENT -> "Ajuste por conteo físico"
                        }
                    }

                    onConfirm(type, deltaMillis, finalReason, referenceText.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
            ) {
                Text("Confirmar Movimiento", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.Quantity
import kotlin.math.roundToLong

enum class DiscountMode {
    PERCENT, FIXED_AMOUNT
}

enum class DiscountScope {
    SELECTED_LINE, GLOBAL_CART
}

@Composable
fun ApplyDiscountDialog(
    isOpen: Boolean,
    selectedLine: CartLine?,
    cartLines: List<CartLine>,
    maxCashierDiscountPercent: Double,
    isCashierRole: Boolean,
    onDismiss: () -> Unit,
    onApplyDiscount: (targetLineId: String?, percent: Double, fixedCents: Long) -> Unit
) {
    if (!isOpen) return

    var scope by remember {
        mutableStateOf(if (selectedLine != null) DiscountScope.SELECTED_LINE else DiscountScope.GLOBAL_CART)
    }
    var mode by remember { mutableStateOf(DiscountMode.PERCENT) }
    var inputValue by remember { mutableStateOf("") }

    val baseMoney: Money = remember(scope, selectedLine, cartLines) {
        if (scope == DiscountScope.SELECTED_LINE && selectedLine != null) {
            PricingRules.calculateLineTotal(selectedLine.unitPrice, selectedLine.quantity)
        } else {
            Money(cartLines.sumOf { PricingRules.calculateLineTotal(it.unitPrice, it.quantity).cents })
        }
    }

    val parsedPercent = remember(mode, inputValue) {
        if (mode == DiscountMode.PERCENT) {
            inputValue.toDoubleOrNull() ?: 0.0
        } else 0.0
    }

    val parsedFixedCents = remember(mode, inputValue) {
        if (mode == DiscountMode.FIXED_AMOUNT) {
            val amount = inputValue.toDoubleOrNull() ?: 0.0
            (amount * 100.0).roundToLong().coerceAtLeast(0L)
        } else 0L
    }

    val (finalTotal, discountCalculated) = remember(baseMoney, parsedPercent, parsedFixedCents) {
        if (mode == DiscountMode.PERCENT) {
            PricingRules.calculateLineTotalWithDiscount(
                unitPrice = baseMoney,
                quantity = Quantity.ONE,
                discountPercent = parsedPercent
            )
        } else {
            PricingRules.calculateLineTotalWithDiscount(
                unitPrice = baseMoney,
                quantity = Quantity.ONE,
                discountCents = parsedFixedCents
            )
        }
    }

    val effectivePercent = remember(baseMoney, discountCalculated) {
        if (baseMoney.cents > 0L) {
            (discountCalculated.cents.toDouble() / baseMoney.cents.toDouble()) * 100.0
        } else 0.0
    }

    val requiresSupervisor = remember(isCashierRole, effectivePercent, maxCashierDiscountPercent) {
        isCashierRole && effectivePercent > maxCashierDiscountPercent
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(520.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = NexusTheme.colors.accent
                        )
                        Text(
                            text = "Aplicar Descuento",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NexusTheme.colors.textSecondary
                        )
                    }
                }

                // Scope selector (Partida vs Global)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scope == DiscountScope.SELECTED_LINE && selectedLine != null,
                        onClick = { if (selectedLine != null) scope = DiscountScope.SELECTED_LINE },
                        label = {
                            Text(
                                if (selectedLine != null) "Partida: ${selectedLine.name.take(18)}" else "Partida (No selec.)",
                                fontSize = 12.sp
                            )
                        },
                        enabled = selectedLine != null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = scope == DiscountScope.GLOBAL_CART,
                        onClick = { scope = DiscountScope.GLOBAL_CART },
                        label = { Text("Toda la Venta (Global)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Mode selector (Percent vs Amount)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == DiscountMode.PERCENT,
                        onClick = { mode = DiscountMode.PERCENT; inputValue = "" },
                        label = { Text("Porcentaje (%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == DiscountMode.FIXED_AMOUNT,
                        onClick = { mode = DiscountMode.FIXED_AMOUNT; inputValue = "" },
                        label = { Text("Monto Fijo ($)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Input field
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it.filter { c -> c.isDigit() || c == '.' } },
                    label = {
                        Text(if (mode == DiscountMode.PERCENT) "Porcentaje de Descuento (0 - 100%)" else "Monto de Descuento en Pesos ($)")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (discountCalculated.cents > 0L) {
                            val targetId = if (scope == DiscountScope.SELECTED_LINE) selectedLine?.id else null
                            onApplyDiscount(targetId, parsedPercent, parsedFixedCents)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusTheme.colors.accent,
                        unfocusedBorderColor = NexusTheme.colors.borderSubtle
                    )
                )

                // Quick presets for percentage
                if (mode == DiscountMode.PERCENT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20, 25).forEach { preset ->
                            OutlinedButton(
                                onClick = { inputValue = preset.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Text("$preset%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Live calculation preview card
                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Importe Base:", fontSize = 12.sp, color = NexusTheme.colors.textSecondary)
                            Text(baseMoney.format(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NexusTheme.colors.textPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Descuento (${effectivePercent.let { "%.1f".format(it) }}%):",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.dangerText
                            )
                            Text("-${discountCalculated.format()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.dangerText)
                        }
                        HorizontalDivider(color = NexusTheme.colors.borderSubtle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total con Descuento:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)
                            Text(finalTotal.format(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = NexusTheme.colors.accent)
                        }
                    }
                }

                // Supervisor alert if required
                if (requiresSupervisor) {
                    Surface(
                        color = Color(0xFF332005),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF8C5D00)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "El descuento supera el ${maxCashierDiscountPercent.toInt()}% permitido. Se solicitará PIN de Supervisor.",
                                fontSize = 11.sp,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val targetId = if (scope == DiscountScope.SELECTED_LINE) selectedLine?.id else null
                            onApplyDiscount(targetId, 0.0, 0L)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Quitar Descuento", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val targetId = if (scope == DiscountScope.SELECTED_LINE) selectedLine?.id else null
                            onApplyDiscount(targetId, parsedPercent, parsedFixedCents)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.accent),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = if (requiresSupervisor) "Solicitar Autorización" else "Aplicar Descuento",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

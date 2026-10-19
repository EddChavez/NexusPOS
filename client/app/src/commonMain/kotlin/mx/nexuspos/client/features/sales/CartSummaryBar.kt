package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.core.Money

/**
 * Barra horizontal de resumen de venta actual ([CartSummaryBar]) para NexusPOS en Compose Desktop.
 *
 * Ubicada estratégicamente entre la parrilla de artículos ([CartGrid]) y el panel de cobro ([BottomCheckoutPanel]).
 * Ofrece al cajero un desglose ágil y de alto contraste de:
 * 1. Métricas de conteo físico (productos distintos en badge oscuro y suma total de unidades).
 * 2. Métricas fiscales y comerciales (Subtotal, desglose de IVA y descuentos en Verde Neón).
 */
@Composable
fun CartSummaryBar(
    state: CartSummaryBarState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NexusTheme.colors.bgSurfaceSubtle,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // =========================================================================
            // LADO IZQUIERDO: MÉTRICAS DE CANTIDAD Y UNIDADES
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pequeño círculo o Badge oscuro con el número de productos
                Surface(
                    shape = CircleShape,
                    color = NexusTheme.colors.bgCanvas,
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = state.totalProducts.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                // Texto principal en blanco / alto contraste
                Text(
                    text = "Productos en la venta actual",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                // Separador visual
                Text(
                    text = "•",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary
                )

                // Texto secundario en color gris azulado claro
                Text(
                    text = "${state.totalUnitsFormatted} Unidades totales",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = NexusTheme.colors.textSecondary
                )
            }

            // =========================================================================
            // LADO DERECHO: MÉTRICAS FINANCIERAS (SUBTOTAL, IVA, DESCUENTOS)
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subtotal
                FinancialMetricItem(
                    label = "Subtotal:",
                    value = state.subtotalFormatted,
                    valueColor = NexusTheme.colors.textPrimary
                )

                // IVA (tasa dinámica o configurable)
                FinancialMetricItem(
                    label = "${state.taxLabel}:",
                    value = state.taxFormatted,
                    valueColor = NexusTheme.colors.textPrimary
                )

                // Descuentos en Verde Neón / Esmeralda
                FinancialMetricItem(
                    label = "Descuentos:",
                    value = state.discountFormatted,
                    valueColor = NexusTheme.colors.actionPrimary
                )
            }
        }
    }
}

/**
 * Sobrecarga que expone directamente los parámetros del carrito para facilitar
 * la inyección reactiva directa desde la pantalla o ViewModel.
 */
@Composable
fun CartSummaryBar(
    totalProducts: Int,
    totalUnits: Double,
    subtotal: Money,
    tax: Money,
    discount: Money = Money.ZERO,
    modifier: Modifier = Modifier,
    taxLabel: String = "IVA 0%"
) {
    val unitsFormatted = if (totalUnits == 0.0) {
        "0"
    } else if (totalUnits % 1.0 == 0.0) {
        totalUnits.toLong().toString()
    } else {
        val rounded = kotlin.math.round(totalUnits * 1000.0) / 1000.0
        rounded.toString().trimEnd('0').trimEnd('.')
    }

    val isDiscounted = discount.isPositive()
    val discountFormatted = if (isDiscounted) "-${discount.format()}" else "-$0.00"

    val state = CartSummaryBarState(
        totalProducts = totalProducts,
        totalUnits = totalUnits,
        totalUnitsFormatted = unitsFormatted,
        subtotalFormatted = subtotal.format(),
        taxFormatted = tax.format(),
        taxLabel = taxLabel,
        discountFormatted = discountFormatted,
        hasDiscount = isDiscounted
    )

    CartSummaryBar(
        state = state,
        modifier = modifier
    )
}

/**
 * Componente interno para renderizar cada métrica financiera en línea con etiqueta y valor.
 */
@Composable
private fun FinancialMetricItem(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = NexusTheme.colors.textSecondary
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

package mx.nexuspos.client.features.sales

import mx.nexuspos.core.Money

/**
 * Modelo de estado inmutable para la barra de resumen de venta actual ([CartSummaryBar]).
 *
 * Expone de forma reactiva las métricas físicas y financieras del carrito activo:
 * - Cantidad de tipos de productos distintos en la venta actual.
 * - Suma de unidades totales (piezas enteras y kilogramos/litros decimales).
 * - Desglose financiero en línea: Subtotal, Impuestos (IVA dinámico) y Descuentos aplicados.
 */
data class CartSummaryBarState(
    val totalProducts: Int = 0,
    val totalUnits: Double = 0.0,
    val totalUnitsFormatted: String = "0",
    val subtotalFormatted: String = "$0.00",
    val taxFormatted: String = "$0.00",
    val taxLabel: String = "IVA 0%",
    val discountFormatted: String = "-$0.00",
    val hasDiscount: Boolean = false
)

/**
 * Mapea la información del carrito en [SalesUiState] a la proyección [CartSummaryBarState].
 */
fun SalesUiState.toCartSummaryBarState(): CartSummaryBarState {
    val totalProducts = cartLines.size
    val totalUnits = cartLines.sumOf { it.quantity.toDouble }

    val unitsFormatted = if (totalUnits == 0.0) {
        "0"
    } else if (totalUnits % 1.0 == 0.0) {
        totalUnits.toLong().toString()
    } else {
        // Formato limpio con hasta 3 decimales para productos a granel/pesados
        val rounded = kotlin.math.round(totalUnits * 1000.0) / 1000.0
        rounded.toString().trimEnd('0').trimEnd('.')
    }

    // Tasa de impuesto predominante en el carrito
    val maxTaxRate = cartLines.maxOfOrNull { it.taxRate } ?: 0.0
    val taxLabel = if (maxTaxRate > 0.0) "IVA ${(maxTaxRate * 100).toInt()}%" else "IVA 0%"

    val isDiscounted = discount.isPositive()
    val discountFormatted = if (isDiscounted) "-${discount.format()}" else "-$0.00"

    return CartSummaryBarState(
        totalProducts = totalProducts,
        totalUnits = totalUnits,
        totalUnitsFormatted = unitsFormatted,
        subtotalFormatted = subtotal.format(),
        taxFormatted = tax.format(),
        taxLabel = taxLabel,
        discountFormatted = discountFormatted,
        hasDiscount = isDiscounted
    )
}

package mx.nexuspos.client.features.sales.v2.components

import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

internal val ProductUnit.symbol: String
    get() = Quantity.ZERO.format(this).substringAfter(' ')

internal fun parseSalesQuantity(text: String): Quantity? {
    val normalized = text.replace(',', '.')
    val whole = normalized.substringBefore('.')
    val fraction = normalized.substringAfter('.', "")
    if (whole.any { it !in '0'..'9' } || fraction.length > 3 || fraction.any { it !in '0'..'9' }) {
        return null
    }
    val units = whole.ifEmpty { "0" }.toLongOrNull() ?: return null
    val millis = fraction.padEnd(3, '0').toLong()
    if (units > (Long.MAX_VALUE - millis) / 1000L) return null
    return Quantity(units * 1000L + millis).takeIf { it.isPositive() }
}

internal fun formatCheckoutSummary(lines: List<CartLine>): String {
    val count = lines.size
    val quantities = lines.groupingBy { it.unit }.fold(Quantity.ZERO) { total, line -> total + line.quantity }
    return buildList {
        add(if (count == 1) "1 artículo" else "$count artículos")
        for (unit in ProductUnit.entries) {
            val quantity = quantities[unit] ?: continue
            val formatted = quantity.format(unit)
            add(
                if (unit == ProductUnit.PIECE) {
                    val label = if (quantity == Quantity.ONE) "unidad" else "unidades"
                    "${formatted.substringBefore(' ')} $label"
                } else {
                    formatted
                }
            )
        }
    }.joinToString(" · ")
}

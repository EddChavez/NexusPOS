package mx.nexuspos.client.features.sales.v2.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

class SalesQuantityFormatTest {
    @Test
    fun parsesWholeAndFractionalQuantitiesWithoutRounding() {
        assertEquals(Quantity.pieces(4), parseSalesQuantity("4"))
        assertEquals(Quantity(250), parseSalesQuantity("0.250"))
        assertEquals(Quantity(250), parseSalesQuantity("0,250"))
        assertEquals(Quantity(Long.MAX_VALUE), parseSalesQuantity("9223372036854775.807"))
        for (input in listOf("", ".", "0", "-1", "1e3", "1..2", "0.0001", "9223372036854775.808")) {
            assertNull(parseSalesQuantity(input), input)
        }
    }

    @Test
    fun groupsCompatibleQuantitiesAndInflectsPieceCounts() {
        val onePiece = line(ProductUnit.PIECE, 1000)
        val fourPieces = line(ProductUnit.PIECE, 4000)
        assertEquals("0 artículos", formatCheckoutSummary(emptyList()))
        assertEquals("1 artículo · 1 unidad", formatCheckoutSummary(listOf(onePiece)))
        assertEquals("1 artículo · 4 unidades", formatCheckoutSummary(listOf(fourPieces)))
        assertEquals("2 artículos · 5 unidades", formatCheckoutSummary(listOf(onePiece, fourPieces)))
        assertEquals(
            "3 artículos · 4 unidades · 0.250 kg · 1.500 L",
            formatCheckoutSummary(listOf(fourPieces, line(ProductUnit.KILOGRAM, 250), line(ProductUnit.LITER, 1500)))
        )
    }

    private fun line(unit: ProductUnit, millis: Long) = CartLine(
        id = unit.code,
        productId = null,
        presentationId = null,
        barcode = "",
        name = unit.label,
        unit = unit,
        quantity = Quantity(millis),
        unitPrice = Money.ZERO,
        taxRate = 0.0,
        total = Money.ZERO
    )
}

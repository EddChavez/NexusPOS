package mx.nexuspos.desktop.sales

import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.SalesUiState
import mx.nexuspos.client.features.sales.toCartSummaryBarState
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.test.*

class CartSummaryBarTest {

    @Test
    fun testEmptyCartSummaryState() {
        val state = SalesUiState()
        val summary = state.toCartSummaryBarState()

        assertEquals(0, summary.totalProducts)
        assertEquals(0.0, summary.totalUnits)
        assertEquals("0", summary.totalUnitsFormatted)
        assertEquals("$0.00", summary.subtotalFormatted)
        assertEquals("$0.00", summary.taxFormatted)
        assertEquals("IVA 0%", summary.taxLabel)
        assertEquals("-$0.00", summary.discountFormatted)
        assertFalse(summary.hasDiscount)
    }

    @Test
    fun testCartWithPieceAndWeighedProducts() {
        val lines = listOf(
            CartLine(
                id = "c1",
                productId = "p1",
                presentationId = "pr1",
                barcode = "7501055300076",
                name = "Coca Cola 600ml",
                category = "Refrescos",
                unit = ProductUnit.PIECE,
                quantity = Quantity.pieces(3),
                unitPrice = Money.fromCents(1850),
                taxRate = 0.16,
                total = Money.fromCents(5550)
            ),
            CartLine(
                id = "c2",
                productId = "p2",
                presentationId = "pr2",
                barcode = "200000101250",
                name = "Manzana Gala",
                category = "Frutas y Verduras",
                unit = ProductUnit.KILOGRAM,
                quantity = Quantity.fromDouble(1.250),
                unitPrice = Money.fromCents(4500),
                taxRate = 0.0,
                total = Money.fromCents(5625)
            )
        )

        val state = SalesUiState(
            cartLines = lines,
            subtotal = Money.fromCents(10408),
            tax = Money.fromCents(767),
            total = Money.fromCents(11175),
            discount = Money.ZERO
        )

        val summary = state.toCartSummaryBarState()

        assertEquals(2, summary.totalProducts)
        assertEquals(4.25, summary.totalUnits)
        assertEquals("4.25", summary.totalUnitsFormatted)
        assertEquals("$104.08", summary.subtotalFormatted)
        assertEquals("$7.67", summary.taxFormatted)
        assertEquals("IVA 16%", summary.taxLabel)
        assertEquals("-$0.00", summary.discountFormatted)
        assertFalse(summary.hasDiscount)
    }

    @Test
    fun testTaxAndDiscountCalculations() {
        val lines = listOf(
            CartLine(
                id = "c1",
                productId = "p1",
                presentationId = "pr1",
                barcode = "7501234567890",
                name = "Galletas Marías 170g",
                category = "Abarrotes",
                unit = ProductUnit.PIECE,
                quantity = Quantity.pieces(5),
                unitPrice = Money.fromCents(1500),
                taxRate = 0.08,
                total = Money.fromCents(7500)
            )
        )

        val state = SalesUiState(
            cartLines = lines,
            subtotal = Money.fromCents(6944),
            tax = Money.fromCents(556),
            total = Money.fromCents(7500),
            discount = Money.fromCents(500) // Descuento de $5.00
        )

        val summary = state.toCartSummaryBarState()

        assertEquals(1, summary.totalProducts)
        assertEquals(5.0, summary.totalUnits)
        assertEquals("5", summary.totalUnitsFormatted)
        assertEquals("$69.44", summary.subtotalFormatted)
        assertEquals("$5.56", summary.taxFormatted)
        assertEquals("IVA 8%", summary.taxLabel)
        assertEquals("-$5.00", summary.discountFormatted)
        assertTrue(summary.hasDiscount)
    }
}

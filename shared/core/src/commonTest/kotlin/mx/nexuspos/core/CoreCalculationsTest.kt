package mx.nexuspos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreCalculationsTest {

    @Test
    fun testMoneyBasicOperationsAndFormatting() {
        val m1 = Money.pesos(15.50)
        val m2 = Money.pesos(4.50)

        assertEquals(2000L, (m1 + m2).cents)
        assertEquals("$20.00", (m1 + m2).format())

        val diff = m1 - m2
        assertEquals(1100L, diff.cents)
        assertEquals("$11.00", diff.format())

        val mult = m2 * 3L
        assertEquals(1350L, mult.cents)
        assertEquals("$13.50", mult.format())

        val negative = Money(-150L)
        assertEquals("-$1.50", negative.format())
        assertTrue(negative.isNegative())
    }

    @Test
    fun testQuantityFormatting() {
        val qtyPiece = Quantity.pieces(2)
        assertEquals("2 pza", qtyPiece.format(ProductUnit.PIECE))

        val qtyWeight = Quantity.fromDouble(0.250)
        assertEquals("0.250 kg", qtyWeight.format(ProductUnit.KILOGRAM))

        val qtyLiter = Quantity.fromDouble(1.500)
        assertEquals("1.500 L", qtyLiter.format(ProductUnit.LITER))
    }

    @Test
    fun testPricingRulesExactGroceryCalculations() {
        // Document example: 0.250 kg * $48.00/kg = $12.00
        val pricePerKg = Money.pesos(48.00)
        val weight = Quantity.fromDouble(0.250)
        val total = PricingRules.calculateLineTotal(pricePerKg, weight)
        assertEquals(Money.pesos(12.00), total)
        assertEquals("$12.00", total.format())

        // 1 piece of milk at $28.50
        val milkPrice = Money.pesos(28.50)
        val onePiece = Quantity.pieces(1)
        val milkTotal = PricingRules.calculateLineTotal(milkPrice, onePiece)
        assertEquals(Money.pesos(28.50), milkTotal)

        // 0.450 kg at $35.00/kg -> 450 * 3500 / 1000 = 1575 cents -> $15.75
        val fruitPrice = Money.pesos(35.00)
        val fruitWeight = Quantity.fromDouble(0.450)
        val fruitTotal = PricingRules.calculateLineTotal(fruitPrice, fruitWeight)
        assertEquals(Money.pesos(15.75), fruitTotal)

        // Change calculation
        val cashReceived = Money.pesos(50.00)
        val change = PricingRules.calculateChange(fruitTotal, cashReceived)
        assertEquals(Money.pesos(34.25), change)
    }

    @Test
    fun testTaxExtraction() {
        // $116.00 with 16% VAT includes $16.00 VAT
        val totalWithTax = Money.pesos(116.00)
        val vat = PricingRules.extractTaxFromTotal(totalWithTax, 0.16)
        assertEquals(Money.pesos(16.00), vat)

        // 0% VAT
        val zeroVat = PricingRules.extractTaxFromTotal(Money.pesos(50.00), 0.0)
        assertEquals(Money.ZERO, zeroVat)
    }
}

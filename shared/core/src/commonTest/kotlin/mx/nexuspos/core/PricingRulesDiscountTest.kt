package mx.nexuspos.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PricingRulesDiscountTest {

    @Test
    fun testCalculateLineTotalWithPercentageDiscount() {
        val unitPrice = Money.pesos(100.0) // $100.00
        val qty = Quantity.pieces(2) // 2 pieces = $200.00 base

        // 10% discount on $200 -> $20.00 discount, $180.00 final total
        val (finalTotal, discount) = PricingRules.calculateLineTotalWithDiscount(
            unitPrice = unitPrice,
            quantity = qty,
            discountPercent = 10.0
        )
        assertEquals(18000L, finalTotal.cents)
        assertEquals(2000L, discount.cents)
    }

    @Test
    fun testCalculateLineTotalWithFixedCentsDiscount() {
        val unitPrice = Money.pesos(50.0) // $50.00
        val qty = Quantity.pieces(3) // 3 pieces = $150.00 base

        // $25.00 fixed discount -> $125.00 final total
        val (finalTotal, discount) = PricingRules.calculateLineTotalWithDiscount(
            unitPrice = unitPrice,
            quantity = qty,
            discountCents = 2500L
        )
        assertEquals(12500L, finalTotal.cents)
        assertEquals(2500L, discount.cents)
    }

    @Test
    fun testDiscountCannotExceedTotal() {
        val unitPrice = Money.pesos(40.0)
        val qty = Quantity.pieces(1)

        // Attempting $50 discount on $40 total
        val (finalTotal, discount) = PricingRules.calculateLineTotalWithDiscount(
            unitPrice = unitPrice,
            quantity = qty,
            discountCents = 5000L
        )
        assertEquals(0L, finalTotal.cents)
        assertEquals(4000L, discount.cents) // Capped at base total
    }
}

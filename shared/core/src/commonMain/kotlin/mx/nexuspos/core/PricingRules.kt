package mx.nexuspos.core

import kotlin.math.roundToLong

object PricingRules {

    /**
     * Calculates the line subtotal: unitPrice * quantity with round half-up to centavo.
     * Formula: (unitPriceCents * quantityMillis + 500) / 1000
     */
    fun calculateLineTotal(unitPrice: Money, quantity: Quantity): Money {
        if (unitPrice.cents == 0L || quantity.millis == 0L) return Money.ZERO
        val product = unitPrice.cents * quantity.millis
        val roundedCents = if (product >= 0) {
            (product + 500L) / 1000L
        } else {
            (product - 500L) / 1000L
        }
        return Money(roundedCents)
    }

    /**
     * Calculates change given total amount and cash received.
     */
    fun calculateChange(total: Money, received: Money): Money {
        val changeCents = received.cents - total.cents
        return if (changeCents > 0L) Money(changeCents) else Money.ZERO
    }

    /**
     * Extracts tax amount from a line total when price includes tax.
     * Example: for 16% VAT ($116 total): tax = $16, net = $100.
     */
    fun extractTaxFromTotal(total: Money, taxRate: Double): Money {
        if (taxRate <= 0.0 || total.cents <= 0L) return Money.ZERO
        val taxFactor = 1.0 + taxRate
        val netCents = (total.cents / taxFactor).roundToLong()
        val taxCents = total.cents - netCents
        return Money(taxCents)
    }

    /**
     * Adds tax to a net amount.
     */
    fun calculateTaxOnNet(net: Money, taxRate: Double): Money {
        if (taxRate <= 0.0 || net.cents <= 0L) return Money.ZERO
        val taxCents = (net.cents * taxRate).roundToLong()
        return Money(taxCents)
    }
}

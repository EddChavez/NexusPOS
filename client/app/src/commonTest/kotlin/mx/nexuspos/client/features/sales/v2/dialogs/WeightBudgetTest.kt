package mx.nexuspos.client.features.sales.v2.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.Quantity

class WeightBudgetTest {
    @Test
    fun returnsWholeGramsWithoutExceedingTheBudget() {
        val price = Money(18500)
        val result = weightForBudget(price, Money(2000))
        assertEquals(Quantity(108), result)
        assertEquals(Money(1998), PricingRules.calculateLineTotal(price, result!!))

        assertEquals(Quantity(200), weightForBudget(Money(10000), Money(2000)))
    }

    @Test
    fun roundsUpOnlyWhenTheCartTotalFitsTheBudget() {
        assertEquals(Quantity(8), weightForBudget(Money(127), Money(1)))
        assertEquals(Quantity(1), weightForBudget(Money(1001), Money(1)))
        assertNull(weightForBudget(Money(1500), Money(1)))
        assertEquals(Quantity(1), weightForBudget(Money(11000), Money(20)))
    }

    @Test
    fun rejectsInvalidOrUnsupportedAmounts() {
        assertNull(weightForBudget(Money.ZERO, Money(2000)))
        assertNull(weightForBudget(Money(18500), Money.ZERO))
        assertNull(weightForBudget(Money(1), Money(Long.MAX_VALUE)))
    }
}

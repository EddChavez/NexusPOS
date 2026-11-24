package mx.nexuspos.client.features.sales.v2.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.nexuspos.client.features.sales.CheckoutPaymentMethod.CREDIT
import mx.nexuspos.client.features.sales.CheckoutPaymentMethod.LOYALTY
import mx.nexuspos.contracts.credit.CustomerDto

class CheckoutPaymentRulesTest {
    @Test
    fun parsesCentsExactlyWithEitherDecimalSeparator() {
        assertEquals(101L, parseCheckoutCents("1.01"))
        assertEquals(101L, parseCheckoutCents("1,01"))
        assertEquals(29L, parseCheckoutCents(".29"))
        assertEquals(Long.MAX_VALUE, parseCheckoutCents("92233720368547758.07"))
        for (input in listOf("", ".", "-1", "1e3", "1.001", "1,000.00", "92233720368547758.08")) {
            assertNull(parseCheckoutCents(input), input)
        }
    }

    @Test
    fun usesCustomerBalanceAndCreditSettings() {
        val customer = CustomerDto(
            id = "customer", name = "Cliente", loyaltyBalanceCents = 320,
            creditEnabled = true, availableCreditCents = 1500
        )
        assertEquals(320L, LOYALTY.availableCents(customer, offline = false))
        assertEquals(1500L, CREDIT.availableCents(customer, offline = false))
        assertEquals(Long.MAX_VALUE, CREDIT.availableCents(customer.copy(hasCreditLimit = false), offline = false))
        assertEquals(0L, CREDIT.availableCents(customer.copy(creditEnabled = false), offline = false))
        assertEquals(0L, LOYALTY.availableCents(customer, offline = true))
        assertEquals(0L, CREDIT.availableCents(null, offline = false))
    }
}

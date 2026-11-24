package mx.nexuspos.client.features.sales.v2.dialogs

import mx.nexuspos.client.features.sales.CheckoutPaymentMethod
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

internal val CheckoutPaymentMethod.requiresCustomer: Boolean
    get() = this == CheckoutPaymentMethod.CREDIT || this == CheckoutPaymentMethod.LOYALTY

internal fun CheckoutPaymentMethod.availableCents(customer: CustomerDto?, offline: Boolean): Long {
    if (!requiresCustomer) return Long.MAX_VALUE
    if (offline || customer == null || !customer.isActive) return 0L
    return when (this) {
        CheckoutPaymentMethod.LOYALTY -> customer.loyaltyBalanceCents.coerceAtLeast(0L)
        CheckoutPaymentMethod.CREDIT -> when {
            !customer.creditEnabled -> 0L
            !customer.hasCreditLimit -> Long.MAX_VALUE
            else -> customer.availableCreditCents.coerceAtLeast(0L)
        }
        else -> Long.MAX_VALUE
    }
}

internal fun parseCheckoutCents(input: String): Long? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.none { it in '0'..'9' }) return null
    val whole = normalized.substringBefore('.')
    val fraction = normalized.substringAfter('.', "")
    if (whole.any { it !in '0'..'9' } || fraction.length > 2 || fraction.any { it !in '0'..'9' }) return null
    val pesos = whole.ifEmpty { "0" }.toLongOrNull() ?: return null
    val cents = fraction.padEnd(2, '0').toLong()
    if (pesos > (Long.MAX_VALUE - cents) / 100L) return null
    return pesos * 100L + cents
}

internal fun checkoutAmountText(cents: Long): String = Money(cents).format().removePrefix("$")

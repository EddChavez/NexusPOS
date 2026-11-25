package mx.nexuspos.contracts.system

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogRounding(val label: String) {
    NONE("Sin redondeo"),
    UP_TO_TENTH("Décima superior"),
    NEAREST_HALF("50 centavos más cercanos"),
    NEAREST_WHOLE("Peso más cercano");

    fun apply(cents: Long): Long {
        require(cents >= 0)
        val increment = when (this) {
            NONE -> return cents
            UP_TO_TENTH -> 10
            NEAREST_HALF -> 50
            NEAREST_WHOLE -> 100
        }
        val remainder = cents % increment
        if (remainder == 0L) return cents
        return if (this == UP_TO_TENTH || remainder * 2 >= increment) {
            cents - remainder + increment
        } else cents - remainder
    }
}

@Serializable
data class CommercialPreferences(
    val quickSaleEnabled: Boolean = true,
    val requireOpeningFund: Boolean = true,
    val creditSalesEnabled: Boolean = true,
    val creditPaymentMethods: Set<String> = setOf("CASH", "TRANSFER", "CARD"),
    val defaultVatPercent: Int = 0,
    val catalogRounding: CatalogRounding = CatalogRounding.NONE,
    val matchBarcodeWithoutLeadingZeros: Boolean = false,
    val allowShiftHandoff: Boolean = false,
    val cashDrawerLimitCents: Long? = null
)

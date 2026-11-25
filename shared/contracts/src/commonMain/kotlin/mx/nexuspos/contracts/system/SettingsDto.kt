package mx.nexuspos.contracts.system

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(
    val storeName: String = "Mi Tiendita",
    val storeAddress: String = "Conocido, Local 1",
    val storePhone: String = "555-123-4567",
    val storeTaxId: String = "",
    val ticketHeader: String = "¡Gracias por su compra!",
    val ticketFooter: String = "Conserve su ticket para cualquier aclaración.",
    val allowNegativeStock: Boolean = true,
    val currencySymbol: String = "$",
    val blindCashCountEnabled: Boolean = false,
    val maxCashierDiscountPercent: Double = 10.0,
    val loyaltyEnabled: Boolean = true,
    val loyaltyCashbackPercent: Double = 1.0,
    val loyaltyMinRedeemCents: Long = 0L,
    val whatsappCountryCode: String = "52",
    val whatsappBankDetails: String = "",
    val whatsappCustomFooter: String = "¡Gracias por su preferencia!",
    val commerce: CommercialPreferences = CommercialPreferences()
)

@Serializable
data class SettingsPreferencesPatch(
    val store: StorePreferences? = null,
    val policies: OperatingPolicies? = null
)

@Serializable
data class StorePreferences(
    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val storeTaxId: String,
    val ticketHeader: String,
    val ticketFooter: String,
    val currencySymbol: String,
    val whatsappCountryCode: String,
    val whatsappBankDetails: String,
    val whatsappCustomFooter: String
)

@Serializable
data class OperatingPolicies(
    val allowNegativeStock: Boolean,
    val blindCashCountEnabled: Boolean,
    val maxCashierDiscountPercent: Double,
    val loyaltyEnabled: Boolean,
    val loyaltyCashbackPercent: Double,
    val loyaltyMinRedeemCents: Long
)

@Serializable
data class SystemInfoDto(
    val appVersion: String,
    val apiVersion: String = "v1",
    val serverTime: Long,
    val dbEngine: String = "PostgreSQL 16",
    val connectedDevices: Int = 1
)

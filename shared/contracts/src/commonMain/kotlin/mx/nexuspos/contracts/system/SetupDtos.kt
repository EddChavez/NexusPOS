package mx.nexuspos.contracts.system

import kotlinx.serialization.Serializable

@Serializable
enum class SetupState { NEW, LEGACY, READY }

@Serializable
data class SetupStatus(val state: SetupState)

@Serializable
data class StoreSetupRequest(
    val code: String,
    val businessName: String,
    val businessType: String = "",
    val country: String = "MX",
    val branchName: String,
    val address: String,
    val phone: String = "",
    val timeZone: String = "America/Mexico_City",
    val registerName: String,
    val rootName: String,
    val rootPin: String,
    val allowNegativeStock: Boolean = true,
    val blindCashCount: Boolean = false,
    val maxCashierDiscountPercent: Double = 10.0,
    val loyaltyEnabled: Boolean = true
)

@Serializable
data class StoreSetupResult(val registerId: String, val registerName: String)

@Serializable
data class AdoptRootRequest(
    val code: String,
    val administratorPin: String,
    val rootPin: String,
    val replacementCashierPin: String? = null
)

@Serializable
data class RecoverRootRequest(val code: String, val rootPin: String)

@Serializable
data class StoreProfile(
    val businessId: String? = null,
    val branchId: String? = null,
    val businessName: String = "",
    val businessType: String = "",
    val country: String = "MX",
    val branchName: String = "",
    val address: String = "",
    val phone: String = "",
    val timeZone: String = "America/Mexico_City",
    val ownerName: String? = null
)

@Serializable
data class SaleFolioStatus(val folio: Long? = null, val prefix: String? = null)

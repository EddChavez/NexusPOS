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
    val currencySymbol: String = "$"
)

@Serializable
data class SystemInfoDto(
    val appVersion: String,
    val apiVersion: String = "v1",
    val serverTime: Long,
    val dbEngine: String = "PostgreSQL 16",
    val connectedDevices: Int = 1
)

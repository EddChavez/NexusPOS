package mx.nexuspos.contracts.mobile

import kotlinx.serialization.Serializable
import mx.nexuspos.contracts.catalog.ProductPresentationDto

@Serializable data class MobileAdminRequest(val pin: String)
@Serializable data class MobilePairingCode(val code: String, val expiresAt: Long)
@Serializable data class MobilePairRequest(val code: String, val name: String)
@Serializable data class MobileDeviceCredential(val id: String, val token: String)
@Serializable data class MobileDevice(val id: String, val name: String, val revoked: Boolean)
@Serializable data class MobileSessionRequest(val pin: String)
@Serializable data class MobileSession(val token: String, val userName: String, val idleMinutes: Int = 15)

@Serializable data class MobileRegister(val id: String, val name: String, val open: Boolean)
@Serializable data class MobileOverview(val date: String, val netSalesCents: Long, val registers: List<MobileRegister>)
@Serializable data class MobileProduct(
    val id: String,
    val name: String,
    val sku: String,
    val unit: String,
    val stockMillis: Long?,
    val presentations: List<ProductPresentationDto>,
    val matchedPresentationId: String? = null
)

@Serializable data class MobileShift(
    val id: String,
    val registerId: String,
    val registerName: String,
    val cashierName: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val expectedCents: Long? = null,
    val countedCents: Long? = null,
    val differenceCents: Long? = null
)
@Serializable data class MobileShifts(val date: String, val registers: List<MobileRegister>, val shifts: List<MobileShift>)
@Serializable data class MobileSale(val folio: Long, val totalCents: Long, val status: String)
@Serializable data class MobileShiftSales(val shift: MobileShift, val sales: List<MobileSale>, val nextBeforeFolio: Long? = null)

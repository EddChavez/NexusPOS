package mx.nexuspos.contracts.identity

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val pin: String,
    val deviceToken: String = ""
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val name: String,
    val role: String, // CASHIER, SUPERVISOR, ADMIN
    val permissions: List<String>
)

@Serializable
data class AuthorizeRequest(
    val pin: String,
    val action: String // CANCEL_SALE, OVERRIDE_CREDIT, CASH_OUT, DISCOUNT
)

@Serializable
data class AuthorizeResponse(
    val authorized: Boolean,
    val supervisorId: String = "",
    val supervisorName: String = ""
)

@Serializable
data class PairingCodeResponse(
    val code: String,
    val expiresAt: Long
)

@Serializable
data class PairingClaimRequest(
    val code: String,
    val deviceName: String,
    val deviceType: String // CASHIER_DESKTOP, SUPERVISOR_MOBILE
)

@Serializable
data class PairingClaimResponse(
    val deviceToken: String,
    val deviceId: String
)

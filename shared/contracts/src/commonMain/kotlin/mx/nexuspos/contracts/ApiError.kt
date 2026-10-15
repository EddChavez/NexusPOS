package mx.nexuspos.contracts

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
    val requestId: String = ""
)

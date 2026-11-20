package mx.nexuspos.contracts.whatsapp

import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppMessagePayload(
    val recipientPhone: String,
    val messageText: String,
    val waUrl: String = ""
)

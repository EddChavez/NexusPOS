package mx.nexuspos.contracts.events

import kotlinx.serialization.Serializable

@Serializable
data class RealtimeEvent(
    val type: String, // catalog.changed, stock.low, sale.completed, cash.session.opened, cash.session.closed
    val payloadJson: String,
    val timestamp: Long = 0L
)

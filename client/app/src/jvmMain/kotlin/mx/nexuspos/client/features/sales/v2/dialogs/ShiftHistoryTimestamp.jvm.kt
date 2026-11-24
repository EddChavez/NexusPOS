package mx.nexuspos.client.features.sales.v2.dialogs

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val historyDateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")

internal actual fun formatShiftHistoryTimestamp(timestamp: Long, includeDate: Boolean): String {
    val formatter = if (includeDate) historyDateTimeFormat else historyTimeFormat
    return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

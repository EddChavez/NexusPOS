package mx.nexuspos.client.features.sales.v2.dialogs

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
internal actual fun formatShiftHistoryTimestamp(timestamp: Long, includeDate: Boolean): String =
    NSDateFormatter().apply {
        dateFormat = if (includeDate) "dd/MM/yyyy HH:mm" else "HH:mm"
    }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))

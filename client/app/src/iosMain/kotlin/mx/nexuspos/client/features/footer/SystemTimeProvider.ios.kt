package mx.nexuspos.client.features.footer

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformCurrentTimeParts(): LocalTimeParts {
    val date = NSDate()
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitDay or NSCalendarUnitMonth or NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = date
    )
    return LocalTimeParts(
        dayOfMonth = components.day.toInt(),
        monthNumber = components.month.toInt(),
        hour24 = components.hour.toInt(),
        minute = components.minute.toInt()
    )
}

actual fun getPlatformEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

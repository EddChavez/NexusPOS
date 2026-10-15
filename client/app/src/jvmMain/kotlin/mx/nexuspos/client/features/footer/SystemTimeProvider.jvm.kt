package mx.nexuspos.client.features.footer

import java.time.LocalDateTime
import java.time.ZoneId

actual fun getPlatformCurrentTimeParts(): LocalTimeParts {
    val now = LocalDateTime.now(ZoneId.systemDefault())
    return LocalTimeParts(
        dayOfMonth = now.dayOfMonth,
        monthNumber = now.monthValue,
        hour24 = now.hour,
        minute = now.minute
    )
}

actual fun getPlatformEpochMillis(): Long = System.currentTimeMillis()

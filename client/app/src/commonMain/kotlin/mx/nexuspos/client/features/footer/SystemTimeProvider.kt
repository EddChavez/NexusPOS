package mx.nexuspos.client.features.footer

data class LocalTimeParts(
    val dayOfMonth: Int,
    val monthNumber: Int,
    val hour24: Int,
    val minute: Int
)

expect fun getPlatformCurrentTimeParts(): LocalTimeParts
expect fun getPlatformEpochMillis(): Long

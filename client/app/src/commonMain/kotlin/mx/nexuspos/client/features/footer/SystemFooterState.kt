package mx.nexuspos.client.features.footer

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDateTime
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus

/**
 * Estado inmutable para la barra inferior de estado y telemetría.
 */
data class SystemFooterUiState(
    val networkStatus: ServerConnectionState = ServerConnectionState.CONNECTED,
    val scaleReading: ScaleReading = ScaleReading(),
    val versionText: String = "NexusPOS v2.4 Desktop • © 2024"
)

/**
 * Representación visual del estado de conectividad con el servidor local.
 */
data class NetworkStatusDisplay(
    val label: String,
    val indicatorColor: Color,
    val dotSymbol: String = "●"
)

/**
 * Mapea el [ServerConnectionState] a los textos y colores definidos para el footer.
 */
fun ServerConnectionState.toFooterDisplay(): NetworkStatusDisplay {
    return when (this) {
        ServerConnectionState.CONNECTED -> NetworkStatusDisplay(
            label = "Nexus Lan Server",
            indicatorColor = Color(0xFF2E7D32)
        )
        ServerConnectionState.RECONNECTING -> NetworkStatusDisplay(
            label = "Nexus Lan Server",
            indicatorColor = Color(0xFFF57F17)
        )
        ServerConnectionState.NO_SERVER -> NetworkStatusDisplay(
            label = "Nexus Lan Server",
            indicatorColor = Color(0xFFC62828)
        )
    }
}

/**
 * Representación visual del estado de la báscula serial en el footer.
 */
data class ScaleStatusDisplay(
    val label: String,
    val indicatorColor: Color,
    val dotSymbol: String = "●"
)

/**
 * Mapea el [ScaleStatus] a dot y etiqueta en formato similar al servidor.
 */
fun ScaleStatus.toFooterDisplay(): ScaleStatusDisplay {
    return when (this) {
        ScaleStatus.STABLE -> ScaleStatusDisplay("Estable", Color(0xFF2E7D32))
        ScaleStatus.READING -> ScaleStatusDisplay("Leyendo...", Color(0xFFF57F17))
        ScaleStatus.ZERO -> ScaleStatusDisplay("En cero", Color(0xFF1565C0))
        ScaleStatus.DISCONNECTED -> ScaleStatusDisplay("Desconectada", Color(0xFF757575))
    }
}

/**
 * Formatea una fecha y hora local con el formato requerido por la terminal POS:
 * Ejemplo: "15 - oct 10:12 am"
 */
fun formatTerminalDateTime(
    dayOfMonth: Int,
    monthNumber: Int,
    hour24: Int,
    minute: Int
): String {
    val day = dayOfMonth.toString().padStart(2, '0')
    val monthShort = when (monthNumber) {
        1 -> "ene"
        2 -> "feb"
        3 -> "mar"
        4 -> "abr"
        5 -> "may"
        6 -> "jun"
        7 -> "jul"
        8 -> "ago"
        9 -> "sep"
        10 -> "oct"
        11 -> "nov"
        12 -> "dic"
        else -> ""
    }
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    val hour12Str = hour12.toString().padStart(2, '0')
    val minStr = minute.toString().padStart(2, '0')
    val amPm = if (hour24 < 12) "am" else "pm"

    return "$day - $monthShort $hour12Str:$minStr $amPm"
}

fun formatTerminalDateTime(dateTime: LocalDateTime): String = formatTerminalDateTime(
    dayOfMonth = dateTime.dayOfMonth,
    monthNumber = dateTime.monthNumber,
    hour24 = dateTime.hour,
    minute = dateTime.minute
)

/**
 * Obtiene la fecha y hora actual del sistema formateada.
 */
fun getCurrentFormattedTerminalTime(): String {
    val parts = getPlatformCurrentTimeParts()
    return formatTerminalDateTime(
        dayOfMonth = parts.dayOfMonth,
        monthNumber = parts.monthNumber,
        hour24 = parts.hour24,
        minute = parts.minute
    )
}

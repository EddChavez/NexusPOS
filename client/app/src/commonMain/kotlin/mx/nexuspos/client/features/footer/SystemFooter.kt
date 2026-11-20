package mx.nexuspos.client.features.footer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus

/**
 * Barra inferior de estado y telemetría (Footer / Status Bar) para Compose Desktop.
 *
 * Consolida el monitoreo de infraestructura (servidor local LAN), periféricos (báscula serial RS-232),
 * versión del software y reloj local en tiempo real con aislamiento de recomposición.
 */
@Composable
fun SystemFooter(
    networkStatus: ServerConnectionState = ServerConnectionState.CONNECTED,
    offlinePendingCount: Int = 0,
    scaleReading: ScaleReading = ScaleReading(),
    versionText: String = "NexusPOS v2.4 Desktop • © 2026",
    onScaleClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth().height(28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // =====================================================================
                // 1. INDICADOR DEL SERVIDOR (IZQUIERDA)
                // =====================================================================
                ServerStatusSection(
                    networkStatus = networkStatus,
                    offlinePendingCount = offlinePendingCount,
                    onSyncClick = onSyncClick
                )

                // =====================================================================
                // 2. INFORMACIÓN DEL SISTEMA (CENTRO)
                // =====================================================================
                SystemInfoSection(versionText = versionText)

                // =====================================================================
                // 3. INDICADOR DE BÁSCULA Y RELOJ EN TIEMPO REAL (DERECHA)
                // =====================================================================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScaleStatusSection(
                        scaleReading = scaleReading,
                        onScaleClick = onScaleClick
                    )

                    VerticalDivider(
                        modifier = Modifier.height(13.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    RealTimeClock()
                }
            }
        }
    }
}

/**
 * Alias semántico para [SystemFooter].
 */
@Composable
fun StatusBar(
    networkStatus: ServerConnectionState = ServerConnectionState.CONNECTED,
    offlinePendingCount: Int = 0,
    scaleReading: ScaleReading = ScaleReading(),
    versionText: String = "NexusPOS v2.4 Desktop • © 2026",
    onScaleClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = SystemFooter(
    networkStatus = networkStatus,
    offlinePendingCount = offlinePendingCount,
    scaleReading = scaleReading,
    versionText = versionText,
    onScaleClick = onScaleClick,
    onSyncClick = onSyncClick,
    modifier = modifier
)

/**
 * Sección izquierda: Estado de conexión con el servidor local con texto fijo "Nexus Lan Server" y contador de contingencia.
 */
@Composable
private fun ServerStatusSection(
    networkStatus: ServerConnectionState,
    offlinePendingCount: Int = 0,
    onSyncClick: (() -> Unit)? = null
) {
    val display = networkStatus.toFooterDisplay()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            color = display.indicatorColor,
            shape = CircleShape,
            modifier = Modifier.size(7.dp)
        ) {}

        Text(
            text = "Nexus Lan Server",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (offlinePendingCount > 0) {
            Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.clickable { onSyncClick?.invoke() }
            ) {
                Text(
                    text = "• $offlinePendingCount pendientes [🔄]",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Sección derecha: Indicador de báscula serial RS-232 con peso y punto de estado (sin texto descriptivo).
 */
@Composable
private fun ScaleStatusSection(
    scaleReading: ScaleReading,
    onScaleClick: (() -> Unit)?
) {
    val scaleDisplay = scaleReading.status.toFooterDisplay()

    Row(
        modifier = Modifier
            .then(if (onScaleClick != null) Modifier.clickable { onScaleClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "⚖",
            fontSize = 11.sp
        )

        Text(
            text = scaleReading.weightText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            color = scaleDisplay.indicatorColor,
            shape = CircleShape,
            modifier = Modifier.size(7.dp)
        ) {}
    }
}

/**
 * Sección central: Versión y copyright del software.
 */
@Composable
private fun SystemInfoSection(versionText: String) {
    Text(
        text = versionText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )
}

/**
 * Sección derecha: Reloj dinámico con recomposición estrictamente aislada a este Composable.
 */
@Composable
private fun RealTimeClock(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(getCurrentFormattedTerminalTime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val nowMillis = getPlatformEpochMillis()
            val millisUntilNextMinute = 60_000L - (nowMillis % 60_000L)
            delay(millisUntilNextMinute.coerceAtLeast(1000L))
            currentTime = getCurrentFormattedTerminalTime()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(12.dp)
        )

        Text(
            text = currentTime,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package mx.nexuspos.client.features.footer

import androidx.compose.foundation.BorderStroke
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
import kotlinx.datetime.Clock
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
    scaleReading: ScaleReading = ScaleReading(),
    versionText: String = "NexusPOS v2.4 Desktop • © 2024",
    onScaleClick: (() -> Unit)? = null,
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
                // 1. MONITOREO DE INFRAESTRUCTURA Y PERIFÉRICOS (IZQUIERDA)
                // =====================================================================
                InfrastructureStatusSection(
                    networkStatus = networkStatus,
                    scaleReading = scaleReading,
                    onScaleClick = onScaleClick
                )

                // =====================================================================
                // 2. INFORMACIÓN DEL SISTEMA (CENTRO)
                // =====================================================================
                SystemInfoSection(versionText = versionText)

                // =====================================================================
                // 3. RELOJ EN TIEMPO REAL (DERECHA - AISLADO)
                // =====================================================================
                RealTimeClock()
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
    scaleReading: ScaleReading = ScaleReading(),
    versionText: String = "NexusPOS v2.4 Desktop • © 2024",
    onScaleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) = SystemFooter(
    networkStatus = networkStatus,
    scaleReading = scaleReading,
    versionText = versionText,
    onScaleClick = onScaleClick,
    modifier = modifier
)

/**
 * Sección izquierda: Estado de conexión LAN y lectura de báscula serial.
 */
@Composable
private fun InfrastructureStatusSection(
    networkStatus: ServerConnectionState,
    scaleReading: ScaleReading,
    onScaleClick: (() -> Unit)?
) {
    val display = networkStatus.toFooterDisplay()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Indicador de Servidor LAN
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
                text = display.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        VerticalDivider(
            modifier = Modifier.height(13.dp),
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Indicador de Báscula Serial RS-232
        val scaleStatusColor = when (scaleReading.status) {
            ScaleStatus.STABLE -> Color(0xFF2E7D32)
            ScaleStatus.READING -> Color(0xFFF57F17)
            ScaleStatus.ZERO -> Color(0xFF1565C0)
            ScaleStatus.DISCONNECTED -> Color(0xFF757575)
        }

        Row(
            modifier = Modifier
                .then(if (onScaleClick != null) Modifier.clickable { onScaleClick() } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                color = scaleStatusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(0.5.dp, scaleStatusColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = scaleReading.status.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = scaleStatusColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
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
            val nowMillis = Clock.System.now().toEpochMilliseconds()
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

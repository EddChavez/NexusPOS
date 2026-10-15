package mx.nexuspos.desktop.footer

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDateTime
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.footer.SystemFooterUiState
import mx.nexuspos.client.features.footer.formatTerminalDateTime
import mx.nexuspos.client.features.footer.toFooterDisplay
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemFooterTest {

    @Test
    fun testFormatTerminalDateTimeMorning() {
        val dt = LocalDateTime(2026, 10, 15, 10, 12)
        val formatted = formatTerminalDateTime(dt)
        assertEquals("15 - oct 10:12 am", formatted)
    }

    @Test
    fun testFormatTerminalDateTimeAfternoon() {
        val dt = LocalDateTime(2026, 5, 4, 18, 45)
        val formatted = formatTerminalDateTime(dt)
        assertEquals("04 - may 06:45 pm", formatted)
    }

    @Test
    fun testFormatTerminalDateTimeMidnight() {
        val dt = LocalDateTime(2026, 1, 1, 0, 5)
        val formatted = formatTerminalDateTime(dt)
        assertEquals("01 - ene 12:05 am", formatted)
    }

    @Test
    fun testFormatTerminalDateTimeNoon() {
        val dt = LocalDateTime(2026, 12, 31, 12, 0)
        val formatted = formatTerminalDateTime(dt)
        assertEquals("31 - dic 12:00 pm", formatted)
    }

    @Test
    fun testNetworkStatusDisplayMapping() {
        val connectedDisplay = ServerConnectionState.CONNECTED.toFooterDisplay()
        assertEquals("Servidor LAN sincronizado", connectedDisplay.label)
        assertEquals(Color(0xFF2E7D32), connectedDisplay.indicatorColor)

        val reconnectingDisplay = ServerConnectionState.RECONNECTING.toFooterDisplay()
        assertEquals("Conectando...", reconnectingDisplay.label)
        assertEquals(Color(0xFFF57F17), reconnectingDisplay.indicatorColor)

        val disconnectedDisplay = ServerConnectionState.NO_SERVER.toFooterDisplay()
        assertEquals("Servidor Desconectado", disconnectedDisplay.label)
        assertEquals(Color(0xFFC62828), disconnectedDisplay.indicatorColor)
    }

    @Test
    fun testScaleReadingProperties() {
        val reading = ScaleReading(
            weightText = "1.230 kg",
            status = ScaleStatus.STABLE
        )
        assertEquals("1.230 kg", reading.weightText)
        assertEquals(ScaleStatus.STABLE, reading.status)
        assertEquals("ESTABLE", reading.status.label)
    }

    @Test
    fun testSystemFooterUiStateDefaults() {
        val state = SystemFooterUiState()
        assertEquals(ServerConnectionState.CONNECTED, state.networkStatus)
        assertEquals("0.000 kg", state.scaleReading.weightText)
        assertEquals(ScaleStatus.STABLE, state.scaleReading.status)
        assertEquals("NexusPOS v2.4 Desktop • © 2024", state.versionText)
    }

    @Test
    fun testGetCurrentFormattedTerminalTimeRuntimeClock() {
        val current = mx.nexuspos.client.features.footer.getCurrentFormattedTerminalTime()
        kotlin.test.assertTrue(current.isNotBlank())
        kotlin.test.assertTrue(current.contains("-"))
        kotlin.test.assertTrue(current.contains("am") || current.contains("pm"))
    }
}

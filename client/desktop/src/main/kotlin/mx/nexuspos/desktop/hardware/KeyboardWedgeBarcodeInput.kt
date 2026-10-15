package mx.nexuspos.desktop.hardware

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Handles USB / Bluetooth barcode scanners operating in Keyboard Wedge / HID mode.
 *
 * Scanners send keystrokes with very low inter-key delay (< 50-80ms) terminating
 * with an Enter / Return key stroke. This class differentiates human typing from
 * rapid scanner bursts and emits the scanned barcode string.
 */
class KeyboardWedgeBarcodeInput(
    private val maxInterKeyDelayMs: Long = 80L,
    private val onBarcodeScanned: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private var lastKeyTimestamp = 0L

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val now = System.currentTimeMillis()
        if (buffer.isNotEmpty() && (now - lastKeyTimestamp) > maxInterKeyDelayMs) {
            // Inter-key latency exceeded, likely normal user typing -> discard accumulated wedge buffer
            buffer.clear()
        }
        lastKeyTimestamp = now

        if (event.key == Key.Enter) {
            if (buffer.length >= 3) {
                val scannedCode = buffer.toString().trim()
                buffer.clear()
                onBarcodeScanned(scannedCode)
                return true
            }
            buffer.clear()
            return false
        }

        val codePoint = event.utf16CodePoint
        if (codePoint > 0) {
            val char = codePoint.toChar()
            if (char.isLetterOrDigit() || char in "-_.*") {
                buffer.append(char)
            }
        }

        return false
    }
}

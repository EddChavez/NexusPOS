package mx.nexuspos.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import mx.nexuspos.client.features.sales.SalesScreen
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.desktop.hardware.KeyboardWedgeBarcodeInput

fun main() = application {
    val viewModel = remember { SalesViewModel() }

    // Hardware Keyboard Wedge Barcode Scanner Adapter
    val barcodeWedge = remember {
        KeyboardWedgeBarcodeInput { scannedCode ->
            viewModel.onScannerInputChanged(scannedCode)
            viewModel.onScannerSubmitted()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NexusPOS - Terminal de Punto de Venta (LAN-First)",
        state = WindowState(width = 1280.dp, height = 820.dp),
        onKeyEvent = { keyEvent ->
            // First check if this is an incoming barcode scan from USB hardware scanner
            if (barcodeWedge.onKeyEvent(keyEvent)) {
                true
            } else if (keyEvent.type == KeyEventType.KeyDown) {
                // Function Key Shortcuts
                when (keyEvent.key) {
                    Key.F4 -> {
                        viewModel.showCustomerDialog(true)
                        true
                    }
                    Key.F12 -> {
                        val state = viewModel.uiState.value
                        if (state.cartLines.isNotEmpty() && state.activeSession != null) {
                            viewModel.showCashPaymentDialog(true)
                            true
                        } else false
                    }
                    Key.Escape -> {
                        viewModel.showCashPaymentDialog(false)
                        viewModel.showCustomerDialog(false)
                        viewModel.dismissWeighingDialog()
                        viewModel.showSessionDialog(false)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        val lightColors = lightColorScheme(
            primary = Color(0xFF1B5E20), // Deep Forest Green
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA5D6A7),
            onPrimaryContainer = Color(0xFF002204),
            secondary = Color(0xFF00695C),
            onSecondary = Color.White,
            background = Color(0xFFF8F9FA),
            surface = Color.White,
            error = Color(0xFFB00020)
        )

        val darkColors = darkColorScheme(
            primary = Color(0xFF81C784),
            onPrimary = Color(0xFF00390A),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            error = Color(0xFFCF6679)
        )

        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors) {
            SalesScreen(viewModel = viewModel)
        }
    }
}

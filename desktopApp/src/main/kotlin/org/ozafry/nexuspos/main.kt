package org.ozafry.nexuspos

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.ozafry.nexuspos.db.DesktopDatabaseDriverFactory

fun main() = application {
    val driverFactory = remember { DesktopDatabaseDriverFactory() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "NexusPOS - Terminal de Ventas",
        state = WindowState(width = 1200.dp, height = 800.dp)
    ) {
        App(driverFactory = driverFactory, registerId = "REG-01")
    }
}
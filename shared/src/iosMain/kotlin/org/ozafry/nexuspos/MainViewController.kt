package org.ozafry.nexuspos

import androidx.compose.ui.window.ComposeUIViewController
import org.ozafry.nexuspos.db.NativeDatabaseDriverFactory

fun MainViewController() = ComposeUIViewController {
    App(
        driverFactory = NativeDatabaseDriverFactory(),
        registerId = "IOS-01",
        initialMode = POSViewMode.SUPERVISOR
    )
}
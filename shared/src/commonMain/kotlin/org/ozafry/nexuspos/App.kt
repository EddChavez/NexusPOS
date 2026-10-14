package org.ozafry.nexuspos

import androidx.compose.runtime.*
import org.ozafry.nexuspos.db.DatabaseDriverFactory
import org.ozafry.nexuspos.db.createDatabase
import org.ozafry.nexuspos.repository.OfflineSaleRepository
import org.ozafry.nexuspos.sync.SyncEngine
import org.ozafry.nexuspos.ui.screens.CashierScreen
import org.ozafry.nexuspos.ui.screens.SupervisorScreen
import org.ozafry.nexuspos.ui.theme.NexusPOSTheme

enum class POSViewMode {
    CASHIER,
    SUPERVISOR
}

@Composable
fun App(
    driverFactory: DatabaseDriverFactory? = null,
    registerId: String = "REG-01",
    initialMode: POSViewMode = POSViewMode.CASHIER
) {
    var viewMode by remember { mutableStateOf(initialMode) }

    // Si no se provee driverFactory (p. ej. en previews o testing), no inicializamos DB o usamos fallback
    if (driverFactory == null) {
        // Fallback or preview
        NexusPOSTheme {
            // Placeholder when driverFactory is missing
        }
        return
    }

    val repository = remember(driverFactory) {
        val db = createDatabase(driverFactory)
        val repo = OfflineSaleRepository(db)
        repo.seedInitialDataIfEmpty()
        repo
    }

    val syncEngine = remember(repository, registerId) {
        SyncEngine(
            repository = repository,
            registerId = registerId
        )
    }

    NexusPOSTheme {
        when (viewMode) {
            POSViewMode.CASHIER -> {
                CashierScreen(
                    repository = repository,
                    syncEngine = syncEngine,
                    registerId = registerId,
                    onSwitchToSupervisor = { viewMode = POSViewMode.SUPERVISOR }
                )
            }
            POSViewMode.SUPERVISOR -> {
                SupervisorScreen(
                    repository = repository,
                    onBackToCashier = { viewMode = POSViewMode.CASHIER }
                )
            }
        }
    }
}
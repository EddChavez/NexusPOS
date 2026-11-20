package mx.nexuspos.client.features.header

import mx.nexuspos.client.core.ServerConnectionState

enum class AppModule(val keyShortcut: String, val title: String) {
    SALES("F1", "Ventas"),
    CUSTOMERS("F2", "Clientes"),
    PRODUCTS("F3", "Productos"),
    INVENTORY("F4", "Inventario"),
    SETTINGS("F5", "Configuración"),
    CASH_CUT("F6", "Cortes");

    val displayLabel: String get() = "[$keyShortcut] $title"
}

enum class ScaleStatus(val label: String) {
    STABLE("ESTABLE"),
    READING("LEYENDO"),
    ZERO("EN CERO"),
    DISCONNECTED("DESCONECTADA")
}

data class ScaleReading(
    val weightText: String = "0.000 kg",
    val status: ScaleStatus = ScaleStatus.STABLE
) {
    val weightKg: Double
        get() = weightText.replace("kg", "").trim().toDoubleOrNull() ?: 0.0

    val isStableWeight: Boolean
        get() = status == ScaleStatus.STABLE && weightKg > 0.005
}

data class TopHeaderUiState(
    val brandTitle: String = "NexusPOS",
    val brandSubtitle: String = "Abarrotes & Miscelánea",
    val activeModule: AppModule = AppModule.SALES,
    val scaleReading: ScaleReading = ScaleReading(),
    val connectionState: ServerConnectionState = ServerConnectionState.CONNECTED,
    val offlinePendingSalesCount: Int = 0,
    val registerId: String = "01",
    val cashierName: String = "Don Pepe",
    val cashierRole: String = "CASHIER"
)

sealed interface TopHeaderEvent {
    data class SelectModule(val module: AppModule) : TopHeaderEvent
    data object Logout : TopHeaderEvent
    data object SwitchCashier : TopHeaderEvent
    data object ClickScale : TopHeaderEvent
    data object ClickConnection : TopHeaderEvent
    data object ClickSyncOffline : TopHeaderEvent
}

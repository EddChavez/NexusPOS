package mx.nexuspos.desktop.header

import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.header.AppModule
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.header.TopHeaderUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class TopHeaderStateTest {

    @Test
    fun testAppModuleShortcutsAndLabels() {
        assertEquals("F1", AppModule.SALES.keyShortcut)
        assertEquals("[F1] Ventas", AppModule.SALES.displayLabel)

        assertEquals("F2", AppModule.CUSTOMERS.keyShortcut)
        assertEquals("[F2] Clientes", AppModule.CUSTOMERS.displayLabel)

        assertEquals("F3", AppModule.PRODUCTS.keyShortcut)
        assertEquals("[F3] Productos", AppModule.PRODUCTS.displayLabel)

        assertEquals("F4", AppModule.INVENTORY.keyShortcut)
        assertEquals("[F4] Inventario", AppModule.INVENTORY.displayLabel)

        assertEquals("F5", AppModule.SETTINGS.keyShortcut)
        assertEquals("[F5] Configuración", AppModule.SETTINGS.displayLabel)

        assertEquals("F6", AppModule.CASH_CUT.keyShortcut)
        assertEquals("[F6] Cortes", AppModule.CASH_CUT.displayLabel)
    }

    @Test
    fun testScaleReadingDefaultsAndStatuses() {
        val defaultScale = ScaleReading()
        assertEquals("0.000 kg", defaultScale.weightText)
        assertEquals(ScaleStatus.STABLE, defaultScale.status)

        val readingScale = ScaleReading("1.250 kg", ScaleStatus.READING)
        assertEquals("1.250 kg", readingScale.weightText)
        assertEquals("LEYENDO", readingScale.status.label)
    }

    @Test
    fun testTopHeaderUiStateDefaults() {
        val state = TopHeaderUiState(
            registerId = "02",
            cashierName = "María L.",
            activeModule = AppModule.SALES,
            connectionState = ServerConnectionState.CONNECTED
        )

        assertEquals("NexusPOS", state.brandTitle)
        assertEquals("Abarrotes & Miscelánea", state.brandSubtitle)
        assertEquals(AppModule.SALES, state.activeModule)
        assertEquals("02", state.registerId)
        assertEquals("María L.", state.cashierName)
        assertEquals(ServerConnectionState.CONNECTED, state.connectionState)
    }

    @Test
    fun testTopHeaderModuleSwitching() {
        val initial = TopHeaderUiState()
        val updated = initial.copy(activeModule = AppModule.PRODUCTS)

        assertEquals(AppModule.SALES, initial.activeModule)
        assertEquals(AppModule.PRODUCTS, updated.activeModule)
        assertEquals("F3", updated.activeModule.keyShortcut)
    }
}

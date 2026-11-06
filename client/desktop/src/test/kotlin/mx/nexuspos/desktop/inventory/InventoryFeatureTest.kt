package mx.nexuspos.desktop.inventory

import mx.nexuspos.client.features.inventory.InventoryTab
import mx.nexuspos.client.features.inventory.InventoryUiState
import mx.nexuspos.client.features.inventory.StockFilter
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.StockMovementDto
import kotlin.test.*

class InventoryFeatureTest {

    private val prodOptimal = ProductDto(
        id = "p-1",
        sku = "PRD-001",
        name = "Coca Cola 600ml",
        baseUnit = "PZA",
        costCents = 1200L,
        usesInventory = true,
        currentStockMillis = 24000L, // 24
        minStockMillis = 6000L       // 6
    )

    private val prodLowStock = ProductDto(
        id = "p-2",
        sku = "PRD-002",
        name = "Leche Entera 1L",
        baseUnit = "PZA",
        costCents = 1800L,
        usesInventory = true,
        currentStockMillis = 4000L, // 4 (bajo mínimo)
        minStockMillis = 6000L      // 6
    )

    private val prodOutOfStock = ProductDto(
        id = "p-3",
        sku = "PRD-003",
        name = "Pan Blanco Bimbo",
        baseUnit = "PZA",
        costCents = 3200L,
        usesInventory = true,
        currentStockMillis = 0L,    // 0 (agotado)
        minStockMillis = 4000L      // 4
    )

    @Test
    fun testDefaultUiState() {
        val state = InventoryUiState()

        assertNull(state.summary)
        assertTrue(state.products.isEmpty())
        assertTrue(state.movements.isEmpty())
        assertNull(state.selectedProduct)
        assertNull(state.selectedDepartmentId)
        assertEquals("", state.searchQuery)
        assertEquals(StockFilter.ALL, state.stockFilter)
        assertEquals(InventoryTab.STOCK_TABLE, state.activeTab)
        assertFalse(state.showMovementDialog)
        assertNull(state.productForMovement)
        assertFalse(state.isLoading)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun testStockFilterLogic() {
        val allProducts = listOf(prodOptimal, prodLowStock, prodOutOfStock)

        // Filter ALL
        val filterAll = allProducts
        assertEquals(3, filterAll.size)

        // Filter LOW_STOCK
        val filterLow = allProducts.filter {
            it.usesInventory && it.currentStockMillis <= it.minStockMillis && it.currentStockMillis > 0
        }
        assertEquals(1, filterLow.size)
        assertEquals("PRD-002", filterLow.first().sku)

        // Filter OUT_OF_STOCK
        val filterOut = allProducts.filter {
            it.usesInventory && it.currentStockMillis <= 0
        }
        assertEquals(1, filterOut.size)
        assertEquals("PRD-003", filterOut.first().sku)
    }

    @Test
    fun testValuationCalculation() {
        // Valuation = (stockMillis * costCents) / 1000L
        val valOptimal = (prodOptimal.currentStockMillis * prodOptimal.costCents) / 1000L // 24 * 1200 = 28800 cents ($288.00)
        val valLow = (prodLowStock.currentStockMillis * prodLowStock.costCents) / 1000L     // 4 * 1800 = 7200 cents ($72.00)
        val valOut = (prodOutOfStock.currentStockMillis * prodOutOfStock.costCents) / 1000L // 0 cents

        assertEquals(28800L, valOptimal)
        assertEquals(7200L, valLow)
        assertEquals(0L, valOut)

        val totalValuation = valOptimal + valLow + valOut
        assertEquals(36000L, totalValuation) // $360.00
    }

    @Test
    fun testMovementDialogStateTransitions() {
        var state = InventoryUiState()

        // Open dialog for product
        state = state.copy(showMovementDialog = true, productForMovement = prodOptimal)
        assertTrue(state.showMovementDialog)
        assertEquals("p-1", state.productForMovement?.id)

        // Close dialog
        state = state.copy(showMovementDialog = false, productForMovement = null)
        assertFalse(state.showMovementDialog)
        assertNull(state.productForMovement)
    }

    @Test
    fun testKardexTabSwitch() {
        var state = InventoryUiState()
        assertEquals(InventoryTab.STOCK_TABLE, state.activeTab)

        state = state.copy(activeTab = InventoryTab.KARDEX)
        assertEquals(InventoryTab.KARDEX, state.activeTab)
    }
}

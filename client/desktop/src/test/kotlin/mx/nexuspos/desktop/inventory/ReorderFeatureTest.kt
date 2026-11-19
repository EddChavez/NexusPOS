package mx.nexuspos.desktop.inventory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.inventory.InventoryTab
import mx.nexuspos.client.features.inventory.InventoryViewModel
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import mx.nexuspos.desktop.hardware.PaperWidth
import kotlin.test.*

class ReorderFeatureTest {

    private fun createSampleItems(): List<ReorderSuggestionItemDto> {
        return listOf(
            ReorderSuggestionItemDto(
                productId = "prod-coke",
                sku = "REF-001",
                barcode = "7501055300078",
                productName = "Coca-Cola 600ml",
                departmentId = "dept-1",
                departmentName = "Bebidas",
                baseUnit = "PZA",
                currentStockMillis = 0L,
                minStockMillis = 10000L,
                maxStockMillis = 30000L,
                suggestedOrderMillis = 30000L,
                costCents = 1200L,
                estimatedInvestmentCents = 36000L,
                urgency = ReorderUrgency.CRITICAL_OUT_OF_STOCK
            ),
            ReorderSuggestionItemDto(
                productId = "prod-sabritas",
                sku = "BOT-002",
                barcode = "7501011115483",
                productName = "Papas Sabritas 45g",
                departmentId = "dept-2",
                departmentName = "Botanas",
                baseUnit = "PZA",
                currentStockMillis = 2000L,
                minStockMillis = 5000L,
                maxStockMillis = 0L,
                suggestedOrderMillis = 8000L,
                costCents = 1500L,
                estimatedInvestmentCents = 12000L,
                urgency = ReorderUrgency.LOW_STOCK
            )
        )
    }

    private class FakeApiClient(val sampleReport: ReorderSuggestionsReportDto) : ApiClient(baseUrl = "http://localhost") {
        override suspend fun getReorderSuggestions(departmentId: String?, onlyOutOfStock: Boolean): Result<ReorderSuggestionsReportDto> {
            val filtered = if (onlyOutOfStock) {
                sampleReport.items.filter { it.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK }
            } else sampleReport.items
            return Result.success(sampleReport.copy(items = filtered, totalItemsCount = filtered.size))
        }
    }

    @Test
    fun testInventoryViewModelReorderTabAndQuantityEditing() {
        val items = createSampleItems()
        val report = ReorderSuggestionsReportDto(
            generatedAt = 1700000000000L,
            totalItemsCount = items.size,
            criticalOutOfStockCount = 1,
            totalEstimatedInvestmentCents = 48000L,
            items = items
        )
        val fakeClient = FakeApiClient(report)
        val vm = InventoryViewModel(apiClient = fakeClient, scope = CoroutineScope(Dispatchers.Unconfined))

        vm.onTabSelected(InventoryTab.REORDER_SUGGESTIONS)
        assertEquals(InventoryTab.REORDER_SUGGESTIONS, vm.uiState.value.activeTab)
        assertNotNull(vm.uiState.value.reorderReport)
        assertEquals(2, vm.uiState.value.reorderReport?.items?.size)

        // Modify quantity for Coca-Cola to 24 units (24,000 millis)
        vm.updateSuggestedQuantity("prod-coke", 24000L)
        assertEquals(24000L, vm.uiState.value.editedOrderQuantities["prod-coke"])

        // Filter only out-of-stock
        vm.onToggleReorderOnlyOutOfStock(true)
        assertTrue(vm.uiState.value.reorderOnlyOutOfStock)
        assertEquals(1, vm.uiState.value.reorderReport?.items?.size)

        // Reset
        vm.resetEditedQuantities()
        assertTrue(vm.uiState.value.editedOrderQuantities.isEmpty())
    }

    @Test
    fun testReorderExportHelperWhatsAppFormatting() {
        val items = createSampleItems()
        val text = ReorderExportHelper.generateWhatsAppText(
            items = items,
            storeName = "Abarrotes Don Pepe",
            departmentName = "General"
        )

        assertTrue(text.contains("PEDIDO DE COMPRA - ABARROTES DON PEPE"))
        assertTrue(text.contains("Coca-Cola 600ml"))
        assertTrue(text.contains("30 PZA"))
        assertTrue(text.contains("(Agotado)"))
        assertTrue(text.contains("Papas Sabritas 45g"))
        assertTrue(text.contains("8 PZA"))
        assertTrue(text.contains("Total de partidas: 2"))
        assertTrue(text.contains("Inversión estimada: $480.00"))
    }

    @Test
    fun testReorderExportHelperCsvFormatting() {
        val items = createSampleItems()
        val csv = ReorderExportHelper.generateCsvContent(items)

        val lines = csv.trim().lines()
        assertEquals(3, lines.size) // Header + 2 items
        assertTrue(lines[0].startsWith("SKU,CodigoBarras,Producto,Departamento"))
        assertTrue(lines[1].contains("REF-001"))
        assertTrue(lines[1].contains("7501055300078"))
        assertTrue(lines[1].contains("AGOTADO_CRITICO"))
        assertTrue(lines[2].contains("BOT-002"))
        assertTrue(lines[2].contains("BAJO_MINIMO"))
    }

    @Test
    fun testEscPosReceiptPrinterReorderListText() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_80)
        val items = createSampleItems()
        val settings = SettingsDto(storeName = "Abarrotes Don Pepe")

        val text = printer.formatReorderListText(items, settings, departmentName = "Refrescos y Botanas")

        assertTrue(text.contains("LISTA DE RESURTIDO Y COMPRA"))
        assertTrue(text.contains("Abarrotes Don Pepe"))
        assertTrue(text.contains("Total Partidas: 2"))
        assertTrue(text.contains("Coca-Cola 600ml"))
        assertTrue(text.contains("Papas Sabritas 45g"))
        assertTrue(text.contains("INVERSIÓN TOTAL EST.:"))
        assertTrue(text.contains("$480.00"))

        val bytes = printer.generateReorderListBytes(items, settings, departmentName = "Refrescos y Botanas")
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x1B.toByte(), bytes[0])
    }
}

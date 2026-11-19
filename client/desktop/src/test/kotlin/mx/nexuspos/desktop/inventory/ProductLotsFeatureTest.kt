package mx.nexuspos.desktop.inventory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.inventory.InventoryTab
import mx.nexuspos.client.features.inventory.InventoryViewModel
import mx.nexuspos.contracts.inventory.*
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import mx.nexuspos.desktop.hardware.PaperWidth
import kotlin.test.*

class ProductLotsFeatureTest {

    private fun createSampleReport(): ExpirationsReportDto {
        val lots = listOf(
            ProductLotDto(
                id = "lot-1",
                productId = "prod-milk",
                productName = "Leche Entera 1L",
                productSku = "LAC-001",
                barcode = "750100000001",
                baseUnit = "PZA",
                lotNumber = "L-MILK-01",
                expirationDate = "2026-11-15",
                daysUntilExpiration = -4,
                quantityMillis = 8000L,
                initialQuantityMillis = 10000L,
                costCents = 2400L,
                valuationCents = 19200L, // 8 * 2400 = 19200
                status = LotExpirationStatus.EXPIRED,
                notes = "Refrigerador 2",
                isActive = true,
                createdAt = 1700000000000L
            ),
            ProductLotDto(
                id = "lot-2",
                productId = "prod-cheese",
                productName = "Queso Panela 400g",
                productSku = "LAC-002",
                barcode = "750100000002",
                baseUnit = "PZA",
                lotNumber = "L-CHZ-02",
                expirationDate = "2026-11-22",
                daysUntilExpiration = 3,
                quantityMillis = 5000L,
                initialQuantityMillis = 5000L,
                costCents = 4500L,
                valuationCents = 22500L,
                status = LotExpirationStatus.NEAR_EXPIRATION,
                notes = "Anaquel lácteos",
                isActive = true,
                createdAt = 1700000000000L
            ),
            ProductLotDto(
                id = "lot-3",
                productId = "prod-yogurt",
                productName = "Yogurt Natural 1L",
                productSku = "LAC-003",
                barcode = "750100000003",
                baseUnit = "PZA",
                lotNumber = "L-YOG-03",
                expirationDate = "2026-12-15",
                daysUntilExpiration = 26,
                quantityMillis = 15000L,
                initialQuantityMillis = 15000L,
                costCents = 3000L,
                valuationCents = 45000L,
                status = LotExpirationStatus.OPTIMAL,
                notes = "Almacén frío",
                isActive = true,
                createdAt = 1700000000000L
            )
        )

        return ExpirationsReportDto(
            generatedAt = 1700000000000L,
            totalLotsCount = lots.size,
            expiredLotsCount = 1,
            nearExpirationLotsCount = 1,
            totalExpiredValuationCents = 19200L,
            totalAtRiskValuationCents = 22500L,
            lots = lots
        )
    }

    private class FakeLotsApiClient(
        val report: ExpirationsReportDto
    ) : ApiClient(baseUrl = "http://localhost") {

        var lastCreatedRequest: CreateProductLotRequest? = null
        var lastWriteOffId: String? = null
        var lastWriteOffRequest: LotWriteOffRequest? = null

        override suspend fun getExpirationsReport(): Result<ExpirationsReportDto> {
            return Result.success(report)
        }

        override suspend fun createProductLot(req: CreateProductLotRequest): Result<ProductLotDto> {
            lastCreatedRequest = req
            return Result.success(
                ProductLotDto(
                    id = "lot-new-999",
                    productId = req.productId,
                    productName = "Nuevo Perecedero",
                    productSku = "PER-999",
                    barcode = "750999999999",
                    baseUnit = "PZA",
                    lotNumber = req.lotNumber,
                    expirationDate = req.expirationDate,
                    daysUntilExpiration = 30,
                    quantityMillis = req.quantityMillis,
                    initialQuantityMillis = req.quantityMillis,
                    costCents = req.costCents,
                    valuationCents = (req.quantityMillis * req.costCents) / 1000L,
                    status = LotExpirationStatus.OPTIMAL,
                    notes = req.notes,
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        override suspend fun writeOffProductLot(lotId: String, req: LotWriteOffRequest): Result<ProductLotDto> {
            lastWriteOffId = lotId
            lastWriteOffRequest = req
            val orig = report.lots.first { it.id == lotId }
            return Result.success(orig.copy(quantityMillis = 0L, isActive = false))
        }
    }

    @Test
    fun testInventoryViewModelLotsFlow() {
        val sampleReport = createSampleReport()
        val fakeClient = FakeLotsApiClient(sampleReport)
        val vm = InventoryViewModel(apiClient = fakeClient, scope = CoroutineScope(Dispatchers.Unconfined))

        // 1. Seleccionar pestaña LOTS_AND_EXPIRATIONS
        vm.onTabSelected(InventoryTab.LOTS_AND_EXPIRATIONS)
        assertEquals(InventoryTab.LOTS_AND_EXPIRATIONS, vm.uiState.value.activeTab)
        assertNotNull(vm.uiState.value.lotsReport)
        assertEquals(3, vm.uiState.value.lotsReport?.lots?.size)
        assertEquals(1, vm.uiState.value.lotsReport?.expiredLotsCount)
        assertEquals(19200L, vm.uiState.value.lotsReport?.totalExpiredValuationCents)

        // 2. Probar filtro por semáforo
        vm.onLotStatusFilterSelected(LotExpirationStatus.EXPIRED)
        assertEquals(LotExpirationStatus.EXPIRED, vm.uiState.value.selectedLotStatusFilter)

        vm.onLotStatusFilterSelected(null)
        assertNull(vm.uiState.value.selectedLotStatusFilter)

        // 3. Probar apertura y creación de lote
        vm.openCreateLotDialog()
        assertTrue(vm.uiState.value.showCreateLotDialog)

        vm.createLot(
            productId = "prod-apple",
            lotNumber = "LOT-APP-01",
            expirationDate = "2026-12-01",
            quantityMillis = 12000L,
            costCents = 1800L,
            addToGeneralStock = true,
            notes = "Manzana Washington"
        )
        assertFalse(vm.uiState.value.showCreateLotDialog)
        assertNotNull(fakeClient.lastCreatedRequest)
        assertEquals("LOT-APP-01", fakeClient.lastCreatedRequest?.lotNumber)
        assertTrue(vm.uiState.value.successMessage?.contains("LOT-APP-01") == true)

        // 4. Probar apertura y confirmación de baja por merma
        val lotToExpire = sampleReport.lots.first()
        vm.openLotWriteOffDialog(lotToExpire)
        assertEquals(lotToExpire.id, vm.uiState.value.lotForWriteOff?.id)

        vm.writeOffLot(
            lotId = lotToExpire.id,
            quantityMillis = 8000L,
            reason = "Baja total por merma en anaquel",
            userId = "usr-supervisor"
        )
        assertNull(vm.uiState.value.lotForWriteOff)
        assertEquals(lotToExpire.id, fakeClient.lastWriteOffId)
        assertEquals(8000L, fakeClient.lastWriteOffRequest?.quantityMillis)
        assertTrue(vm.uiState.value.successMessage?.contains("Baja de merma procesada") == true)
    }

    @Test
    fun testEscPosPrinterExpirationsReportFormatting() {
        val sampleReport = createSampleReport()
        val settings = SettingsDto(storeName = "ABARROTES EL ROBLE")
        val printer80 = EscPosReceiptPrinter(PaperWidth.MM_80)

        val formattedText = printer80.formatExpirationsReportText(sampleReport, settings)
        assertTrue(formattedText.contains("ABARROTES EL ROBLE"))
        assertTrue(formattedText.contains("REPORTE DE CADUCIDADES"))
        assertTrue(formattedText.contains("MERMA CONFIRMADA"))
        assertTrue(formattedText.contains("L-MILK-01"))
        assertTrue(formattedText.contains("[CADUCADO]"))
        assertTrue(formattedText.contains("L-CHZ-02"))
        assertTrue(formattedText.contains("[PRÓXIMO 3d]"))
        assertTrue(formattedText.contains("Firma Auditor / Encargado"))

        val bytes80 = printer80.generateExpirationsReportBytes(sampleReport, settings)
        assertTrue(bytes80.isNotEmpty())

        val printer58 = EscPosReceiptPrinter(PaperWidth.MM_58)
        val bytes58 = printer58.generateExpirationsReportBytes(sampleReport, settings)
        assertTrue(bytes58.isNotEmpty())
    }
}

package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import kotlin.test.*

class SalesScaleFeatureTest {

    private class MockScaleSalesApiClient : ApiClient() {
        val sampleBulkProduct = ProductDto(
            id = "prod-manzana",
            sku = "MANZ-KG",
            name = "Manzana Golden",
            baseUnit = "KG",
            costCents = 2500L,
            presentations = listOf(
                ProductPresentationDto(
                    id = "pres-manz",
                    productId = "prod-manzana",
                    barcode = "2001",
                    name = "Manzana Granel",
                    factorMillis = 1000L,
                    priceCents = 4000L // $40.00 / kg
                )
            )
        )

        override suspend fun searchProducts(
            barcode: String?,
            query: String?,
            departmentId: String?,
            includeInactive: Boolean
        ): Result<List<ProductDto>> {
            if (barcode == "2001" || query == "2001") {
                return Result.success(listOf(sampleBulkProduct))
            }
            return Result.success(emptyList())
        }
    }

    @Test
    fun testBulkProductScanWithAutoAcceptWeight() = runBlocking {
        val mockApi = MockScaleSalesApiClient()
        val vm = SalesViewModel(mockApi, scope = CoroutineScope(Dispatchers.Unconfined))

        // Báscula estable en 1.500 kg y auto-aceptar activo
        vm.scaleReadingProvider = { ScaleReading("1.500 kg", ScaleStatus.STABLE) }
        vm.autoAcceptScaleWeightProvider = { true }

        vm.onScannerInputChanged("2001")
        vm.onScannerSubmitted()

        val state = vm.uiState.value
        assertNull(state.isWeighingProduct, "No debe dejar el modal abierto si auto-aceptar está activo")
        assertEquals(1, state.cartLines.size)

        val line = state.cartLines.first()
        assertEquals("Manzana Golden", line.name)
        assertEquals(1.5, line.quantity.toDouble)
        assertEquals(6000L, line.total.cents, "1.5 kg a $40/kg debe dar $60.00")
    }

    @Test
    fun testBulkProductScanWithoutAutoAcceptOpensDialog() = runBlocking {
        val mockApi = MockScaleSalesApiClient()
        val vm = SalesViewModel(mockApi, scope = CoroutineScope(Dispatchers.Unconfined))

        // Auto-aceptar inactivo
        vm.scaleReadingProvider = { ScaleReading("1.500 kg", ScaleStatus.STABLE) }
        vm.autoAcceptScaleWeightProvider = { false }

        vm.onScannerInputChanged("2001")
        vm.onScannerSubmitted()

        val state = vm.uiState.value
        assertNotNull(state.isWeighingProduct, "Debe abrir el diálogo para confirmar peso")
        assertEquals("Manzana Golden", state.isWeighingProduct!!.first.name)
        assertEquals(0, state.cartLines.size)
    }
}

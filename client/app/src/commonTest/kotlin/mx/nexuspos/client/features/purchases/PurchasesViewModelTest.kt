package mx.nexuspos.client.features.purchases

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.contracts.purchases.*
import kotlin.test.*

class PurchasesViewModelTest {
    @Test
    fun resurtidoKeepsExactQuantityAndReusesTheRequestAfterAConnectionFailure() {
        val submitted = mutableListOf<ReceivePurchaseRequest>()
        val api = object : ApiClient() {
            override suspend fun listSuppliers() = Result.success(listOf(SupplierDto("supplier", "Distribuidora")))
            override suspend fun receivePurchase(pin: String, data: ReceivePurchaseRequest): Result<PurchaseDetails> {
                submitted.add(data)
                if (submitted.size == 1) return Result.failure(IllegalStateException("Se cortó la conexión"))
                return Result.success(PurchaseDetails(
                    PurchaseSummary("receipt", data.supplierId, "Distribuidora", data.documentType, data.documentNumber, 625, createdAt = 0),
                    "Encargado", "", emptyList()
                ))
            }
        }
        var updates = 0
        val viewModel = PurchasesViewModel(api, { updates++ }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        try {
            viewModel.startReceipt(listOf(ReorderSuggestionItemDto(
                productId = "sugar", sku = "AZU", barcode = "", productName = "Azúcar", baseUnit = "KG",
                currentStockMillis = 0, minStockMillis = 0, maxStockMillis = 0, suggestedOrderMillis = 1250,
                costCents = 500, estimatedInvestmentCents = 625, urgency = ReorderUrgency.CRITICAL_OUT_OF_STOCK
            )))
            viewModel.editReceipt { copy(supplierId = "supplier", documentNumber = "FAC-1") }
            viewModel.confirmReceipt("1234")
            assertTrue(viewModel.state.value.awaitingConfirmation)
            assertEquals(1250L, submitted.single().lines.single().quantityMillis)
            assertTrue(viewModel.back())
            viewModel.editReceipt { copy(documentNumber = "Otro folio") }
            assertEquals("FAC-1", viewModel.state.value.receipt?.documentNumber)

            viewModel.confirmReceipt("1234")
            assertEquals(submitted.first(), submitted.last())
            assertEquals(2, submitted.size)
            assertFalse(viewModel.state.value.awaitingConfirmation)
            assertEquals("receipt", viewModel.state.value.details?.summary?.id)
            assertEquals(1, updates)
        } finally {
            viewModel.dispose()
        }
    }
}

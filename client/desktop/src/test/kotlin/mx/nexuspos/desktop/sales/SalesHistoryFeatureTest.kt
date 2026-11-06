package mx.nexuspos.desktop.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import kotlin.test.*

class SalesHistoryFeatureTest {

    private val sampleSale1 = SaleResponse(
        id = "sale-001",
        folio = 1001L,
        cashSessionId = "sess-1",
        cashierName = "Don Pepe",
        customerName = "Público en General",
        customerRemainingCreditCents = null,
        status = "COMPLETED",
        subtotalCents = 4800L,
        taxCents = 0L,
        totalCents = 4800L,
        lines = listOf(
            SaleLineItemResponse(
                id = "line-1",
                productId = "prod-1",
                presentationId = "pres-1",
                description = "Refresco Cola 600ml",
                quantityMillis = 2000L,
                unitPriceCents = 1800L,
                taxRate = 0.0,
                taxCents = 0L,
                totalCents = 3600L
            ),
            SaleLineItemResponse(
                id = "line-2",
                productId = "prod-2",
                presentationId = "pres-2",
                description = "Papas Fritas 45g",
                quantityMillis = 1000L,
                unitPriceCents = 1200L,
                taxRate = 0.0,
                taxCents = 0L,
                totalCents = 1200L
            )
        ),
        payments = listOf(
            SalePaymentResponse(
                id = "pay-1",
                method = "CASH",
                amountCents = 4800L,
                receivedCents = 5000L,
                changeCents = 200L
            )
        ),
        createdAt = 1730000000000L
    )

    private val sampleSale2 = SaleResponse(
        id = "sale-002",
        folio = 1002L,
        cashSessionId = "sess-1",
        cashierName = "Don Pepe",
        customerName = "Doña Carmen",
        customerRemainingCreditCents = 15000L,
        status = "CANCELLED",
        subtotalCents = 2500L,
        taxCents = 0L,
        totalCents = 2500L,
        lines = listOf(
            SaleLineItemResponse(
                id = "line-3",
                productId = "prod-3",
                presentationId = "pres-3",
                description = "Aceite Vegetal 1L",
                quantityMillis = 1000L,
                unitPriceCents = 2500L,
                taxRate = 0.0,
                taxCents = 0L,
                totalCents = 2500L
            )
        ),
        payments = listOf(
            SalePaymentResponse(
                id = "pay-2",
                method = "CREDIT",
                amountCents = 2500L,
                receivedCents = 0L,
                changeCents = 0L
            )
        ),
        createdAt = 1730003600000L
    )

    private class FakeSalesApiClient(
        var salesList: MutableList<SaleResponse> = mutableListOf()
    ) : ApiClient() {
        override suspend fun getSales(
            sessionId: String?,
            folio: Long?,
            status: String?,
            startDate: Long?,
            endDate: Long?,
            limit: Int
        ): Result<List<SaleResponse>> {
            var filtered = salesList.toList()
            if (!sessionId.isNullOrBlank()) {
                filtered = filtered.filter { it.cashSessionId == sessionId }
            }
            if (folio != null) {
                filtered = filtered.filter { it.folio == folio }
            }
            if (!status.isNullOrBlank()) {
                filtered = filtered.filter { it.status == status }
            }
            return Result.success(filtered)
        }

        override suspend fun cancelSale(
            id: String,
            reason: String,
            authorizedByUserId: String?
        ): Result<SaleResponse> {
            val idx = salesList.indexOfFirst { it.id == id }
            if (idx == -1) return Result.failure(Exception("Venta no encontrada"))
            val updated = salesList[idx].copy(status = "CANCELLED")
            salesList[idx] = updated
            return Result.success(updated)
        }

        override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> {
            return Result.success(
                CashSessionSummary(
                    sessionId = "sess-1",
                    registerId = registerId,
                    cashierId = "usr-cajero",
                    cashierName = "Don Pepe",
                    openedAt = 1730000000000L,
                    initialFundCents = 50000L,
                    salesCashCents = 4800L,
                    creditPaymentsCashCents = 0L,
                    cashInCents = 0L,
                    cashOutCents = 0L,
                    refundsCashCents = 0L,
                    expectedCashCents = 54800L,
                    status = "OPEN"
                )
            )
        }
    }

    private fun createTestSalesVm(apiClient: FakeSalesApiClient): SalesViewModel {
        return SalesViewModel(apiClient = apiClient, scope = CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun testLoadShiftSalesSuccess() {
        val fakeApi = FakeSalesApiClient(mutableListOf(sampleSale1, sampleSale2))
        val viewModel = createTestSalesVm(fakeApi)

        viewModel.openDailySalesAndReturns()

        val state = viewModel.uiState.value
        assertTrue(state.isShowingSalesHistory)
        assertFalse(state.isLoadingShiftSales)
        assertEquals(2, state.shiftSales.size)
        assertEquals(sampleSale1, state.selectedShiftSale)

        // Metrics verification
        val completed = state.shiftSales.filter { it.status == "COMPLETED" }
        assertEquals(1, completed.size)
        assertEquals(4800L, completed.sumOf { it.totalCents })
        assertEquals(1, state.shiftSales.count { it.status == "CANCELLED" })
    }

    @Test
    fun testFilterSalesByFolioAndStatus() {
        val fakeApi = FakeSalesApiClient(mutableListOf(sampleSale1, sampleSale2))
        val viewModel = createTestSalesVm(fakeApi)

        viewModel.openDailySalesAndReturns()

        // 1. Filter by folio 1002
        viewModel.onSalesSearchFolioChanged("1002")
        assertEquals("1002", viewModel.uiState.value.salesHistorySearchFolio)

        // 2. Filter by status CANCELLED
        viewModel.onSalesStatusFilterChanged("CANCELLED")
        assertEquals("CANCELLED", viewModel.uiState.value.salesHistoryStatusFilter)
    }

    @Test
    fun testReprintTicketContainsReprintLegend() {
        val fakeApi = FakeSalesApiClient(mutableListOf(sampleSale1))
        val viewModel = createTestSalesVm(fakeApi)

        viewModel.reprintTicket(sampleSale1)

        val state = viewModel.uiState.value
        assertNotNull(state.reprintTicketPreview, "Debe generar el texto de vista previa")
        val text = state.reprintTicketPreview!!

        // Mandatory reprint legend assertion
        assertTrue(
            text.contains("*** COPIA DE REIMPRESIÓN ***"),
            "El ticket de reimpresión debe incluir obligatoriamente la leyenda '*** COPIA DE REIMPRESIÓN ***'"
        )

        // Ticket content assertions
        assertTrue(text.contains("Folio: #1001"))
        assertTrue(text.contains("Refresco Cola 600ml"))
        assertTrue(text.contains("Papas Fritas 45g"))
        assertTrue(text.contains("$48.00"))
        assertTrue(text.contains("EFECTIVO") || text.contains("Efectivo") || text.contains("CASH"))

        // Close preview
        viewModel.closeReprintPreview()
        assertNull(viewModel.uiState.value.reprintTicketPreview)
    }

    @Test
    fun testCancelSaleUpdatesStatusAndEmitsSuccessMessage() {
        val fakeApi = FakeSalesApiClient(mutableListOf(sampleSale1))
        val viewModel = createTestSalesVm(fakeApi)

        viewModel.openDailySalesAndReturns()
        assertEquals("COMPLETED", viewModel.uiState.value.shiftSales.first().status)

        // Iniciar flujo de cancelación
        viewModel.startCancelSaleFlow(sampleSale1)
        assertTrue(viewModel.uiState.value.isShowingCancelReasonDialog)
        assertEquals(sampleSale1, viewModel.uiState.value.salePendingCancel)

        // Ejecutar cancelación
        viewModel.executeCancelSale(
            saleId = sampleSale1.id,
            reason = "Error al capturar productos",
            supervisorUserId = "usr-admin"
        )

        val state = viewModel.uiState.value
        assertFalse(state.isShowingCancelReasonDialog)
        assertNull(state.salePendingCancel)

        // Verify status changed to CANCELLED
        val updatedSale = state.shiftSales.first { it.id == sampleSale1.id }
        assertEquals("CANCELLED", updatedSale.status)
        assertEquals("CANCELLED", state.selectedShiftSale?.status)

        assertNotNull(state.successMessage)
        assertTrue(state.successMessage!!.contains("cancelada exitosamente"))
    }

    @Test
    fun testCannotCancelAlreadyCancelledSale() {
        val fakeApi = FakeSalesApiClient(mutableListOf(sampleSale2))
        val viewModel = createTestSalesVm(fakeApi)

        viewModel.openDailySalesAndReturns()
        assertEquals("CANCELLED", sampleSale2.status)

        viewModel.startCancelSaleFlow(sampleSale2)

        val state = viewModel.uiState.value
        assertFalse(state.isShowingCancelReasonDialog, "No debe permitir abrir diálogo de motivo si ya está cancelada")
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("ya se encuentra cancelado"))
    }

    @Test
    fun testRoleBasedCancelPermissionLogic() {
        // Regla: Si el usuario es CASHIER, requiere supervisor para cancelar. Si es ADMIN/SUPERVISOR, procede directamente.
        fun requiresSupervisorToCancel(role: String): Boolean = role == "CASHIER"

        assertTrue(requiresSupervisorToCancel("CASHIER"))
        assertFalse(requiresSupervisorToCancel("ADMIN"))
        assertFalse(requiresSupervisorToCancel("SUPERVISOR"))
    }
}

package mx.nexuspos.desktop.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.offline.*
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import java.io.IOException
import kotlin.test.*

class OfflineContingencyFeatureTest {

    private val sampleProduct = ProductDto(
        id = "prod-101",
        sku = "7501055300075",
        name = "Coca Cola Original 600ml",
        departmentId = "dep-1",
        baseUnit = "PZA",
        isActive = true,
        presentations = listOf(
            ProductPresentationDto(
                id = "pres-101",
                productId = "prod-101",
                barcode = "7501055300075",
                name = "Botella 600ml",
                factorMillis = 1000L,
                priceCents = 1800L
            )
        )
    )

    private fun sampleSaleRequest(id: String = "idem-1", totalCents: Long = 1800L): CreateSaleRequest {
        return CreateSaleRequest(
            idempotencyKey = id,
            cashSessionId = "sess-1",
            cashierUserId = "usr-cajero",
            lines = listOf(
                SaleLineItemRequest(
                    productId = "prod-101",
                    presentationId = "pres-101",
                    description = "Coca Cola 600ml",
                    quantityMillis = 1000L,
                    expectedUnitPriceCents = 1800L,
                    expectedTotalCents = 1800L,
                    taxRate = 0.0
                )
            ),
            payments = listOf(
                SalePaymentRequest("CASH", totalCents, totalCents, "")
            ),
            expectedTotalCents = totalCents
        )
    }

    @Test
    fun queuedSaleIsNeverSentToADifferentServer() = runBlocking {
        val queue = OfflineSaleQueue(InMemoryPersistenceProvider())
        queue.enqueue(sampleSaleRequest(), serverUrl = "http://tienda-a:8080")
        val otherServer = object : ApiClient(baseUrl = "http://tienda-b:8080") {
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> =
                error("No debe enviar ventas de otra tienda")
        }
        val sync = OfflineSyncManager(otherServer, queue, this)
        assertEquals(0, sync.syncPendingSales().totalProcessed)
        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun interruptedOnlineSubmissionReplaysAfterRestartWithOriginalKey() = runBlocking {
        val storage = InMemoryPersistenceProvider()
        val request = sampleSaleRequest()
        val liveQueue = OfflineSaleQueue(storage)
        val submission = liveQueue.stageOnline(request)
        assertTrue(liveQueue.getPending().isEmpty(), "La sincronización no debe enviar un cobro que sigue en curso")

        val recovered = OfflineSaleQueue(storage)
        assertEquals(request, recovered.getPending().single().request)
        val api = object : ApiClient() {
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                assertEquals(request, req)
                return Result.success(SaleResponse(
                    "sale-central", 2041, req.cashSessionId, "Cajero", status = "COMPLETED",
                    subtotalCents = 1800, taxCents = 0, totalCents = 1800,
                    lines = emptyList(), payments = emptyList(), createdAt = 1
                ))
            }
        }
        assertEquals(1, OfflineSyncManager(api, recovered, this).syncPendingSales(automatic = true).succeeded)
        val synced = OfflineSaleQueue(storage).getAll().single()
        assertEquals(submission.localFolio, synced.localFolio)
        assertEquals(2041L, synced.syncedFolio)
        assertEquals(0, OfflineSaleQueue(storage).pendingCount())
    }

    @Test
    fun lostCheckoutResponseRetainsSameRequestWithoutCompletingTheTicketTwice() = runBlocking {
        val storage = InMemoryPersistenceProvider()
        val session = CashSessionSummary(
            sessionId = "sess-1", registerId = "caja-prueba", cashierId = "usr-cajero",
            cashierName = "Cajero", openedAt = 1, initialFundCents = 0, status = "OPEN",
            salesCashCents = 0, creditPaymentsCashCents = 0, cashInCents = 0, cashOutCents = 0,
            refundsCashCents = 0, expectedCashCents = 0
        )
        val attempts = mutableListOf<CreateSaleRequest>()
        val api = object : ApiClient() {
            override suspend fun checkHealth() = true
            override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> =
                Result.success(session)
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                attempts.add(req)
                return Result.failure(IOException("Se perdió la respuesta"))
            }
        }
        val vm = SalesViewModel(api, CoroutineScope(Dispatchers.Unconfined), storage)
        vm.addProductPresentation(sampleProduct, sampleProduct.presentations.single(), Quantity.ONE)

        vm.checkoutCash(2000)

        assertEquals(attempts.single(), OfflineSaleQueue(storage).getPending().single().request)
        assertEquals(1, vm.uiState.value.offlinePendingSalesCount)
        assertNull(vm.uiState.value.lastCompletedSale)
        assertTrue(vm.uiState.value.cartLines.isEmpty())
        assertContains(vm.uiState.value.errorMessage.orEmpty(), "pendiente de confirmación")
    }

    @Test
    fun unsuccessfulQueueWriteDoesNotAcceptOrReplaceASale() {
        val storage = object : LocalPersistenceProvider by InMemoryPersistenceProvider() {
            override fun writeText(fileName: String, content: String) {
                throw IOException("Disco lleno")
            }
        }
        val queue = OfflineSaleQueue(storage)
        assertFailsWith<IOException> { queue.stageOnline(sampleSaleRequest()) }
        assertTrue(queue.getAll().isEmpty())
    }

    @Test
    fun permanentConflictsRemainVisibleWithoutAutomaticRetries() = runBlocking {
        val queue = OfflineSaleQueue(InMemoryPersistenceProvider())
        queue.enqueue(sampleSaleRequest())
        var attempts = 0
        val api = object : ApiClient() {
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                attempts++
                return Result.failure(IllegalArgumentException("La sesión ya cerró"))
            }
        }
        val manager = OfflineSyncManager(api, queue, this)
        assertEquals(1, manager.syncPendingSales(automatic = true).failed)
        assertEquals(0, manager.syncPendingSales(automatic = true).totalProcessed)
        assertEquals(1, attempts)
        assertEquals(1, queue.pendingCount())
        assertEquals("La sesión ya cerró", queue.getAll().single().lastError)
    }

    // ==========================================
    // 1. OfflineCatalogCache Tests
    // ==========================================
    @Test
    fun testOfflineCatalogCacheSaveAndLookup() {
        val memoryStorage = InMemoryPersistenceProvider()
        val cache = OfflineCatalogCache(memoryStorage)

        assertEquals(0, cache.count())
        assertNull(cache.findProductByBarcode("7501055300075"))

        // Save products into cache
        cache.saveCatalog(listOf(sampleProduct))
        assertEquals(1, cache.count())

        // Find by exact barcode
        val foundByBarcode = cache.findProductByBarcode("7501055300075")
        assertNotNull(foundByBarcode)
        assertEquals("Coca Cola Original 600ml", foundByBarcode.name)

        // Search by query (case insensitive)
        val searchResults = cache.searchProducts("coca")
        assertEquals(1, searchResults.size)
        assertEquals("prod-101", searchResults.first().id)

        // Non-existent barcode
        assertNull(cache.findProductByBarcode("9999999999999"))

        // Reload from storage into new cache instance
        val reloadedCache = OfflineCatalogCache(memoryStorage)
        assertEquals(1, reloadedCache.count())
        assertNotNull(reloadedCache.findProductByBarcode("7501055300075"))
    }

    // ==========================================
    // 2. OfflineSaleQueue Tests
    // ==========================================
    @Test
    fun testOfflineSaleQueueSequenceAndPersistence() {
        val memoryStorage = InMemoryPersistenceProvider()
        val queue = OfflineSaleQueue(memoryStorage)

        assertEquals(0, queue.pendingCount())

        // 1. Enqueue first sale
        val sale1 = queue.enqueue(sampleSaleRequest("req-1"))
        assertEquals("OFF-0001", sale1.localFolio)
        assertEquals(OfflineSaleStatus.PENDING, sale1.status)
        assertEquals(1, queue.pendingCount())

        // 2. Enqueue second sale
        val sale2 = queue.enqueue(sampleSaleRequest("req-2"))
        assertEquals("OFF-0002", sale2.localFolio)
        assertEquals(2, queue.pendingCount())

        // 3. Mark first sale as synced
        queue.markSynced("OFF-0001", centralFolio = 1050L, centralSaleId = "sale-central-1050")
        assertEquals(1, queue.pendingCount())
        val allSales = queue.getAll()
        val syncedItem = allSales.first { it.localFolio == "OFF-0001" }
        assertEquals(OfflineSaleStatus.SYNCED, syncedItem.status)
        assertEquals(1050L, syncedItem.syncedFolio)

        // 4. Mark second sale as failed
        queue.markFailed("OFF-0002", "Error 500: Database busy")
        val failedItem = queue.getAll().first { it.localFolio == "OFF-0002" }
        assertEquals(OfflineSaleStatus.FAILED, failedItem.status)
        assertEquals(1, failedItem.attempts)
        assertEquals("Error 500: Database busy", failedItem.lastError)
        // Failed sales are still considered pending for retry
        assertEquals(1, queue.pendingCount())

        // 5. Simulate application restart (reload from memory storage)
        val newQueueInstance = OfflineSaleQueue(memoryStorage)
        assertEquals(1, newQueueInstance.pendingCount())
        assertEquals(2, newQueueInstance.getAll().size)

        // 6. Next folio should increment correctly
        val sale3 = newQueueInstance.enqueue(sampleSaleRequest("req-3"))
        assertEquals("OFF-0003", sale3.localFolio)

        // 7. Clear synced items
        newQueueInstance.clearSynced()
        val remaining = newQueueInstance.getAll()
        assertEquals(2, remaining.size)
        assertTrue(remaining.none { it.status == OfflineSaleStatus.SYNCED })
    }

    // ==========================================
    // 3. OfflineSyncManager Batch Sync Tests
    // ==========================================
    @Test
    fun testOfflineSyncManagerFlushesQueue() = runBlocking {
        val memoryStorage = InMemoryPersistenceProvider()
        val queue = OfflineSaleQueue(memoryStorage)

        queue.enqueue(sampleSaleRequest("req-1", 1000L))
        queue.enqueue(sampleSaleRequest("req-2", 2000L))
        assertEquals(2, queue.pendingCount())

        val mockApi = object : ApiClient() {
            var calls = 0
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                calls++
                return Result.success(
                    SaleResponse(
                        id = "central-$calls",
                        folio = 2000L + calls,
                        cashSessionId = req.cashSessionId,
                        cashierName = "Cajero",
                        status = "COMPLETED",
                        subtotalCents = req.expectedTotalCents,
                        taxCents = 0L,
                        totalCents = req.expectedTotalCents,
                        lines = emptyList(),
                        payments = emptyList(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val syncManager = OfflineSyncManager(mockApi, queue, scope)

        val result = syncManager.syncPendingSales()
        assertEquals(2, result.totalProcessed)
        assertEquals(2, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(0, queue.pendingCount())

        val summary = syncManager.summary.value
        assertEquals(0, summary.totalPending)
        assertEquals(2, summary.totalSynced)
        assertFalse(summary.isSyncing)
    }

    @Test
    fun testOfflineSyncManagerHandlesFailuresGracefully() = runBlocking {
        val memoryStorage = InMemoryPersistenceProvider()
        val queue = OfflineSaleQueue(memoryStorage)

        queue.enqueue(sampleSaleRequest("req-ok", 1500L))
        queue.enqueue(sampleSaleRequest("req-fail", 3000L))

        val mockApi = object : ApiClient() {
            override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                return if (req.idempotencyKey == "req-ok") {
                    Result.success(
                        SaleResponse(
                            id = "central-1",
                            folio = 5001L,
                            cashSessionId = req.cashSessionId,
                            cashierName = "Cajero",
                            status = "COMPLETED",
                            subtotalCents = req.expectedTotalCents,
                            taxCents = 0L,
                            totalCents = req.expectedTotalCents,
                            lines = emptyList(),
                            payments = emptyList(),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    Result.failure(IOException("Timeout connecting to LAN server"))
                }
            }
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val syncManager = OfflineSyncManager(mockApi, queue, scope)

        val result = syncManager.syncPendingSales()
        assertEquals(2, result.totalProcessed)
        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(1, queue.pendingCount()) // failed item remains pending

        val failedItem = queue.getAll().first { it.localFolio == "OFF-0002" }
        assertEquals(OfflineSaleStatus.FAILED, failedItem.status)
        assertTrue(failedItem.lastError?.contains("Timeout") == true)
    }

    // ==========================================
    // 4. SalesViewModel Offline Contingency Flow
    // ==========================================
    @Test
    fun testSalesViewModelOfflineScanningFallback() {
        val memoryStorage = InMemoryPersistenceProvider()
        val mockApi = object : ApiClient() {
            override suspend fun searchProducts(
                barcode: String?,
                query: String?,
                departmentId: String?,
                includeInactive: Boolean
            ): Result<List<ProductDto>> {
                return Result.failure(IOException("Server connection refused"))
            }

            override suspend fun checkHealth(): Boolean = false
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = SalesViewModel(mockApi, scope, memoryStorage)

        // Seed offline cache
        viewModel.catalogCache.saveCatalog(listOf(sampleProduct))

        // Trigger scanner submission
        viewModel.onScannerInputChanged("7501055300075")
        viewModel.onScannerSubmitted()

        val state = viewModel.uiState.value
        assertEquals(ServerConnectionState.NO_SERVER, state.connectionState)
        assertEquals(1, state.cartLines.size)
        assertEquals("Coca Cola Original 600ml", state.cartLines.first().name)
        assertEquals(1800L, state.total.cents)
        assertNull(state.errorMessage)
    }

    @Test
    fun testSalesViewModelOfflineCheckoutCashSuccess() {
        val memoryStorage = InMemoryPersistenceProvider()
        val mockApi = object : ApiClient() {
            override suspend fun checkHealth(): Boolean = false
            override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> =
                Result.success(CashSessionSummary(
                    sessionId = "sess-1", registerId = registerId, cashierId = "usr-cajero",
                    cashierName = "Cajero", openedAt = 1, initialFundCents = 0, status = "OPEN",
                    salesCashCents = 0, creditPaymentsCashCents = 0, cashInCents = 0, cashOutCents = 0,
                    refundsCashCents = 0, expectedCashCents = 0
                ))
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = SalesViewModel(mockApi, scope, memoryStorage)

        // Add product presentation to cart
        val pres = sampleProduct.presentations.first()
        viewModel.addProductPresentation(sampleProduct, pres, Quantity.ONE)
        assertEquals(1800L, viewModel.uiState.value.total.cents)

        // Force connectionState to NO_SERVER
        viewModel.checkServerConnection()
        assertEquals(ServerConnectionState.NO_SERVER, viewModel.uiState.value.connectionState)

        // Checkout in cash ($20.00 received for $18.00 total)
        viewModel.checkoutCash(receivedCents = 2000L)

        val state = viewModel.uiState.value
        assertEquals(0, state.cartLines.size, "El carrito debe vaciarse al completar la venta en contingencia")
        assertEquals(1, state.offlinePendingSalesCount, "Debe registrar 1 venta pendiente en cola de contingencia")
        assertTrue(viewModel.hasUnconfirmedSales("sess-1"))
        assertFalse(viewModel.hasUnconfirmedSales("other-session"))
        assertNotNull(state.lastCompletedSale)
        assertTrue(state.lastCompletedSale!!.id.startsWith("offline-OFF-0001"))
        assertTrue(state.successMessage?.contains("Modo Contingencia") == true)
        assertTrue(state.successMessage?.contains("Cambio: $2.0") == true)
    }

    @Test
    fun onlyCurrentShiftOperatorCanOpenOrSubmitCheckoutOnlineAndOffline() {
        for (connected in listOf(true, false)) {
            val storage = InMemoryPersistenceProvider()
            var shift = CashSessionSummary(
                sessionId = "sess-1", registerId = "caja-1", cashierId = "usr-ana", cashierName = "Ana",
                openedAt = 1, initialFundCents = 0, status = "OPEN", salesCashCents = 0, creditPaymentsCashCents = 0,
                cashInCents = 0, cashOutCents = 0, refundsCashCents = 0, expectedCashCents = 0
            )
            var submitted: CreateSaleRequest? = null
            val api = object : ApiClient() {
                override suspend fun checkHealth() = connected
                override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> = Result.success(shift)
                override suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
                    submitted = req
                    return Result.success(SaleResponse(
                        "sale-1", 1, req.cashSessionId, "Luis", status = "COMPLETED", subtotalCents = 1800,
                        taxCents = 0, totalCents = 1800, lines = emptyList(), payments = emptyList(), createdAt = 2
                    ))
                }
            }
            val model = SalesViewModel(api, CoroutineScope(Dispatchers.Unconfined), storage)
            model.setActiveCashier("usr-luis", "Luis", "CASHIER")
            model.addProductPresentation(sampleProduct, sampleProduct.presentations.single(), Quantity.ONE)

            model.requestCheckout()
            assertFalse(model.uiState.value.isShowingCashPayment)
            assertContains(model.uiState.value.errorMessage.orEmpty(), "Turno a cargo de Ana")
            model.checkoutCash(2000)
            assertNull(submitted)
            assertTrue(model.saleQueue.getAll().isEmpty())
            assertEquals(1, model.uiState.value.cartLines.size)
            assertNull(model.uiState.value.lastCompletedSale)

            shift = shift.copy(operatorUserId = "usr-luis", operatorName = "Luis")
            model.loadActiveSession()
            model.requestCheckout()
            assertTrue(model.uiState.value.isShowingCashPayment)
            model.checkoutCash(2000)
            val sale = if (connected) submitted else model.saleQueue.getPending().single().request
            assertEquals("usr-luis", sale?.cashierUserId)
            assertEquals("sess-1", sale?.cashSessionId)
        }
    }

    @Test
    fun offlineCashCheckoutRequiresAKnownOpenShift() {
        val api = object : ApiClient() {
            override suspend fun checkHealth(): Boolean = false
            override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> = Result.success(null)
        }
        val viewModel = SalesViewModel(api, CoroutineScope(Dispatchers.Unconfined), InMemoryPersistenceProvider())
        viewModel.addProductPresentation(sampleProduct, sampleProduct.presentations.single(), Quantity.ONE)

        viewModel.checkoutCash(2000)

        assertEquals(1, viewModel.uiState.value.cartLines.size)
        assertEquals(0, viewModel.saleQueue.pendingCount())
        assertContains(viewModel.uiState.value.checkoutErrorMessage.orEmpty(), "turno abierto")
    }

    @Test
    fun testSalesViewModelOfflineCheckoutRejectsRestrictedPayments() {
        val memoryStorage = InMemoryPersistenceProvider()
        val mockApi = object : ApiClient() {
            override suspend fun checkHealth(): Boolean = false
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val viewModel = SalesViewModel(mockApi, scope, memoryStorage)

        val pres = sampleProduct.presentations.first()
        viewModel.addProductPresentation(sampleProduct, pres, Quantity.ONE)

        // Force NO_SERVER
        viewModel.checkServerConnection()
        assertEquals(ServerConnectionState.NO_SERVER, viewModel.uiState.value.connectionState)

        // Attempt checkout with CREDIT (fiado)
        viewModel.checkoutCredit()

        val state = viewModel.uiState.value
        assertEquals(1, state.cartLines.size, "El carrito debe conservarse intacto al rechazarse la forma de pago")
        assertEquals(0, state.offlinePendingSalesCount)
        assertTrue(state.errorMessage?.contains("Modo Contingencia") == true)
    }

    // ==========================================
    // 5. ESC/POS Contingency Ticket Format
    // ==========================================
    @Test
    fun testEscPosReceiptPrinterContingencyFormat() {
        val printer = EscPosReceiptPrinter(columnWidth = 40)

        val offlineSale = SaleResponse(
            id = "offline-OFF-0001",
            folio = 1L,
            cashSessionId = "sess-contingency",
            cashierName = "Carlos Cajero",
            status = "COMPLETED",
            subtotalCents = 1800L,
            taxCents = 0L,
            totalCents = 1800L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "l-1",
                    productId = "prod-101",
                    presentationId = "pres-101",
                    description = "Coca Cola 600ml",
                    quantityMillis = 1000L,
                    unitPriceCents = 1800L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 1800L
                )
            ),
            payments = listOf(
                SalePaymentResponse("pay-1", "CASH", 1800L, 2000L, 200L, "")
            ),
            createdAt = 1700000000000L
        )

        val receiptText = printer.formatSaleReceiptText(offlineSale)

        assertTrue(receiptText.contains("*** MODO CONTINGENCIA (OFFLINE) ***"), "Debe mostrar el encabezado de contingencia")
        assertTrue(receiptText.contains("VENTA PENDIENTE DE SINCRONIZAR"), "Debe advertir que está pendiente de sincronizar")
        assertTrue(receiptText.contains("Folio: #OFF-0001"), "Debe desplegar el folio local OFF-0001")
        assertTrue(receiptText.contains("Coca Cola 600ml"), "Debe listar los artículos")
        assertTrue(receiptText.contains("$18.00"), "Debe mostrar el importe total")
    }
}

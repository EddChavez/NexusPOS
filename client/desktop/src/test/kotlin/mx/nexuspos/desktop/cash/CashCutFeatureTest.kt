package mx.nexuspos.desktop.cash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.cash.CashCutTab
import mx.nexuspos.client.features.cash.CashCutViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashCutFeatureTest {

    private class FakeCashApiClient(
        var activeSession: CashSessionSummary? = null,
        var dailyReport: DailyFinancialReportDto? = null,
        var settings: SettingsDto = SettingsDto(blindCashCountEnabled = false)
    ) : ApiClient() {
        var closedSessionRequest: CloseSessionRequest? = null
        var openedSessionRequest: OpenSessionRequest? = null

        override suspend fun getSettings(): Result<SettingsDto> {
            return Result.success(settings)
        }

        override suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> {
            return Result.success(activeSession)
        }

        override suspend fun closeSession(sessionId: String, req: CloseSessionRequest): Result<CashSessionSummary> {
            closedSessionRequest = req
            val closed = activeSession?.copy(
                status = "CLOSED",
                countedCashCents = req.countedCashCents,
                differenceCents = req.countedCashCents - (activeSession?.expectedCashCents ?: 0L),
                closedAt = System.currentTimeMillis()
            ) ?: CashSessionSummary(
                sessionId = sessionId,
                registerId = "caja-1",
                registerName = "Caja Principal",
                cashierId = "user-1",
                cashierName = "Don Pepe",
                openedAt = System.currentTimeMillis(),
                closedAt = System.currentTimeMillis(),
                initialFundCents = 50000L,
                salesCashCents = 100000L,
                creditPaymentsCashCents = 0L,
                cashInCents = 0L,
                cashOutCents = 0L,
                refundsCashCents = 0L,
                expectedCashCents = 150000L,
                countedCashCents = req.countedCashCents,
                differenceCents = req.countedCashCents - 150000L,
                status = "CLOSED"
            )
            activeSession = closed
            return Result.success(closed)
        }

        override suspend fun openSession(req: OpenSessionRequest): Result<CashSessionSummary> {
            openedSessionRequest = req
            val newSession = CashSessionSummary(
                sessionId = "sess-new",
                registerId = req.registerId,
                registerName = "Caja Principal",
                cashierId = req.userId,
                cashierName = "Don Pepe",
                openedAt = System.currentTimeMillis(),
                closedAt = null,
                initialFundCents = req.initialFundCents,
                salesCashCents = 0L,
                creditPaymentsCashCents = 0L,
                cashInCents = 0L,
                cashOutCents = 0L,
                refundsCashCents = 0L,
                expectedCashCents = req.initialFundCents,
                countedCashCents = null,
                differenceCents = null,
                status = "OPEN"
            )
            activeSession = newSession
            return Result.success(newSession)
        }

        override suspend fun getDailyReport(dateEpochMs: Long?, sessionId: String?): Result<DailyFinancialReportDto> {
            return Result.success(dailyReport ?: DailyFinancialReportDto(
                dateEpochMs = dateEpochMs ?: 0L,
                totalSalesCents = 0L,
                salesCashCents = 0L,
                salesCreditCents = 0L,
                salesOtherCents = 0L,
                totalTicketsCount = 0,
                completedTicketsCount = 0,
                cancelledTicketsCount = 0,
                cancelledAmountCents = 0L,
                creditPaymentsCashCents = 0L,
                cashInCents = 0L,
                cashOutCents = 0L,
                totalCostCents = 0L,
                estimatedGrossProfitCents = 0L,
                profitMarginPercent = 0.0,
                activeSessionsCount = 0
            ))
        }
    }

    private fun sampleOpenSession(): CashSessionSummary = CashSessionSummary(
        sessionId = "sess-100",
        registerId = "caja-1",
        registerName = "Caja Mostrador",
        cashierId = "user-cajero",
        cashierName = "Don Pepe",
        openedAt = 1713135600000L,
        closedAt = null,
        initialFundCents = 50000L,       // $500.00
        salesCashCents = 120000L,        // $1,200.00
        creditPaymentsCashCents = 30000L,// $300.00
        cashInCents = 0L,
        cashOutCents = 20000L,           // $200.00
        refundsCashCents = 0L,
        expectedCashCents = 180000L,     // $1,800.00
        countedCashCents = null,
        differenceCents = null,
        status = "OPEN"
    )

    @Test
    fun testCountingDifferenceCalculation() {
        val initialSession = sampleOpenSession()
        val fakeApi = FakeCashApiClient(activeSession = initialSession)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        assertEquals("sess-100", vm.uiState.value.activeSession?.sessionId)
        assertEquals(180000L, vm.uiState.value.activeSession?.expectedCashCents)

        // Default initial count matches expected
        assertEquals(0L, vm.uiState.value.differenceCents)

        // Case 1: Sobrante (counted $1,950.00 -> +$150.00 difference)
        vm.onCountedCashChanged("1950.00")
        assertEquals(195000L, vm.uiState.value.countedCashCents)
        assertEquals(15000L, vm.uiState.value.differenceCents)

        // Case 2: Faltante (counted $1,700.00 -> -$100.00 difference)
        vm.onCountedCashChanged("1700.00")
        assertEquals(170000L, vm.uiState.value.countedCashCents)
        assertEquals(-10000L, vm.uiState.value.differenceCents)

        // Case 3: Cuadrado exacto ($1,800.00 -> $0.00 difference)
        vm.onCountedCashChanged("1800.00")
        assertEquals(180000L, vm.uiState.value.countedCashCents)
        assertEquals(0L, vm.uiState.value.differenceCents)
    }

    @Test
    fun refreshingExpectedCashPreservesAnExplicitZeroCount() {
        val fakeApi = FakeCashApiClient(activeSession = sampleOpenSession())
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))
        val session = sampleOpenSession()
        fakeApi.activeSession = session.copy(expectedCashCents = 150000)
        vm.loadCurrentSession()
        assertEquals(150000L, vm.uiState.value.countedCashCents)

        vm.onCountedCashChanged("0")
        fakeApi.activeSession = session.copy(expectedCashCents = 140000)
        vm.loadCurrentSession()
        assertEquals(0L, vm.uiState.value.countedCashCents)
        assertEquals(-140000L, vm.uiState.value.differenceCents)
    }

    @Test
    fun testPerformCorteXDispatchesPrintWithoutClosing() {
        val session = sampleOpenSession()
        val fakeApi = FakeCashApiClient(activeSession = session)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        var printInvoked = false
        var isFinalCutArg = true
        vm.onPrintCashCut = { _, isFinal ->
            printInvoked = true
            isFinalCutArg = isFinal
        }

        vm.performCorteX()

        assertTrue(printInvoked, "onPrintCashCut must be called")
        assertFalse(isFinalCutArg, "isFinalZCut must be false for Corte X")
        assertEquals("OPEN", vm.uiState.value.activeSession?.status)
        assertFalse(vm.uiState.value.isSessionClosedJustNow)
        assertTrue(vm.uiState.value.ticketPreviewTitle.contains("Corte X"))
        assertTrue(vm.uiState.value.ticketPreviewText?.contains("CORTE X") == true)
    }

    @Test
    fun testPerformCorteZClosesSessionAndTriggersDrawerAndPrint() {
        val session = sampleOpenSession()
        val fakeApi = FakeCashApiClient(activeSession = session)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        var drawerTriggered = false
        var printInvoked = false
        var isFinalCutArg = false
        var closedCallbackCalled = false

        vm.onOpenCashDrawer = { drawerTriggered = true }
        vm.onPrintCashCut = { _, isFinal ->
            printInvoked = true
            isFinalCutArg = isFinal
        }
        vm.onSessionClosedCallback = { closedCallbackCalled = true }

        vm.onCountedCashChanged("1850.00")
        vm.onNotesChanged("Turno matutino sin incidentes.")
        vm.performCorteZ()

        assertEquals(185000L, fakeApi.closedSessionRequest?.countedCashCents)
        assertEquals("Turno matutino sin incidentes.", fakeApi.closedSessionRequest?.notes)

        assertTrue(drawerTriggered, "Cash drawer kick pulse should be sent on Corte Z")
        assertTrue(printInvoked, "Print cut ticket must be invoked")
        assertTrue(isFinalCutArg, "isFinalZCut must be true for Corte Z")
        assertTrue(closedCallbackCalled, "onSessionClosedCallback should be invoked")

        assertEquals("CLOSED", vm.uiState.value.activeSession?.status)
        assertTrue(vm.uiState.value.isSessionClosedJustNow)
        assertTrue(vm.uiState.value.ticketPreviewTitle.contains("Corte Z"))
        assertTrue(vm.uiState.value.ticketPreviewText?.contains("CORTE Z") == true)

        var reprintedId: String? = null
        vm.onPrintCashCut = { printedSession, isFinal ->
            assertTrue(isFinal)
            reprintedId = printedSession.sessionId
        }
        vm.loadCurrentSession()
        vm.reprintPreview()
        assertEquals(session.sessionId, reprintedId)
    }

    @Test
    fun corteZWaitsUntilLocalSalesAreReconciled() {
        val session = sampleOpenSession()
        val api = FakeCashApiClient(activeSession = session)
        val vm = CashCutViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        vm.hasUnconfirmedSales = { it == session.sessionId }
        vm.performCorteZ()
        assertNull(api.closedSessionRequest)
        assertEquals("OPEN", vm.uiState.value.activeSession?.status)
        assertContains(vm.uiState.value.errorMessage.orEmpty(), "ventas pendientes")
    }

    @Test
    fun testDailyFinancialReportLoadingAndReceiptFormatting() {
        val sampleReport = DailyFinancialReportDto(
            dateEpochMs = 1713139200000L,
            totalSalesCents = 1250000L,       // $12,500.00
            salesCashCents = 950000L,         // $9,500.00
            salesCreditCents = 300000L,       // $3,000.00
            salesOtherCents = 0L,
            totalTicketsCount = 42,
            completedTicketsCount = 40,
            cancelledTicketsCount = 2,
            cancelledAmountCents = 45000L,      // $450.00
            creditPaymentsCashCents = 80000L,  // $800.00
            cashInCents = 0L,
            cashOutCents = 15000L,
            totalCostCents = 750000L,          // $7,500.00
            estimatedGrossProfitCents = 500000L, // $5,000.00
            profitMarginPercent = 40.0,
            activeSessionsCount = 1
        )
        val fakeApi = FakeCashApiClient(activeSession = sampleOpenSession(), dailyReport = sampleReport)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        vm.selectTab(CashCutTab.DAILY_REPORT)

        assertNotNull(vm.uiState.value.dailyReport)
        assertEquals(1250000L, vm.uiState.value.dailyReport?.totalSalesCents)
        assertEquals(500000L, vm.uiState.value.dailyReport?.estimatedGrossProfitCents)
        assertEquals(40.0, vm.uiState.value.dailyReport?.profitMarginPercent)

        // Verify ESC/POS receipt generation
        val printer = EscPosReceiptPrinter(columnWidth = 40)
        val settings = SettingsDto(storeName = "Tienda La Esperanza", storeAddress = "Centro")
        val receiptText = printer.formatDailyReportText(sampleReport, settings)

        assertTrue(receiptText.contains("Tienda La Esperanza"))
        assertTrue(receiptText.contains("REPORTE DIARIO CONSOLIDADO"))
        assertTrue(receiptText.contains("Ventas en Efectivo:"))
        assertTrue(receiptText.contains("$9500.00"))
        assertTrue(receiptText.contains("Ventas a Crédito (Fiado):"))
        assertTrue(receiptText.contains("$3000.00"))
        assertTrue(receiptText.contains("TOTAL VENTAS BRUTAS:"))
        assertTrue(receiptText.contains("$12500.00"))
        assertTrue(receiptText.contains("Costo Reposición:"))
        assertTrue(receiptText.contains("$7500.00"))
        assertTrue(receiptText.contains("GANANCIA BRUTA:"))
        assertTrue(receiptText.contains("$5000.00"))
        assertTrue(receiptText.contains("Margen de Utilidad:"))
        assertTrue(receiptText.contains("40.0%"))
        assertTrue(receiptText.contains("(+) Cobranza Fiado:"))
        assertTrue(receiptText.contains("$800.00"))

        val bytes = printer.generateDailyReportBytes(sampleReport, settings)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size > 100)
    }

    @Test
    fun testOpenNewSessionFlow() {
        val fakeApi = FakeCashApiClient(activeSession = null)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        assertEquals(null, vm.uiState.value.activeSession)

        vm.showOpenSessionDialog(true)
        assertTrue(vm.uiState.value.showOpenSessionDialog)

        vm.onOpenFundTextChanged("600.00")
        vm.openNewSession("user-admin")

        assertEquals(60000L, fakeApi.openedSessionRequest?.initialFundCents)
        assertEquals("user-admin", fakeApi.openedSessionRequest?.userId)
        assertFalse(vm.uiState.value.showOpenSessionDialog)
        assertNotNull(vm.uiState.value.activeSession)
        assertEquals("OPEN", vm.uiState.value.activeSession?.status)
        assertEquals(60000L, vm.uiState.value.activeSession?.expectedCashCents)
    }

    @Test
    fun testDenominationCalculatorUpdatesCountedAndDifference() {
        val sampleSession = CashSessionSummary(
            sessionId = "sess-100",
            registerId = "caja-1",
            registerName = "Caja Principal",
            cashierId = "user-1",
            cashierName = "Don Pepe",
            openedAt = 1000L,
            initialFundCents = 50000L,
            salesCashCents = 100000L,
            creditPaymentsCashCents = 0L,
            cashInCents = 0L,
            cashOutCents = 0L,
            refundsCashCents = 0L,
            expectedCashCents = 150000L, // $1,500.00
            status = "OPEN"
        )
        val fakeApi = FakeCashApiClient(activeSession = sampleSession)
        val vm = CashCutViewModel(fakeApi, CoroutineScope(Dispatchers.Unconfined))

        // Initial reset
        vm.onResetDenominations()
        assertEquals(0L, vm.uiState.value.countedCashCents)
        assertEquals(-150000L, vm.uiState.value.differenceCents)

        // Add 2 billetes de $500 ($1,000.00)
        vm.onDenominationPiecesChanged(denominationCents = 50000L, isCoin = false, pieces = 2)
        assertEquals(100000L, vm.uiState.value.countedCashCents)
        assertEquals(-50000L, vm.uiState.value.differenceCents)

        // Add 5 billetes de $100 ($500.00) -> Total $1,500.00 (Exact match)
        vm.onDenominationPiecesChanged(denominationCents = 10000L, isCoin = false, pieces = 5)
        assertEquals(150000L, vm.uiState.value.countedCashCents)
        assertEquals(0L, vm.uiState.value.differenceCents)

        // Perform Corte Z
        vm.performCorteZ()
        assertNotNull(fakeApi.closedSessionRequest)
        assertEquals(150000L, fakeApi.closedSessionRequest?.countedCashCents)
        assertEquals(2, fakeApi.closedSessionRequest?.denominations?.size)

        // Verify ESC/POS prints denomination breakdown
        val printer = EscPosReceiptPrinter(columnWidth = 40)
        val closedSessionWithDenoms = sampleSession.copy(
            countedCashCents = 150000L,
            status = "CLOSED",
            denominations = fakeApi.closedSessionRequest!!.denominations
        )
        val cutTicket = printer.formatCashCutText(closedSessionWithDenoms, isFinalZCut = true)
        assertTrue(cutTicket.contains("-- DESGLOSE DE EFECTIVO --"))
        assertTrue(cutTicket.contains("BILLETES:"))
        assertTrue(cutTicket.contains("$500.00 x 2"))
        assertTrue(cutTicket.contains("$100.00 x 5"))
    }
}

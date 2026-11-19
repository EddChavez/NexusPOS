package mx.nexuspos.client.features.cash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.cash.OpenSessionRequest

class CashCutViewModel(
    val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val registerId: String = "caja-1"
) {
    private val _uiState = MutableStateFlow(CashCutUiState())
    val uiState: StateFlow<CashCutUiState> = _uiState.asStateFlow()

    // Hardware and print hooks wired from launcher
    var onPrintCashCut: ((session: CashSessionSummary, isFinalZCut: Boolean) -> Unit)? = null
    var onPrintDailyReport: ((report: DailyFinancialReportDto) -> Unit)? = null
    var onOpenCashDrawer: (() -> Unit)? = null
    var cashCutFormatter: ((session: CashSessionSummary, isFinalZCut: Boolean) -> String)? = null
    var dailyReportFormatter: ((report: DailyFinancialReportDto) -> String)? = null
    var onSessionClosedCallback: (() -> Unit)? = null

    init {
        loadCurrentSession()
    }

    fun selectTab(tab: CashCutTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == CashCutTab.DAILY_REPORT && _uiState.value.dailyReport == null) {
            loadDailyReport()
        }
    }

    fun loadCurrentSession(regId: String = registerId) {
        _uiState.update { it.copy(isLoadingSession = true, errorMessage = null) }
        scope.launch {
            val settings = apiClient.getSettings().getOrNull()
            val blindEnabled = settings?.blindCashCountEnabled ?: false

            val res = apiClient.getCurrentSession(regId)
            res.onSuccess { session ->
                _uiState.update {
                    if (session != null) {
                        val initCountedCents = if (blindEnabled) 0L else (if (it.countedCashCents == 0L) session.expectedCashCents else it.countedCashCents)
                        val initCountedText = if (blindEnabled) "" else (if (it.countedCashText.isBlank()) (session.expectedCashCents / 100.0).toString() else it.countedCashText)
                        val diff = if (blindEnabled) 0L else (initCountedCents - session.expectedCashCents)

                        it.copy(
                            activeSession = session,
                            isLoadingSession = false,
                            isBlindCashCountEnabled = blindEnabled,
                            countedCashText = initCountedText,
                            countedCashCents = initCountedCents,
                            differenceCents = diff,
                            denominations = it.denominations.ifEmpty { session.denominations }
                        )
                    } else {
                        it.copy(
                            activeSession = null,
                            isLoadingSession = false,
                            isBlindCashCountEnabled = blindEnabled
                        )
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        activeSession = null,
                        isLoadingSession = false
                    )
                }
            }
        }
    }

    fun onDenominationPiecesChanged(denominationCents: Long, isCoin: Boolean, pieces: Int) {
        _uiState.update { state ->
            val existing = state.denominations.toMutableList()
            val idx = existing.indexOfFirst { it.denominationCents == denominationCents && it.isCoin == isCoin }
            val newEntry = mx.nexuspos.contracts.cash.CashDenominationCountDto(
                denominationCents = denominationCents,
                isCoin = isCoin,
                pieces = pieces
            )
            if (idx >= 0) {
                if (pieces > 0) existing[idx] = newEntry else existing.removeAt(idx)
            } else if (pieces > 0) {
                existing.add(newEntry)
            }

            val totalDenomCents = existing.sumOf { it.totalCents }
            val expected = state.activeSession?.expectedCashCents ?: 0L
            state.copy(
                denominations = existing,
                countedCashCents = totalDenomCents,
                countedCashText = (totalDenomCents / 100.0).toString(),
                differenceCents = totalDenomCents - expected
            )
        }
    }

    fun onResetDenominations() {
        _uiState.update { state ->
            val expected = state.activeSession?.expectedCashCents ?: 0L
            state.copy(
                denominations = emptyList(),
                countedCashCents = 0L,
                countedCashText = "",
                differenceCents = -expected
            )
        }
    }

    fun onCountedCashChanged(text: String) {
        val clean = text.filter { it.isDigit() || it == '.' }
        val cents = ((clean.toDoubleOrNull() ?: 0.0) * 100.0).toLong()
        val expected = _uiState.value.activeSession?.expectedCashCents ?: 0L
        _uiState.update {
            it.copy(
                countedCashText = text,
                countedCashCents = cents,
                differenceCents = cents - expected
            )
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notesText = notes) }
    }

    fun performCorteX() {
        val session = _uiState.value.activeSession
        if (session == null) {
            _uiState.update { it.copy(errorMessage = "No hay ninguna sesión de caja activa para generar Corte X.") }
            return
        }

        // Format ticket text
        val formatted = cashCutFormatter?.invoke(session, false)
            ?: defaultFormatCashCut(session, isFinalZCut = false)

        // Trigger physical print
        onPrintCashCut?.invoke(session, false)

        _uiState.update {
            it.copy(
                ticketPreviewText = formatted,
                ticketPreviewTitle = "Corte X - Arqueo Informativo de Turno",
                successMessage = "Corte X generado e impreso correctamente."
            )
        }
    }

    fun performCorteZ() {
        val session = _uiState.value.activeSession
        if (session == null || session.status != "OPEN") {
            _uiState.update { it.copy(errorMessage = "La sesión actual ya está cerrada o no existe.") }
            return
        }

        val state = _uiState.value
        _uiState.update { it.copy(isClosingSession = true, errorMessage = null) }

        scope.launch {
            val req = CloseSessionRequest(
                countedCashCents = state.countedCashCents,
                notes = state.notesText.trim(),
                denominations = state.denominations
            )
            val res = apiClient.closeSession(session.sessionId, req)
            res.onSuccess { closedSession ->
                // Physical drawer kick
                onOpenCashDrawer?.invoke()

                // Trigger physical printer
                onPrintCashCut?.invoke(closedSession, true)

                // Format receipt preview
                val formatted = cashCutFormatter?.invoke(closedSession, true)
                    ?: defaultFormatCashCut(closedSession, isFinalZCut = true)

                _uiState.update {
                    it.copy(
                        activeSession = closedSession,
                        isClosingSession = false,
                        isSessionClosedJustNow = true,
                        ticketPreviewText = formatted,
                        ticketPreviewTitle = "Corte Z - Cierre Oficial de Turno",
                        successMessage = "¡Corte Z completado! Turno cerrado y ticket oficial impreso."
                    )
                }

                onSessionClosedCallback?.invoke()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isClosingSession = false,
                        errorMessage = "Error al cerrar turno: ${err.message}"
                    )
                }
            }
        }
    }

    fun showOpenSessionDialog(show: Boolean) {
        _uiState.update { it.copy(showOpenSessionDialog = show) }
    }

    fun onOpenFundTextChanged(text: String) {
        _uiState.update { it.copy(openFundText = text) }
    }

    fun openNewSession(userId: String) {
        val fundMoney = (_uiState.value.openFundText.toDoubleOrNull() ?: 0.0) * 100.0
        _uiState.update { it.copy(isOpeningSession = true, errorMessage = null) }
        scope.launch {
            val req = OpenSessionRequest(
                registerId = registerId,
                userId = userId,
                initialFundCents = fundMoney.toLong()
            )
            val res = apiClient.openSession(req)
            res.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        activeSession = session,
                        isOpeningSession = false,
                        showOpenSessionDialog = false,
                        isSessionClosedJustNow = false,
                        countedCashText = (session.expectedCashCents / 100.0).toString(),
                        countedCashCents = session.expectedCashCents,
                        differenceCents = 0L,
                        successMessage = "Turno abierto exitosamente con fondo de $${fundMoney / 100.0}."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isOpeningSession = false,
                        errorMessage = "Error al abrir turno: ${err.message}"
                    )
                }
            }
        }
    }

    fun loadDailyReport(dateEpochMs: Long = _uiState.value.selectedReportDate) {
        _uiState.update { it.copy(isLoadingReport = true, selectedReportDate = dateEpochMs, errorMessage = null) }
        scope.launch {
            val res = apiClient.getDailyReport(dateEpochMs)
            res.onSuccess { report ->
                _uiState.update {
                    it.copy(
                        dailyReport = report,
                        isLoadingReport = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingReport = false,
                        errorMessage = "Error al cargar reporte consolidado: ${err.message}"
                    )
                }
            }
        }
    }

    fun printDailyReportTicket() {
        val report = _uiState.value.dailyReport
        if (report == null) {
            _uiState.update { it.copy(errorMessage = "No hay reporte cargado para imprimir.") }
            return
        }

        val formatted = dailyReportFormatter?.invoke(report)
            ?: defaultFormatDailyReport(report)

        onPrintDailyReport?.invoke(report)

        _uiState.update {
            it.copy(
                ticketPreviewText = formatted,
                ticketPreviewTitle = "Reporte Diario Consolidado",
                successMessage = "Reporte del día enviado a la impresora."
            )
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(ticketPreviewText = null) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    fun triggerCashDrawer() {
        onOpenCashDrawer?.invoke()
        _uiState.update { it.copy(successMessage = "Comando de apertura enviado al cajón de dinero.") }
    }

    private fun defaultFormatCashCut(session: CashSessionSummary, isFinalZCut: Boolean): String {
        val title = if (isFinalZCut) "CORTE Z (CIERRE OFICIAL)" else "CORTE X (ARQUEO INFORMATIVO)"
        return buildString {
            appendLine("========================================")
            appendLine("               NEXUS POS")
            appendLine("        *** $title ***")
            appendLine("========================================")
            appendLine("Sesión ID: #${session.sessionId}")
            appendLine("Cajero: ${session.cashierName}")
            appendLine("Caja: ${session.registerName.ifBlank { "Caja ${session.registerId}" }}")
            appendLine("----------------------------------------")
            appendLine("Fondo Inicial:           $${session.initialFundCents / 100.0}")
            appendLine("(+) Ventas Efectivo:     $${session.salesCashCents / 100.0}")
            appendLine("(+) Abonos Crédito:      $${session.creditPaymentsCashCents / 100.0}")
            appendLine("(+) Entradas:            $${session.cashInCents / 100.0}")
            appendLine("(-) Retiros:             $${session.cashOutCents / 100.0}")
            appendLine("----------------------------------------")
            appendLine("EFECTIVO ESPERADO:       $${session.expectedCashCents / 100.0}")
            session.countedCashCents?.let {
                appendLine("Efectivo Contado:        $${it / 100.0}")
                val diff = session.differenceCents ?: (it - session.expectedCashCents)
                appendLine("Diferencia:              $${diff / 100.0}")
            }
            appendLine("========================================")
        }
    }

    private fun defaultFormatDailyReport(report: DailyFinancialReportDto): String {
        return buildString {
            appendLine("========================================")
            appendLine("               NEXUS POS")
            appendLine("    *** REPORTE DIARIO FINANCIERO ***")
            appendLine("========================================")
            appendLine("Ventas en Efectivo:      $${report.salesCashCents / 100.0}")
            appendLine("Ventas a Crédito Fiado:  $${report.salesCreditCents / 100.0}")
            appendLine("TOTAL VENTAS BRUTAS:     $${report.totalSalesCents / 100.0}")
            appendLine("----------------------------------------")
            appendLine("Costo de Reposición:     $${report.totalCostCents / 100.0}")
            appendLine("GANANCIA BRUTA ESTIMADA: $${report.estimatedGrossProfitCents / 100.0}")
            appendLine("Margen de Utilidad:      ${report.profitMarginPercent}%")
            appendLine("========================================")
        }
    }
}

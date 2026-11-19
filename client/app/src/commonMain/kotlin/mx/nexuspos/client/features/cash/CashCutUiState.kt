package mx.nexuspos.client.features.cash

import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.DailyFinancialReportDto

enum class CashCutTab {
    SHIFT_CUT,      // Arqueo y Corte de Turno
    DAILY_REPORT    // Reporte Financiero del Día
}

data class CashCutUiState(
    val activeTab: CashCutTab = CashCutTab.SHIFT_CUT,
    val activeSession: CashSessionSummary? = null,
    val isLoadingSession: Boolean = false,
    // Conteo y Arqueo
    val countedCashText: String = "",
    val countedCashCents: Long = 0L,
    val differenceCents: Long = 0L, // counted - expected
    val denominations: List<mx.nexuspos.contracts.cash.CashDenominationCountDto> = emptyList(),
    val isBlindCashCountEnabled: Boolean = false,
    val notesText: String = "",
    val isClosingSession: Boolean = false,
    val isSessionClosedJustNow: Boolean = false,
    // Apertura de nuevo turno
    val isOpeningSession: Boolean = false,
    val openFundText: String = "500",
    val showOpenSessionDialog: Boolean = false,
    // Reporte Diario
    val dailyReport: DailyFinancialReportDto? = null,
    val isLoadingReport: Boolean = false,
    val selectedReportDate: Long = System.currentTimeMillis(),
    // Vista Previa de Ticket (Corte X, Corte Z o Reporte Diario)
    val ticketPreviewText: String? = null,
    val ticketPreviewTitle: String = "Vista Previa de Ticket",
    // Feedback
    val successMessage: String? = null,
    val errorMessage: String? = null
)

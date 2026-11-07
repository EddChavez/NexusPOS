package mx.nexuspos.contracts.cash

import kotlinx.serialization.Serializable

@Serializable
data class OpenSessionRequest(
    val registerId: String,
    val userId: String,
    val initialFundCents: Long
)

@Serializable
data class CashMovementRequest(
    val type: String, // CASH_IN, CASH_OUT
    val amountCents: Long,
    val concept: String,
    val userId: String,
    val authorizedByUserId: String? = null
)

@Serializable
data class CloseSessionRequest(
    val countedCashCents: Long,
    val notes: String = ""
)

@Serializable
data class CashSessionSummary(
    val sessionId: String,
    val registerId: String,
    val registerName: String = "",
    val cashierId: String,
    val cashierName: String,
    val openedAt: Long,
    val closedAt: Long? = null,
    val initialFundCents: Long,
    val salesCashCents: Long,
    val creditPaymentsCashCents: Long,
    val cashInCents: Long,
    val cashOutCents: Long,
    val refundsCashCents: Long,
    val expectedCashCents: Long, // initial + salesCash + creditPaymentsCash + cashIn - cashOut - refundsCash
    val countedCashCents: Long? = null,
    val differenceCents: Long? = null, // counted - expected
    val status: String // OPEN, CLOSED
)

@Serializable
data class DailyFinancialReportDto(
    val dateEpochMs: Long,
    val totalSalesCents: Long,
    val salesCashCents: Long,
    val salesCreditCents: Long,
    val salesOtherCents: Long = 0L,
    val totalTicketsCount: Int,
    val completedTicketsCount: Int,
    val cancelledTicketsCount: Int,
    val cancelledAmountCents: Long,
    val creditPaymentsCashCents: Long,
    val cashInCents: Long,
    val cashOutCents: Long,
    val totalCostCents: Long,
    val estimatedGrossProfitCents: Long,
    val profitMarginPercent: Double,
    val activeSessionsCount: Int
)

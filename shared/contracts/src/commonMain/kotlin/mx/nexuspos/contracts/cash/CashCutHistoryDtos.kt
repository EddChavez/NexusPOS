package mx.nexuspos.contracts.cash

import kotlinx.serialization.Serializable

@Serializable
data class CashCutHistoryRequest(
    val pin: String,
    val timeZone: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val registerId: String? = null,
    val cashierId: String? = null,
    val before: CashCutCursor? = null
)

@Serializable
data class CashCutCursor(val closedAt: Long, val sessionId: String)

@Serializable
data class CashCutFilterOption(val id: String, val label: String)

@Serializable
data class CashCutHistoryPage(
    val cuts: List<ClosedCashCut>,
    val registers: List<CashCutFilterOption>,
    val cashiers: List<CashCutFilterOption>,
    val next: CashCutCursor? = null
)

@Serializable
data class ClosedCashCut(
    val sessionId: String,
    val registerId: String,
    val registerName: String,
    val cashierId: String,
    val cashierName: String,
    val openedAt: Long,
    val closedAt: Long,
    val initialFundCents: Long,
    val expectedCashCents: Long,
    val countedCashCents: Long? = null,
    val differenceCents: Long? = null
)

@Serializable
data class CashCutDetailRequest(val pin: String)

@Serializable
data class CashCutHistoryDetail(
    val cut: ClosedCashCut,
    val notes: String = "",
    val denominations: List<CashDenominationCountDto> = emptyList()
)

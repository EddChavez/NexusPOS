package mx.nexuspos.contracts.reports

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
enum class SalesAnalysisGroup { DAY, PRODUCT, DEPARTMENT, CUSTOMER }

@Serializable
data class SalesAnalysisRequest(
    val pin: String,
    val fromDate: String,
    val toDate: String,
    val timeZone: String,
    val groupBy: SalesAnalysisGroup = SalesAnalysisGroup.DAY
)

@Serializable
data class SalesAnalysisTotals(
    val salesCents: Long = 0,
    val cancellationsCents: Long = 0,
    val refundsCents: Long = 0,
    val netSalesCents: Long = 0,
    val costCents: Long = 0,
    val profitCents: Long = 0,
    val estimatedCostEvents: Long = 0
) {
    val marginBasisPoints: Long?
        get() = if (netSalesCents > 0) (profitCents.toDouble() * 10000.0 / netSalesCents).roundToLong() else null
}

@Serializable
data class SalesAnalysisRow(
    val key: String,
    val label: String,
    val sku: String? = null,
    val unit: String? = null,
    val netQuantityMillis: Long? = null,
    val totals: SalesAnalysisTotals
)

@Serializable
data class SalesAnalysisReport(
    val fromDate: String,
    val toDate: String,
    val timeZone: String,
    val groupBy: SalesAnalysisGroup,
    val totals: SalesAnalysisTotals,
    val rows: List<SalesAnalysisRow>
)

package mx.nexuspos.server.modules.reports

import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.Calendar

class ReportsService(private val db: DatabaseFactory) {

    fun getDailyFinancialReport(
        targetEpochMs: Long = System.currentTimeMillis(),
        sessionId: String? = null
    ): DailyFinancialReportDto {
        val cal = Calendar.getInstance().apply {
            timeInMillis = targetEpochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = cal.timeInMillis - 1

        return db.inTransaction { conn ->
            // 1. Completed sales total and count
            val completedSql = """
                SELECT COALESCE(SUM(s.total_cents), 0) as total_sales, COUNT(s.id) as count_completed
                FROM sales s
                WHERE s.status = 'COMPLETED'
                  AND s.created_at >= ? AND s.created_at <= ?
                  AND (? IS NULL OR s.cash_session_id = ?)
            """.trimIndent()
            val compStmt = conn.prepareStatement(completedSql)
            compStmt.setLong(1, startOfDay)
            compStmt.setLong(2, endOfDay)
            compStmt.setString(3, sessionId)
            compStmt.setString(4, sessionId)
            val compRs = compStmt.executeQuery()
            compRs.next()
            val totalSalesCents = compRs.getLong("total_sales")
            val completedTicketsCount = compRs.getInt("count_completed")
            compRs.close()
            compStmt.close()

            // 2. Cancelled sales total and count
            val cancelledSql = """
                SELECT COALESCE(SUM(s.total_cents), 0) as total_cancelled, COUNT(s.id) as count_cancelled
                FROM sales s
                WHERE s.status = 'CANCELLED'
                  AND s.created_at >= ? AND s.created_at <= ?
                  AND (? IS NULL OR s.cash_session_id = ?)
            """.trimIndent()
            val canStmt = conn.prepareStatement(cancelledSql)
            canStmt.setLong(1, startOfDay)
            canStmt.setLong(2, endOfDay)
            canStmt.setString(3, sessionId)
            canStmt.setString(4, sessionId)
            val canRs = canStmt.executeQuery()
            canRs.next()
            val cancelledAmountCents = canRs.getLong("total_cancelled")
            val cancelledTicketsCount = canRs.getInt("count_cancelled")
            canRs.close()
            canStmt.close()

            // 3. Payment methods breakdown (CASH, CREDIT, etc.)
            val paySql = """
                SELECT sp.method, COALESCE(SUM(sp.amount_cents), 0) as method_total
                FROM sale_payments sp
                JOIN sales s ON sp.sale_id = s.id
                WHERE s.status = 'COMPLETED'
                  AND s.created_at >= ? AND s.created_at <= ?
                  AND (? IS NULL OR s.cash_session_id = ?)
                GROUP BY sp.method
            """.trimIndent()
            val payStmt = conn.prepareStatement(paySql)
            payStmt.setLong(1, startOfDay)
            payStmt.setLong(2, endOfDay)
            payStmt.setString(3, sessionId)
            payStmt.setString(4, sessionId)
            val payRs = payStmt.executeQuery()
            var salesCashCents = 0L
            var salesCreditCents = 0L
            var salesOtherCents = 0L
            while (payRs.next()) {
                val method = payRs.getString("method")
                val amount = payRs.getLong("method_total")
                when (method) {
                    "CASH" -> salesCashCents += amount
                    "CREDIT" -> salesCreditCents += amount
                    else -> salesOtherCents += amount
                }
            }
            payRs.close()
            payStmt.close()

            // 4. Credit collections in cash (abonos fiado)
            val credSql = """
                SELECT COALESCE(SUM(amount_cents), 0) as credit_cash
                FROM credit_movements
                WHERE type = 'PAYMENT'
                  AND created_at >= ? AND created_at <= ?
                  AND (? IS NULL OR cash_session_id = ?)
            """.trimIndent()
            val credStmt = conn.prepareStatement(credSql)
            credStmt.setLong(1, startOfDay)
            credStmt.setLong(2, endOfDay)
            credStmt.setString(3, sessionId)
            credStmt.setString(4, sessionId)
            val credRs = credStmt.executeQuery()
            credRs.next()
            val creditPaymentsCashCents = credRs.getLong("credit_cash")
            credRs.close()
            credStmt.close()

            // 5. Cash movements (CASH_IN, CASH_OUT)
            val movSql = """
                SELECT type, COALESCE(SUM(amount_cents), 0) as mov_total
                FROM cash_movements
                WHERE created_at >= ? AND created_at <= ?
                  AND (? IS NULL OR session_id = ?)
                GROUP BY type
            """.trimIndent()
            val movStmt = conn.prepareStatement(movSql)
            movStmt.setLong(1, startOfDay)
            movStmt.setLong(2, endOfDay)
            movStmt.setString(3, sessionId)
            movStmt.setString(4, sessionId)
            val movRs = movStmt.executeQuery()
            var cashInCents = 0L
            var cashOutCents = 0L
            while (movRs.next()) {
                val type = movRs.getString("type")
                val total = movRs.getLong("mov_total")
                if (type == "CASH_IN") cashInCents = total
                if (type == "CASH_OUT") cashOutCents = total
            }
            movRs.close()
            movStmt.close()

            // 6. Cost of Goods Sold (Costo de reposición)
            val costSql = """
                SELECT COALESCE(SUM((sl.quantity_millis * COALESCE(p.cost_cents, 0)) / 1000), 0) as total_cost
                FROM sale_lines sl
                JOIN sales s ON sl.sale_id = s.id
                LEFT JOIN products p ON sl.product_id = p.id
                WHERE s.status = 'COMPLETED'
                  AND s.created_at >= ? AND s.created_at <= ?
                  AND (? IS NULL OR s.cash_session_id = ?)
            """.trimIndent()
            val costStmt = conn.prepareStatement(costSql)
            costStmt.setLong(1, startOfDay)
            costStmt.setLong(2, endOfDay)
            costStmt.setString(3, sessionId)
            costStmt.setString(4, sessionId)
            val costRs = costStmt.executeQuery()
            costRs.next()
            val totalCostCents = costRs.getLong("total_cost")
            costRs.close()
            costStmt.close()

            // 7. Sessions count
            val sessSql = """
                SELECT COUNT(DISTINCT id) as sess_count
                FROM cash_sessions
                WHERE opened_at >= ? AND opened_at <= ?
                  AND (? IS NULL OR id = ?)
            """.trimIndent()
            val sessStmt = conn.prepareStatement(sessSql)
            sessStmt.setLong(1, startOfDay)
            sessStmt.setLong(2, endOfDay)
            sessStmt.setString(3, sessionId)
            sessStmt.setString(4, sessionId)
            val sessRs = sessStmt.executeQuery()
            val activeSessionsCount = if (sessRs.next()) sessRs.getInt("sess_count").coerceAtLeast(1) else 1
            sessRs.close()
            sessStmt.close()

            val estimatedGrossProfitCents = totalSalesCents - totalCostCents
            val profitMarginPercent = if (totalSalesCents > 0L) {
                ((estimatedGrossProfitCents.toDouble() / totalSalesCents.toDouble()) * 10000.0).toLong() / 100.0
            } else 0.0

            DailyFinancialReportDto(
                dateEpochMs = targetEpochMs,
                totalSalesCents = totalSalesCents,
                salesCashCents = salesCashCents,
                salesCreditCents = salesCreditCents,
                salesOtherCents = salesOtherCents,
                totalTicketsCount = completedTicketsCount + cancelledTicketsCount,
                completedTicketsCount = completedTicketsCount,
                cancelledTicketsCount = cancelledTicketsCount,
                cancelledAmountCents = cancelledAmountCents,
                creditPaymentsCashCents = creditPaymentsCashCents,
                cashInCents = cashInCents,
                cashOutCents = cashOutCents,
                totalCostCents = totalCostCents,
                estimatedGrossProfitCents = estimatedGrossProfitCents,
                profitMarginPercent = profitMarginPercent,
                activeSessionsCount = activeSessionsCount
            )
        }
    }
}

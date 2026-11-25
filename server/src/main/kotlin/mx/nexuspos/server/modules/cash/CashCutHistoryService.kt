package mx.nexuspos.server.modules.cash

import mx.nexuspos.contracts.cash.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.sql.ResultSet
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

class CashCutHistoryService(private val db: DatabaseFactory) {
    fun search(request: CashCutHistoryRequest): CashCutHistoryPage {
        val zone = try {
            ZoneId.of(request.timeZone)
        } catch (_: DateTimeException) {
            throw ValidationException("La zona horaria de la consulta no es válida.")
        }
        val from = parseDate(request.fromDate)
        val to = parseDate(request.toDate)
        if (from != null && to != null && from > to) {
            throw ValidationException("La fecha inicial debe ser igual o anterior a la fecha final.")
        }
        val conditions = mutableListOf("s.status = 'CLOSED'", "s.closed_at IS NOT NULL")
        val params = mutableListOf<Any>()
        from?.let {
            conditions.add("s.closed_at >= ?")
            params.add(it.atStartOfDay(zone).toInstant().toEpochMilli())
        }
        to?.let {
            conditions.add("s.closed_at < ?")
            params.add(it.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }
        request.registerId?.let {
            conditions.add("s.register_id = ?")
            params.add(it)
        }
        request.cashierId?.let {
            conditions.add("s.user_id = ?")
            params.add(it)
        }
        request.before?.let {
            conditions.add("(s.closed_at < ? OR (s.closed_at = ? AND s.id < ?))")
            params.add(it.closedAt)
            params.add(it.closedAt)
            params.add(it.sessionId)
        }

        return db.inTransaction { conn ->
            val sql = "${closedCutsQuery()} WHERE ${conditions.joinToString(" AND ")} ORDER BY s.closed_at DESC, s.id DESC LIMIT ?"
            val results = conn.prepareStatement(sql).use { query ->
                params.forEachIndexed { i, value -> query.setObject(i + 1, value) }
                query.setInt(params.size + 1, PAGE_SIZE + 1)
                query.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.closedCut()) } }
            }
            val page = results.take(PAGE_SIZE)
            val next = if (results.size > PAGE_SIZE) page.last().let { CashCutCursor(it.closedAt, it.sessionId) } else null
            CashCutHistoryPage(
                page,
                filterOptions(conn, "s.register_id", "r.name", "cash_registers r ON r.id = s.register_id"),
                filterOptions(conn, "s.user_id", "u.name", "users u ON u.id = s.user_id"),
                next
            )
        }
    }

    fun detail(id: String): CashCutHistoryDetail = db.inTransaction { conn ->
        val cut = conn.prepareStatement(
            "${closedCutsQuery()} WHERE s.id = ? AND s.status = 'CLOSED' AND s.closed_at IS NOT NULL"
        ).use { query ->
            query.setString(1, id)
            query.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("No se encontró el corte cerrado.")
                CashCutHistoryDetail(row.closedCut(), row.getString("notes"))
            }
        }
        val denominations = conn.prepareStatement(
            """
            SELECT denomination_cents, is_coin, pieces, total_cents
            FROM cash_session_denominations WHERE session_id = ?
            ORDER BY is_coin, denomination_cents DESC
            """.trimIndent()
        ).use { query ->
            query.setString(1, id)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(CashDenominationCountDto(rows.getLong(1), rows.getBoolean(2), rows.getInt(3), rows.getLong(4)))
                }
            }
        }
        cut.copy(denominations = denominations)
    }

    private fun filterOptions(conn: Connection, id: String, name: String, join: String): List<CashCutFilterOption> =
        conn.prepareStatement(
            """
            SELECT DISTINCT $id AS option_id, COALESCE(NULLIF($name, ''), $id) AS option_label
            FROM cash_sessions s LEFT JOIN $join WHERE s.status = 'CLOSED' AND s.closed_at IS NOT NULL
            ORDER BY option_label, option_id
            """.trimIndent()
        ).use { query ->
            query.executeQuery().use { rows -> buildList { while (rows.next()) add(CashCutFilterOption(rows.getString(1), rows.getString(2))) } }
        }

    private fun closedCutsQuery(): String = """
        SELECT s.id, s.register_id, COALESCE(NULLIF(r.name, ''), s.register_id) AS register_name,
               s.user_id, COALESCE(NULLIF(u.name, ''), s.user_id) AS cashier_name,
               s.opened_at, s.closed_at, s.initial_fund_cents, s.expected_cash_cents,
               s.counted_cash_cents, s.difference_cents, s.notes
        FROM cash_sessions s
        LEFT JOIN cash_registers r ON r.id = s.register_id
        LEFT JOIN users u ON u.id = s.user_id
    """.trimIndent()

    private fun ResultSet.closedCut() = ClosedCashCut(
        sessionId = getString("id"),
        registerId = getString("register_id"),
        registerName = getString("register_name"),
        cashierId = getString("user_id"),
        cashierName = getString("cashier_name"),
        openedAt = getLong("opened_at"),
        closedAt = getLong("closed_at"),
        initialFundCents = getLong("initial_fund_cents"),
        expectedCashCents = getLong("expected_cash_cents"),
        countedCashCents = getLong("counted_cash_cents").let { if (wasNull()) null else it },
        differenceCents = getLong("difference_cents").let { if (wasNull()) null else it }
    )

    private fun parseDate(text: String?): LocalDate? {
        if (text == null) return null
        return try {
            if (text.length != 10) throw DateTimeException("Expected date in ISO format")
            LocalDate.parse(text)
        } catch (_: DateTimeException) {
            throw ValidationException("Captura una fecha válida con formato AAAA-MM-DD.")
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

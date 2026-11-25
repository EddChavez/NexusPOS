package mx.nexuspos.server.modules.sales

import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.ValidationException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal class TransactionHistoryService(private val db: DatabaseFactory) {
    fun search(
        request: TransactionHistoryRequest,
        zone: ZoneId,
        ownerId: String?,
        includeClosed: Boolean,
        types: Set<TransactionType>
    ): TransactionHistoryPage {
        val today = LocalDate.now(zone)
        val (from, through) = when (request.period) {
            TransactionPeriod.TODAY -> today to today
            TransactionPeriod.YESTERDAY -> today.minusDays(1).let { it to it }
            TransactionPeriod.LAST_SEVEN_DAYS -> today.minusDays(6) to today
            TransactionPeriod.CUSTOM -> parseDate(request.from) to parseDate(request.through)
        }
        if (through < from || through > from.plusYears(1)) {
            throw ValidationException("Captura un rango válido de hasta un año.")
        }
        val term = request.query.trim().take(120).lowercase(Locale.ROOT)
        val pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        val amount = if (term.matches(Regex("""\$?\s*[0-9]+([.,][0-9]{1,2})?"""))) {
            runCatching { BigDecimal(term.removePrefix("$").trim().replace(',', '.')).movePointRight(2).longValueExact() }.getOrNull()
        } else null
        val offset = request.offset.coerceAtLeast(0)
        val searchFields = listOf("CAST(t.folio AS VARCHAR(32))", "t.reference", "t.description", "c.name", "u.name", "r.name")

        return db.inTransaction { connection ->
            val registers = connection.prepareStatement("SELECT id, name FROM cash_registers ORDER BY name, id").use {
                it.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(TransactionRegister(rows.getString("id"), rows.getString("name"))) }
                }
            }
            val selected = if (request.type == null) types else types.intersect(setOf(request.type))
            val sources = transactionSources.filterKeys { selected.contains(it) }.values.distinct()
            if (sources.isEmpty()) return@inTransaction TransactionHistoryPage(emptyList(), registers)
            val clauses = buildList {
                add("t.occurred_at >= ? AND t.occurred_at < ?")
                if (request.type != null) add("t.kind = ?")
                if (ownerId != null) add("t.actor_id = ?")
                if (!includeClosed) add("(t.session_id IS NULL OR s.status = 'OPEN')")
                if (request.registerId != null) add("s.register_id = ?")
                if (term.isNotEmpty()) {
                    val metadata = searchFields.joinToString(" OR ") { "LOWER($it) LIKE ? ESCAPE '!'" }
                    val lines = "EXISTS (SELECT 1 FROM sale_lines l WHERE l.sale_id = t.id AND LOWER(l.description) LIKE ? ESCAPE '!')"
                    val amountFilter = if (amount != null) " OR t.amount_cents = ?" else ""
                    add("($metadata OR (t.kind = 'SALE' AND $lines)$amountFilter)")
                }
            }
            val sql = """
                SELECT t.*, r.name AS register_name, u.name AS operator_name, c.name AS customer_name,
                       s.status AS session_status
                FROM (${sources.joinToString(" UNION ALL ")}) t
                LEFT JOIN cash_sessions s ON s.id = t.session_id
                LEFT JOIN cash_registers r ON r.id = s.register_id
                LEFT JOIN users u ON u.id = t.actor_id
                LEFT JOIN customers c ON c.id = t.customer_id
                WHERE ${clauses.joinToString(" AND ")}
                ORDER BY t.occurred_at DESC, t.kind, t.id DESC LIMIT 51 OFFSET ?
            """.trimIndent()
            val records = connection.prepareStatement(sql).use { statement ->
                var next = 1
                statement.setLong(next++, from.atStartOfDay(zone).toInstant().toEpochMilli())
                statement.setLong(next++, through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                request.type?.let { statement.setString(next++, it.name) }
                ownerId?.let { statement.setString(next++, it) }
                request.registerId?.let { statement.setString(next++, it) }
                if (term.isNotEmpty()) repeat(searchFields.size + 1) { statement.setString(next++, pattern) }
                amount?.let { statement.setLong(next++, it) }
                statement.setInt(next, offset)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val folio = rows.getLong("folio").let { if (rows.wasNull()) null else it }
                            add(TransactionRecord(
                                rows.getString("id"), TransactionType.valueOf(rows.getString("kind")), rows.getLong("occurred_at"),
                                rows.getString("register_name"), rows.getString("operator_name"), rows.getString("description"),
                                rows.getLong("amount_cents"), folio, rows.getString("status"), rows.getString("customer_name"),
                                rows.getString("payment_method"), rows.getString("session_status") == "OPEN",
                                rows.getString("reference")
                            ))
                        }
                    }
                }
            }
            TransactionHistoryPage(records.take(50), registers, (offset + 50).takeIf { records.size > 50 })
        }
    }

    private fun parseDate(value: String?): LocalDate =
        value?.takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: throw ValidationException("Captura las fechas con formato AAAA-MM-DD.")
}

private val salesSource = """
    SELECT 'SALE' AS kind, a.id, a.created_at AS occurred_at, a.cash_session_id AS session_id,
           a.user_id AS actor_id, a.total_cents AS amount_cents,
           (SELECT l.description FROM sale_lines l WHERE l.sale_id = a.id ORDER BY l.id LIMIT 1) AS description,
           a.folio, a.status, a.customer_id, CAST(NULL AS VARCHAR(32)) AS payment_method,
           CAST(NULL AS VARCHAR(32)) AS reference
    FROM sales a
""".trimIndent()

private val movementsSource = """
    SELECT m.type AS kind, m.id, m.created_at AS occurred_at, m.session_id, m.user_id AS actor_id,
           m.amount_cents, m.concept AS description, CAST(NULL AS BIGINT) AS folio,
           CASE WHEN m.reverses_movement_id IS NOT NULL THEN 'REVERSAL'
                WHEN EXISTS (SELECT 1 FROM cash_movements reversal WHERE reversal.reverses_movement_id = m.id) THEN 'REVERSED' END AS status,
           CAST(NULL AS VARCHAR(64)) AS customer_id, CAST(NULL AS VARCHAR(32)) AS payment_method,
           CAST(NULL AS VARCHAR(32)) AS reference
    FROM cash_movements m WHERE m.type IN ('CASH_IN', 'CASH_OUT')
""".trimIndent()

private val transactionSources = mapOf(
    TransactionType.SALE to salesSource,
    TransactionType.CASH_IN to movementsSource,
    TransactionType.CASH_OUT to movementsSource,
    TransactionType.OPENING_FUND to """
        SELECT 'OPENING_FUND' AS kind, s.id, s.opened_at AS occurred_at, s.id AS session_id, s.user_id AS actor_id,
               s.initial_fund_cents AS amount_cents, 'Fondo inicial' AS description, CAST(NULL AS BIGINT) AS folio,
               CAST(NULL AS VARCHAR(32)) AS status, CAST(NULL AS VARCHAR(64)) AS customer_id,
               CAST(NULL AS VARCHAR(32)) AS payment_method, CAST(NULL AS VARCHAR(32)) AS reference
        FROM cash_sessions s
    """.trimIndent(),
    TransactionType.CREDIT_PAYMENT to """
        SELECT 'CREDIT_PAYMENT' AS kind, p.id, p.created_at AS occurred_at, p.cash_session_id AS session_id,
               p.user_id AS actor_id, p.amount_cents, p.description, CAST(NULL AS BIGINT) AS folio,
               CAST(NULL AS VARCHAR(32)) AS status, p.customer_id, p.payment_method, p.receipt_folio AS reference
        FROM credit_movements p WHERE p.type = 'PAYMENT'
    """.trimIndent()
)

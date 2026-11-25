package mx.nexuspos.server.modules.mobile

import mx.nexuspos.contracts.mobile.*
import mx.nexuspos.contracts.reports.SalesAnalysisRequest
import mx.nexuspos.server.modules.catalog.CatalogService
import mx.nexuspos.server.modules.reports.SalesAnalysisService
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneId

internal class MobileQueries(
    private val db: DatabaseFactory,
    private val catalog: CatalogService,
    private val sales: SalesAnalysisService,
    private val fallbackZone: ZoneId = ZoneId.of(System.getenv("NEXUS_STORE_TIMEZONE") ?: "America/Mexico_City")
) {
    fun overview(): MobileOverview {
        val zone = storeZone()
        val date = LocalDate.now(zone).toString()
        val report = sales.report(SalesAnalysisRequest("", date, date, zone.id))
        return MobileOverview(date, report.totals.netSalesCents, db.inTransaction(::registers))
    }

    fun products(query: String?, barcode: String?): List<MobileProduct> {
        val term = query?.trim()?.takeIf { it.isNotEmpty() }
        val code = barcode?.trim()?.takeIf { it.isNotEmpty() }
        if (term == null && code == null) return emptyList()
        if ((term?.length ?: 0) > 100 || (code?.length ?: 0) > 100) throw ValidationException("La búsqueda es demasiado larga.")
        return catalog.searchProducts(barcode = code, query = term).mapNotNull { product ->
            val exact = (code ?: term)?.let { value -> product.presentations.firstOrNull { it.barcode == value } }
            if (code != null && exact == null) return@mapNotNull null
            MobileProduct(product.id, product.name, product.sku, product.baseUnit,
                product.currentStockMillis.takeIf { product.usesInventory }, product.presentations, exact?.id)
        }
    }

    fun shifts(dateText: String?, register: String?): MobileShifts {
        val zone = storeZone()
        val date = if (dateText == null) LocalDate.now(zone) else {
            if (!dateText.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) throw ValidationException("Usa la fecha AAAA-MM-DD.")
            runCatching { LocalDate.parse(dateText) }.getOrElse { throw ValidationException("Captura una fecha válida.") }
        }
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.inTransaction { conn ->
            val rows = conn.prepareStatement(
                """
                $shiftSql WHERE s.opened_at < ? AND (s.closed_at IS NULL OR s.closed_at >= ?)
                ${if (register.isNullOrBlank()) "" else "AND s.register_id = ?"}
                ORDER BY s.opened_at DESC, s.id DESC
                """.trimIndent()
            ).use {
                it.setLong(1, end)
                it.setLong(2, start)
                if (!register.isNullOrBlank()) it.setString(3, register)
                it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.shift()) } }
            }
            MobileShifts(date.toString(), registers(conn), rows)
        }
    }

    fun shiftSales(id: String, folio: Long?, before: Long?): MobileShiftSales = db.inTransaction { conn ->
        val shift = conn.prepareStatement("$shiftSql WHERE s.id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs ->
                if (!rs.next()) throw NotFoundException("No se encontró el turno.")
                rs.shift()
            }
        }
        val salesRows = conn.prepareStatement(
            """
            SELECT folio, total_cents, status FROM sales WHERE cash_session_id = ?
            ${if (folio == null) "" else "AND folio = ?"}
            ${if (before == null) "" else "AND folio < ?"}
            ORDER BY folio DESC LIMIT 31
            """.trimIndent()
        ).use {
            var i = 1
            it.setString(i++, id)
            if (folio != null) it.setLong(i++, folio)
            if (before != null) it.setLong(i, before)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(MobileSale(rs.getLong(1), rs.getLong(2), rs.getString(3))) } }
        }
        MobileShiftSales(shift, salesRows.take(30), salesRows.getOrNull(29)?.folio?.takeIf { salesRows.size > 30 })
    }

    private fun storeZone(): ZoneId = db.inTransaction { connection ->
        connection.prepareStatement("SELECT setting_value FROM settings WHERE setting_key = 'store_time_zone'").use {
            it.executeQuery().use { row -> if (row.next()) ZoneId.of(row.getString(1)) else fallbackZone }
        }
    }

    private fun registers(conn: Connection): List<MobileRegister> =
        conn.prepareStatement(
            """
            SELECT r.id, r.name, EXISTS(SELECT 1 FROM cash_sessions s WHERE s.register_id = r.id AND s.status = 'OPEN')
            FROM cash_registers r ORDER BY r.name, r.id
            """.trimIndent()
        ).use { it.executeQuery().use { rs -> buildList { while (rs.next()) add(MobileRegister(rs.getString(1), rs.getString(2), rs.getBoolean(3))) } } }

    private fun ResultSet.number(name: String): Long? = getLong(name).let { if (wasNull()) null else it }

    private fun ResultSet.shift(): MobileShift {
        val closed = getString("status") == "CLOSED"
        return MobileShift(getString("id"), getString("register_id"), getString("register_name"), getString("cashier"),
            getLong("opened_at"), number("closed_at"),
            if (closed) number("expected_cash_cents") else null,
            if (closed) number("counted_cash_cents") else null,
            if (closed) number("difference_cents") else null)
    }

    private companion object {
        val shiftSql = """
            SELECT s.id, s.register_id, r.name AS register_name, COALESCE(u.name, s.user_id) AS cashier,
                s.opened_at, s.closed_at, s.status, s.expected_cash_cents, s.counted_cash_cents, s.difference_cents
            FROM cash_sessions s JOIN cash_registers r ON r.id = s.register_id
            LEFT JOIN users u ON u.id = s.user_id
        """.trimIndent()
    }
}

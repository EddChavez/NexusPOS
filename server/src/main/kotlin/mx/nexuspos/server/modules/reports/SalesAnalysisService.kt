package mx.nexuspos.server.modules.reports

import mx.nexuspos.contracts.reports.*
import mx.nexuspos.server.modules.sales.roundSaleCost
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.ValidationException
import java.math.BigInteger
import java.sql.ResultSet
import java.sql.Connection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SalesAnalysisService(private val db: DatabaseFactory) {
    fun report(request: SalesAnalysisRequest): SalesAnalysisReport {
        val (from, to, zone) = try {
            require(request.fromDate.length == 10 && request.toDate.length == 10)
            Triple(LocalDate.parse(request.fromDate), LocalDate.parse(request.toDate), ZoneId.of(request.timeZone))
        } catch (_: DateTimeException) {
            throw ValidationException("Indica fechas AAAA-MM-DD y una zona horaria válida.")
        } catch (_: IllegalArgumentException) {
            throw ValidationException("Indica fechas AAAA-MM-DD.")
        }
        if (from > to) throw ValidationException("La fecha inicial debe ser igual o anterior a la fecha final.")
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return db.inTransaction { conn ->
            val grand = Totals()
            val groups = linkedMapOf<String, AnalysisBucket>()
            conn.prepareStatement(ledgerSql).use { query ->
                repeat(3) { index ->
                    query.setLong(index * 2 + 1, start)
                    query.setLong(index * 2 + 2, endExclusive)
                }
                query.fetchSize = 500
                query.executeQuery().use { rows ->
                    while (rows.next()) {
                        val event = rows.entry()
                        val key = event.key(request.groupBy, zone)
                        val bucket = groups.getOrPut(key) { AnalysisBucket() }
                        bucket.accept(event, request.groupBy, zone)
                        grand.accept(event)
                    }
                }
            }
            val stock = if (request.groupBy == SalesAnalysisGroup.ROTATION) stockForPeriod(conn, start, endExclusive) else emptyMap()
            val rows = groups.map { (key, bucket) ->
                val row = SalesAnalysisRow(key, bucket.label, bucket.sku, bucket.unit, bucket.quantity.takeIf { bucket.unit != null }, bucket.totals.result())
                val inventory = stock[bucket.productId]
                if (inventory == null) row else row.withStock(inventory)
            }
            val salesOrder = compareByDescending<SalesAnalysisRow> { it.totals.netSalesCents }.thenBy { it.label }.thenBy { it.key }
            val sorted = when (request.groupBy) {
                SalesAnalysisGroup.DAY, SalesAnalysisGroup.HOUR -> rows.sortedBy { it.key }
                SalesAnalysisGroup.PARETO -> {
                    val ranking = rows.filter { it.totals.netSalesCents > 0 }.sortedWith(salesOrder)
                    val positiveTotal = ranking.fold(BigInteger.ZERO) { sum, row -> sum + BigInteger.valueOf(row.totals.netSalesCents) }
                    var cumulative = BigInteger.ZERO
                    ranking.map {
                        cumulative += BigInteger.valueOf(it.totals.netSalesCents)
                        it.copy(cumulativeShareBasisPoints = basisPoints(cumulative, positiveTotal))
                    }
                }
                SalesAnalysisGroup.ROTATION -> {
                    val sold = groups.values.mapNotNull { it.productId }.toSet()
                    val withoutSales = stock.values.filter { it.id !in sold && (it.consumed != 0L || (it.active && it.average > 0)) }.map {
                        SalesAnalysisRow("stock:${it.id}", it.name, it.sku, it.unit, 0, SalesAnalysisTotals()).withStock(it)
                    }
                    (rows.filter { it.averageStockMillis != null } + withoutSales)
                        .sortedWith(compareByDescending<SalesAnalysisRow> { it.turnoverBasisPoints ?: -1L }.thenBy { it.label }.thenBy { it.key })
                }
                else -> rows.sortedWith(salesOrder)
            }
            SalesAnalysisReport(request.fromDate, request.toDate, zone.id, request.groupBy, grand.result(), sorted)
        }
    }

    private data class PeriodStock(
        val id: String, val name: String, val sku: String, val unit: String,
        val active: Boolean, val average: Long, val consumed: Long
    )

    private fun stockForPeriod(connection: Connection, start: Long, end: Long): Map<String, PeriodStock> =
        connection.prepareStatement(
            """
            SELECT p.id, p.name, p.sku, p.base_unit, p.is_active,
                   COALESCE(b.quantity_millis, 0) - COALESCE(m.since_start, 0) AS opening,
                   COALESCE(b.quantity_millis, 0) - COALESCE(m.since_end, 0) AS closing,
                   COALESCE(m.consumed, 0) AS consumed
            FROM products p LEFT JOIN stock_balances b ON b.product_id = p.id
            LEFT JOIN (
                SELECT product_id, SUM(quantity_millis) AS since_start,
                       SUM(CASE WHEN created_at >= ? THEN quantity_millis ELSE 0 END) AS since_end,
                       -SUM(CASE WHEN created_at < ? AND type IN ('SALE', 'REFUND') THEN quantity_millis ELSE 0 END) AS consumed
                FROM stock_movements WHERE created_at >= ? GROUP BY product_id
            ) m ON m.product_id = p.id
            WHERE p.uses_inventory = TRUE
            """.trimIndent()
        ).use {
            it.setLong(1, end)
            it.setLong(2, end)
            it.setLong(3, start)
            it.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        val opening = rs.getLong("opening")
                        val closing = rs.getLong("closing")
                        if (opening < 0 || closing < 0) continue
                        val average = opening / 2 + closing / 2 + (opening % 2 + closing % 2) / 2
                        val id = rs.getString("id")
                        put(id, PeriodStock(
                            id, rs.getString("name"), rs.getString("sku"), rs.getString("base_unit"),
                            rs.getBoolean("is_active"), average, rs.getLong("consumed")
                        ))
                    }
                }
            }
        }

    private fun SalesAnalysisRow.withStock(inventory: PeriodStock) = copy(
        averageStockMillis = inventory.average,
        stockConsumedMillis = inventory.consumed,
        turnoverBasisPoints = if (inventory.average > 0) basisPoints(
            BigInteger.valueOf(inventory.consumed.coerceAtLeast(0)), BigInteger.valueOf(inventory.average)
        ) else null
    )

    private fun basisPoints(amount: BigInteger, total: BigInteger): Long =
        BigDecimal(amount).movePointRight(4).divide(BigDecimal(total), 0, RoundingMode.HALF_UP)
            .toBigInteger().min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()

    private fun ResultSet.entry(): ReportEvent {
        val kind = getString("event_kind")
        val factor = getLong("factor")
        val sold = getLong("sold_qty")
        val units = getLong("event_qty")
        val snapshot = getLong("cost_snapshot_cents").let { if (wasNull()) null else it }
        val legacyCost = if (snapshot == null) {
            val cost = getLong("catalog_cost")
            roundSaleCost(BigInteger.valueOf(baseQuantity(sold, factor)) * BigInteger.valueOf(cost), 1000)
        } else snapshot
        val costEffect = when (kind) {
            "RETURN" -> {
                if (!getBoolean("restocked")) 0L else {
                    val previous = getLong("restocked_before")
                    val cumulative = BigInteger.valueOf(legacyCost)
                    roundSaleCost(cumulative * BigInteger.valueOf(previous + units), sold) -
                        roundSaleCost(cumulative * BigInteger.valueOf(previous), sold)
                }
            }
            else -> legacyCost
        }
        val quantity = if (kind == "RETURN") {
            val previous = getLong("returned_before")
            baseQuantity(previous + units, factor) - baseQuantity(previous, factor)
        } else baseQuantity(units, factor)
        return ReportEvent(
            kind, getLong("posted_at"), getString("product_id"), getString("product_name"), getString("product_sku"),
            getString("product_unit"), getString("department_id"), getString("department_name"),
            quantity, getLong("net_amount"), costEffect,
            snapshot == null && (kind != "RETURN" || getBoolean("restocked")), getString("customer_id"), getString("customer_name")
        )
    }

    private fun baseQuantity(amount: Long, factor: Long): Long =
        (BigInteger.valueOf(amount) * BigInteger.valueOf(factor) / BigInteger.valueOf(1000)).longValueExact()

    private data class ReportEvent(
        val kind: String, val date: Long, val productId: String?, val product: String, val sku: String?, val unit: String,
        val departmentId: String?, val department: String?, val quantity: Long, val amount: Long, val cost: Long, val estimated: Boolean,
        val customerId: String?, val customerName: String?
    ) {
        fun localDay(zone: ZoneId): String = Instant.ofEpochMilli(date).atZone(zone).toLocalDate().toString()
        fun localHour(zone: ZoneId): String = Instant.ofEpochMilli(date).atZone(zone).hour.toString().padStart(2, '0')

        fun key(group: SalesAnalysisGroup, zone: ZoneId): String = when (group) {
            SalesAnalysisGroup.DAY -> localDay(zone)
            SalesAnalysisGroup.HOUR -> localHour(zone)
            SalesAnalysisGroup.PRODUCT, SalesAnalysisGroup.PARETO, SalesAnalysisGroup.ROTATION ->
                "${productId?.let { "product:$it" } ?: "manual:$product"}:$unit"
            SalesAnalysisGroup.DEPARTMENT -> departmentId ?: ""
            SalesAnalysisGroup.CUSTOMER -> customerId ?: ""
        }
    }

    private class AnalysisBucket {
        val totals = Totals()
        var label = ""
        var sku: String? = null
        var unit: String? = null
        var productId: String? = null
        var quantity = 0L

        fun accept(event: ReportEvent, group: SalesAnalysisGroup, zone: ZoneId) {
            label = when (group) {
                SalesAnalysisGroup.DAY -> event.localDay(zone)
                SalesAnalysisGroup.HOUR -> "${event.localHour(zone)}:00–${event.localHour(zone)}:59"
                SalesAnalysisGroup.PRODUCT, SalesAnalysisGroup.PARETO, SalesAnalysisGroup.ROTATION -> event.product
                SalesAnalysisGroup.DEPARTMENT -> event.department ?: "Sin departamento"
                SalesAnalysisGroup.CUSTOMER -> event.customerName ?: "Público general"
            }
            if (group in setOf(SalesAnalysisGroup.PRODUCT, SalesAnalysisGroup.PARETO, SalesAnalysisGroup.ROTATION)) {
                productId = event.productId
                sku = event.sku
                unit = event.unit
                quantity = checkedTotal(quantity, if (event.kind == "SALE") event.quantity else -event.quantity)
            }
            totals.accept(event)
        }
    }

    private class Totals {
        var sales = 0L
        var cancelled = 0L
        var refunded = 0L
        var cost = 0L
        var estimated = 0L

        fun accept(event: ReportEvent) {
            when (event.kind) {
                "SALE" -> sales = checkedTotal(sales, event.amount)
                "CANCEL" -> cancelled = checkedTotal(cancelled, event.amount)
                "RETURN" -> refunded = checkedTotal(refunded, event.amount)
            }
            cost = checkedTotal(cost, if (event.kind == "SALE") event.cost else -event.cost)
            if (event.estimated) estimated++
        }

        fun result(): SalesAnalysisTotals {
            val net = checkedTotal(checkedTotal(sales, -cancelled), -refunded)
            return SalesAnalysisTotals(sales, cancelled, refunded, net, cost, checkedTotal(net, -cost), estimated)
        }
    }

    private companion object {
        fun checkedTotal(a: Long, b: Long): Long = try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            throw ValidationException("Los importes del periodo exceden el límite. Consulta un intervalo más corto.")
        }

        val ledgerSql = """
            WITH events AS (
                SELECT 'SALE' AS event_kind, s.created_at AS posted_at, line.id AS line_id,
                       line.quantity_millis AS event_qty, line.total_cents - line.tax_cents AS net_amount,
                       FALSE AS restocked, CAST(0 AS BIGINT) AS restocked_before, CAST(0 AS BIGINT) AS returned_before
                FROM sales s JOIN sale_lines line ON line.sale_id = s.id
                WHERE s.created_at >= ? AND s.created_at < ? AND (s.status <> 'CANCELLED' OR s.cancelled_at IS NOT NULL)
                UNION ALL
                SELECT 'CANCEL', s.cancelled_at, line.id, line.quantity_millis, line.total_cents - line.tax_cents, FALSE, 0, 0
                FROM sales s JOIN sale_lines line ON line.sale_id = s.id
                WHERE s.status = 'CANCELLED' AND s.cancelled_at >= ? AND s.cancelled_at < ?
                UNION ALL
                SELECT 'RETURN', r.created_at, rl.sale_line_id, rl.quantity_millis, rl.total_cents - rl.tax_cents, rl.reenter_stock,
                       CASE WHEN rl.reenter_stock THEN COALESCE((
                           SELECT SUM(prior.quantity_millis) FROM sale_refund_lines prior
                           JOIN sale_refunds prior_refund ON prior_refund.id = prior.refund_id
                           WHERE prior.sale_line_id = rl.sale_line_id AND prior.reenter_stock AND prior_refund.folio < r.folio
                       ), 0) ELSE 0 END,
                       COALESCE((
                           SELECT SUM(prior.quantity_millis) FROM sale_refund_lines prior
                           JOIN sale_refunds prior_refund ON prior_refund.id = prior.refund_id
                           WHERE prior.sale_line_id = rl.sale_line_id AND prior_refund.folio < r.folio
                       ), 0)
                FROM sale_refunds r JOIN sale_refund_lines rl ON rl.refund_id = r.id
                WHERE r.created_at >= ? AND r.created_at < ?
            )
            SELECT e.*, sl.product_id, sl.quantity_millis AS sold_qty, sl.cost_snapshot_cents,
                   COALESCE(sl.base_factor_millis, pp.factor_millis, 1000) AS factor,
                   COALESCE(sl.snapshot_product_name, p.name, sl.description) AS product_name,
                   COALESCE(sl.snapshot_sku, p.sku) AS product_sku,
                   COALESCE(sl.snapshot_base_unit, p.base_unit, 'PZA') AS product_unit,
                   CASE WHEN sl.base_factor_millis IS NOT NULL THEN sl.snapshot_department_id ELSE p.department_id END AS department_id,
                   CASE WHEN sl.base_factor_millis IS NOT NULL THEN sl.snapshot_department_name ELSE d.name END AS department_name,
                   COALESCE(p.cost_cents, 0) AS catalog_cost, s.customer_id, c.name AS customer_name
            FROM events e
            JOIN sale_lines sl ON sl.id = e.line_id
            JOIN sales s ON s.id = sl.sale_id
            LEFT JOIN customers c ON c.id = s.customer_id
            LEFT JOIN products p ON p.id = sl.product_id
            LEFT JOIN product_presentations pp ON pp.id = sl.presentation_id
            LEFT JOIN departments d ON d.id = p.department_id
            ORDER BY e.posted_at, e.event_kind, sl.id
        """.trimIndent()
    }
}

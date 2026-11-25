package mx.nexuspos.server

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ClosedShiftSalesTest {
    @Test
    fun closedShiftReadsRequireSupervisorAndShowPaymentsCancellationAndRefunds() = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:shift_sales_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val origin = openShift(http)
        val cancelled = sell(http, origin.sessionId, method = "CARD")
        val refundedLater = sell(http, origin.sessionId, method = "CASH")
        val openApi = http.get("/api/v1/sales?sessionId=${origin.sessionId}")
        assertEquals(HttpStatusCode.OK, openApi.status)
        assertEquals(2, openApi.body<List<SaleResponse>>().size)
        val originPath = historyPath(origin.sessionId)
        assertEquals(HttpStatusCode.NotFound, search(http, originPath).status)
        assertEquals(HttpStatusCode.OK, http.post("/api/v1/sales/${cancelled.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest("Cobro duplicado", "usr-admin"))
        }.status)
        val closed = close(http, origin.sessionId)
        assertEquals(1200L, closed.expectedCashCents)
        val later = openShift(http)
        val laterSale = sell(http, later.sessionId, method = "CASH")

        assertEquals(HttpStatusCode.Forbidden, search(http, originPath, ClosedShiftSalesRequest("0000")).status)
        assertEquals(HttpStatusCode.Forbidden, detail(http, originPath, cancelled.id, "0000").status)
        assertEquals(HttpStatusCode.Forbidden, http.get("/api/v1/sales?sessionId=${origin.sessionId}").status)
        assertEquals(HttpStatusCode.Forbidden, http.get("/api/v1/sales/${cancelled.id}").status)
        assertEquals(HttpStatusCode.Forbidden, http.get("/api/v1/sales/${cancelled.id}/refunds").status)
        assertTrue(http.get("/api/v1/sales?folio=${cancelled.folio}").body<List<SaleResponse>>().isEmpty())
        assertEquals(listOf(laterSale.id), http.get("/api/v1/sales").body<List<SaleResponse>>().map { it.id })
        assertEquals(HttpStatusCode.NotFound, detail(http, originPath, laterSale.id).status)

        val cancellation = detail(http, originPath, cancelled.id).body<ClosedShiftSaleDetail>()
        assertEquals("CANCELLED", cancellation.sale.status)
        assertEquals("Cobro duplicado", cancellation.cancellation?.reason)
        assertEquals("CARD", cancellation.sale.payments.single().method)
        assertEquals("REF-123", cancellation.sale.payments.single().reference)
        assertEquals(HttpStatusCode.Conflict, http.post("/api/v1/sales/${refundedLater.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest("Turno cerrado", "usr-admin"))
        }.status)

        val refund = CreateSaleRefundRequest(
            cashSessionId = later.sessionId, cashierUserId = "usr-admin", reason = "DEFECTO",
            refundMethod = "CASH", lines = listOf(RefundLineItemRequest(refundedLater.lines.single().id, 1000, reenterStock = false))
        )
        suspend fun submitRefund(pin: String?) = http.post("/api/v1/sales/${refundedLater.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(refund.copy(supervisorPin = pin))
        }
        assertEquals(HttpStatusCode.BadRequest, submitRefund(null).status)
        assertEquals(HttpStatusCode.Created, submitRefund("1234").status)
        val located = search(http, originPath, ClosedShiftSalesRequest("1234", folio = refundedLater.folio)).body<ClosedShiftSalesPage>()
        assertEquals(listOf(refundedLater.id), located.items.map { it.id })
        assertEquals("REFUNDED", located.items.single().status)
        assertTrue(search(http, originPath, ClosedShiftSalesRequest("1234", folio = laterSale.folio)).body<ClosedShiftSalesPage>().items.isEmpty())
        val refundDetail = detail(http, originPath, refundedLater.id).body<ClosedShiftSaleDetail>()
        assertEquals(later.sessionId, refundDetail.sale.refunds.single().cashSessionId)
        assertEquals(1200L, refundDetail.sale.refunds.single().totalCents)
        assertEquals(closed.expectedCashCents, http.post("/api/v1/cash-sessions/history/${origin.sessionId}") {
            contentType(ContentType.Application.Json)
            setBody(CashCutDetailRequest("1234"))
        }.body<CashCutHistoryDetail>().cut.expectedCashCents)

        db.inTransaction { conn ->
            conn.prepareStatement("UPDATE sales SET cancellation_reason = NULL, cancelled_at = NULL WHERE id = ?").use {
                it.setString(1, cancelled.id)
                it.executeUpdate()
            }
        }
        val legacy = detail(http, originPath, cancelled.id).body<ClosedShiftSaleDetail>()
        assertEquals("CANCELLED", legacy.sale.status)
        assertNull(legacy.cancellation)

        db.inTransaction { conn ->
            conn.prepareStatement(
                """
                INSERT INTO sales (id, folio, cash_session_id, user_id, status, subtotal_cents, tax_cents, total_cents, idempotency_key, created_at)
                VALUES (?, ?, ?, 'usr-cajero', 'COMPLETED', 1200, 0, 1200, ?, 1795500000000)
                """.trimIndent()
            ).use {
                for (folio in 4001..4031) {
                    val key = "page-$folio"
                    it.setString(1, key)
                    it.setInt(2, folio)
                    it.setString(3, origin.sessionId)
                    it.setString(4, key)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }
        val first = search(http, originPath).body<ClosedShiftSalesPage>()
        assertEquals((4031L downTo 4002L).toList(), first.items.map { it.folio })
        assertEquals(4002L, first.nextBeforeFolio)
        val second = search(http, originPath, ClosedShiftSalesRequest("1234", beforeFolio = first.nextBeforeFolio))
            .body<ClosedShiftSalesPage>()
        assertEquals(listOf(4001L, refundedLater.folio, cancelled.folio), second.items.map { it.folio })
        assertNull(second.nextBeforeFolio)
    }

    private fun historyPath(id: String) = "/api/v1/cash-sessions/history/$id/sales"

    private suspend fun search(http: HttpClient, path: String, data: ClosedShiftSalesRequest = ClosedShiftSalesRequest("1234")) =
        http.post(path) {
            contentType(ContentType.Application.Json)
            setBody(data)
        }

    private suspend fun detail(http: HttpClient, path: String, id: String, pin: String = "1234") =
        http.post("$path/$id") {
            contentType(ContentType.Application.Json)
            setBody(CashCutDetailRequest(pin))
        }

    private suspend fun openShift(http: HttpClient): CashSessionSummary = http.post("/api/v1/cash-sessions") {
        contentType(ContentType.Application.Json)
        setBody(OpenSessionRequest("caja-1", "usr-cajero", 0))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun close(http: HttpClient, id: String): CashSessionSummary = http.post("/api/v1/cash-sessions/$id/close") {
        contentType(ContentType.Application.Json)
        setBody(CloseSessionRequest(1200))
    }.also { assertEquals(HttpStatusCode.OK, it.status) }.body()

    private suspend fun sell(http: HttpClient, sessionId: String, method: String): SaleResponse = http.post("/api/v1/sales") {
        contentType(ContentType.Application.Json)
        setBody(CreateSaleRequest(
            UUID.randomUUID().toString(), sessionId, "usr-cajero",
            lines = listOf(SaleLineItemRequest(description = "Venta rápida", quantityMillis = 1000,
                expectedUnitPriceCents = 1200, expectedTotalCents = 1200)),
            payments = listOf(SalePaymentRequest(method, 1200, reference = if (method == "CARD") "REF-123" else "")),
            expectedTotalCents = 1200
        ))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()
}

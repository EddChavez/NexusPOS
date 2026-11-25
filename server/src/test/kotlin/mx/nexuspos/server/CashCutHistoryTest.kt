package mx.nexuspos.server

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class CashCutHistoryTest {
    @Test
    fun filtersUseTheLocalClosingDayAndExcludeOpenSessions() = historyTest { db, client ->
        val start = Instant.parse("2026-11-24T06:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-11-25T06:00:00Z").toEpochMilli()
        db.inTransaction { conn ->
            conn.prepareStatement("INSERT INTO cash_registers (id, name) VALUES ('history-second', 'Caja secundaria')").use { it.executeUpdate() }
        }
        insertCut(db, "start", "caja-1", "usr-cajero", start)
        insertCut(db, "just-before", "caja-1", "usr-cajero", start - 1)
        insertCut(db, "end", "caja-1", "usr-cajero", end)
        insertCut(db, "last", "caja-1", "usr-admin", end - 1)
        insertCut(db, "another-register", "history-second", "usr-cajero", start + 1)
        insertCut(db, "open", "caja-1", "usr-cajero", null)

        val filter = CashCutHistoryRequest(
            "1234", "America/Mexico_City", "2026-11-24", "2026-11-24",
            registerId = "caja-1", cashierId = "usr-cajero"
        )
        val filtered = search(client, filter).body<CashCutHistoryPage>()
        assertEquals(listOf("start"), filtered.cuts.map { it.sessionId })
        assertEquals(setOf("history-second", "caja-1"), filtered.registers.map { it.id }.toSet())
        assertEquals(setOf("usr-cajero", "usr-admin"), filtered.cashiers.map { it.id }.toSet())

        val day = search(client, filter.copy(registerId = null, cashierId = null)).body<CashCutHistoryPage>()
        assertEquals(listOf("last", "another-register", "start"), day.cuts.map { it.sessionId })
        assertEquals(HttpStatusCode.BadRequest, search(client, filter.copy(fromDate = "2026-11-25")).status)
        assertEquals(HttpStatusCode.BadRequest, search(client, filter.copy(toDate = "2026-02-30")).status)
        assertEquals(HttpStatusCode.BadRequest, search(client, filter.copy(timeZone = "invalid-zone")).status)
        assertEquals(HttpStatusCode.Forbidden, search(client, filter.copy(pin = "0000")).status)
        assertEquals(HttpStatusCode.Forbidden, detail(client, "start", "0000").status)
        assertEquals(HttpStatusCode.NotFound, detail(client, "open").status)
    }

    @Test
    fun paginationStaysStableWhenAnotherCutClosesAndUsesTheIdToBreakTies() = historyTest { db, client ->
        val timestamp = 1795500000000L
        repeat(31) { i -> insertCut(db, "history-${i.toString().padStart(2, '0')}", "caja-1", "usr-cajero", timestamp) }
        val query = CashCutHistoryRequest("1234", "UTC")
        val first = search(client, query).body<CashCutHistoryPage>()
        assertEquals(30, first.cuts.size)
        assertEquals("history-30", first.cuts.first().sessionId)
        assertEquals("history-01", first.next?.sessionId)

        insertCut(db, "newer", "caja-1", "usr-cajero", timestamp + 1)
        val second = search(client, query.copy(before = first.next)).body<CashCutHistoryPage>()
        assertEquals(listOf("history-00"), second.cuts.map { it.sessionId })
        assertNull(second.next)
    }

    @Test
    fun concurrentCloseSavesOneOfficialCountAndHistoryKeepsItsAmounts() = historyTest { db, client ->
        val opened = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest("caja-1", "usr-cajero", 1000))
        }.body<CashSessionSummary>()
        val request = CloseSessionRequest(1200, "Revisado por el encargado", listOf(CashDenominationCountDto(100, true, 12)))
        val results = coroutineScope {
            List(2) {
                async {
                    client.post("/api/v1/cash-sessions/${opened.sessionId}/close") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.status
                }
            }.awaitAll()
        }
        assertEquals(listOf(200, 409), results.map { it.value }.sorted())

        db.inTransaction { conn ->
            conn.prepareStatement(
                """
                INSERT INTO cash_movements (id, session_id, type, amount_cents, concept, user_id, created_at)
                VALUES ('imported-entry', ?, 'CASH_IN', 800, 'Movimiento importado', 'usr-cajero', 1795500000000)
                """.trimIndent()
            ).use {
                it.setString(1, opened.sessionId)
                it.executeUpdate()
            }
        }
        val recalculated = client.get("/api/v1/cash-sessions/${opened.sessionId}/summary").body<CashSessionSummary>()
        assertEquals(1800L, recalculated.expectedCashCents)
        val details = detail(client, opened.sessionId).body<CashCutHistoryDetail>()
        assertEquals(1000L, details.cut.expectedCashCents)
        assertEquals(1200L, details.cut.countedCashCents)
        assertEquals(200L, details.cut.differenceCents)
        assertEquals(request.notes, details.notes)
        assertEquals(request.denominations, details.denominations)
        assertEquals(details.cut, search(client, CashCutHistoryRequest("1234", "UTC")).body<CashCutHistoryPage>().cuts.single())
    }

    private fun historyTest(block: suspend (DatabaseFactory, HttpClient) -> Unit) = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:cash_history_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        startApplication()
        block(db, client)
    }

    private suspend fun search(client: HttpClient, request: CashCutHistoryRequest) =
        client.post("/api/v1/cash-sessions/history/search") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private suspend fun detail(client: HttpClient, id: String, pin: String = "1234") =
        client.post("/api/v1/cash-sessions/history/$id") {
            contentType(ContentType.Application.Json)
            setBody(CashCutDetailRequest(pin))
        }

    private fun insertCut(db: DatabaseFactory, id: String, register: String, cashier: String, closedAt: Long?) {
        db.inTransaction { conn ->
            conn.prepareStatement(
                """
                INSERT INTO cash_sessions
                    (id, register_id, user_id, opened_at, initial_fund_cents, closed_at, status, expected_cash_cents)
                VALUES (?, ?, ?, ?, 1000, ?, ?, 1250)
                """.trimIndent()
            ).use {
                it.setString(1, id)
                it.setString(2, register)
                it.setString(3, cashier)
                it.setLong(4, (closedAt ?: 1795500000000L) - 1000)
                it.setObject(5, closedAt)
                it.setString(6, if (closedAt == null) "OPEN" else "CLOSED")
                it.executeUpdate()
            }
        }
    }
}

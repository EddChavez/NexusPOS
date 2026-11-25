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
import java.util.UUID
import kotlin.test.*

class CashMovementReversalsTest {
    @Test
    fun reversalsAreAuditedAuthorizedAndIdempotentAndDoNotChangeClosedCuts() = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:cash_movements_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val first = open(client)
        addMovement(client, first, "CASH_IN", 2000, "Entrada de prueba")
        addMovement(client, first, "CASH_OUT", 1250, "Salida de prueba")
        addMovement(client, first, "CASH_OUT", 300, "Salida sin corregir")
        val original = history(client, first).body<CashMovementHistoryPage>()
        val cashIn = original.movements.single { it.amountCents == 2000L }
        val cashOut = original.movements.single { it.amountCents == 1250L }
        val untouched = original.movements.single { it.amountCents == 300L }
        assertTrue(original.isOpen)
        assertEquals(HttpStatusCode.Forbidden, history(client, first, "0000").status)
        assertEquals(HttpStatusCode.Forbidden, reverse(client, first, cashIn.id, "0000", "Error").status)
        assertEquals(HttpStatusCode.BadRequest, reverse(client, first, cashIn.id, "1234", "  ").status)
        assertEquals(HttpStatusCode.BadRequest, reverse(client, first, cashIn.id, "1234", "x".repeat(257)).status)

        val attempts = coroutineScope {
            List(2) { async { reverse(client, first, cashIn.id, "1234", "Entrada duplicada") } }.awaitAll()
        }
        assertTrue(attempts.all { it.status == HttpStatusCode.OK })
        val withdrawal = attempts.first().body<AuditedCashMovement>()
        assertEquals(withdrawal, attempts.last().body<AuditedCashMovement>())
        assertEquals("CASH_OUT", withdrawal.type)
        assertEquals(cashIn.id, withdrawal.reversesId)
        assertEquals("usr-admin", withdrawal.authorizedByUserId)
        assertEquals("Administrador", withdrawal.authorizedByName)
        assertEquals("Entrada duplicada", withdrawal.concept)
        val deposit = reverse(client, first, cashOut.id, "1234", "Salida por error").body<AuditedCashMovement>()
        assertEquals("CASH_IN", deposit.type)
        assertEquals(cashOut.id, deposit.reversesId)
        assertEquals(HttpStatusCode.Conflict, reverse(client, first, cashIn.id, "1234", "Otro motivo").status)
        assertEquals(HttpStatusCode.BadRequest, reverse(client, first, withdrawal.id, "1234", "Revertir compensación").status)

        val page = history(client, first).body<CashMovementHistoryPage>()
        assertEquals(5, page.movements.size)
        val originalSaved = page.movements.single { it.id == cashIn.id }
        assertEquals(cashIn.copy(reversedByMovementId = withdrawal.id), originalSaved)
        val summary = client.get("/api/v1/cash-sessions/$first/summary").body<CashSessionSummary>()
        assertEquals(3250L, summary.cashInCents)
        assertEquals(3550L, summary.cashOutCents)
        assertEquals(9700L, summary.expectedCashCents)

        val closed = client.post("/api/v1/cash-sessions/$first/close") {
            contentType(ContentType.Application.Json)
            setBody(CloseSessionRequest(9700))
        }.body<CashSessionSummary>()
        assertEquals(0L, closed.differenceCents)
        assertFalse(history(client, first).body<CashMovementHistoryPage>().isOpen)
        assertEquals(withdrawal, reverse(client, first, cashIn.id, "1234", "Entrada duplicada").body<AuditedCashMovement>())
        assertEquals(HttpStatusCode.Conflict, reverse(client, first, untouched.id, "1234", "Turno cerrado").status)
        val second = open(client)
        assertEquals(HttpStatusCode.NotFound, reverse(client, second, untouched.id, "1234", "Movimiento de otro turno").status)
        assertTrue(history(client, second).body<CashMovementHistoryPage>().movements.isEmpty())
        addMovement(client, second, "CASH_IN", 2500, "Conciliar con corte")
        val movement = history(client, second).body<CashMovementHistoryPage>().movements.single()
        val (reversalStatus, closeResult) = coroutineScope {
            val reversed = async { reverse(client, second, movement.id, "1234", "Corrección").status }
            val cut = async {
                client.post("/api/v1/cash-sessions/$second/close") {
                    contentType(ContentType.Application.Json)
                    setBody(CloseSessionRequest(10000))
                }.body<CashSessionSummary>()
            }
            reversed.await() to cut.await()
        }
        val movementsAfterClose = history(client, second).body<CashMovementHistoryPage>().movements
        when (reversalStatus) {
            HttpStatusCode.OK -> {
                assertEquals(2, movementsAfterClose.size)
                assertEquals(10000L, closeResult.expectedCashCents)
            }
            HttpStatusCode.Conflict -> {
                assertEquals(1, movementsAfterClose.size)
                assertEquals(12500L, closeResult.expectedCashCents)
            }
            else -> fail("Resultado de reversión inesperado: $reversalStatus")
        }
    }

    private suspend fun open(client: HttpClient): String = client.post("/api/v1/cash-sessions") {
        contentType(ContentType.Application.Json)
        setBody(OpenSessionRequest("caja-1", "usr-cajero", 10000))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<CashSessionSummary>().sessionId

    private suspend fun addMovement(client: HttpClient, session: String, type: String, cents: Long, concept: String) {
        val result = client.post("/api/v1/cash-sessions/$session/movements") {
            contentType(ContentType.Application.Json)
            setBody(CashMovementRequest(type, cents, concept, "usr-cajero"))
        }
        assertEquals(HttpStatusCode.Created, result.status)
    }

    private suspend fun history(client: HttpClient, session: String, pin: String = "1234") =
        client.post("/api/v1/cash-sessions/$session/movements/history") {
            contentType(ContentType.Application.Json)
            setBody(CashMovementHistoryRequest(pin))
        }

    private suspend fun reverse(client: HttpClient, session: String, movement: String, pin: String, reason: String) =
        client.post("/api/v1/cash-sessions/$session/movements/$movement/reverse") {
            contentType(ContentType.Application.Json)
            setBody(ReverseCashMovementRequest(pin, reason))
        }
}

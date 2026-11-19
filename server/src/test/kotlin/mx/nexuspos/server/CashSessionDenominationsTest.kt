package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashDenominationCountDto
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class CashSessionDenominationsTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testCashSessionCloseWithDenominationsAndAudit() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Abrir sesión con fondo inicial de $500.00
        val openRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-admin", initialFundCents = 50000L))
        }
        assertEquals(HttpStatusCode.Created, openRes.status)
        val opened = openRes.body<CashSessionSummary>()
        assertEquals(50000L, opened.initialFundCents)

        // 2. Cerrar sesión con desglose de denominaciones
        // 2 billetes de $500 ($1000) + 5 monedas de $10 ($50) = $1,050.00 contado
        val denoms = listOf(
            CashDenominationCountDto(denominationCents = 50000L, isCoin = false, pieces = 2),
            CashDenominationCountDto(denominationCents = 1000L, isCoin = true, pieces = 5)
        )
        val closeReq = CloseSessionRequest(
            countedCashCents = 105000L,
            notes = "Cierre de turno con arqueo detallado",
            denominations = denoms
        )

        val closeRes = client.post("/api/v1/cash-sessions/${opened.sessionId}/close") {
            contentType(ContentType.Application.Json)
            setBody(closeReq)
        }
        assertEquals(HttpStatusCode.OK, closeRes.status)
        val closed = closeRes.body<CashSessionSummary>()
        assertEquals("CLOSED", closed.status)
        assertEquals(105000L, closed.countedCashCents)
        assertEquals(2, closed.denominations.size)
        assertEquals(50000L, closed.denominations[0].denominationCents)
        assertEquals(2, closed.denominations[0].pieces)

        // 3. Consultar la sesión y validar que devuelva las denominaciones persistidas
        val getRes = client.get("/api/v1/cash-sessions/${opened.sessionId}/summary")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val summary = getRes.body<CashSessionSummary>()
        assertEquals(2, summary.denominations.size)
        val totalCountedFromDenoms = summary.denominations.sumOf { it.totalCents }
        assertEquals(105000L, totalCountedFromDenoms)
    }

    @Test
    fun testBlindCountAndDiscountSettingsPersistence() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Obtener settings actuales
        val getRes = client.get("/api/v1/system/settings")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val initialSettings = getRes.body<SettingsDto>()
        assertFalse(initialSettings.blindCashCountEnabled)
        assertEquals(10.0, initialSettings.maxCashierDiscountPercent)

        // Actualizar activando modo ciego y límite de 15%
        val updatedDto = initialSettings.copy(
            blindCashCountEnabled = true,
            maxCashierDiscountPercent = 15.0
        )
        val putRes = client.put("/api/v1/system/settings") {
            contentType(ContentType.Application.Json)
            setBody(updatedDto)
        }
        assertEquals(HttpStatusCode.OK, putRes.status)
        val saved = putRes.body<SettingsDto>()
        assertTrue(saved.blindCashCountEnabled)
        assertEquals(15.0, saved.maxCashierDiscountPercent)
    }
}

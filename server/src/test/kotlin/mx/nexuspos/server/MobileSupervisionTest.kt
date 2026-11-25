package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.contracts.catalog.*
import mx.nexuspos.contracts.mobile.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.modules.mobile.MobileAccess
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.Security
import mx.nexuspos.server.platform.UnauthorizedException
import java.util.UUID
import kotlin.test.*

class MobileSupervisionTest {
    @Test
    fun registeredSupervisorCanReadOnlyMobileProjectionsUntilDeviceIsRevoked() = testApplication {
        val db = database()
        application { nexusModule(db) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        assertEquals(HttpStatusCode.Unauthorized, http.get("$mobile/overview").status)
        db.inTransaction { conn ->
            conn.prepareStatement("INSERT INTO users (id, name, role, pin_hash, is_active) VALUES ('mobile-supervisor', 'Encargado', 'SUPERVISOR', ?, TRUE)").use {
                it.setString(1, Security.hashPin("5678"))
                it.executeUpdate()
            }
        }

        suspend fun issue(pin: String) = http.post("$admin/pairing") { jsonBody(MobileAdminRequest(pin)) }
        assertEquals(HttpStatusCode.Unauthorized, issue("5678").status)
        val code = issue("1234").body<MobilePairingCode>()
        suspend fun claim() = http.post("$mobile/pairing/claim") { jsonBody(MobilePairRequest(code.code, "Celular de tienda")) }
        val claims = coroutineScope { listOf(async { claim() }, async { claim() }).map { it.await() } }
        assertEquals(setOf(HttpStatusCode.Created, HttpStatusCode.Unauthorized), claims.map { it.status }.toSet())
        val device = claims.single { it.status == HttpStatusCode.Created }.body<MobileDeviceCredential>()
        assertEquals(HttpStatusCode.Unauthorized, http.get("$mobile/overview") { bearerAuth(device.token) }.status)
        suspend fun login(pin: String) = http.post("$mobile/session") { bearerAuth(device.token); jsonBody(MobileSessionRequest(pin)) }
        assertEquals(HttpStatusCode.Unauthorized, login("0000").status)
        val session = login("5678").body<MobileSession>()
        assertEquals("Encargado", session.userName)
        suspend fun read(path: String) = http.get("$mobile/$path") { bearerAuth(session.token) }

        val created = http.post("/api/v1/products") {
            jsonBody(CreateProductRequest(
                sku = "MOB-CAFE", name = "Café móvil", costCents = 345L, usesInventory = true,
                initialStockMillis = 12000L,
                presentations = listOf(
                    CreatePresentationRequest(barcode = "7500000000001", name = "Pieza", factorMillis = 1000, priceCents = 1200),
                    CreatePresentationRequest(barcode = "7500000000002", name = "Caja", factorMillis = 6000, priceCents = 6500)
                )
            ))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<ProductDto>()
        val response = read("products?barcode=7500000000002")
        assertFalse(response.bodyAsText().contains("costCents"))
        val found = response.body<List<MobileProduct>>().single()
        assertEquals(created.id, read("products?query=MOB-CAFE").body<List<MobileProduct>>().single().id)
        val manual = read("products?query=7500000000002").body<List<MobileProduct>>().single()
        assertEquals(found.matchedPresentationId, manual.matchedPresentationId)
        assertEquals("Caja", found.presentations.single { it.id == found.matchedPresentationId }.name)
        assertEquals(12000L, found.stockMillis)
        assertTrue(read("products?barcode=750000000000").body<List<MobileProduct>>().isEmpty())

        val open = http.post("/api/v1/cash-sessions") {
            jsonBody(OpenSessionRequest("caja-1", "usr-cajero", 0))
        }.body<CashSessionSummary>()
        val sale = http.post("/api/v1/sales") {
            jsonBody(CreateSaleRequest(
                UUID.randomUUID().toString(), open.sessionId, "usr-cajero",
                lines = listOf(SaleLineItemRequest(description = "Venta móvil", quantityMillis = 1000,
                    expectedUnitPriceCents = 1200, expectedTotalCents = 1200)),
                payments = listOf(SalePaymentRequest("CARD", 1200, reference = "REFERENCIA-PRIVADA")),
                expectedTotalCents = 1200
            ))
        }.body<SaleResponse>()
        val overview = read("overview").body<MobileOverview>()
        assertEquals(1200L, overview.netSalesCents)
        assertTrue(overview.registers.single { it.id == "caja-1" }.open)
        val visible = read("shifts?date=${overview.date}&registerId=caja-1").body<MobileShifts>().shifts.single()
        assertNull(visible.expectedCents)
        assertNull(visible.countedCents)
        assertNull(visible.differenceCents)
        val sales = read("shifts/${open.sessionId}/sales?folio=${sale.folio}")
        assertFalse(sales.bodyAsText().contains("REFERENCIA-PRIVADA"))
        assertEquals(listOf(sale.folio), sales.body<MobileShiftSales>().sales.map { it.folio })
        assertEquals(HttpStatusCode.OK, http.post("/api/v1/cash-sessions/${open.sessionId}/close") {
            jsonBody(CloseSessionRequest(0))
        }.status)
        assertEquals(0L, read("shifts/${open.sessionId}/sales").body<MobileShiftSales>().shift.countedCents)

        val devices = http.post("$admin/devices") { jsonBody(MobileAdminRequest("1234")) }.body<List<MobileDevice>>()
        assertEquals(listOf(device.id), devices.map { it.id })
        val revoked = http.post("$admin/devices/${device.id}/revoke") { jsonBody(MobileAdminRequest("1234")) }
        assertTrue(revoked.body<Boolean>())
        assertEquals(HttpStatusCode.Unauthorized, read("overview").status)
        assertEquals(HttpStatusCode.Forbidden, login("5678").status)
    }

    @Test
    fun pairingExpiresAndSessionExpiresAfterFifteenMinutesOfInactivity() = testApplication {
        val db = database()
        application { nexusModule(db) }
        client.get("/health")
        var time = 1000L
        val access = MobileAccess(db) { time }
        val expired = access.issue("1234")
        time = expired.expiresAt
        assertFailsWith<UnauthorizedException> { access.claim(MobilePairRequest(expired.code, "Tablet")) }
        val valid = access.issue("1234")
        val device = access.claim(MobilePairRequest(valid.code, "Tablet"))
        val session = access.login(device.token, "1234")
        val idle = 15 * 60_000L
        time += idle - 1
        access.authorize(session.token)
        time += idle
        assertFailsWith<UnauthorizedException> { access.authorize(session.token) }
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun database() = DatabaseFactory(
        "jdbc:h2:mem:mobile_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "sa", ""
    )

    private companion object {
        const val mobile = "/api/v1/mobile"
        const val admin = "/api/v1/mobile-admin"
    }
}

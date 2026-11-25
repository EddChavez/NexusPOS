package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.*
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*

class PresentationPromotionTest {
    @Test
    fun promotionByPairsIsAuthoritativeAndRejectsExpiredQuotes() = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:promotions_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""
        )
        application { nexusModule(db) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val product = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(CreateProductRequest("PROMO-QA", "Leche prueba", usesInventory = false,
                presentations = listOf(CreatePresentationRequest(barcode = "PROMO-QA", name = "Pieza", priceCents = 1000))))
        }.body<ProductDto>()
        val presentation = product.presentations.single()
        val route = "/api/v1/products/${product.id}/presentations/${presentation.id}/promotion"
        val today = LocalDate.now(ZoneId.of("America/Mexico_City"))
        val admin = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("1234"))
        }.body<LoginResponse>().token
        suspend fun configure(percent: Int, day: LocalDate) = client.put(route) {
            bearerAuth(admin)
            contentType(ContentType.Application.Json)
            setBody(UpdatePresentationPromotionRequest(PairPromotionSchedule(percent, day.toString(), day.toString())))
        }
        val schedule = configure(100, today).also { assertEquals(HttpStatusCode.OK, it.status) }
            .body<ProductDto>().presentations.single().promotion
        assertNotNull(schedule)
        val shift = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest("caja-1", "usr-admin", 0))
        }.body<CashSessionSummary>()
        suspend fun sale(quoted: Long, total: Long, key: String) = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(CreateSaleRequest(
                key, shift.sessionId, "usr-admin",
                lines = listOf(SaleLineItemRequest(
                    presentation.id, product.id, product.name, 3000, 1000, total, expectedPromotionDiscountCents = quoted
                )),
                payments = listOf(SalePaymentRequest("CASH", total)), expectedTotalCents = total
            ))
        }
        assertEquals(HttpStatusCode.Conflict, sale(1100, 1900, "bad-promo").status)
        val twoForOne = sale(1000, 2000, "two-for-one").also { assertEquals(HttpStatusCode.Created, it.status) }.body<SaleResponse>()
        assertEquals(1000L, twoForOne.lines.single().discountCents)
        assertEquals(2000L, twoForOne.totalCents)
        assertEquals(HttpStatusCode.OK, configure(50, today).status)
        assertEquals(HttpStatusCode.Created, sale(500, 2500, "half-off").status)
        assertEquals(HttpStatusCode.OK, configure(100, today.minusDays(1)).status)
        assertEquals(HttpStatusCode.Conflict, sale(1000, 2000, "expired-promo").status)
        assertEquals(HttpStatusCode.Created, sale(0, 3000, "public-price").status)
    }
}

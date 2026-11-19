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
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class SaleRefundModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testPartialRefundStockAndCashSessionIntegrity() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Abrir sesión de caja
        val openSessionRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(initialFundCents = 100_000L, registerId = "caja-1", userId = "usr-admin"))
        }
        assertEquals(HttpStatusCode.Created, openSessionRes.status)
        val session = openSessionRes.body<CashSessionSummary>()

        // 2. Obtener producto de prueba sembrado: Coca-Cola 600ml
        val prods = client.get("/api/v1/products?barcode=7501055300078").body<List<ProductDto>>()
        val cokeProd = prods.first()
        val cokePres = cokeProd.presentations.first { it.barcode == "7501055300078" }

        // 3. Completar venta: 2x Coca Cola ($18.50 c/u = $37.00 total)
        val idempotencyKey = UUID.randomUUID().toString()
        val saleReq = CreateSaleRequest(
            idempotencyKey = idempotencyKey,
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokePres.name,
                    quantityMillis = 2000L,
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 3700L,
                    taxRate = 0.16
                )
            ),
            payments = listOf(
                SalePaymentRequest(method = "CASH", amountCents = 3700L)
            ),
            expectedTotalCents = 3700L
        )
        val saleRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleReq)
        }
        assertEquals(HttpStatusCode.Created, saleRes.status)
        val createdSale = saleRes.body<SaleResponse>()
        assertEquals("COMPLETED", createdSale.status)
        assertEquals(3700L, createdSale.totalCents)
        val saleLine = createdSale.lines.first()

        // 4. Devolución Parcial de 1 Coca Cola con reingreso a stock
        val refundReq = CreateSaleRefundRequest(
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            reason = "DEFECTO",
            notes = "Cliente reportó gas bajo",
            refundMethod = "CASH",
            lines = listOf(
                RefundLineItemRequest(
                    saleLineId = saleLine.id,
                    quantityMillis = 1000L,
                    reenterStock = true
                )
            )
        )
        val refundRes = client.post("/api/v1/sales/${createdSale.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(refundReq)
        }
        assertEquals(HttpStatusCode.Created, refundRes.status)
        val refundData = refundRes.body<SaleRefundResponse>()
        assertEquals(1L, refundData.folio)
        assertEquals(1850L, refundData.totalCents)
        assertEquals("DEFECTO", refundData.reason)

        // 5. Verificar estado de la venta: ahora es PARTIALLY_REFUNDED
        val saleUpdated = client.get("/api/v1/sales/${createdSale.id}").body<SaleResponse>()
        assertEquals("PARTIALLY_REFUNDED", saleUpdated.status)
        val lineUpdated = saleUpdated.lines.first { it.id == saleLine.id }
        assertEquals(1000L, lineUpdated.refundedQuantityMillis)
        assertEquals(1000L, lineUpdated.returnableQuantityMillis)

        // 6. Intentar cancelar la venta completa: debe ser rechazada porque ya tiene devoluciones parciales
        val cancelRes = client.post("/api/v1/sales/${createdSale.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest(reason = "Error de captura"))
        }
        assertEquals(HttpStatusCode.BadRequest, cancelRes.status)

        // 7. Verificar que la sesión de caja descontó los 1850 cents de la devolución
        val sessionSummary = client.get("/api/v1/cash-sessions/${session.sessionId}/summary").body<CashSessionSummary>()
        assertEquals(1850L, sessionSummary.refundsCashCents)
        // expectedCash = fund (100_000) + sales (3700) - refunds (1850) = 101_850
        assertEquals(101_850L, sessionSummary.expectedCashCents)

        // 8. Devolver el resto: 1 Coca Cola restante (1000 millis)
        val finalRefundReq = CreateSaleRefundRequest(
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            reason = "CAMBIO",
            refundMethod = "CASH",
            lines = listOf(
                RefundLineItemRequest(saleLineId = saleLine.id, quantityMillis = 1000L, reenterStock = true)
            )
        )
        val finalRefundRes = client.post("/api/v1/sales/${createdSale.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(finalRefundReq)
        }
        assertEquals(HttpStatusCode.Created, finalRefundRes.status)
        val finalRefundData = finalRefundRes.body<SaleRefundResponse>()
        assertEquals(2L, finalRefundData.folio)
        assertEquals(1850L, finalRefundData.totalCents)

        // 9. La venta ahora debe estar en REFUNDED y sin artículos retornables
        val saleFullyRefunded = client.get("/api/v1/sales/${createdSale.id}").body<SaleResponse>()
        assertEquals("REFUNDED", saleFullyRefunded.status)
        assertTrue(saleFullyRefunded.lines.all { it.returnableQuantityMillis == 0L })

        // 10. Intentar devolver nuevamente sobre la misma venta: debe fallar con 400
        val overRefundRes = client.post("/api/v1/sales/${createdSale.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(finalRefundReq)
        }
        assertEquals(HttpStatusCode.BadRequest, overRefundRes.status)
    }
}

package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemRequest
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ReportsModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testDailyFinancialReportWithGrossProfitAndCancellations() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Abrir sesión de caja con fondo de $500.00
        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 50000L))
        }.body<CashSessionSummary>()
        assertEquals("OPEN", session.status)

        // 2. Crear producto con costo $12.00 (1200 cents) y precio venta $20.00 (2000 cents)
        val prod = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductRequest(
                    sku = "GAL-TEST",
                    name = "Galletas Test 100g",
                    departmentId = null,
                    baseUnit = "PZA",
                    costCents = 1200L,
                    taxRate = 0.0,
                    initialStockMillis = 50000L,
                    presentations = listOf(
                        CreatePresentationRequest(
                            barcode = "7500000099999",
                            name = "Galletas Test Pza",
                            priceCents = 2000L,
                            factorMillis = 1000L
                        )
                    )
                )
            )
        }.body<ProductDto>()

        val pres = prod.presentations.first { it.barcode == "7500000099999" }

        // 3. Completar venta en efectivo: 3 piezas = $60.00 venta, costo = 3 * $12 = $36.00
        val sale = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateSaleRequest(
                    idempotencyKey = UUID.randomUUID().toString(),
                    cashSessionId = session.sessionId,
                    cashierUserId = "usr-cajero",
                    lines = listOf(
                        SaleLineItemRequest(
                            productId = prod.id,
                            presentationId = pres.id,
                            description = pres.name,
                            quantityMillis = 3000L,
                            expectedUnitPriceCents = 2000L,
                            expectedTotalCents = 6000L
                        )
                    ),
                    payments = listOf(SalePaymentRequest(method = "CASH", amountCents = 6000L)),
                    expectedTotalCents = 6000L
                )
            )
        }.body<SaleResponse>()
        assertEquals("COMPLETED", sale.status)

        // 4. Consultar reporte del día
        val rep1 = client.get("/api/v1/reports/daily?sessionId=${session.sessionId}").body<DailyFinancialReportDto>()
        assertEquals(6000L, rep1.totalSalesCents)
        assertEquals(6000L, rep1.salesCashCents)
        assertEquals(0L, rep1.salesCreditCents)
        assertEquals(1, rep1.completedTicketsCount)
        assertEquals(0, rep1.cancelledTicketsCount)
        assertEquals(3600L, rep1.totalCostCents, "El costo total de 3 piezas debe ser 3 * 1200 = 3600")
        assertEquals(2400L, rep1.estimatedGrossProfitCents, "Ganancia bruta = 6000 - 3600 = 2400")
        assertEquals(40.0, rep1.profitMarginPercent, "Margen = 2400 / 6000 = 40.0%")

        // 5. Cancelar la venta
        val cancelRes = client.post("/api/v1/sales/${sale.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest(reason = "Devolución", authorizedByUserId = "usr-admin"))
        }
        assertEquals(HttpStatusCode.OK, cancelRes.status)

        // 6. Consultar reporte del día después de cancelación
        val rep2 = client.get("/api/v1/reports/daily?sessionId=${session.sessionId}").body<DailyFinancialReportDto>()
        assertEquals(0L, rep2.totalSalesCents)
        assertEquals(0L, rep2.salesCashCents)
        assertEquals(0, rep2.completedTicketsCount)
        assertEquals(1, rep2.cancelledTicketsCount)
        assertEquals(6000L, rep2.cancelledAmountCents)
        assertEquals(0L, rep2.totalCostCents)
        assertEquals(0L, rep2.estimatedGrossProfitCents)
    }
}

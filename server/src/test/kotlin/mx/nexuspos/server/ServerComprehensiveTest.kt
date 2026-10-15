package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemRequest
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SystemInfoDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ServerComprehensiveTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testHealthAndSystemInfo() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val healthRes = client.get("/health")
        assertEquals(HttpStatusCode.OK, healthRes.status)

        val infoRes = client.get("/api/v1/system/info")
        assertEquals(HttpStatusCode.OK, infoRes.status)
        val info = infoRes.body<SystemInfoDto>()
        assertEquals("v1", info.apiVersion)
    }

    @Test
    fun testAuthLoginWithBCrypt() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Test admin login (PIN 1234)
        val adminRes = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(pin = "1234"))
        }
        assertEquals(HttpStatusCode.OK, adminRes.status)
        val admin = adminRes.body<LoginResponse>()
        assertEquals("ADMIN", admin.role)

        // Test cashier login (PIN 0000)
        val cashierRes = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(pin = "0000"))
        }
        assertEquals(HttpStatusCode.OK, cashierRes.status)
        val cashier = cashierRes.body<LoginResponse>()
        assertEquals("CASHIER", cashier.role)

        // Invalid PIN
        val invalidRes = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(pin = "9999"))
        }
        assertEquals(HttpStatusCode.Unauthorized, invalidRes.status)
    }

    @Test
    fun testCatalogSearch() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Search barcode Coca-Cola 600ml
        val cokeRes = client.get("/api/v1/products?barcode=7501055300078")
        assertEquals(HttpStatusCode.OK, cokeRes.status)
        val cokeList = cokeRes.body<List<ProductDto>>()
        assertTrue(cokeList.isNotEmpty())
        assertEquals("Coca-Cola Original 600ml", cokeList.first().name)
        assertEquals(1850L, cokeList.first().presentations.first { it.barcode == "7501055300078" }.priceCents)

        // Search bulk weight product (Jitomate)
        val tomatoRes = client.get("/api/v1/products?query=Jitomate")
        assertEquals(HttpStatusCode.OK, tomatoRes.status)
        val tomatoList = tomatoRes.body<List<ProductDto>>()
        assertTrue(tomatoList.isNotEmpty())
        assertEquals("KG", tomatoList.first().baseUnit)
    }

    @Test
    fun testCompleteSaleCashAndIdempotencyAndSession() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Open cash session with $500.00
        val sessionRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 50000L))
        }
        val sessionBody = sessionRes.bodyAsText()
        assertEquals(HttpStatusCode.Created, sessionRes.status, "Session creation failed: $sessionBody")
        val session = sessionRes.body<CashSessionSummary>()
        assertEquals("OPEN", session.status)
        assertEquals(50000L, session.initialFundCents)

        // 2. Fetch Coca-Cola presentation
        val prods = client.get("/api/v1/products?barcode=7501055300078").body<List<ProductDto>>()
        val cokeProd = prods.first()
        val cokePres = cokeProd.presentations.first { it.barcode == "7501055300078" }

        // 3. Complete Sale: 2 Cokes ($18.50 each = $37.00 total)
        val idempotencyKey = UUID.randomUUID().toString()
        val saleReq = CreateSaleRequest(
            idempotencyKey = idempotencyKey,
            cashSessionId = session.sessionId,
            cashierUserId = "usr-cajero",
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokePres.name,
                    quantityMillis = 2000L, // 2 pieces
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 3700L,
                    taxRate = 0.16
                )
            ),
            payments = listOf(
                SalePaymentRequest(
                    method = "CASH",
                    amountCents = 3700L,
                    receivedCents = 5000L // 50 pesos
                )
            ),
            expectedTotalCents = 3700L
        )

        val saleRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleReq)
        }
        assertEquals(HttpStatusCode.Created, saleRes.status)
        val sale = saleRes.body<SaleResponse>()
        assertEquals(3700L, sale.totalCents)
        assertEquals(1300L, sale.payments.first().changeCents) // $13.00 change
        assertTrue(sale.folio >= 1000L)

        // 4. Test Idempotency: Repeating same request returns same folio without double charging
        val duplicateRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleReq)
        }
        assertEquals(HttpStatusCode.Created, duplicateRes.status)
        val duplicateSale = duplicateRes.body<SaleResponse>()
        assertEquals(sale.folio, duplicateSale.folio)
        assertEquals(sale.id, duplicateSale.id)

        // 5. Check Cash Session Summary
        val summaryRes = client.get("/api/v1/cash-sessions/${session.sessionId}/summary")
        assertEquals(HttpStatusCode.OK, summaryRes.status)
        val summary = summaryRes.body<CashSessionSummary>()
        assertEquals(3700L, summary.salesCashCents)
        assertEquals(53700L, summary.expectedCashCents) // $500 initial + $37 sales

        // 6. Close Cash Session with $537.00 counted (zero difference)
        val closeRes = client.post("/api/v1/cash-sessions/${session.sessionId}/close") {
            contentType(ContentType.Application.Json)
            setBody(CloseSessionRequest(countedCashCents = 53700L, notes = "Corte exacto"))
        }
        assertEquals(HttpStatusCode.OK, closeRes.status)
        val closedSession = closeRes.body<CashSessionSummary>()
        assertEquals("CLOSED", closedSession.status)
        assertEquals(0L, closedSession.differenceCents)
    }

    @Test
    fun testCreditFiadoAndPaymentFlow() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Open session
        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 10000L))
        }.body<CashSessionSummary>()

        // Check Don Pancho (cust-1)
        val customer = client.get("/api/v1/customers/cust-1").body<CustomerDto>()
        assertEquals(150000L, customer.creditLimitCents) // $1,500.00 limit
        assertEquals(35000L, customer.currentBalanceCents) // $350.00 current balance

        // Sale fiada: 1 Aceite 123 ($42.00)
        val oilProd = client.get("/api/v1/products?barcode=7501011115501").body<List<ProductDto>>().first()
        val oilPres = oilProd.presentations.first()

        val creditSaleRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateSaleRequest(
                    idempotencyKey = UUID.randomUUID().toString(),
                    cashSessionId = session.sessionId,
                    cashierUserId = "usr-cajero",
                    customerId = "cust-1",
                    lines = listOf(
                        SaleLineItemRequest(
                            productId = oilProd.id,
                            presentationId = oilPres.id,
                            description = oilPres.name,
                            quantityMillis = 1000L,
                            expectedUnitPriceCents = 4200L,
                            expectedTotalCents = 4200L
                        )
                    ),
                    payments = listOf(
                        SalePaymentRequest(
                            method = "CREDIT",
                            amountCents = 4200L
                        )
                    ),
                    expectedTotalCents = 4200L
                )
            )
        }
        val creditSaleBody = creditSaleRes.bodyAsText()
        assertEquals(HttpStatusCode.Created, creditSaleRes.status, "Credit sale failed: $creditSaleBody")
        val creditSale = creditSaleRes.body<SaleResponse>()

        assertEquals(4200L, creditSale.totalCents)

        // Customer balance should now be $350.00 + $42.00 = $392.00 (39200 cents)
        val updatedCustomer = client.get("/api/v1/customers/cust-1").body<CustomerDto>()
        assertEquals(39200L, updatedCustomer.currentBalanceCents)

        // Customer pays $100.00 abono
        val paymentRes = client.post("/api/v1/customers/cust-1/credit/payments") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterCreditPaymentRequest(
                    customerId = "cust-1",
                    cashSessionId = session.sessionId,
                    cashierUserId = "usr-cajero",
                    amountCents = 10000L,
                    notes = "Abono parcial"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, paymentRes.status)
        val movement = paymentRes.body<CreditMovementDto>()
        assertEquals(29200L, movement.balanceAfterCents) // $292.00

        // Abono enters cash session
        val summary = client.get("/api/v1/cash-sessions/${session.sessionId}/summary").body<CashSessionSummary>()
        assertEquals(10000L, summary.creditPaymentsCashCents)
        assertEquals(20000L, summary.expectedCashCents) // $100 initial + $100 abono
    }

    @Test
    fun testConfigurableNegativeStockPolicy() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Disable negative stock
        val updateRes = client.patch("/api/v1/system/settings") {
            contentType(ContentType.Application.Json)
            setBody(SettingsDto(allowNegativeStock = false))
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)

        // Open session
        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 10000L))
        }.body<CashSessionSummary>()

        val prod = client.get("/api/v1/products?barcode=7501000111204").body<List<ProductDto>>().first()
        val pres = prod.presentations.first()

        // Attempt to buy 1000 packs (1,000,000 millis), which exceeds available stock of 45,000 millis
        val excessiveSaleRes = client.post("/api/v1/sales") {
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
                            quantityMillis = 1000000L,
                            expectedUnitPriceCents = 2850L,
                            expectedTotalCents = 2850000L
                        )
                    ),
                    payments = listOf(SalePaymentRequest(method = "CASH", amountCents = 2850000L)),
                    expectedTotalCents = 2850000L
                )
            )
        }
        // Should be rejected with 422 Unprocessable Entity
        assertEquals(HttpStatusCode.UnprocessableEntity, excessiveSaleRes.status)
    }
}

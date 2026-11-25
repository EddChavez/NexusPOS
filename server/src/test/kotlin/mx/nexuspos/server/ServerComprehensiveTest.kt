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
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.PresentationPriceTier
import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.contracts.catalog.UpdateProductKitRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.UpdatePresentationPricesRequest
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.RefundLineItemRequest
import mx.nexuspos.contracts.sales.SaleRefundResponse
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

        suspend fun submit(request: CreateSaleRequest) = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val (saleRes, duplicateRes) = coroutineScope {
            val first = async { submit(saleReq) }
            val second = async { submit(saleReq) }
            first.await() to second.await()
        }
        assertEquals(HttpStatusCode.Created, saleRes.status)
        val sale = saleRes.body<SaleResponse>()
        assertEquals(3700L, sale.totalCents)
        assertEquals(1300L, sale.payments.first().changeCents)
        assertTrue(sale.folio >= 1000L)

        assertEquals(HttpStatusCode.Created, duplicateRes.status)
        val duplicateSale = duplicateRes.body<SaleResponse>()
        assertEquals(sale.folio, duplicateSale.folio)
        assertEquals(sale.id, duplicateSale.id)

        val changedPayment = saleReq.payments.single().copy(receivedCents = 6000L)
        assertEquals(HttpStatusCode.Conflict, submit(saleReq.copy(payments = listOf(changedPayment))).status)
        val stock = client.get("/api/v1/products/${cokeProd.id}").body<ProductDto>().currentStockMillis
        assertEquals(cokeProd.currentStockMillis - 2000L, stock)

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
    fun volumePricesAreStoredPerPresentationAndRecalculatedAtCheckout() = testApplication {
        application { nexusModule(createTestDb()) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val product = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(CreateProductRequest(
                sku = "PRUEBA-MAYOREO",
                name = "Galletas para pruebas de mayoreo",
                initialStockMillis = 50000,
                presentations = listOf(
                    CreatePresentationRequest(barcode = "P-UNIT", name = "Pieza", priceCents = 300),
                    CreatePresentationRequest(barcode = "P-PACK", name = "Caja", factorMillis = 6000, priceCents = 1500)
                )
            ))
        }.body<ProductDto>()
        val pack = product.presentations.single { it.barcode == "P-PACK" }
        val tiers = listOf(PresentationPriceTier(2000, 1200), PresentationPriceTier(4000, 1000))
        suspend fun savePrices(values: List<PresentationPriceTier>) =
            client.put("/api/v1/products/${product.id}/presentations/${pack.id}/prices") {
                contentType(ContentType.Application.Json)
                setBody(UpdatePresentationPricesRequest(values))
            }
        assertEquals(HttpStatusCode.BadRequest, savePrices(listOf(tiers[0], tiers[0])).status)
        assertEquals(HttpStatusCode.BadRequest, savePrices(listOf(PresentationPriceTier(2000, 1600))).status)
        assertEquals(HttpStatusCode.OK, savePrices(tiers.reversed()).status)
        val fromBarcode = client.get("/api/v1/products?barcode=P-PACK").body<List<ProductDto>>().single()
        assertEquals(tiers, fromBarcode.presentations.single { it.id == pack.id }.priceTiers)
        assertTrue(fromBarcode.presentations.single { it.barcode == "P-UNIT" }.priceTiers.isEmpty())

        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 10000L))
        }.body<CashSessionSummary>()
        suspend fun sell(price: Long, total: Long) = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(CreateSaleRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                cashSessionId = session.sessionId,
                cashierUserId = "usr-cajero",
                lines = listOf(SaleLineItemRequest(
                    productId = product.id, presentationId = pack.id, description = "Caja",
                    quantityMillis = 4000, expectedUnitPriceCents = price, expectedTotalCents = total,
                    taxRate = 0.0, discountPercent = 10.0
                )),
                payments = listOf(SalePaymentRequest("CASH", total, total)),
                expectedTotalCents = total
            ))
        }
        assertEquals(HttpStatusCode.Conflict, sell(1200, 4320).status)
        val result = sell(1000, 3600)
        assertEquals(HttpStatusCode.Created, result.status)
        assertEquals(1000L, result.body<SaleResponse>().lines.single().unitPriceCents)
        assertEquals(26000L, client.get("/api/v1/products/${product.id}").body<ProductDto>().currentStockMillis)

        assertEquals(HttpStatusCode.OK, savePrices(emptyList()).status)
        val snapshot = client.get("/api/v1/products/${product.id}").body<ProductDto>()
        assertTrue(snapshot.presentations.single { it.id == pack.id }.priceTiers.isEmpty())
    }

    @Test
    fun kitStockAndRefundsUseTheOriginalComponents() = testApplication {
        application { nexusModule(createTestDb()) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        assertEquals(HttpStatusCode.OK, client.put("/api/v1/system/settings") {
            contentType(ContentType.Application.Json)
            setBody(SettingsDto(allowNegativeStock = false))
        }.status)

        suspend fun create(code: String, unit: String, hasStock: Boolean, stock: Long): ProductDto =
            client.post("/api/v1/products") {
                contentType(ContentType.Application.Json)
                setBody(CreateProductRequest(
                    sku = code, name = code, baseUnit = unit, usesInventory = hasStock,
                    initialStockMillis = stock,
                    taxRate = if (hasStock) 0.0 else 0.16,
                    presentations = listOf(CreatePresentationRequest(barcode = code, name = code, priceCents = 100))
                ))
            }.body()
        val piece = create("KIT-PIEZA", "PZA", true, 10000)
        val weight = create("KIT-GRANEL", "KG", true, 4000)
        val kit = create("KIT-PRUEBA", "PZA", false, 0)

        suspend fun saveRecipe(components: List<KitComponent>) = client.put("/api/v1/products/${kit.id}/kit") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductKitRequest(components))
        }
        val original = listOf(KitComponent(piece.id, 2000), KitComponent(weight.id, 250))
        assertEquals(HttpStatusCode.BadRequest, saveRecipe(listOf(KitComponent(kit.id, 1000))).status)
        assertEquals(HttpStatusCode.BadRequest, saveRecipe(listOf(KitComponent(piece.id, 1500))).status)
        assertEquals(HttpStatusCode.OK, saveRecipe(original).status)
        assertEquals(original.toSet(),
            client.get("/api/v1/products?barcode=KIT-PRUEBA").body<List<ProductDto>>().single().kitComponents.toSet())

        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-admin", initialFundCents = 10000))
        }.body<CashSessionSummary>()

        fun line(product: ProductDto, quantity: Long, recipe: List<KitComponent>? = null, discount: Long = 0) =
            SaleLineItemRequest(
                productId = product.id, presentationId = product.presentations.single().id,
                description = product.name, quantityMillis = quantity, expectedUnitPriceCents = 100,
                expectedTotalCents = quantity / 10 - discount, expectedKitComponents = recipe,
                discountCents = discount
            )
        suspend fun sell(vararg lines: SaleLineItemRequest): io.ktor.client.statement.HttpResponse {
            val total = lines.sumOf { it.expectedTotalCents }
            return client.post("/api/v1/sales") {
                contentType(ContentType.Application.Json)
                setBody(CreateSaleRequest(
                    idempotencyKey = UUID.randomUUID().toString(), cashSessionId = session.sessionId,
                    cashierUserId = "usr-admin", lines = lines.toList(),
                    payments = listOf(SalePaymentRequest("CASH", total, total)), expectedTotalCents = total
                ))
            }
        }
        suspend fun stock(product: ProductDto) =
            client.get("/api/v1/products/${product.id}").body<ProductDto>().currentStockMillis

        assertEquals(HttpStatusCode.BadRequest, sell(line(kit, 1500, original)).status)
        assertEquals(HttpStatusCode.UnprocessableEntity,
            sell(line(kit, 3000, original), line(piece, 5000)).status)
        assertEquals(10000L, stock(piece))
        assertEquals(4000L, stock(weight))

        val sale = sell(line(kit, 3000, original, discount = 1)).body<SaleResponse>()
        assertTrue(sale.lines.single().isKit)
        assertEquals(299L, sale.totalCents)
        assertEquals(4000L, stock(piece))
        assertEquals(3250L, stock(weight))
        assertEquals(0L, stock(kit))

        val changed = listOf(KitComponent(piece.id, 1000), KitComponent(weight.id, 500))
        assertEquals(HttpStatusCode.OK, saveRecipe(changed).status)
        assertEquals(HttpStatusCode.Conflict, sell(line(kit, 1000, original)).status)

        suspend fun refund(quantity: Long, returnStock: Boolean) =
            client.post("/api/v1/sales/${sale.id}/refund") {
                contentType(ContentType.Application.Json)
                setBody(CreateSaleRefundRequest(
                    cashSessionId = session.sessionId, cashierUserId = "usr-admin", reason = "OTRO",
                    lines = listOf(RefundLineItemRequest(sale.lines.single().id, quantity, returnStock))
                ))
            }
        assertEquals(HttpStatusCode.BadRequest, refund(500, true).status)
        val refunds = listOf(true, false, true).map { refund(1000, it).body<SaleRefundResponse>() }
        assertEquals(listOf(100L, 99L, 100L), refunds.map { it.totalCents })
        assertEquals(sale.taxCents, refunds.sumOf { it.taxCents })
        assertEquals(8000L, stock(piece))
        assertEquals(3750L, stock(weight))

        val second = sell(line(kit, 1000, changed)).body<SaleResponse>()
        assertEquals(HttpStatusCode.OK, saveRecipe(emptyList()).status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/sales/${second.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest(reason = "Prueba de cancelación"))
        }.status)
        assertEquals(8000L, stock(piece))
        assertEquals(3750L, stock(weight))
        assertEquals(0L, stock(kit))
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

    @Test
    fun testSalesHistoryAndCancellationFlow() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Open Session with $100 fund
        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 10000L))
        }.body<CashSessionSummary>()

        // 2. Fetch Coca-Cola presentation
        val prods = client.get("/api/v1/products?barcode=7501055300078").body<List<ProductDto>>()
        val prod = prods.first()
        val pres = prod.presentations.first()

        // 3. Complete sale ($37.00 cash)
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
                            quantityMillis = 2000L,
                            expectedUnitPriceCents = 1850L,
                            expectedTotalCents = 3700L
                        )
                    ),
                    payments = listOf(SalePaymentRequest(method = "CASH", amountCents = 3700L)),
                    expectedTotalCents = 3700L
                )
            )
        }.body<SaleResponse>()

        assertEquals("COMPLETED", sale.status)

        // 4. Query GET /api/v1/sales by sessionId
        val sessionSales = client.get("/api/v1/sales?sessionId=${session.sessionId}").body<List<SaleResponse>>()
        assertEquals(1, sessionSales.size)
        assertEquals(sale.id, sessionSales.first().id)
        assertEquals(sale.folio, sessionSales.first().folio)
        assertEquals(1, sessionSales.first().lines.size)

        // 5. Query GET /api/v1/sales by folio
        val folioSales = client.get("/api/v1/sales?folio=${sale.folio}").body<List<SaleResponse>>()
        assertEquals(1, folioSales.size)
        assertEquals(sale.folio, folioSales.first().folio)

        // 6. Cancel the sale via POST /api/v1/sales/{id}/cancel
        val cancelRes = client.post("/api/v1/sales/${sale.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest(reason = "Error de captura de cajero", authorizedByUserId = "usr-admin"))
        }
        assertEquals(HttpStatusCode.OK, cancelRes.status)
        val cancelledSale = cancelRes.body<SaleResponse>()
        assertEquals("CANCELLED", cancelledSale.status)

        // 7. Verify GET /api/v1/sales?status=CANCELLED
        val cancelledList = client.get("/api/v1/sales?sessionId=${session.sessionId}&status=CANCELLED").body<List<SaleResponse>>()
        assertEquals(1, cancelledList.size)
        assertEquals("CANCELLED", cancelledList.first().status)

        // 8. Verify cash session expected cash reverted
        val summary = client.get("/api/v1/cash-sessions/${session.sessionId}/summary").body<CashSessionSummary>()
        assertEquals(0L, summary.salesCashCents, "Las ventas en efectivo deben descontar la venta cancelada")
        assertEquals(10000L, summary.expectedCashCents, "El efectivo esperado debe regresar al fondo inicial de $100.00")
    }
}

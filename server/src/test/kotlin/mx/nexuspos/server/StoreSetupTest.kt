package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.contracts.catalog.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.identity.*
import mx.nexuspos.contracts.system.*
import mx.nexuspos.server.modules.setup.SetupPurpose
import mx.nexuspos.server.modules.setup.StoreSetupService
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class StoreSetupTest {
    @Test
    fun freshStoreBootstrapsOnceAndRequiresGranularPermissions() = testApplication {
        val db = database()
        application { nexusModule(db, provisioningRequired = true) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        assertEquals(SetupState.NEW, http.get("/api/v1/setup/status").body<SetupStatus>().state)
        assertEquals(HttpStatusCode.Conflict, http.get("/api/v1/products").status)
        val setup = StoreSetupService(db)
        val code = setup.issue(SetupPurpose.COMPLETE)
        val request = StoreSetupRequest(
            code = code, businessName = "Abarrotes pruebas", branchName = "Matriz",
            address = "Calle 1", registerName = "Caja pruebas", rootName = "Propietaria", rootPin = "284619"
        )
        assertEquals(HttpStatusCode.BadRequest, http.post("/api/v1/setup/complete") { payload(request.copy(rootPin = "123456")) }.status)
        val submissions = coroutineScope {
            List(2) { async { http.post("/api/v1/setup/complete") { payload(request) }.status } }.map { it.await() }
        }
        assertEquals(setOf(HttpStatusCode.Created, HttpStatusCode.Conflict), submissions.toSet())
        assertEquals(HttpStatusCode.Unauthorized, http.post("/api/v1/auth/login") { payload(LoginRequest("1234")) }.status)
        val root = http.post("/api/v1/auth/login") { payload(LoginRequest("284619")) }.body<LoginResponse>()
        assertTrue(http.get("/api/v1/users/${root.userId}") { bearerAuth(root.token) }.body<UserDto>().isRoot)
        assertEquals("Matriz", http.get("/api/v1/system/store-profile") { bearerAuth(root.token) }.body<StoreProfile>().branchName)
        assertEquals(HttpStatusCode.Forbidden, http.delete("/api/v1/users/${root.userId}") { bearerAuth(root.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, http.get("/api/v1/users").status)

        val permissions = setOf("customers.view", "customers.update", "customers.credit.payment")
        val role = http.post("/api/v1/roles") {
            bearerAuth(root.token)
            payload(SaveRoleRequest("Cuentas por cobrar", permissions))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<RoleDto>()
        val user = http.post("/api/v1/users") {
            bearerAuth(root.token)
            payload(CreateUserRequest("Operadora", role.id, "5168"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<UserDto>()
        assertEquals(role.id, user.role)
        val operator = http.post("/api/v1/auth/login") { payload(LoginRequest("5168")) }.body<LoginResponse>()
        assertEquals(permissions, operator.permissions.toSet())
        val customer = http.post("/api/v1/customers") {
            bearerAuth(root.token)
            payload(CreateCustomerRequest("Cliente"))
        }.body<CustomerDto>()
        assertEquals(HttpStatusCode.OK, http.get("/api/v1/customers") { bearerAuth(operator.token) }.status)
        assertEquals(HttpStatusCode.Forbidden, http.put("/api/v1/customers/${customer.id}") {
            bearerAuth(operator.token)
            payload(UpdateCustomerRequest(name = customer.name, creditEnabled = true, creditLimitCents = 1500))
        }.status)
        assertEquals(HttpStatusCode.Forbidden, http.post("/api/v1/customers/${customer.id}/reactivate") {
            bearerAuth(operator.token)
        }.status)
        assertEquals(HttpStatusCode.Conflict, http.delete("/api/v1/roles/${role.id}") { bearerAuth(root.token) }.status)
        assertEquals(HttpStatusCode.OK, http.put("/api/v1/roles/${role.id}") {
            bearerAuth(root.token)
            payload(SaveRoleRequest(role.name, permissions - "customers.view"))
        }.status)
        assertEquals(HttpStatusCode.Forbidden, http.get("/api/v1/customers") { bearerAuth(operator.token) }.status)

        val recovery = setup.issue(SetupPurpose.RECOVER)
        assertEquals(HttpStatusCode.NoContent, http.post("/api/v1/setup/recover-root") {
            payload(RecoverRootRequest(recovery, "713805"))
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, http.get("/api/v1/users") { bearerAuth(root.token) }.status)
        assertEquals(HttpStatusCode.OK, http.post("/api/v1/auth/login") { payload(LoginRequest("713805")) }.status)
    }

    @Test
    fun migrationAdoptsExistingAdministratorWithoutLosingOpenShift() = testApplication {
        val db = database()
        application { nexusModule(db) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        assertEquals(SetupState.LEGACY, http.get("/api/v1/setup/status").body<SetupStatus>().state)
        assertEquals(HttpStatusCode.Created, http.post("/api/v1/cash-sessions") {
            payload(OpenSessionRequest("caja-1", "usr-cajero", 500))
        }.status)
        val setup = StoreSetupService(db)
        val code = setup.issue(SetupPurpose.ADOPT)
        assertEquals(HttpStatusCode.BadRequest, http.post("/api/v1/setup/adopt-root") {
            payload(AdoptRootRequest(code, "1234", "139825"))
        }.status)
        assertEquals(HttpStatusCode.NoContent, http.post("/api/v1/setup/adopt-root") {
            payload(AdoptRootRequest(code, "1234", "139825", "5187"))
        }.status)
        val root = http.post("/api/v1/auth/login") { payload(LoginRequest("139825")) }.body<LoginResponse>()
        assertEquals("usr-admin", root.userId)
        assertEquals(HttpStatusCode.OK, http.get("/api/v1/cash-sessions/current") { bearerAuth(root.token) }.status)
    }

    @Test
    fun controlsValidateTillAndPricesWithoutChangingHistoricalSales() = testApplication {
        val db = database()
        application { nexusModule(db, provisioningRequired = true) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        http.get("/api/v1/setup/status")
        val code = StoreSetupService(db).issue(SetupPurpose.COMPLETE)
        assertEquals(HttpStatusCode.Created, http.post("/api/v1/setup/complete") {
            payload(StoreSetupRequest(code, "Tienda", branchName = "Matriz", address = "Calle 1",
                registerName = "Caja", rootName = "Propietaria", rootPin = "284619"))
        }.status)
        val root = http.post("/api/v1/auth/login") { payload(LoginRequest("284619")) }.body<LoginResponse>()
        val preferences = CommercialPreferences(
            quickSaleEnabled = false, requireOpeningFund = false, allowShiftHandoff = true,
            matchBarcodeWithoutLeadingZeros = true, cashDrawerLimitCents = 900,
            creditSalesEnabled = false, creditPaymentMethods = setOf("CARD"), defaultVatPercent = 8
        )
        assertEquals(preferences, http.put("/api/v1/system/settings/commerce") {
            bearerAuth(root.token); payload(preferences)
        }.body<CommercialPreferences>())
        val product = http.post("/api/v1/products") {
            bearerAuth(root.token)
            payload(CreateProductRequest("P", "Bolsa", usesInventory = false,
                presentations = listOf(CreatePresentationRequest(barcode = "000412", name = "Bolsa", priceCents = 1100))))
        }.body<ProductDto>()
        val presentation = product.presentations.single()
        assertEquals(presentation.id, http.get("/api/v1/products?barcode=412") {
            bearerAuth(root.token)
        }.body<List<ProductDto>>().single().presentations.single().id)
        assertEquals(HttpStatusCode.OK, http.put("/api/v1/price-lists/2") {
            bearerAuth(root.token); payload(UpdateCatalogPriceList("Especial", true))
        }.status)
        assertEquals(HttpStatusCode.OK, http.put("/api/v1/products/${product.id}/presentations/${presentation.id}/list-prices/2") {
            bearerAuth(root.token); payload(UpdatePresentationListPrice(975))
        }.status)
        val shift = http.post("/api/v1/cash-sessions") {
            bearerAuth(root.token); payload(OpenSessionRequest("caja-1", root.userId, 100))
        }.body<CashSessionSummary>()
        assertEquals(0L, shift.initialFundCents)
        val cashier = http.post("/api/v1/users") {
            bearerAuth(root.token); payload(CreateUserRequest("Cajera", "CASHIER", "5168"))
        }.body<UserDto>()
        val actor = http.post("/api/v1/auth/login") { payload(LoginRequest("5168")) }.body<LoginResponse>()
        val handoff = http.post("/api/v1/cash-sessions/${shift.sessionId}/handoff") { bearerAuth(actor.token) }
            .body<CashSessionSummary>()
        assertEquals(root.userId, handoff.cashierId)
        assertEquals(cashier.id, handoff.operatorUserId)
        val levelSale = CreateSaleRequest(UUID.randomUUID().toString(), shift.sessionId, actor.userId,
            lines = listOf(SaleLineItemRequest(presentation.id, product.id, "Bolsa", 1000, 975, 975, priceListLevel = 2)),
            payments = listOf(SalePaymentRequest("CASH", 975, 975)), expectedTotalCents = 975)
        assertEquals(HttpStatusCode.Forbidden, http.post("/api/v1/sales") {
            bearerAuth(actor.token); payload(levelSale)
        }.status)
        assertEquals(HttpStatusCode.OK, http.post("/api/v1/cash-sessions/${shift.sessionId}/handoff") { bearerAuth(root.token) }.status)
        val rootSale = levelSale.copy(cashierUserId = root.userId)
        assertEquals(HttpStatusCode.Forbidden, http.post("/api/v1/sales") { bearerAuth(root.token); payload(rootSale) }.status)
        val sale = http.post("/api/v1/sales") {
            bearerAuth(root.token); header("X-Nexus-Cash-Approval", "284619"); payload(rootSale)
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<SaleResponse>()
        assertEquals(2, sale.lines.single().priceListLevel)
        assertEquals(975L, sale.lines.single().unitPriceCents)
        assertEquals(HttpStatusCode.OK, http.put("/api/v1/price-lists/2") {
            bearerAuth(root.token); payload(UpdateCatalogPriceList("Especial", false))
        }.status)
        val historical = http.get("/api/v1/sales/${sale.id}") { bearerAuth(root.token) }.body<SaleResponse>()
        assertEquals(sale.lines.single().priceListLevel, historical.lines.single().priceListLevel)
        assertEquals(sale.totalCents, historical.totalCents)
        val manualLine = rootSale.lines.single().copy(
            priceListLevel = null, manualPriceCents = 810, expectedUnitPriceCents = 810, expectedTotalCents = 810
        )
        val manual = rootSale.copy(idempotencyKey = UUID.randomUUID().toString(), lines = listOf(manualLine),
            payments = listOf(SalePaymentRequest("CARD", 810)), expectedTotalCents = 810)
        val manualResult = http.post("/api/v1/sales") {
            bearerAuth(root.token); payload(manual)
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<SaleResponse>()
        assertTrue(manualResult.lines.single().manualPriceOverride)
        assertEquals(810L, manualResult.lines.single().unitPriceCents)
        assertEquals(HttpStatusCode.Forbidden, http.post("/api/v1/sales") {
            bearerAuth(actor.token); payload(manual.copy(idempotencyKey = UUID.randomUUID().toString(), cashierUserId = actor.userId))
        }.status)
    }

    private inline fun <reified T : Any> HttpRequestBuilder.payload(value: T) {
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    private fun database() = DatabaseFactory(
        "jdbc:h2:mem:setup_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa", ""
    )
}

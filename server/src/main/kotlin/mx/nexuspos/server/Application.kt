package mx.nexuspos.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.cash.CashCutDetailRequest
import mx.nexuspos.contracts.cash.CashCutHistoryRequest
import mx.nexuspos.contracts.cash.ClosedShiftSalesRequest
import mx.nexuspos.contracts.cash.CashMovementHistoryRequest
import mx.nexuspos.contracts.cash.CashMovementRequest
import mx.nexuspos.contracts.cash.ReverseCashMovementRequest
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.UpdatePresentationPricesRequest
import mx.nexuspos.contracts.catalog.UpdateProductKitRequest
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.events.RealtimeEvent
import mx.nexuspos.contracts.identity.AuthorizeRequest
import mx.nexuspos.contracts.identity.CreateUserRequest
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.PairingClaimRequest
import mx.nexuspos.contracts.identity.UpdateUserRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.LotWriteOffRequest
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.purchases.PurchaseAuthorization
import mx.nexuspos.contracts.purchases.ReceivePurchaseRequest
import mx.nexuspos.contracts.purchases.ReturnPurchaseRequest
import mx.nexuspos.contracts.purchases.SaveSupplierRequest
import mx.nexuspos.contracts.reports.SalesAnalysisRequest
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SettingsPreferencesPatch
import mx.nexuspos.server.modules.cash.CashCutHistoryService
import mx.nexuspos.server.modules.cash.CashMovementAuditService
import mx.nexuspos.server.modules.cash.CashSessionService
import mx.nexuspos.server.modules.catalog.CatalogService
import mx.nexuspos.server.modules.credit.CreditService
import mx.nexuspos.server.modules.identity.IdentityService
import mx.nexuspos.server.modules.inventory.InventoryService
import mx.nexuspos.server.modules.inventory.ProductLotsService
import mx.nexuspos.server.modules.loyalty.LoyaltyService
import mx.nexuspos.server.modules.purchases.PurchaseService
import mx.nexuspos.server.modules.reports.ReportsService
import mx.nexuspos.server.modules.reports.SalesAnalysisService
import mx.nexuspos.server.modules.sales.ParkedSalesService
import mx.nexuspos.server.modules.sales.SalesService
import mx.nexuspos.server.modules.system.SettingsService
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.*
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::nexusModule)
        .start(wait = true)
}

fun Application.nexusModule(dbFactory: DatabaseFactory = DatabaseFactory()) {
    // Run Flyway migrations
    dbFactory.migrate()

    // Initialize Services
    val identityService = IdentityService(dbFactory)
    val catalogService = CatalogService(dbFactory)
    val inventoryService = InventoryService(dbFactory)
    val productLotsService = ProductLotsService(dbFactory)
    val cashService = CashSessionService(dbFactory)
    val cashCutHistory = CashCutHistoryService(dbFactory)
    val cashMovementAudit = CashMovementAuditService(dbFactory)
    val creditService = CreditService(dbFactory)
    val loyaltyService = LoyaltyService(dbFactory)
    val settingsService = SettingsService(dbFactory)
    val salesService = SalesService(dbFactory, settingsService)
    val parkedSalesService = ParkedSalesService(dbFactory)
    val reportsService = ReportsService(dbFactory)
    val salesAnalysis = SalesAnalysisService(dbFactory)
    val purchaseService = PurchaseService(dbFactory)

    fun requireSupervisor(pin: String, action: String): String {
        if (!pin.matches(Regex("[0-9]{4}"))) throw UnauthorizedException("Captura el PIN de un encargado.")
        val authorization = identityService.authorizeSupervisor(AuthorizeRequest(pin, action))
        if (!authorization.authorized) throw ForbiddenException("PIN de encargado incorrecto o usuario inactivo.")
        return authorization.supervisorId
    }

    val activeWsSessions = Collections.synchronizedSet(HashSet<DefaultWebSocketServerSession>())

    val jsonConfig = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    suspend fun broadcastEventJson(type: String, jsonPayload: String) {
        val event = RealtimeEvent(type = type, payloadJson = jsonPayload, timestamp = System.currentTimeMillis())
        val messageText = jsonConfig.encodeToString(event)
        val sessionsToNotify = synchronized(activeWsSessions) { activeWsSessions.toList() }
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()

        for (session in sessionsToNotify) {
            try {
                session.send(Frame.Text(messageText))
            } catch (e: Exception) {
                deadSessions.add(session)
            }
        }

        if (deadSessions.isNotEmpty()) {
            synchronized(activeWsSessions) {
                activeWsSessions.removeAll(deadSessions.toSet())
            }
        }
    }

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("Idempotency-Key")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CallLogging)

    install(StatusPages) {
        exception<AppException> { call, cause ->
            val status = when (cause) {
                is NotFoundException -> HttpStatusCode.NotFound
                is UnauthorizedException -> HttpStatusCode.Unauthorized
                is ForbiddenException -> HttpStatusCode.Forbidden
                is PriceChangedException, is IdempotencyMismatchException, is SessionClosedException,
                is StockChangedException, is DuplicatePurchaseDocumentException -> HttpStatusCode.Conflict
                is ValidationException -> HttpStatusCode.BadRequest
                is CreditLimitExceededException, is InsufficientStockException -> HttpStatusCode.UnprocessableEntity
                else -> HttpStatusCode.InternalServerError
            }
            call.respond(
                status,
                ApiError(
                    code = cause.code,
                    message = cause.message,
                    details = cause.details,
                    requestId = call.request.uri
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.application.environment.log.error(
                "❌ Error 500 en [${call.request.httpMethod.value} ${call.request.uri}]: ${cause.message}",
                cause
            )
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    code = "INTERNAL_SERVER_ERROR",
                    message = cause.message ?: "Error interno del servidor",
                    details = mapOf("stackTrace" to cause.stackTraceToString().take(1000)),
                    requestId = call.request.uri
                )
            )
        }
    }

    routing {
        get("/") {
            call.respondText("NexusPOS LAN-First Server v2.0 (PostgreSQL)", ContentType.Text.Plain)
        }

        get("/health") {
            val ready = withContext(Dispatchers.IO) {
                try {
                    dbFactory.dataSource.connection.use { it.isValid(2) }
                } catch (_: java.sql.SQLException) {
                    false
                }
            }
            if (ready) call.respond(mapOf("status" to "UP"))
            else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DOWN"))
        }

        route("/api/v1") {
            // System
            get("/system/info") {
                call.respond(settingsService.getSystemInfo())
            }

            get("/system/settings") {
                call.respond(settingsService.getSettings())
            }

            patch("/system/settings") {
                val req = call.receive<SettingsDto>()
                val updated = settingsService.updateSettings(req)
                broadcastEventJson("settings.changed", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            patch("/system/settings/preferences") {
                val updated = settingsService.updatePreferences(call.receive<SettingsPreferencesPatch>())
                broadcastEventJson("settings.changed", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            put("/system/settings") {
                val req = call.receive<SettingsDto>()
                val updated = settingsService.updateSettings(req)
                broadcastEventJson("settings.changed", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            // Users Management
            get("/users") {
                val includeInactive = call.request.queryParameters["includeInactive"]?.toBooleanStrictOrNull() ?: true
                call.respond(identityService.getAllUsers(includeInactive))
            }

            get("/users/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de usuario requerido")
                call.respond(identityService.getUserById(id))
            }

            post("/users") {
                val req = call.receive<CreateUserRequest>()
                val user = identityService.createUser(req)
                broadcastEventJson("user.created", jsonConfig.encodeToString(user))
                call.respond(HttpStatusCode.Created, user)
            }

            put("/users/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de usuario requerido")
                val req = call.receive<UpdateUserRequest>()
                val updated = identityService.updateUser(id, req)
                broadcastEventJson("user.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            delete("/users/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de usuario requerido")
                val deactivated = identityService.deactivateUser(id)
                broadcastEventJson("user.deactivated", jsonConfig.encodeToString(deactivated))
                call.respond(deactivated)
            }

            post("/users/{id}/reactivate") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de usuario requerido")
                val reactivated = identityService.reactivateUser(id)
                broadcastEventJson("user.updated", jsonConfig.encodeToString(reactivated))
                call.respond(reactivated)
            }

            // Identity & Auth
            post("/auth/login") {
                val req = call.receive<LoginRequest>()
                val res = identityService.login(req)
                call.respond(res)
            }

            post("/auth/authorize") {
                val req = call.receive<AuthorizeRequest>()
                val res = identityService.authorizeSupervisor(req)
                call.respond(res)
            }

            post("/auth/authorize-supervisor") {
                val req = call.receive<AuthorizeRequest>()
                val res = identityService.authorizeSupervisor(req)
                call.respond(res)
            }

            post("/pairing/codes") {
                val res = identityService.createPairingCode()
                call.respond(HttpStatusCode.Created, res)
            }

            post("/pairing/claim") {
                val req = call.receive<PairingClaimRequest>()
                val res = identityService.claimPairingCode(req)
                call.respond(HttpStatusCode.Created, res)
            }

            // Catalog
            get("/departments") {
                call.respond(catalogService.getAllDepartments())
            }

            post("/departments") {
                val req = call.receive<CreateDepartmentRequest>()
                val created = catalogService.createDepartment(req)
                broadcastEventJson("department.created", jsonConfig.encodeToString(created))
                call.respond(HttpStatusCode.Created, created)
            }

            put("/departments/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de departamento requerido")
                val req = call.receive<UpdateDepartmentRequest>()
                val updated = catalogService.updateDepartment(id, req)
                broadcastEventJson("department.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            delete("/departments/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de departamento requerido")
                catalogService.deleteDepartment(id)
                broadcastEventJson("department.deleted", jsonConfig.encodeToString(mapOf("id" to id)))
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted", "id" to id))
            }

            get("/products") {
                val barcode = call.request.queryParameters["barcode"]
                val query = call.request.queryParameters["query"]
                val departmentId = call.request.queryParameters["departmentId"]
                val includeInactive = call.request.queryParameters["includeInactive"]?.toBooleanStrictOrNull() ?: false
                val products = catalogService.searchProducts(barcode, query, departmentId, includeInactive)
                call.respond(products)
            }

            get("/products/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                call.respond(catalogService.getProductById(id))
            }

            post("/products") {
                val req = call.receive<CreateProductRequest>()
                val product = catalogService.createProduct(req)
                broadcastEventJson("product.created", jsonConfig.encodeToString(product))
                call.respond(HttpStatusCode.Created, product)
            }

            put("/products/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                val req = call.receive<UpdateProductRequest>()
                val updated = catalogService.updateProduct(id, req)
                broadcastEventJson("product.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            put("/products/{id}/presentations/{presentationId}/prices") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                val presentationId = call.parameters["presentationId"]
                    ?: throw ValidationException("ID de presentación requerido")
                val request = call.receive<UpdatePresentationPricesRequest>()
                val updated = catalogService.updatePresentationPrices(id, presentationId, request.tiers)
                broadcastEventJson("product.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            put("/products/{id}/kit") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                val request = call.receive<UpdateProductKitRequest>()
                val updated = catalogService.updateProductKit(id, request.components)
                broadcastEventJson("product.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            delete("/products/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                val deactivated = catalogService.deactivateProduct(id)
                broadcastEventJson("product.deactivated", jsonConfig.encodeToString(deactivated))
                call.respond(deactivated)
            }

            post("/products/{id}/reactivate") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de producto requerido")
                val reactivated = catalogService.reactivateProduct(id)
                broadcastEventJson("product.updated", jsonConfig.encodeToString(reactivated))
                call.respond(reactivated)
            }

            // Bulk Catalog Export & Import (Excel / CSV)
            get("/catalog/export") {
                val csv = catalogService.exportCatalogCsv()
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"catalogo_nexuspos.csv\"")
                call.respondText(csv, ContentType.Text.CSV.withCharset(Charsets.UTF_8))
            }

            post("/catalog/import") {
                val req = call.receive<CatalogImportRequest>()
                val result = catalogService.importCatalogRows(req.rows)
                broadcastEventJson("catalog.changed", "{}")
                call.respond(result)
            }

            // Inventory
            get("/inventory/summary") {
                call.respond(inventoryService.getInventorySummary())
            }

            get("/inventory/reorder-suggestions") {
                val departmentId = call.request.queryParameters["departmentId"]
                val onlyOutOfStock = call.request.queryParameters["onlyOutOfStock"]?.toBooleanStrictOrNull() ?: false
                call.respond(inventoryService.getReorderSuggestions(departmentId, onlyOutOfStock))
            }

            get("/inventory/movements") {
                val productId = call.request.queryParameters["productId"]
                val type = call.request.queryParameters["type"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                call.respond(inventoryService.getStockMovements(productId, type, limit))
            }

            post("/inventory/movements") {
                val req = call.receive<RegisterStockMovementRequest>()
                val movement = inventoryService.registerMovement(req)
                broadcastEventJson("inventory.movement.created", jsonConfig.encodeToString(movement))
                broadcastEventJson("inventory.updated", jsonConfig.encodeToString(mapOf("productId" to req.productId)))
                call.respond(HttpStatusCode.Created, movement)
            }

            // Product Lots & Expirations (Perecederos)
            get("/inventory/lots") {
                val productId = call.request.queryParameters["productId"]
                val statusStr = call.request.queryParameters["status"]
                val statusFilter = statusStr?.let {
                    try { LotExpirationStatus.valueOf(it.uppercase()) } catch (_: Exception) { null }
                }
                call.respond(productLotsService.getLots(productId, statusFilter))
            }

            post("/inventory/lots") {
                val req = call.receive<CreateProductLotRequest>()
                val lot = productLotsService.createLot(req)
                broadcastEventJson("inventory.lot.created", jsonConfig.encodeToString(lot))
                broadcastEventJson("inventory.updated", jsonConfig.encodeToString(mapOf("productId" to req.productId)))
                call.respond(HttpStatusCode.Created, lot)
            }

            get("/inventory/lots/report") {
                call.respond(productLotsService.getExpirationsReport())
            }

            post("/inventory/lots/{id}/write-off") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de lote requerido")
                val req = call.receive<LotWriteOffRequest>()
                val updatedLot = productLotsService.writeOffLot(id, req)
                broadcastEventJson("inventory.lot.written_off", jsonConfig.encodeToString(updatedLot))
                broadcastEventJson("inventory.updated", jsonConfig.encodeToString(mapOf("productId" to updatedLot.productId)))
                call.respond(updatedLot)
            }

            get("/inventory/lots/expiring/{productId}") {
                val productId = call.parameters["productId"] ?: throw ValidationException("ID de producto requerido")
                val lot = productLotsService.getExpiringLotForProduct(productId)
                if (lot == null) {
                    call.respond(HttpStatusCode.NotFound, ApiError("NO_ACTIVE_LOTS", "No hay lotes activos para el producto"))
                } else {
                    call.respond(lot)
                }
            }

            // Cash Sessions
            post("/cash-sessions") {
                val req = call.receive<OpenSessionRequest>()
                val session = cashService.openSession(req)
                broadcastEventJson("cash.session.opened", jsonConfig.encodeToString(session))
                call.respond(HttpStatusCode.Created, session)
            }

            post("/cash-sessions/history/search") {
                val req = call.receive<CashCutHistoryRequest>()
                requireSupervisor(req.pin, "CASH_CUT_HISTORY")
                call.respond(cashCutHistory.search(req))
            }

            post("/cash-sessions/history/{id}") {
                val req = call.receive<CashCutDetailRequest>()
                requireSupervisor(req.pin, "CASH_CUT_HISTORY")
                val id = call.parameters["id"] ?: throw ValidationException("Indica el corte.")
                call.respond(cashCutHistory.detail(id))
            }

            post("/cash-sessions/history/{id}/sales") {
                val req = call.receive<ClosedShiftSalesRequest>()
                requireSupervisor(req.pin, "CLOSED_SHIFT_SALES")
                val id = call.parameters["id"] ?: throw ValidationException("Indica el turno cerrado.")
                call.respond(salesService.getClosedShiftSales(id, req.folio, req.beforeFolio))
            }

            post("/cash-sessions/history/{id}/sales/{saleId}") {
                val req = call.receive<CashCutDetailRequest>()
                requireSupervisor(req.pin, "CLOSED_SHIFT_SALES")
                val id = call.parameters["id"] ?: throw ValidationException("Indica el turno cerrado.")
                val saleId = call.parameters["saleId"] ?: throw ValidationException("Indica la venta.")
                call.respond(salesService.getClosedShiftSale(id, saleId))
            }

            get("/cash-sessions/current") {
                val registerId = call.request.queryParameters["registerId"] ?: "caja-1"
                val session = cashService.getCurrentOpenSession(registerId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, ApiError("NO_OPEN_SESSION", "No hay sesión abierta para la caja '$registerId'"))
                } else {
                    call.respond(session)
                }
            }

            get("/cash-sessions/{id}/summary") {
                val sessionId = call.parameters["id"] ?: throw ValidationException("ID de sesión requerido")
                call.respond(cashService.getSessionSummary(sessionId))
            }

            post("/cash-sessions/{id}/movements") {
                val sessionId = call.parameters["id"] ?: throw ValidationException("ID de sesión requerido")
                val req = call.receive<CashMovementRequest>()
                cashService.recordMovement(sessionId, req)
                call.respond(HttpStatusCode.Created, mapOf("status" to "OK"))
            }

            post("/cash-sessions/{id}/movements/history") {
                val request = call.receive<CashMovementHistoryRequest>()
                requireSupervisor(request.pin, "CASH_MOVEMENT_HISTORY")
                val sessionId = call.parameters["id"] ?: throw ValidationException("Indica el turno de caja.")
                call.respond(cashMovementAudit.list(sessionId, request.before))
            }

            post("/cash-sessions/{id}/movements/{movementId}/reverse") {
                val request = call.receive<ReverseCashMovementRequest>()
                val supervisorId = requireSupervisor(request.pin, "REVERSE_CASH_MOVEMENT")
                val sessionId = call.parameters["id"] ?: throw ValidationException("Indica el turno de caja.")
                val movementId = call.parameters["movementId"] ?: throw ValidationException("Indica el movimiento.")
                call.respond(cashMovementAudit.reverse(sessionId, movementId, request.reason, supervisorId))
            }

            post("/cash-sessions/{id}/close") {
                val sessionId = call.parameters["id"] ?: throw ValidationException("ID de sesión requerido")
                val req = call.receive<CloseSessionRequest>()
                val closed = cashService.closeSession(sessionId, req)
                broadcastEventJson("cash.session.closed", jsonConfig.encodeToString(closed))
                call.respond(closed)
            }

            // Customers & Credit
            get("/customers") {
                val q = call.request.queryParameters["query"]
                val includeInactive = call.request.queryParameters["includeInactive"]?.toBooleanStrictOrNull() ?: false
                call.respond(creditService.searchCustomers(q, includeInactive))
            }

            get("/customers/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                call.respond(creditService.getCustomer(id))
            }

            post("/customers") {
                val req = call.receive<CreateCustomerRequest>()
                val customer = creditService.createCustomer(req)
                broadcastEventJson("customer.created", jsonConfig.encodeToString(customer))
                call.respond(HttpStatusCode.Created, customer)
            }

            put("/customers/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                val req = call.receive<UpdateCustomerRequest>()
                val updated = creditService.updateCustomer(id, req)
                broadcastEventJson("customer.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            delete("/customers/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                val deactivated = creditService.deactivateCustomer(id)
                broadcastEventJson("customer.deactivated", jsonConfig.encodeToString(deactivated))
                call.respond(deactivated)
            }

            get("/customers/{id}/statement") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                call.respond(creditService.getCustomerStatement(id))
            }

            post("/customers/{id}/credit/payments") {
                val req = call.receive<RegisterCreditPaymentRequest>()
                val mov = creditService.registerPayment(req)
                broadcastEventJson("credit.payment", jsonConfig.encodeToString(mov))
                call.respond(HttpStatusCode.Created, mov)
            }

            get("/customers/{id}/credit/movements") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                call.respond(creditService.getMovements(id))
            }

            // Customer Loyalty / Monedero Electrónico
            get("/customers/{id}/loyalty") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                call.respond(loyaltyService.getLoyaltyMovements(id))
            }

            get("/customers/{id}/loyalty/summary") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                call.respond(loyaltyService.getLoyaltySummary(id))
            }

            post("/customers/{id}/loyalty/adjust") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de cliente requerido")
                val req = call.receive<AdjustLoyaltyBalanceRequest>()
                val updated = loyaltyService.adjustLoyaltyBalance(req.copy(customerId = id))
                broadcastEventJson("customer.loyalty.adjusted", jsonConfig.encodeToString(updated))
                broadcastEventJson("customer.updated", jsonConfig.encodeToString(updated))
                call.respond(updated)
            }

            // Sales
            get("/sales") {
                val sessionId = call.request.queryParameters["sessionId"]
                val folio = call.request.queryParameters["folio"]?.toLongOrNull()
                val status = call.request.queryParameters["status"]
                val startDate = call.request.queryParameters["startDate"]?.toLongOrNull()
                val endDate = call.request.queryParameters["endDate"]?.toLongOrNull()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val sales = salesService.getSales(
                    sessionId = sessionId,
                    startDate = startDate,
                    endDate = endDate,
                    folio = folio,
                    status = status,
                    limit = limit
                )
                call.respond(sales)
            }

            post("/sales") {
                val req = call.receive<CreateSaleRequest>()
                val sale = salesService.completeSale(req)
                broadcastEventJson("sale.completed", jsonConfig.encodeToString(sale))
                call.respond(HttpStatusCode.Created, sale)
            }

            get("/sales/{id}") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de venta requerido")
                call.respond(salesService.getSaleById(id))
            }

            post("/sales/{id}/cancel") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de venta requerido")
                val req = call.receive<CancelSaleRequest>()
                val cancelled = salesService.cancelSale(id, req)
                broadcastEventJson("sale.cancelled", jsonConfig.encodeToString(cancelled))
                call.respond(cancelled)
            }

            post("/sales/{id}/refund") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de venta requerido")
                val req = call.receive<CreateSaleRefundRequest>()
                val refund = salesService.refundSale(id, req)
                broadcastEventJson("sale.refunded", jsonConfig.encodeToString(refund))
                call.respond(HttpStatusCode.Created, refund)
            }

            get("/sales/{id}/refunds") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de venta requerido")
                call.respond(salesService.getRefundsBySaleId(id))
            }

            route("/purchases") {
                get("/suppliers") {
                    call.respond(purchaseService.listSuppliers())
                }
                post("/suppliers") {
                    val req = call.receive<PurchaseAuthorization<SaveSupplierRequest>>()
                    requireSupervisor(req.pin, "PURCHASES")
                    call.respond(HttpStatusCode.Created, purchaseService.saveSupplier(null, req.data))
                }
                put("/suppliers/{id}") {
                    val req = call.receive<PurchaseAuthorization<SaveSupplierRequest>>()
                    requireSupervisor(req.pin, "PURCHASES")
                    val id = call.parameters["id"] ?: throw ValidationException("Indica el proveedor.")
                    call.respond(purchaseService.saveSupplier(id, req.data))
                }
                get("/receipts") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    call.respond(purchaseService.listReceipts(limit, offset))
                }
                post("/receipts") {
                    val req = call.receive<PurchaseAuthorization<ReceivePurchaseRequest>>()
                    call.respond(HttpStatusCode.Created, purchaseService.receive(req.data, requireSupervisor(req.pin, "PURCHASES")))
                }
                get("/receipts/{id}") {
                    val id = call.parameters["id"] ?: throw ValidationException("Indica la compra.")
                    call.respond(purchaseService.getReceipt(id))
                }
                post("/receipts/{id}/returns") {
                    val id = call.parameters["id"] ?: throw ValidationException("Indica la compra.")
                    val req = call.receive<PurchaseAuthorization<ReturnPurchaseRequest>>()
                    call.respond(HttpStatusCode.Created, purchaseService.returnStock(id, req.data, requireSupervisor(req.pin, "PURCHASES")))
                }
            }

            // Financial & Daily Reports
            post("/reports/sales-analysis") {
                val request = call.receive<SalesAnalysisRequest>()
                requireSupervisor(request.pin, "SALES_ANALYSIS")
                call.respond(salesAnalysis.report(request))
            }

            get("/reports/daily") {
                val dateParam = call.request.queryParameters["date"]?.toLongOrNull() ?: System.currentTimeMillis()
                val sessionParam = call.request.queryParameters["sessionId"]
                val report = reportsService.getDailyFinancialReport(dateParam, sessionParam)
                call.respond(report)
            }

            // Parked Sales (Tickets en Espera)
            get("/parked-sales") {
                val registerId = call.request.queryParameters["registerId"]
                call.respond(parkedSalesService.getParkedSales(registerId))
            }

            post("/parked-sales") {
                val req = call.receive<ParkSaleRequest>()
                val parked = parkedSalesService.parkSale(req)
                broadcastEventJson("parked_sale.created", jsonConfig.encodeToString(parked))
                call.respond(HttpStatusCode.Created, parked)
            }

            post("/parked-sales/{id}/retrieve") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de ticket en espera requerido")
                val req = call.receive<RetrieveParkedSaleRequest>()
                val retrieved = parkedSalesService.retrieveParkedSale(id, req)
                broadcastEventJson("parked_sale.retrieved", jsonConfig.encodeToString(retrieved))
                call.respond(retrieved)
            }

            post("/parked-sales/{id}/discard") {
                val id = call.parameters["id"] ?: throw ValidationException("ID de ticket en espera requerido")
                val userId = call.request.queryParameters["userId"] ?: "cajero"
                val discarded = parkedSalesService.discardParkedSale(id, userId)
                broadcastEventJson("parked_sale.discarded", jsonConfig.encodeToString(discarded))
                call.respond(discarded)
            }
        }

        // WebSockets
        webSocket("/ws/events") {
            activeWsSessions.add(this)
            try {
                for (frame in incoming) {
                    // client heartbeat/messages
                }
            } catch (e: ClosedSendChannelException) {
                // client disconnected
            } finally {
                activeWsSessions.remove(this)
            }
        }
    }
}

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
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.cash.CashMovementRequest
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CatalogImportRequest
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
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.server.modules.cash.CashSessionService
import mx.nexuspos.server.modules.catalog.CatalogService
import mx.nexuspos.server.modules.credit.CreditService
import mx.nexuspos.server.modules.identity.IdentityService
import mx.nexuspos.server.modules.inventory.InventoryService
import mx.nexuspos.server.modules.reports.ReportsService
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
    val cashService = CashSessionService(dbFactory)
    val creditService = CreditService(dbFactory)
    val settingsService = SettingsService(dbFactory)
    val salesService = SalesService(dbFactory, settingsService)
    val parkedSalesService = ParkedSalesService(dbFactory)
    val reportsService = ReportsService(dbFactory)

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
                is PriceChangedException, is IdempotencyMismatchException, is SessionClosedException -> HttpStatusCode.Conflict
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
            call.respond(mapOf("status" to "UP"))
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

            // Cash Sessions
            post("/cash-sessions") {
                val req = call.receive<OpenSessionRequest>()
                val session = cashService.openSession(req)
                broadcastEventJson("cash.session.opened", jsonConfig.encodeToString(session))
                call.respond(HttpStatusCode.Created, session)
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

            // Financial & Daily Reports
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

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
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.events.RealtimeEvent
import mx.nexuspos.contracts.identity.AuthorizeRequest
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.PairingClaimRequest
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.server.modules.cash.CashSessionService
import mx.nexuspos.server.modules.catalog.CatalogService
import mx.nexuspos.server.modules.credit.CreditService
import mx.nexuspos.server.modules.identity.IdentityService
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
    val cashService = CashSessionService(dbFactory)
    val creditService = CreditService(dbFactory)
    val settingsService = SettingsService(dbFactory)
    val salesService = SalesService(dbFactory, settingsService)

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

            get("/products") {
                val barcode = call.request.queryParameters["barcode"]
                val query = call.request.queryParameters["query"]
                val products = catalogService.searchProducts(barcode, query)
                call.respond(products)
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

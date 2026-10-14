package org.ozafry.nexuspos.server

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
import org.ozafry.nexuspos.models.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class CentralServerState {
    val categories = ConcurrentHashMap<String, CategoryDto>()
    val products = ConcurrentHashMap<String, ProductDto>()
    val sales = ConcurrentHashMap<String, SaleDto>()
    val shifts = ConcurrentHashMap<String, CashShiftDto>()
    val processedEventIds = Collections.synchronizedSet(HashSet<String>())
    val supervisorSessions = Collections.synchronizedSet(HashSet<DefaultWebSocketServerSession>())

    init {
        seedInitialData()
    }

    private fun seedInitialData() {
        val now = System.currentTimeMillis()
        val catBebidas = CategoryDto("cat-1", "Bebidas", "#1E88E5", "coffee")
        val catAlimentos = CategoryDto("cat-2", "Alimentos", "#43A047", "fastfood")
        val catSnacks = CategoryDto("cat-3", "Snacks", "#FB8C00", "cookie")

        categories[catBebidas.id] = catBebidas
        categories[catAlimentos.id] = catAlimentos
        categories[catSnacks.id] = catSnacks

        val initialProducts = listOf(
            ProductDto("prod-1", "BEB-001", "7501001", "Café Americano 12oz", catBebidas.id, 35.0, 10.0, 0.16, 100.0, true, now),
            ProductDto("prod-2", "BEB-002", "7501002", "Capuchino 12oz", catBebidas.id, 45.0, 15.0, 0.16, 80.0, true, now),
            ProductDto("prod-3", "BEB-003", "7501003", "Refresco Cola 600ml", catBebidas.id, 22.0, 12.0, 0.16, 150.0, true, now),
            ProductDto("prod-4", "ALI-001", "7502001", "Sandwich Jamón y Queso", catAlimentos.id, 65.0, 25.0, 0.16, 50.0, true, now),
            ProductDto("prod-5", "ALI-002", "7502002", "Croissant de Mantequilla", catAlimentos.id, 40.0, 18.0, 0.16, 40.0, true, now),
            ProductDto("prod-6", "SNK-001", "7503001", "Papas Fritas Saladas", catSnacks.id, 25.0, 12.0, 0.16, 120.0, true, now),
            ProductDto("prod-7", "SNK-002", "7503002", "Galletas de Avena", catSnacks.id, 20.0, 8.0, 0.16, 90.0, true, now)
        )

        for (p in initialProducts) {
            products[p.id] = p
        }
    }

    suspend fun broadcastTelemetry(event: TelemetryEvent) {
        val jsonPayload = Json.encodeToString(event)
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()

        synchronized(supervisorSessions) {
            for (session in supervisorSessions) {
                try {
                    session.outgoing.trySend(Frame.Text(jsonPayload))
                } catch (e: Exception) {
                    deadSessions.add(session)
                }
            }
            supervisorSessions.removeAll(deadSessions)
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::nexusModule)
        .start(wait = true)
}

fun Application.nexusModule() {
    val state = CentralServerState()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
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
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Error interno del servidor"))
            )
        }
    }

    routing {
        get("/") {
            call.respond(mapOf("app" to "NexusPOS Server", "status" to "READY"))
        }

        get("/health") {
            call.respond(
                mapOf(
                    "status" to "UP",
                    "version" to "1.0.0",
                    "totalProducts" to state.products.size.toString(),
                    "totalSales" to state.sales.size.toString(),
                    "connectedSupervisors" to state.supervisorSessions.size.toString(),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
        }

        route("/api") {
            // Catalogo de productos y categorias
            get("/catalog") {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val filteredProducts = state.products.values
                    .filter { it.updatedAt >= since }
                    .toList()
                call.respond(
                    SyncPullResponse(
                        products = filteredProducts,
                        categories = state.categories.values.toList(),
                        serverTimestamp = System.currentTimeMillis()
                    )
                )
            }

            post("/catalog/products") {
                val product = call.receive<ProductDto>()
                state.products[product.id] = product
                call.respond(HttpStatusCode.Created, product)
            }

            // Sync Push: Cajas envían eventos fuera de línea
            post("/sync/push") {
                val pushRequest = call.receive<SyncPushRequest>()
                val accepted = mutableListOf<String>()
                val rejected = mutableMapOf<String, String>()

                for (event in pushRequest.events) {
                    try {
                        if (state.processedEventIds.contains(event.id)) {
                            // Idempotencia: Ya procesado anteriormente con éxito
                            accepted.add(event.id)
                            continue
                        }

                        when (event.aggregateType) {
                            "SALE" -> {
                                val sale = Json.decodeFromString<SaleDto>(event.payloadJson)
                                state.sales[sale.id] = sale

                                // Descontar stock centralizado
                                for (item in sale.items) {
                                    val currentProduct = state.products[item.productId]
                                    if (currentProduct != null) {
                                        val newStock = currentProduct.stockQuantity - item.quantity
                                        val updatedProduct = currentProduct.copy(
                                            stockQuantity = newStock,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        state.products[item.productId] = updatedProduct

                                        if (newStock < 10.0) {
                                            state.broadcastTelemetry(
                                                TelemetryEvent(
                                                    type = "LOW_STOCK",
                                                    registerId = pushRequest.registerId,
                                                    cashierName = sale.cashierName,
                                                    message = "Stock bajo para '${currentProduct.name}': quedan $newStock",
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                        }
                                    }
                                }

                                state.processedEventIds.add(event.id)
                                accepted.add(event.id)

                                // Notificar a los supervisores conectados vía WebSocket
                                state.broadcastTelemetry(
                                    TelemetryEvent(
                                        type = "SALE_COMPLETED",
                                        registerId = sale.registerId,
                                        cashierName = sale.cashierName,
                                        message = "Venta ${sale.id.takeLast(6)} completada por $${sale.total} (${sale.paymentMethod})",
                                        totalAmount = sale.total,
                                        timestamp = sale.createdAt
                                    )
                                )
                            }
                            "SHIFT" -> {
                                val shift = Json.decodeFromString<CashShiftDto>(event.payloadJson)
                                state.shifts[shift.id] = shift
                                state.processedEventIds.add(event.id)
                                accepted.add(event.id)

                                val eventType = if (shift.status == "OPEN") "SHIFT_OPENED" else "SHIFT_CLOSED"
                                state.broadcastTelemetry(
                                    TelemetryEvent(
                                        type = eventType,
                                        registerId = shift.registerId,
                                        cashierName = shift.cashierName,
                                        message = "Turno ${shift.status} en caja '${shift.registerId}' por cajero ${shift.cashierName}",
                                        totalAmount = if (shift.status == "CLOSED") shift.actualCash else shift.initialCash,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                            else -> {
                                state.processedEventIds.add(event.id)
                                accepted.add(event.id)
                            }
                        }
                    } catch (e: Exception) {
                        rejected[event.id] = e.message ?: "Error al procesar evento"
                    }
                }

                call.respond(
                    SyncPushResponse(
                        acceptedEventIds = accepted,
                        rejectedEventIds = rejected,
                        serverTimestamp = System.currentTimeMillis()
                    )
                )
            }

            // Endpoints para app de supervisión
            get("/sales") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val sortedSales = state.sales.values
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                call.respond(sortedSales)
            }

            get("/shifts") {
                call.respond(state.shifts.values.sortedByDescending { it.openedAt })
            }
        }

        // WebSocket para telemetría en tiempo real hacia supervisores
        webSocket("/ws/telemetry") {
            state.supervisorSessions.add(this)
            try {
                // Mensaje inicial de bienvenida
                send(
                    Json.encodeToString(
                        TelemetryEvent(
                            type = "CONNECTED",
                            registerId = "SERVER",
                            cashierName = "System",
                            message = "Conectado al canal de telemetría de NexusPOS",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )

                for (frame in incoming) {
                    // Mantener vivo el canal escuchando pings o mensajes
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                }
            } catch (e: ClosedSendChannelException) {
                // Desconexion normal
            } catch (e: Throwable) {
                // Error o cierre
            } finally {
                state.supervisorSessions.remove(this)
            }
        }
    }
}

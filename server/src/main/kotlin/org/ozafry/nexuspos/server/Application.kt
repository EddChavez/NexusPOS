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
        val catDespensa = CategoryDto("cat-despensa", "Despensa y Abarrotes", "#1E88E5", "kitchen")
        val catBebidas = CategoryDto("cat-bebidas", "Bebidas y Refrescos", "#0284C7", "local_drink")
        val catLacteos = CategoryDto("cat-lacteos", "Lácteos y Huevos", "#059669", "egg")
        val catBotanas = CategoryDto("cat-botanas", "Botanas y Dulces", "#D97706", "cookie")
        val catGranel = CategoryDto("cat-granel", "Frutas y Granel", "#16A34A", "scale")
        val catLimpieza = CategoryDto("cat-limpieza", "Limpieza y Hogar", "#7C3AED", "cleaning_services")

        listOf(catDespensa, catBebidas, catLacteos, catBotanas, catGranel, catLimpieza).forEach {
            categories[it.id] = it
        }

        val initialProducts = listOf(
            // Despensa y Abarrotes
            ProductDto("p-ab-1", "AB-001", "7501000111204", "Arroz Morelos 1kg", catDespensa.id, 28.50, 21.00, 0.00, 45.0, "PZA", true, now),
            ProductDto("p-ab-2", "AB-002", "7501000111211", "Frijol Negro Verde Valle 1kg", catDespensa.id, 38.00, 29.00, 0.00, 40.0, "PZA", true, now),
            ProductDto("p-ab-3", "AB-003", "7501011115501", "Aceite Vegetal 123 1L", catDespensa.id, 42.00, 33.50, 0.00, 35.0, "PZA", true, now),
            ProductDto("p-ab-4", "AB-004", "7501020512345", "Atún Dolores en Agua 140g", catDespensa.id, 22.50, 16.00, 0.00, 60.0, "PZA", true, now),
            ProductDto("p-ab-5", "AB-005", "7501030411112", "Azúcar Estándar Zulka 1kg", catDespensa.id, 32.00, 24.00, 0.00, 50.0, "PZA", true, now),
            ProductDto("p-ab-6", "AB-006", "7501008044455", "Sopa de Fideo La Moderna 200g", catDespensa.id, 11.50, 7.50, 0.00, 80.0, "PZA", true, now),
            ProductDto("p-ab-7", "AB-007", "7501058611113", "Mayonesa McCormick Limón 390g", catDespensa.id, 46.00, 36.00, 0.16, 25.0, "PZA", true, now),
            ProductDto("p-ab-8", "AB-008", "7501059288882", "Café Nescafé Clásico 120g", catDespensa.id, 78.00, 61.00, 0.16, 20.0, "PZA", true, now),

            // Bebidas y Refrescos
            ProductDto("p-beb-1", "BEB-001", "7501055310884", "Coca-Cola Original 600ml", catBebidas.id, 19.00, 14.50, 0.16, 120.0, "PZA", true, now),
            ProductDto("p-beb-2", "BEB-002", "7501055310891", "Coca-Cola 2.5L No Retornable", catBebidas.id, 44.00, 34.00, 0.16, 40.0, "PZA", true, now),
            ProductDto("p-beb-3", "BEB-003", "7501055300052", "Agua Purificada Ciel 1L", catBebidas.id, 13.00, 7.50, 0.16, 90.0, "PZA", true, now),
            ProductDto("p-beb-4", "BEB-004", "7501055322214", "Jugo del Valle Mango 413ml", catBebidas.id, 18.00, 12.50, 0.16, 45.0, "PZA", true, now),
            ProductDto("p-beb-5", "BEB-005", "7501064191113", "Refresco Jarrito Mandarina 600ml", catBebidas.id, 14.00, 9.50, 0.16, 50.0, "PZA", true, now),

            // Lácteos y Huevos
            ProductDto("p-lac-1", "LAC-001", "7501020511119", "Leche Entera Lala 1L", catLacteos.id, 27.00, 21.50, 0.00, 60.0, "PZA", true, now),
            ProductDto("p-lac-2", "LAC-002", "7501020522221", "Crema Ácida Lala 450ml", catLacteos.id, 31.00, 23.00, 0.00, 30.0, "PZA", true, now),
            ProductDto("p-lac-3", "LAC-003", "7501032333334", "Queso Panela Fud 400g", catLacteos.id, 58.00, 44.00, 0.00, 18.0, "PZA", true, now),
            ProductDto("p-lac-4", "LAC-004", "7501020544445", "Yoghurt para Beber Fresa 220g", catLacteos.id, 15.50, 10.50, 0.00, 40.0, "PZA", true, now),

            // Botanas y Dulces
            ProductDto("p-bot-1", "BOT-001", "7501011123456", "Papas Sabritas Sal 45g", catBotanas.id, 22.00, 16.50, 0.16, 70.0, "PZA", true, now),
            ProductDto("p-bot-2", "BOT-002", "7501011123463", "Doritos Nacho 58g", catBotanas.id, 22.00, 16.50, 0.16, 65.0, "PZA", true, now),
            ProductDto("p-bot-3", "BOT-003", "7501000122224", "Galletas Marías Gamesa 170g", catBotanas.id, 18.50, 13.00, 0.00, 55.0, "PZA", true, now),
            ProductDto("p-bot-4", "BOT-004", "7501000133336", "Galletas Emperador Chocolate 101g", catBotanas.id, 20.00, 14.50, 0.16, 45.0, "PZA", true, now),
            ProductDto("p-bot-5", "BOT-005", "7501035011119", "Mazapán de la Rosa 28g", catBotanas.id, 7.00, 4.00, 0.16, 150.0, "PZA", true, now),

            // Frutas y Granel
            ProductDto("p-gra-1", "GRA-001", "2000001000001", "Jitomate Saladet (kg)", catGranel.id, 34.00, 22.00, 0.00, 25.0, "KG", true, now),
            ProductDto("p-gra-2", "GRA-002", "2000002000008", "Cebolla Blanca (kg)", catGranel.id, 28.00, 17.00, 0.00, 20.0, "KG", true, now),
            ProductDto("p-gra-3", "GRA-003", "2000003000005", "Plátano Tabasco (kg)", catGranel.id, 22.00, 13.50, 0.00, 30.0, "KG", true, now),
            ProductDto("p-gra-4", "GRA-004", "2000004000002", "Limón con Semilla (kg)", catGranel.id, 42.00, 28.00, 0.00, 18.0, "KG", true, now),
            ProductDto("p-gra-5", "GRA-005", "2000005000009", "Huevo Blanco a Granel (kg)", catGranel.id, 48.00, 37.00, 0.00, 45.0, "KG", true, now),

            // Limpieza y Hogar
            ProductDto("p-lim-1", "LIM-001", "7501025401112", "Cloralex Blanqueador 950ml", catLimpieza.id, 21.00, 15.00, 0.16, 30.0, "PZA", true, now),
            ProductDto("p-lim-2", "LIM-002", "7501035902221", "Limpiador Fabuloso Lavanda 1L", catLimpieza.id, 26.50, 19.00, 0.16, 35.0, "PZA", true, now),
            ProductDto("p-lim-3", "LIM-003", "7501018503334", "Jabón Zote Rosa 400g", catLimpieza.id, 24.00, 17.00, 0.16, 40.0, "PZA", true, now),
            ProductDto("p-lim-4", "LIM-004", "7501025404441", "Papel Higiénico Pétalo 4 rollos", catLimpieza.id, 36.00, 26.50, 0.16, 28.0, "PZA", true, now)
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
            call.application.environment.log.error(
                "❌ Error 500 no controlado en [${call.request.httpMethod.value} ${call.request.uri}]: ${cause.message}",
                cause
            )
            cause.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to (cause.message ?: "Error interno del servidor"),
                    "type" to (cause::class.simpleName ?: "Exception"),
                    "uri" to call.request.uri,
                    "stackTrace" to cause.stackTraceToString()
                )
            )
        }
    }

    val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
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
                val pushRequest = try {
                    call.receive<SyncPushRequest>()
                } catch (e: Exception) {
                    call.application.environment.log.error("❌ Fallo al deserializar SyncPushRequest: ${e.message}", e)
                    throw e
                }

                call.application.environment.log.info("📥 Sync push recibido de caja '${pushRequest.registerId}' con ${pushRequest.events.size} eventos")
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
                                val sale = jsonParser.decodeFromString<SaleDto>(event.payloadJson)
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
                                val shift = jsonParser.decodeFromString<CashShiftDto>(event.payloadJson)
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
                        call.application.environment.log.warn("⚠️ Error procesando evento ${event.id}: ${e.message}")
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

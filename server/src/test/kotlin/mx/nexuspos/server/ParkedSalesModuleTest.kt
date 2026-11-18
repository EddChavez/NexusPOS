package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.ParkedSaleItemDto
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ParkedSalesModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testParkAndRetrieveSale() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Aparcar una venta con 2 artículos
        val parkReq = ParkSaleRequest(
            customLabel = "Doña Carmen - Verduras",
            cashierUserId = "user-cajero-1",
            cashierName = "Don Pepe",
            registerId = "caja-1",
            customerId = null,
            customerName = null,
            items = listOf(
                ParkedSaleItemDto(
                    id = "item-1",
                    productId = "prod-jitomate",
                    barcode = "JIT-01",
                    name = "Jitomate Saladet",
                    unitCode = "KG",
                    quantityMillis = 1500L,
                    unitPriceCents = 2800L,
                    taxRate = 0.0,
                    totalCents = 4200L,
                    isWeighed = true
                ),
                ParkedSaleItemDto(
                    id = "item-2",
                    productId = "prod-cebolla",
                    barcode = "CEB-01",
                    name = "Cebolla Blanca",
                    unitCode = "KG",
                    quantityMillis = 800L,
                    unitPriceCents = 3000L,
                    taxRate = 0.0,
                    totalCents = 2400L,
                    isWeighed = true
                )
            ),
            totalCents = 6600L
        )

        val parkResponse = client.post("/api/v1/parked-sales") {
            contentType(ContentType.Application.Json)
            setBody(parkReq)
        }
        assertEquals(HttpStatusCode.Created, parkResponse.status)
        val parkedSale = parkResponse.body<ParkedSaleResponse>()
        assertEquals("Doña Carmen - Verduras", parkedSale.customLabel)
        assertEquals("PARKED", parkedSale.status)
        assertEquals(2, parkedSale.itemsCount)
        assertEquals(6600L, parkedSale.totalCents)
        assertEquals(2, parkedSale.items.size)

        // 2. Consultar lista de tickets en espera
        val listResponse = client.get("/api/v1/parked-sales")
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val parkedList = listResponse.body<List<ParkedSaleResponse>>()
        assertEquals(1, parkedList.size)
        assertEquals(parkedSale.id, parkedList[0].id)
        assertEquals("Doña Carmen - Verduras", parkedList[0].customLabel)

        // 3. Recuperar el ticket desde otra caja (ej. caja-2)
        val retrieveReq = RetrieveParkedSaleRequest(
            retrievedByUserId = "user-cajero-2",
            registerId = "caja-2"
        )
        val retrieveResponse = client.post("/api/v1/parked-sales/${parkedSale.id}/retrieve") {
            contentType(ContentType.Application.Json)
            setBody(retrieveReq)
        }
        assertEquals(HttpStatusCode.OK, retrieveResponse.status)
        val retrieved = retrieveResponse.body<ParkedSaleResponse>()
        assertEquals("RETRIEVED", retrieved.status)
        assertEquals("caja-2", retrieved.registerId)
        assertEquals("user-cajero-2", retrieved.retrievedByUserId)
        assertEquals(2, retrieved.items.size)
        assertEquals("Jitomate Saladet", retrieved.items[0].name)

        // 4. Verificar que ya no aparece en la lista activa de tickets en espera
        val emptyListResponse = client.get("/api/v1/parked-sales")
        assertEquals(HttpStatusCode.OK, emptyListResponse.status)
        val emptyList = emptyListResponse.body<List<ParkedSaleResponse>>()
        assertTrue(emptyList.isEmpty(), "La lista activa de tickets en espera debe estar vacía tras recuperar el ticket")

        // 5. Intentar recuperar de nuevo el mismo ticket debe fallar
        val duplicateRetrieveResponse = client.post("/api/v1/parked-sales/${parkedSale.id}/retrieve") {
            contentType(ContentType.Application.Json)
            setBody(retrieveReq)
        }
        assertTrue(duplicateRetrieveResponse.status.value in 400..499)
    }

    @Test
    fun testDiscardParkedSale() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val parkReq = ParkSaleRequest(
            customLabel = "Cliente que no volvió",
            cashierUserId = "user-1",
            cashierName = "Don Pepe",
            registerId = "caja-1",
            customerId = null,
            customerName = null,
            items = listOf(
                ParkedSaleItemDto(
                    id = "i-1",
                    productId = null,
                    barcode = "VAR",
                    name = "Varios",
                    unitCode = "PZA",
                    quantityMillis = 1000L,
                    unitPriceCents = 5000L,
                    taxRate = 0.0,
                    totalCents = 5000L,
                    isWeighed = false
                )
            ),
            totalCents = 5000L
        )

        val parkResponse = client.post("/api/v1/parked-sales") {
            contentType(ContentType.Application.Json)
            setBody(parkReq)
        }
        assertEquals(HttpStatusCode.Created, parkResponse.status)
        val parked = parkResponse.body<ParkedSaleResponse>()

        // Descartar
        val discardResponse = client.post("/api/v1/parked-sales/${parked.id}/discard?userId=user-1")
        assertEquals(HttpStatusCode.OK, discardResponse.status)
        val discarded = discardResponse.body<ParkedSaleResponse>()
        assertEquals("DISCARDED", discarded.status)

        // La lista activa debe estar vacía
        val listRes = client.get("/api/v1/parked-sales")
        val activeList = listRes.body<List<ParkedSaleResponse>>()
        assertTrue(activeList.isEmpty())
    }

    @Test
    fun testCannotParkEmptyItems() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val emptyParkReq = ParkSaleRequest(
            customLabel = "Vacío",
            cashierUserId = "user-1",
            cashierName = "Don Pepe",
            registerId = "caja-1",
            customerId = null,
            customerName = null,
            items = emptyList(),
            totalCents = 0L
        )

        val res = client.post("/api/v1/parked-sales") {
            contentType(ContentType.Application.Json)
            setBody(emptyParkReq)
        }
        assertTrue(res.status.value in 400..499)
    }
}

package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class ProductLotsModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testLotsAndExpirationsFullCycle() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Obtener producto existente para pruebas
        val prods = client.get("/api/v1/products").body<List<ProductDto>>()
        assertTrue(prods.isNotEmpty())
        val product = prods.first()

        val today = LocalDate.now()
        val expiredDateStr = today.minusDays(2).toString()
        val nearExpDateStr = today.plusDays(3).toString()
        val optimalDateStr = today.plusDays(45).toString()

        // 2. Registrar Lote 1: Caducado
        val createExpRes = client.post("/api/v1/inventory/lots") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductLotRequest(
                    productId = product.id,
                    lotNumber = "LOT-EXP-001",
                    expirationDate = expiredDateStr,
                    quantityMillis = 10_000L, // 10 unidades
                    costCents = 2500L, // $25.00 c/u
                    addToGeneralStock = true,
                    notes = "Lote caducado para prueba"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createExpRes.status)
        val expLot = createExpRes.body<ProductLotDto>()
        assertEquals(LotExpirationStatus.EXPIRED, expLot.status)
        assertTrue(expLot.daysUntilExpiration <= 0)
        assertEquals(10_000L, expLot.quantityMillis)
        assertEquals(25_000L, expLot.valuationCents) // (10000 * 2500) / 1000 = 25000 cents ($250.00)

        // 3. Registrar Lote 2: Próximo a caducar
        val createNearRes = client.post("/api/v1/inventory/lots") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductLotRequest(
                    productId = product.id,
                    lotNumber = "LOT-NEAR-002",
                    expirationDate = nearExpDateStr,
                    quantityMillis = 5_000L,
                    costCents = 2000L,
                    addToGeneralStock = true
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createNearRes.status)
        val nearLot = createNearRes.body<ProductLotDto>()
        assertEquals(LotExpirationStatus.NEAR_EXPIRATION, nearLot.status)
        assertEquals(3, nearLot.daysUntilExpiration)

        // 4. Registrar Lote 3: Óptimo
        val createOptRes = client.post("/api/v1/inventory/lots") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductLotRequest(
                    productId = product.id,
                    lotNumber = "LOT-OPT-003",
                    expirationDate = optimalDateStr,
                    quantityMillis = 20_000L,
                    costCents = 2000L,
                    addToGeneralStock = false
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createOptRes.status)
        val optLot = createOptRes.body<ProductLotDto>()
        assertEquals(LotExpirationStatus.OPTIMAL, optLot.status)

        // 5. Probar acumulación de lote existente
        val accumulateRes = client.post("/api/v1/inventory/lots") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductLotRequest(
                    productId = product.id,
                    lotNumber = "LOT-OPT-003",
                    expirationDate = optimalDateStr,
                    quantityMillis = 10_000L,
                    addToGeneralStock = false
                )
            )
        }
        assertEquals(HttpStatusCode.Created, accumulateRes.status)
        val accumulatedLot = accumulateRes.body<ProductLotDto>()
        assertEquals(optLot.id, accumulatedLot.id)
        assertEquals(30_000L, accumulatedLot.quantityMillis)
        assertEquals(30_000L, accumulatedLot.initialQuantityMillis)

        // 6. Probar reporte de caducidades
        val report = client.get("/api/v1/inventory/lots/report").body<ExpirationsReportDto>()
        assertTrue(report.totalLotsCount >= 3)
        assertTrue(report.expiredLotsCount >= 1)
        assertTrue(report.nearExpirationLotsCount >= 1)
        assertEquals(25_000L, report.totalExpiredValuationCents) // $250.00 en merma confirmada
        assertEquals(10_000L, report.totalAtRiskValuationCents) // 5 * 2000 = 10000 cents en riesgo

        // 7. Probar consulta de lote que vence primero
        val nextExpiring = client.get("/api/v1/inventory/lots/expiring/${product.id}").body<ProductLotDto>()
        assertEquals(expLot.id, nextExpiring.id)
        assertEquals("LOT-EXP-001", nextExpiring.lotNumber)

        // 8. Probar baja por merma (Write-off) de lote caducado
        val writeOffRes = client.post("/api/v1/inventory/lots/${expLot.id}/write-off") {
            contentType(ContentType.Application.Json)
            setBody(
                LotWriteOffRequest(
                    quantityMillis = 10_000L,
                    reason = "Baja total por caducidad en anaquel",
                    userId = "usr-admin"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, writeOffRes.status)
        val writtenOffLot = writeOffRes.body<ProductLotDto>()
        assertEquals(0L, writtenOffLot.quantityMillis)
        assertFalse(writtenOffLot.isActive)

        // 9. Verificar Kardex: debe registrarse movimiento LOSS
        val movements = client.get("/api/v1/inventory/movements?productId=${product.id}&type=LOSS").body<List<StockMovementDto>>()
        assertTrue(movements.any { it.reason.contains("Baja total por caducidad") && it.quantityMillis < 0 })

        // 10. Validaciones de error
        val invalidDateRes = client.post("/api/v1/inventory/lots") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductLotRequest(
                    productId = product.id,
                    expirationDate = "fecha-invalida",
                    quantityMillis = 1000L
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidDateRes.status)
    }
}

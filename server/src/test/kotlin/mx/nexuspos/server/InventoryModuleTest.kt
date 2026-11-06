package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class InventoryModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testInventoryLifecycleAndMovements() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Crear producto con stock inicial de 20 piezas y costo $15.00 (1500 cents)
        val createProdReq = CreateProductRequest(
            sku = "GAL-OREO",
            name = "Galletas Oreo 114g",
            departmentId = "dept-botanas",
            baseUnit = "PZA",
            costCents = 1500L,
            taxRate = 0.0,
            usesInventory = true,
            initialStockMillis = 20000L, // 20 PZA
            minStockMillis = 5000L,      // 5 PZA
            maxStockMillis = 50000L,
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501000998877",
                    name = "Galletas Oreo 114g",
                    factorMillis = 1000L,
                    priceCents = 2200L
                )
            )
        )

        val prodRes = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(createProdReq)
        }
        assertEquals(HttpStatusCode.Created, prodRes.status)
        val product = prodRes.body<ProductDto>()
        assertEquals(20000L, product.currentStockMillis)

        // 2. Consultar Resumen de Inventario
        val summaryRes = client.get("/api/v1/inventory/summary")
        assertEquals(HttpStatusCode.OK, summaryRes.status)
        val summary = summaryRes.body<InventorySummaryDto>()
        assertTrue(summary.totalProductsCount >= 1)
        assertTrue(summary.trackedProductsCount >= 1)
        assertTrue(summary.totalValuationCents > 0L)

        // 3. Registrar Entrada de Mercancía (+10 piezas)
        val entryReq = RegisterStockMovementRequest(
            productId = product.id,
            type = "ENTRY",
            quantityMillis = 10000L,
            reason = "Compra factura F-8921",
            reference = "F-8921",
            userId = "usr-admin"
        )
        val entryRes = client.post("/api/v1/inventory/movements") {
            contentType(ContentType.Application.Json)
            setBody(entryReq)
        }
        assertEquals(HttpStatusCode.Created, entryRes.status)
        val entryMov = entryRes.body<StockMovementDto>()
        assertEquals("ENTRY", entryMov.type)
        assertEquals(10000L, entryMov.quantityMillis)
        assertEquals(30000L, entryMov.balanceAfterMillis)

        // Verificar que el producto ahora tenga 30 piezas
        val updatedProd1 = client.get("/api/v1/products/${product.id}").body<ProductDto>()
        assertEquals(30000L, updatedProd1.currentStockMillis)

        // 4. Registrar Merma (-3 piezas)
        val lossReq = RegisterStockMovementRequest(
            productId = product.id,
            type = "LOSS",
            quantityMillis = 3000L,
            reason = "Paquetes rotos o aplastados",
            reference = "MERMA-01",
            userId = "usr-cajero"
        )
        val lossRes = client.post("/api/v1/inventory/movements") {
            contentType(ContentType.Application.Json)
            setBody(lossReq)
        }
        assertEquals(HttpStatusCode.Created, lossRes.status)
        val lossMov = lossRes.body<StockMovementDto>()
        assertEquals("LOSS", lossMov.type)
        assertEquals(-3000L, lossMov.quantityMillis)
        assertEquals(27000L, lossMov.balanceAfterMillis)

        // Verificar que el producto ahora tenga 27 piezas
        val updatedProd2 = client.get("/api/v1/products/${product.id}").body<ProductDto>()
        assertEquals(27000L, updatedProd2.currentStockMillis)

        // 5. Registrar Ajuste por Conteo Físico (se contaron 25 piezas en físico -> delta -2 piezas)
        val adjustReq = RegisterStockMovementRequest(
            productId = product.id,
            type = "ADJUSTMENT",
            quantityMillis = -2000L,
            reason = "Ajuste por conteo físico mensual",
            reference = "INV-MENSUAL",
            userId = "usr-supervisor"
        )
        val adjustRes = client.post("/api/v1/inventory/movements") {
            contentType(ContentType.Application.Json)
            setBody(adjustReq)
        }
        assertEquals(HttpStatusCode.Created, adjustRes.status)
        val adjustMov = adjustRes.body<StockMovementDto>()
        assertEquals("ADJUSTMENT", adjustMov.type)
        assertEquals(-2000L, adjustMov.quantityMillis)
        assertEquals(25000L, adjustMov.balanceAfterMillis)

        // 6. Consultar Kardex de Movimientos filtrado por producto
        val kardexRes = client.get("/api/v1/inventory/movements?productId=${product.id}")
        assertEquals(HttpStatusCode.OK, kardexRes.status)
        val movements = kardexRes.body<List<StockMovementDto>>()
        // Al menos debe tener el movimiento inicial de creación + ENTRY + LOSS + ADJUSTMENT = 4
        assertTrue(movements.size >= 4)
        assertEquals("ADJUSTMENT", movements[0].type)
        assertEquals("LOSS", movements[1].type)
        assertEquals("ENTRY", movements[2].type)
    }
}

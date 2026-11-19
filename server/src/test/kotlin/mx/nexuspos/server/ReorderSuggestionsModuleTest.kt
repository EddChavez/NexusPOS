package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ReorderSuggestionsModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testReorderSuggestionsFormulasAndFilters() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Consultar productos sembrados
        val prods = client.get("/api/v1/products").body<List<ProductDto>>()
        assertTrue(prods.isNotEmpty())

        val coke = prods.first { it.presentations.any { p -> p.barcode == "7501055300078" } }

        // Reducir stock de Coca Cola a 0 mediante movimiento de ajuste
        val currentCokeStock = coke.currentStockMillis
        client.post("/api/v1/inventory/movements") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterStockMovementRequest(
                    productId = coke.id,
                    type = "LOSS",
                    quantityMillis = currentCokeStock,
                    reason = "Ajuste de prueba para dejar en 0"
                )
            )
        }

        // 2. Consultar reporte de pedido sugerido
        val report = client.get("/api/v1/inventory/reorder-suggestions").body<ReorderSuggestionsReportDto>()
        assertTrue(report.totalItemsCount > 0)
        assertTrue(report.criticalOutOfStockCount > 0)
        assertTrue(report.totalEstimatedInvestmentCents > 0L)

        // Coca Cola debe figurar como crítica (CRITICAL_OUT_OF_STOCK)
        val cokeItem = report.items.firstOrNull { it.productId == coke.id }
        assertNotNull(cokeItem)
        assertEquals(0L, cokeItem.currentStockMillis)
        assertEquals(ReorderUrgency.CRITICAL_OUT_OF_STOCK, cokeItem.urgency)
        assertTrue(cokeItem.suggestedOrderMillis > 0L)
        assertEquals((cokeItem.suggestedOrderMillis * coke.costCents) / 1000L, cokeItem.estimatedInvestmentCents)

        // 3. Probar filtro onlyOutOfStock = true
        val outOfStockReport = client.get("/api/v1/inventory/reorder-suggestions?onlyOutOfStock=true").body<ReorderSuggestionsReportDto>()
        assertTrue(outOfStockReport.items.all { it.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK })
        assertTrue(outOfStockReport.items.all { it.currentStockMillis <= 0L })

        // 4. Probar filtro por departamento
        if (coke.departmentId != null) {
            val deptReport = client.get("/api/v1/inventory/reorder-suggestions?departmentId=${coke.departmentId}").body<ReorderSuggestionsReportDto>()
            assertTrue(deptReport.items.all { it.departmentId == coke.departmentId })
        }
    }
}

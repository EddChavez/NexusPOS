package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CatalogImportRowDto
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class CatalogImportExportTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testExportCatalogCsv() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Crear departamento y producto
        val deptRes = client.post("/api/v1/departments") {
            contentType(ContentType.Application.Json)
            setBody(CreateDepartmentRequest(name = "Bebidas"))
        }
        val dept = deptRes.body<DepartmentDto>()

        val createProd = CreateProductRequest(
            sku = "REF-COCA-600",
            name = "Coca Cola 600ml",
            departmentId = dept.id,
            baseUnit = "PZA",
            costCents = 1200L,
            taxRate = 0.16,
            usesInventory = true,
            initialStockMillis = 15000L,
            minStockMillis = 5000L,
            maxStockMillis = 30000L,
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501055309999",
                    name = "Coca Cola 600ml Botella",
                    factorMillis = 1000L,
                    priceCents = 1800L
                )
            )
        )
        val prodRes = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(createProd)
        }
        assertEquals(HttpStatusCode.Created, prodRes.status)

        // GET /api/v1/catalog/export
        val exportRes = client.get("/api/v1/catalog/export")
        assertEquals(HttpStatusCode.OK, exportRes.status)
        val csvText = exportRes.bodyAsText()

        // Verificar BOM UTF-8 y encabezados
        assertTrue(csvText.startsWith("\uFEFF"), "El CSV exportado debe comenzar con el BOM UTF-8 para compatibilidad con Excel")
        assertTrue(csvText.contains("SKU,CODIGO_BARRAS,NOMBRE,DEPARTAMENTO,UNIDAD,COSTO,PRECIO,STOCK_ACTUAL,STOCK_MINIMO,ACTIVO"))
        assertTrue(csvText.contains("REF-COCA-600"))
        assertTrue(csvText.contains("7501055309999"))
        assertTrue(csvText.contains("Coca Cola 600ml"))
        assertTrue(csvText.contains("18.00"))
    }

    @Test
    fun testImportCatalogRows_create_and_update() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Crear departamento y producto base
        val deptRes = client.post("/api/v1/departments") {
            contentType(ContentType.Application.Json)
            setBody(CreateDepartmentRequest(name = "Abarrotes"))
        }
        val dept = deptRes.body<DepartmentDto>()

        val createProd = CreateProductRequest(
            sku = "GAL-OREO-100",
            name = "Galletas Oreo 100g",
            departmentId = dept.id,
            baseUnit = "PZA",
            costCents = 1000L,
            taxRate = 0.0,
            usesInventory = true,
            initialStockMillis = 10000L,
            minStockMillis = 2000L,
            maxStockMillis = 20000L,
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501000111222",
                    name = "Oreo 100g Pieza",
                    factorMillis = 1000L,
                    priceCents = 1500L
                )
            )
        )
        val baseRes = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(createProd)
        }
        assertEquals(HttpStatusCode.Created, baseRes.status)

        // 2. Importación masiva:
        // - Fila 1: Actualizar GAL-OREO-100 (precio sube a $17.50, stock a 25 piezas)
        // - Fila 2: Nuevo producto Sabritas Sal 45g
        // - Fila 3: Fila con error (nombre vacío)
        val importReq = CatalogImportRequest(
            rows = listOf(
                CatalogImportRowDto(
                    rowNumber = 1,
                    sku = "GAL-OREO-100",
                    barcode = "7501000111222",
                    name = "Galletas Oreo 100g Original",
                    department = "Galletas",
                    priceCents = 1750L,
                    costCents = 1100L,
                    baseUnit = "PZA",
                    currentStockMillis = 25000L,
                    minStockMillis = 5000L,
                    taxRate = 0.0,
                    isActive = true
                ),
                CatalogImportRowDto(
                    rowNumber = 2,
                    sku = "BOT-SABR-45",
                    barcode = "7501011122334",
                    name = "Papas Sabritas Sal 45g",
                    department = "Botanas",
                    priceCents = 2000L,
                    costCents = 1400L,
                    baseUnit = "PZA",
                    currentStockMillis = 30000L,
                    minStockMillis = 10000L,
                    taxRate = 0.0,
                    isActive = true
                ),
                CatalogImportRowDto(
                    rowNumber = 3,
                    sku = "INV-001",
                    barcode = "00000000",
                    name = "", // Inválido
                    department = "General",
                    priceCents = 1000L,
                    costCents = 500L,
                    baseUnit = "PZA"
                )
            )
        )

        val importRes = client.post("/api/v1/catalog/import") {
            contentType(ContentType.Application.Json)
            setBody(importReq)
        }
        assertEquals(HttpStatusCode.OK, importRes.status)
        val result = importRes.body<CatalogImportResponse>()

        assertEquals(3, result.totalRows)
        assertEquals(1, result.updatedCount, "Debe haber actualizado el producto existente GAL-OREO-100")
        assertEquals(1, result.createdCount, "Debe haber creado el nuevo producto BOT-SABR-45")
        assertEquals(1, result.errorCount, "Debe reportar 1 error por nombre vacío")

        assertEquals("UPDATED", result.rows[0].status)
        assertEquals("CREATED", result.rows[1].status)
        assertEquals("ERROR", result.rows[2].status)

        // 3. Verificar en el catálogo que el producto nuevo existe y el existente se actualizó
        val getProdsRes = client.get("/api/v1/products")
        val prods = getProdsRes.body<List<ProductDto>>()

        val updatedOreo = prods.first { it.sku == "GAL-OREO-100" }
        assertEquals("Galletas Oreo 100g Original", updatedOreo.name)
        assertEquals(1750L, updatedOreo.presentations.first().priceCents)
        assertEquals(25000L, updatedOreo.currentStockMillis)

        val newSabritas = prods.first { it.sku == "BOT-SABR-45" }
        assertEquals("Papas Sabritas Sal 45g", newSabritas.name)
        assertEquals(2000L, newSabritas.presentations.first().priceCents)
        assertEquals(30000L, newSabritas.currentStockMillis)
        assertEquals("7501011122334", newSabritas.presentations.first().barcode)
    }
}

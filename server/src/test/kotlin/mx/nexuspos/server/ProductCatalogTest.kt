package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class ProductCatalogTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testProductLifecycleCrud() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Alta de producto
        val createReq = CreateProductRequest(
            sku = "REF-COCA-2L",
            name = "Refresco Coca Cola 2L",
            departmentId = "dept-bebidas",
            baseUnit = "PZA",
            costCents = 2800L,
            taxRate = 0.16,
            usesInventory = true,
            initialStockMillis = 24000L, // 24 piezas
            minStockMillis = 6000L,
            maxStockMillis = 50000L,
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501055301234",
                    name = "Coca Cola 2L Pieza",
                    factorMillis = 1000L,
                    priceCents = 3800L
                )
            )
        )

        val createRes = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(createReq)
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val created = createRes.body<ProductDto>()
        assertEquals("REF-COCA-2L", created.sku)
        assertEquals("Refresco Coca Cola 2L", created.name)
        assertEquals(24000L, created.currentStockMillis)
        assertEquals(1, created.presentations.size)
        assertEquals("7501055301234", created.presentations.first().barcode)

        // 2. Consulta por ID y por búsqueda
        val getRes = client.get("/api/v1/products/${created.id}")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val fetched = getRes.body<ProductDto>()
        assertEquals(created.id, fetched.id)

        val searchRes = client.get("/api/v1/products?query=Coca%20Cola%202L")
        assertEquals(HttpStatusCode.OK, searchRes.status)
        val searchList = searchRes.body<List<ProductDto>>()
        assertTrue(searchList.any { it.id == created.id })

        // 3. Modificación del producto
        val updateReq = UpdateProductRequest(
            sku = "REF-COCA-2L",
            name = "Refresco Coca Cola Original 2 Litros",
            departmentId = "dept-bebidas",
            baseUnit = "PZA",
            costCents = 3000L,
            taxRate = 0.16,
            usesInventory = true,
            minStockMillis = 8000L,
            maxStockMillis = 60000L,
            isActive = true,
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501055301234",
                    name = "Coca Cola 2L Pieza",
                    factorMillis = 1000L,
                    priceCents = 4000L
                )
            )
        )

        val updateRes = client.put("/api/v1/products/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(updateReq)
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updated = updateRes.body<ProductDto>()
        assertEquals("Refresco Coca Cola Original 2 Litros", updated.name)
        assertEquals(4000L, updated.presentations.first().priceCents)

        // 4. Desactivación (Soft Delete)
        val deleteRes = client.delete("/api/v1/products/${created.id}")
        assertEquals(HttpStatusCode.OK, deleteRes.status)
        val deactivated = deleteRes.body<ProductDto>()
        assertFalse(deactivated.isActive)

        // Verificamos que no salga en búsqueda activa
        val searchAfterDelete = client.get("/api/v1/products?query=Coca%20Cola%20Original")
        val activeList = searchAfterDelete.body<List<ProductDto>>()
        assertFalse(activeList.any { it.id == created.id })

        // Pero sí aparece con includeInactive=true
        val searchWithInactive = client.get("/api/v1/products?query=Coca%20Cola%20Original&includeInactive=true")
        val allList = searchWithInactive.body<List<ProductDto>>()
        assertTrue(allList.any { it.id == created.id })

        // 5. Reactivación
        val reactivateRes = client.post("/api/v1/products/${created.id}/reactivate")
        assertEquals(HttpStatusCode.OK, reactivateRes.status)
        val reactivated = reactivateRes.body<ProductDto>()
        assertTrue(reactivated.isActive)
    }

    @Test
    fun testDuplicateBarcodeValidation() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Alta primer producto
        val prod1 = CreateProductRequest(
            sku = "GAL-001",
            name = "Galletas Marías 170g",
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501000123456",
                    name = "Galletas Marías",
                    priceCents = 1500L
                )
            )
        )
        val res1 = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(prod1)
        }
        assertEquals(HttpStatusCode.Created, res1.status)

        // 2. Intentar registrar otro producto con el mismo código de barras
        val prod2 = CreateProductRequest(
            sku = "GAL-002",
            name = "Galletas Gamesa Clásicas",
            presentations = listOf(
                CreatePresentationRequest(
                    barcode = "7501000123456", // DUPLICADO
                    name = "Galletas Gamesa",
                    priceCents = 1800L
                )
            )
        )
        val res2 = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(prod2)
        }
        assertEquals(HttpStatusCode.BadRequest, res2.status)
    }

    @Test
    fun testDepartmentCrud() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Crear nuevo departamento
        val createRes = client.post("/api/v1/departments") {
            contentType(ContentType.Application.Json)
            setBody(CreateDepartmentRequest(name = "Panadería"))
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createdDept = createRes.body<DepartmentDto>()
        assertEquals("Panadería", createdDept.name)

        // 2. Validar duplicado (case-insensitive)
        val dupRes = client.post("/api/v1/departments") {
            contentType(ContentType.Application.Json)
            setBody(CreateDepartmentRequest(name = "panadería"))
        }
        assertEquals(HttpStatusCode.BadRequest, dupRes.status)

        // 3. Modificar departamento
        val updateRes = client.put("/api/v1/departments/${createdDept.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateDepartmentRequest(name = "Panadería & Repostería"))
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updatedDept = updateRes.body<DepartmentDto>()
        assertEquals("Panadería & Repostería", updatedDept.name)

        // 4. Crear producto asignado a este departamento
        val prodRes = client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProductRequest(
                    sku = "PAN-001",
                    name = "Bolillo Tradicional",
                    departmentId = createdDept.id,
                    presentations = listOf(
                        CreatePresentationRequest(
                            barcode = "7500001",
                            name = "Bolillo",
                            priceCents = 250L
                        )
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, prodRes.status)
        val prod = prodRes.body<ProductDto>()
        assertEquals(createdDept.id, prod.departmentId)

        // 5. Eliminar departamento: se debe eliminar y el producto debe quedar con departmentId = null (General)
        val deleteRes = client.delete("/api/v1/departments/${createdDept.id}")
        assertEquals(HttpStatusCode.OK, deleteRes.status)

        // Verificar que ya no existe el departamento
        val deptsRes = client.get("/api/v1/departments").body<List<DepartmentDto>>()
        assertFalse(deptsRes.any { it.id == createdDept.id })

        // Verificar que el producto sigue existiendo pero desvinculado a null
        val prodCheck = client.get("/api/v1/products/${prod.id}").body<ProductDto>()
        assertNull(prodCheck.departmentId)
    }
}

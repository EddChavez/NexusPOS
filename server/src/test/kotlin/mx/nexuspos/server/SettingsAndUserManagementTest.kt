package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.identity.CreateUserRequest
import mx.nexuspos.contracts.identity.UpdateUserRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.system.OperatingPolicies
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SettingsPreferencesPatch
import mx.nexuspos.contracts.system.StorePreferences
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class SettingsAndUserManagementTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testUpdateAndRetrieveStoreSettings() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Obtener settings iniciales
        val initialRes = client.get("/api/v1/system/settings")
        assertEquals(HttpStatusCode.OK, initialRes.status)
        val initialSettings = initialRes.body<SettingsDto>()
        assertNotNull(initialSettings.storeName)

        // 2. Modificar settings completos
        val updatedReq = SettingsDto(
            storeName = "Abarrotes El Güero",
            storeAddress = "Calle Morelos #45, Col. Centro",
            storePhone = "442-123-9999",
            storeTaxId = "GUEA800101XYZ",
            ticketHeader = "¡Bienvenido a su tienda de confianza!",
            ticketFooter = "Gracias por su preferencia. No se aceptan devoluciones en perecederos.",
            allowNegativeStock = false,
            currencySymbol = "$"
        )

        val patchRes = client.put("/api/v1/system/settings") {
            contentType(ContentType.Application.Json)
            setBody(updatedReq)
        }
        assertEquals(HttpStatusCode.OK, patchRes.status)
        val patchedSettings = patchRes.body<SettingsDto>()
        assertEquals("Abarrotes El Güero", patchedSettings.storeName)
        assertEquals("GUEA800101XYZ", patchedSettings.storeTaxId)
        assertEquals(false, patchedSettings.allowNegativeStock)

        // 3. Verificar persistencia con GET posterior
        val getRes = client.get("/api/v1/system/settings")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val finalSettings = getRes.body<SettingsDto>()
        assertEquals("Abarrotes El Güero", finalSettings.storeName)
        assertEquals("Calle Morelos #45, Col. Centro", finalSettings.storeAddress)
        assertEquals("442-123-9999", finalSettings.storePhone)
        assertEquals("GUEA800101XYZ", finalSettings.storeTaxId)
        assertEquals(false, finalSettings.allowNegativeStock)
    }

    @Test
    fun sectionUpdatesPreserveOtherPreferencesAndRejectInvalidPolicies() = testApplication {
        val db = createTestDb()
        application { nexusModule(db) }
        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val path = "/api/v1/system/settings"
        val store = StorePreferences(
            storeName = "Tienda Norte",
            storeAddress = "Calle 1",
            storePhone = "5551234567",
            storeTaxId = "RFC123",
            ticketHeader = "Bienvenidos",
            ticketFooter = "Gracias",
            currencySymbol = "$",
            whatsappCountryCode = "54",
            whatsappBankDetails = "Cuenta de la tienda",
            whatsappCustomFooter = "Mensaje personalizado"
        )
        val policies = OperatingPolicies(false, true, 15.5, true, 2.25, 1225L)

        suspend fun update(patch: SettingsPreferencesPatch) = client.patch("$path/preferences") {
            contentType(ContentType.Application.Json)
            setBody(patch)
        }

        assertEquals(HttpStatusCode.OK, update(SettingsPreferencesPatch(store = store)).status)
        val policyResponse = update(SettingsPreferencesPatch(policies = policies))
        assertEquals(HttpStatusCode.OK, policyResponse.status)
        val afterPolicies = policyResponse.body<SettingsDto>()
        assertEquals("54", afterPolicies.whatsappCountryCode)
        assertEquals("Cuenta de la tienda", afterPolicies.whatsappBankDetails)
        assertEquals("Mensaje personalizado", afterPolicies.whatsappCustomFooter)
        assertEquals("Tienda Norte", afterPolicies.storeName)
        assertEquals(1225L, afterPolicies.loyaltyMinRedeemCents)

        assertEquals(HttpStatusCode.OK, update(SettingsPreferencesPatch(store = store.copy(storeName = "Tienda Centro"))).status)
        val invalid = SettingsPreferencesPatch(
            store = store.copy(storeName = "Nombre que no debe persistir"),
            policies = policies.copy(maxCashierDiscountPercent = 101.0)
        )
        assertEquals(HttpStatusCode.BadRequest, update(invalid).status)

        val persisted = client.get(path).body<SettingsDto>()
        assertEquals("Tienda Centro", persisted.storeName)
        assertFalse(persisted.allowNegativeStock)
        assertTrue(persisted.blindCashCountEnabled)
        assertEquals(15.5, persisted.maxCashierDiscountPercent)
        assertEquals(2.25, persisted.loyaltyCashbackPercent)
        assertEquals(1225L, persisted.loyaltyMinRedeemCents)
    }

    @Test
    fun testUserLifecycleCrud() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Listar usuarios iniciales precargados
        val listRes = client.get("/api/v1/users")
        assertEquals(HttpStatusCode.OK, listRes.status)
        val initialUsers = listRes.body<List<UserDto>>()
        assertTrue(initialUsers.any { it.role == "ADMIN" && it.name == "Administrador" })
        assertTrue(initialUsers.any { it.role == "CASHIER" && it.name == "Cajero Principal" })

        // 2. Crear nuevo usuario Cajero con PIN de 4 dígitos
        val createRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequest(
                    name = "Laura Gómez",
                    role = "CASHIER",
                    pin = "5544"
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val newUser = createRes.body<UserDto>()
        assertEquals("Laura Gómez", newUser.name)
        assertEquals("CASHIER", newUser.role)
        assertTrue(newUser.isActive)

        // 3. Consultar por ID
        val getRes = client.get("/api/v1/users/${newUser.id}")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val fetchedUser = getRes.body<UserDto>()
        assertEquals(newUser.id, fetchedUser.id)

        // 4. Actualizar usuario: ascenso a SUPERVISOR y cambio de PIN
        val updateRes = client.put("/api/v1/users/${newUser.id}") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateUserRequest(
                    name = "Laura Gómez (Encargada)",
                    role = "SUPERVISOR",
                    pin = "9988"
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updatedUser = updateRes.body<UserDto>()
        assertEquals("Laura Gómez (Encargada)", updatedUser.name)
        assertEquals("SUPERVISOR", updatedUser.role)

        // 5. Desactivar usuario
        val deleteRes = client.delete("/api/v1/users/${newUser.id}")
        assertEquals(HttpStatusCode.OK, deleteRes.status)
        val deactivatedUser = deleteRes.body<UserDto>()
        assertFalse(deactivatedUser.isActive)

        // 6. Reactivar usuario
        val reactivateRes = client.post("/api/v1/users/${newUser.id}/reactivate")
        assertEquals(HttpStatusCode.OK, reactivateRes.status)
        val reactivatedUser = reactivateRes.body<UserDto>()
        assertTrue(reactivatedUser.isActive)
    }

    @Test
    fun testPinValidationRules() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // A. PIN muy corto (3 dígitos) -> 400
        val shortPinRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "Test 1", role = "CASHIER", pin = "123"))
        }
        assertEquals(HttpStatusCode.BadRequest, shortPinRes.status)

        // B. PIN muy largo (5 dígitos) -> 400
        val longPinRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "Test 2", role = "CASHIER", pin = "12345"))
        }
        assertEquals(HttpStatusCode.BadRequest, longPinRes.status)

        // C. PIN con letras -> 400
        val alphaPinRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "Test 3", role = "CASHIER", pin = "abcd"))
        }
        assertEquals(HttpStatusCode.BadRequest, alphaPinRes.status)

        // D. PIN duplicado con admin existente (1234) -> 400
        val dupPinRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "Test Dup", role = "CASHIER", pin = "1234"))
        }
        assertEquals(HttpStatusCode.BadRequest, dupPinRes.status)
    }

    @Test
    fun testPreventDeactivatingLastAdmin() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Obtener el ID del administrador por defecto
        val users = client.get("/api/v1/users").body<List<UserDto>>()
        val defaultAdmin = users.first { it.role == "ADMIN" }

        // 2. Intentar desactivarlo siendo el único -> 400
        val tryDeactivate = client.delete("/api/v1/users/${defaultAdmin.id}")
        assertEquals(HttpStatusCode.BadRequest, tryDeactivate.status)

        // 3. Crear un segundo ADMIN
        val secondAdminRes = client.post("/api/v1/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(name = "Segundo Admin", role = "ADMIN", pin = "7788"))
        }
        assertEquals(HttpStatusCode.Created, secondAdminRes.status)

        // 4. Ahora sí se puede desactivar el primer admin
        val deactivateSuccess = client.delete("/api/v1/users/${defaultAdmin.id}")
        assertEquals(HttpStatusCode.OK, deactivateSuccess.status)
        val result = deactivateSuccess.body<UserDto>()
        assertFalse(result.isActive)
    }
}

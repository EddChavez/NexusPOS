package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.credit.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class CustomerAndCreditTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testCustomerCrudAndFolioGeneration() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Create Customer
        val createReq = CreateCustomerRequest(
            name = "Ramiro Sánchez",
            phone = "555-987-6543",
            email = "ramiro@email.com",
            rfc = "SAR900101XYZ",
            address = "Calle Fresno #100",
            notes = "Cliente nuevo en la colonia",
            creditEnabled = true,
            hasCreditLimit = true,
            creditLimitCents = 100000L // $1,000.00
        )
        val createRes = client.post("/api/v1/customers") {
            contentType(ContentType.Application.Json)
            setBody(createReq)
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createdCust = createRes.body<CustomerDto>()
        assertEquals("Ramiro Sánchez", createdCust.name)
        assertTrue(createdCust.folio.startsWith("CLI-"))
        assertEquals(100000L, createdCust.creditLimitCents)
        assertEquals(0L, createdCust.currentBalanceCents)
        assertEquals(100000L, createdCust.availableCreditCents)
        assertTrue(createdCust.isActive)

        // 2. Search Customer by Folio and Phone
        val searchByFolio = client.get("/api/v1/customers?query=${createdCust.folio}")
        assertEquals(HttpStatusCode.OK, searchByFolio.status)
        val searchList = searchByFolio.body<List<CustomerDto>>()
        assertEquals(1, searchList.size)
        assertEquals(createdCust.id, searchList.first().id)

        // 3. Update Customer
        val updateReq = UpdateCustomerRequest(
            name = "Ramiro Sánchez Gómez",
            phone = "555-987-6543",
            email = "ramiro.sanchez@email.com",
            rfc = "SAR900101XYZ",
            address = "Calle Fresno #100 Int 2",
            notes = "Actualizado teléfono de casa",
            creditEnabled = true,
            hasCreditLimit = true,
            creditLimitCents = 150000L, // Aumento a $1,500.00
            isActive = true
        )
        val updateRes = client.put("/api/v1/customers/${createdCust.id}") {
            contentType(ContentType.Application.Json)
            setBody(updateReq)
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updatedCust = updateRes.body<CustomerDto>()
        assertEquals("Ramiro Sánchez Gómez", updatedCust.name)
        assertEquals(150000L, updatedCust.creditLimitCents)

        // 4. Soft Delete (Deactivate) Customer
        val deleteRes = client.delete("/api/v1/customers/${createdCust.id}")
        assertEquals(HttpStatusCode.OK, deleteRes.status)
        val deactivatedCust = deleteRes.body<CustomerDto>()
        assertFalse(deactivatedCust.isActive)

        // Verify inactive customer is excluded from default search
        val activeSearch = client.get("/api/v1/customers?query=Ramiro")
        val activeList = activeSearch.body<List<CustomerDto>>()
        assertTrue(activeList.isEmpty())

        // Verify inactive customer can be retrieved with includeInactive=true
        val allSearch = client.get("/api/v1/customers?query=Ramiro&includeInactive=true")
        val allList = allSearch.body<List<CustomerDto>>()
        assertEquals(1, allList.size)
    }

    @Test
    fun testCreditOverdrawAndSupervisorAuthFlow() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Open Cash Session
        val openRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 50000L))
        }
        val session = openRes.body<mx.nexuspos.contracts.cash.CashSessionSummary>()

        // cust-2 has credit limit of $800.00 (80000 cents) and balance 0
        // Attempt sale for $1,000.00 without supervisor PIN -> Must fail with 422
        val overdrawReq = CreateSaleRequest(
            idempotencyKey = "sale-overdraw-test-1",
            cashSessionId = session.sessionId,
            cashierUserId = "usr-cajero",
            customerId = "cust-2",
            lines = listOf(
                SaleLineItemRequest(
                    productId = null,
                    presentationId = null,
                    description = "Bulto Maíz Híbrido",
                    quantityMillis = 1000L,
                    expectedUnitPriceCents = 100000L, // $1,000.00
                    expectedTotalCents = 100000L,
                    taxRate = 0.0
                )
            ),
            payments = listOf(
                SalePaymentRequest(
                    method = "CREDIT",
                    amountCents = 100000L
                )
            ),
            expectedTotalCents = 100000L
        )

        val failRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(overdrawReq)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, failRes.status)

        // Attempt sale for $1,000.00 WITH supervisor PIN (admin pin is 1234)
        val authorizedReq = overdrawReq.copy(
            idempotencyKey = "sale-overdraw-test-2",
            supervisorAuthPin = "1234",
            supervisorAuthReason = "Autorizado por dueño"
        )
        val successRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(authorizedReq)
        }
        assertEquals(HttpStatusCode.Created, successRes.status)
        val sale = successRes.body<SaleResponse>()
        assertEquals(100000L, sale.totalCents)

        // Check statement has the audit note
        val statementRes = client.get("/api/v1/customers/cust-2/statement")
        assertEquals(HttpStatusCode.OK, statementRes.status)
        val statement = statementRes.body<CustomerStatementDto>()
        assertEquals(100000L, statement.customer.currentBalanceCents)
        val chargeMovement = statement.movements.last()
        assertEquals("CHARGE", chargeMovement.type)
        assertTrue(chargeMovement.description.contains("Sobregiro autorizado por"))

        // Attempt payment exceeding debt -> Must fail (ValidationException)
        val overpayReq = RegisterCreditPaymentRequest(
            customerId = "cust-2",
            cashSessionId = session.sessionId,
            cashierUserId = "usr-cajero",
            amountCents = 150000L // Exceeds 100000L
        )
        val overpayRes = client.post("/api/v1/customers/cust-2/credit/payments") {
            contentType(ContentType.Application.Json)
            setBody(overpayReq)
        }
        assertEquals(HttpStatusCode.BadRequest, overpayRes.status)

        // Valid partial payment of $400.00 -> Balance becomes $600.00
        val paymentReq = RegisterCreditPaymentRequest(
            customerId = "cust-2",
            cashSessionId = session.sessionId,
            cashierUserId = "usr-cajero",
            amountCents = 40000L
        )
        val payRes = client.post("/api/v1/customers/cust-2/credit/payments") {
            contentType(ContentType.Application.Json)
            setBody(paymentReq)
        }
        assertEquals(HttpStatusCode.Created, payRes.status)
        val mov = payRes.body<CreditMovementDto>()
        assertEquals(60000L, mov.balanceAfterCents)
        assertTrue(mov.receiptFolio?.startsWith("REC-") == true)

        // Check customer balance
        val custAfterRes = client.get("/api/v1/customers/cust-2")
        val custAfter = custAfterRes.body<CustomerDto>()
        assertEquals(60000L, custAfter.currentBalanceCents)
    }

    @Test
    fun testCreditReversalOnSaleCancellation() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // Open Cash Session
        val openRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(registerId = "caja-1", userId = "usr-cajero", initialFundCents = 50000L))
        }
        val session = openRes.body<mx.nexuspos.contracts.cash.CashSessionSummary>()

        // Initial balance of cust-1 is $350.00 (35000 cents)
        val saleReq = CreateSaleRequest(
            idempotencyKey = "sale-cancel-reversal-1",
            cashSessionId = session.sessionId,
            cashierUserId = "usr-cajero",
            customerId = "cust-1",
            lines = listOf(
                SaleLineItemRequest(
                    productId = null,
                    presentationId = null,
                    description = "Artículo fiado prueba",
                    quantityMillis = 1000L,
                    expectedUnitPriceCents = 20000L, // $200.00
                    expectedTotalCents = 20000L,
                    taxRate = 0.0
                )
            ),
            payments = listOf(
                SalePaymentRequest(
                    method = "CREDIT",
                    amountCents = 20000L
                )
            ),
            expectedTotalCents = 20000L
        )

        val saleRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleReq)
        }
        assertEquals(HttpStatusCode.Created, saleRes.status)
        val sale = saleRes.body<SaleResponse>()

        // Balance should be $350 + $200 = $550
        val custMid = client.get("/api/v1/customers/cust-1").body<CustomerDto>()
        assertEquals(55000L, custMid.currentBalanceCents)

        // Cancel sale
        val cancelRes = client.post("/api/v1/sales/${sale.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest(reason = "Cliente devolvió la mercancía", authorizedByUserId = "usr-admin"))
        }
        assertEquals(HttpStatusCode.OK, cancelRes.status)

        // Balance should be back to $350
        val custAfter = client.get("/api/v1/customers/cust-1").body<CustomerDto>()
        assertEquals(35000L, custAfter.currentBalanceCents)

        // Statement must contain REVERSAL movement
        val statement = client.get("/api/v1/customers/cust-1/statement").body<CustomerStatementDto>()
        val reversalMov = statement.movements.firstOrNull { it.type == "REVERSAL" }
        assertNotNull(reversalMov)
        assertEquals(20000L, reversalMov.amountCents)
        assertEquals(35000L, reversalMov.balanceAfterCents)
        assertTrue(reversalMov.description.contains("Cancelación de venta"))
    }
}

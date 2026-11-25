package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*

class TransactionHistoryModuleTest {
    @Test
    fun filtersTransactionsByDateTypeRegisterContentAndOperatorPermissions() = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:transactions_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        suspend fun login(pin: String) = http.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(pin))
        }.body<LoginResponse>().token
        val admin = login("1234")
        val cashier = login("0000")

        val zone = ZoneId.of("America/Mexico_City")
        val today = LocalDate.now(zone)
        val timestamp = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        db.inTransaction { conn ->
            conn.createStatement().use { statement ->
                listOf(
                    "INSERT INTO cash_registers (id, name) VALUES ('qa-other', 'Caja lateral')",
                    """INSERT INTO cash_sessions (id, register_id, user_id, initial_fund_cents, opened_at, closed_at, status) VALUES
                        ('qa-first','caja-1','usr-cajero',5000,$timestamp,NULL,'OPEN'),
                        ('qa-other-session','qa-other','usr-admin',0,$timestamp,NULL,'OPEN'),
                        ('qa-closed','caja-1','usr-cajero',2000,$yesterday,$timestamp,'CLOSED')""",
                    """INSERT INTO sales (id, folio, cash_session_id, user_id, status, subtotal_cents, tax_cents, total_cents, idempotency_key, created_at) VALUES
                        ('qa-sale',321,'qa-first','usr-cajero','COMPLETED',850,0,850,'qa-sale',$timestamp),
                        ('qa-hidden',654,'qa-other-session','usr-admin','COMPLETED',2000,0,2000,'qa-hidden',$timestamp),
                        ('qa-past',123,'qa-closed','usr-cajero','COMPLETED',333,0,333,'qa-past',$yesterday)""",
                    "INSERT INTO sale_lines (id, sale_id, description, quantity_millis, unit_price_cents, tax_rate, tax_cents, total_cents) VALUES ('qa-item','qa-sale','Agua mineral',1000,850,0,0,850)",
                    """INSERT INTO cash_movements (id, session_id, type, amount_cents, concept, user_id, created_at) VALUES
                        ('qa-in','qa-first','CASH_IN',1900,'Cambio recibido','usr-cajero',$timestamp),
                        ('qa-out','qa-first','CASH_OUT',550,'Retiro para cambio','usr-cajero',$timestamp)""",
                    "INSERT INTO customers (id,name) VALUES ('qa-client','Cliente Mar')",
                    """INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, cash_session_id, description, user_id, created_at, payment_method, receipt_folio) VALUES
                        ('qa-payment','qa-client','PAYMENT',2500,0,'qa-first','Pago de crédito','usr-cajero',$timestamp,'TRANSFER','AB-0105')"""
                ).forEach(statement::executeUpdate)
            }
        }

        suspend fun search(request: TransactionHistoryRequest, token: String? = admin) = http.post("/api/v1/transactions/search") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.Unauthorized, search(TransactionHistoryRequest(), null).status)
        val restricted = search(TransactionHistoryRequest(), cashier).body<TransactionHistoryPage>()
        assertEquals(TransactionType.entries.toSet(), restricted.records.map { it.type }.toSet())
        assertEquals(setOf("qa-sale", "qa-in", "qa-out", "qa-payment", "qa-first"), restricted.records.map { it.id }.toSet())
        assertEquals("Caja lateral", restricted.registers.single { it.id == "qa-other" }.name)
        assertEquals("qa-hidden", search(TransactionHistoryRequest(type = TransactionType.SALE, registerId = "qa-other"))
            .body<TransactionHistoryPage>().records.single().id)
        for (query in listOf("321", "8,50", "agua mineral")) {
            assertEquals("qa-sale", search(TransactionHistoryRequest(query = query)).body<TransactionHistoryPage>().records.single().id)
        }
        val payment = search(TransactionHistoryRequest(query = "cliente mar", type = TransactionType.CREDIT_PAYMENT))
            .body<TransactionHistoryPage>().records.single()
        assertEquals("TRANSFER", payment.paymentMethod)
        assertEquals(payment, search(TransactionHistoryRequest(query = "ab-0105")).body<TransactionHistoryPage>().records.single())
        val past = TransactionHistoryRequest(period = TransactionPeriod.YESTERDAY, type = TransactionType.SALE)
        assertEquals("qa-past", search(past).body<TransactionHistoryPage>().records.single().id)
        assertTrue(search(past, cashier).body<TransactionHistoryPage>().records.isEmpty())
        assertEquals(HttpStatusCode.BadRequest, search(TransactionHistoryRequest(period = TransactionPeriod.CUSTOM)).status)
    }
}

package mx.nexuspos.server

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto
import mx.nexuspos.contracts.loyalty.LoyaltySummaryDto
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class LoyaltyModuleTest {

    private fun createTestDb(): DatabaseFactory {
        val dbName = "test_" + UUID.randomUUID().toString().replace("-", "")
        return DatabaseFactory(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = ""
        )
    }

    @Test
    fun testLoyaltyEarningRedemptionAdjustmentsAndRefunds() = testApplication {
        val testDb = createTestDb()
        application {
            nexusModule(testDb)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // 1. Configurar Settings con Monedero Activo: 2% Cashback, mínimo de canje $5.00 (500 cents)
        val initialSettings = client.get("/api/v1/system/settings").body<SettingsDto>()
        val updatedSettingsReq = initialSettings.copy(
            loyaltyEnabled = true,
            loyaltyCashbackPercent = 2.0,
            loyaltyMinRedeemCents = 500L
        )
        val savedSettings = client.put("/api/v1/system/settings") {
            contentType(ContentType.Application.Json)
            setBody(updatedSettingsReq)
        }.body<SettingsDto>()

        assertTrue(savedSettings.loyaltyEnabled)
        assertEquals(2.0, savedSettings.loyaltyCashbackPercent)
        assertEquals(500L, savedSettings.loyaltyMinRedeemCents)

        // 2. Abrir sesión de caja
        val openSessionRes = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest(initialFundCents = 50_000L, registerId = "caja-1", userId = "usr-admin"))
        }
        assertEquals(HttpStatusCode.Created, openSessionRes.status)
        val session = openSessionRes.body<CashSessionSummary>()

        // 3. Crear cliente registrado para el programa de lealtad
        val createCustRes = client.post("/api/v1/customers") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateCustomerRequest(
                    name = "Carlos Santana",
                    phone = "555-888-9999",
                    email = "carlos@example.com",
                    creditLimitCents = 100_000L
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createCustRes.status)
        val customer = createCustRes.body<CustomerDto>()
        assertEquals(0L, customer.loyaltyBalanceCents)
        assertEquals(0L, customer.totalLoyaltyEarnedCents)
        assertEquals(0L, customer.totalLoyaltyRedeemedCents)

        // Obtener producto existente en catálogo (ej: Coca Cola 600ml a $18.50)
        val prods = client.get("/api/v1/products?barcode=7501055300078").body<List<ProductDto>>()
        val cokeProd = prods.first()
        val cokePres = cokeProd.presentations.first { it.barcode == "7501055300078" }

        // 4. Venta 1: 10x Coca Cola = $185.00 (18500 cents), pagado en EFECTIVO por el cliente
        val sale1Req = CreateSaleRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            customerId = customer.id,
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokeProd.name,
                    quantityMillis = 10_000L, // 10 unidades
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 18500L
                )
            ),
            payments = listOf(
                SalePaymentRequest(method = "CASH", amountCents = 18500L)
            ),
            expectedTotalCents = 18500L
        )

        val sale1Res = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(sale1Req)
        }
        assertEquals(HttpStatusCode.Created, sale1Res.status)
        val sale1 = sale1Res.body<SaleResponse>()
        assertEquals(18500L, sale1.totalCents)

        // Verificar recompensa de lealtad (2% de $185.00 = $3.70 -> 370 cents)
        assertNotNull(sale1.loyaltyReward)
        assertEquals(370L, sale1.loyaltyReward!!.earnedCents)
        assertEquals(0L, sale1.loyaltyReward!!.redeemedCents)
        assertEquals(370L, sale1.loyaltyReward!!.finalBalanceCents)

        // Consultar cliente y verificar saldo actualizado
        val custAfterSale1 = client.get("/api/v1/customers/${customer.id}").body<CustomerDto>()
        assertEquals(370L, custAfterSale1.loyaltyBalanceCents)
        assertEquals(370L, custAfterSale1.totalLoyaltyEarnedCents)
        assertEquals(0L, custAfterSale1.totalLoyaltyRedeemedCents)

        // Consultar movimientos de monedero
        val movementsAfterSale1 = client.get("/api/v1/customers/${customer.id}/loyalty").body<List<LoyaltyMovementDto>>()
        assertEquals(1, movementsAfterSale1.size)
        assertEquals("EARN", movementsAfterSale1.first().type)
        assertEquals(370L, movementsAfterSale1.first().amountCents)
        assertEquals(370L, movementsAfterSale1.first().balanceAfterCents)

        // 5. Intento de canje por debajo del mínimo (300 cents < 500 cents min)
        val saleMinRedeemFailReq = CreateSaleRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            customerId = customer.id,
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokeProd.name,
                    quantityMillis = 1_000L,
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 1850L
                )
            ),
            payments = listOf(
                SalePaymentRequest(method = "LOYALTY", amountCents = 300L),
                SalePaymentRequest(method = "CASH", amountCents = 1550L)
            ),
            expectedTotalCents = 1850L
        )
        val failMinRedeemRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleMinRedeemFailReq)
        }
        assertEquals(HttpStatusCode.BadRequest, failMinRedeemRes.status)

        // 6. Ajuste manual de monedero (Supervisor agrega $10.00 = 1000 cents de cortesía)
        val adjustReq = AdjustLoyaltyBalanceRequest(
            customerId = customer.id,
            amountCents = 1000L,
            reason = "Bono de cortesía apertura",
            userId = "usr-admin"
        )
        val custAfterAdjust = client.post("/api/v1/customers/${customer.id}/loyalty/adjust") {
            contentType(ContentType.Application.Json)
            setBody(adjustReq)
        }.body<CustomerDto>()
        // Saldo anterior 370 + 1000 = 1370 cents ($13.70)
        assertEquals(1370L, custAfterAdjust.loyaltyBalanceCents)

        // 7. Venta Mixta: 5x Coca Cola = $92.50 (9250 cents).
        // Paga $10.00 (1000 cents) con LOYALTY y $82.50 (8250 cents) con CASH.
        // Cashback del 2% solo debe aplicar sobre el dinero real ($82.50 * 2% = $1.65 -> 165 cents)
        val sale2Req = CreateSaleRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            customerId = customer.id,
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokeProd.name,
                    quantityMillis = 5_000L,
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 9250L
                )
            ),
            payments = listOf(
                SalePaymentRequest(method = "LOYALTY", amountCents = 1000L),
                SalePaymentRequest(method = "CASH", amountCents = 8250L)
            ),
            expectedTotalCents = 9250L
        )
        val sale2Res = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(sale2Req)
        }
        assertEquals(HttpStatusCode.Created, sale2Res.status)
        val sale2 = sale2Res.body<SaleResponse>()
        assertNotNull(sale2.loyaltyReward)
        assertEquals(1000L, sale2.loyaltyReward!!.redeemedCents)
        assertEquals(165L, sale2.loyaltyReward!!.earnedCents) // 2% de 8250
        // Saldo esperado: 1370 - 1000 + 165 = 535 cents
        assertEquals(535L, sale2.loyaltyReward!!.finalBalanceCents)

        val custAfterSale2 = client.get("/api/v1/customers/${customer.id}").body<CustomerDto>()
        assertEquals(535L, custAfterSale2.loyaltyBalanceCents)
        assertEquals(370L + 1000L + 165L, custAfterSale2.totalLoyaltyEarnedCents) // 1535 cents
        assertEquals(1000L, custAfterSale2.totalLoyaltyRedeemedCents)

        // 8. Intento de pagar con más saldo del disponible (ej: 600 cents cuando tiene 535)
        val saleOverdraftReq = CreateSaleRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            customerId = customer.id,
            lines = listOf(
                SaleLineItemRequest(
                    productId = cokeProd.id,
                    presentationId = cokePres.id,
                    description = cokeProd.name,
                    quantityMillis = 1_000L,
                    expectedUnitPriceCents = 1850L,
                    expectedTotalCents = 1850L
                )
            ),
            payments = listOf(
                SalePaymentRequest(method = "LOYALTY", amountCents = 600L),
                SalePaymentRequest(method = "CASH", amountCents = 1250L)
            ),
            expectedTotalCents = 1850L
        )
        val overdraftRes = client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            setBody(saleOverdraftReq)
        }
        assertEquals(HttpStatusCode.BadRequest, overdraftRes.status)

        // 9. Devolución de la Venta 1: Reversión proporcional de cashback
        // Devolvemos 5 de las 10 unidades de la Venta 1 (50% de la venta = $92.50)
        // El cashback a revertir es 50% de 370 cents = 185 cents
        val sale1Line = sale1.lines.first()
        val refundReq = CreateSaleRefundRequest(
            cashSessionId = session.sessionId,
            cashierUserId = "usr-admin",
            supervisorPin = "1234",
            reason = "DEFECTIVE",
            refundMethod = "CASH",
            notes = "Devolución parcial prueba",
            lines = listOf(
                RefundLineItemRequest(
                    saleLineId = sale1Line.id,
                    quantityMillis = 5_000L,
                    reenterStock = true
                )
            )
        )
        val refundRes = client.post("/api/v1/sales/${sale1.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(refundReq)
        }
        assertEquals(HttpStatusCode.Created, refundRes.status)

        // Verificar que el saldo del cliente se redujo por la reversión: 535 - 185 = 350 cents
        val custAfterRefund = client.get("/api/v1/customers/${customer.id}").body<CustomerDto>()
        assertEquals(350L, custAfterRefund.loyaltyBalanceCents)

        // 10. Resumen de lealtad (Summary)
        val summary = client.get("/api/v1/customers/${customer.id}/loyalty/summary").body<LoyaltySummaryDto>()
        assertEquals(350L, summary.currentBalanceCents)
        assertEquals(1000L, summary.totalRedeemedCents)
        assertTrue(summary.movements.size >= 4) // EARN (sale1), ADJUSTMENT, REDEEM (sale2), EARN (sale2), REFUND_REVERSAL
    }
}

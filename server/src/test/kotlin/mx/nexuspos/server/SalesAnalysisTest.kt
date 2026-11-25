package mx.nexuspos.server

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.*
import mx.nexuspos.contracts.catalog.*
import mx.nexuspos.contracts.reports.*
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SalesAnalysisTest {
    @Test
    fun snapshotsKeepPresentationKitAndDepartmentCostsAndReportAdjustmentsOnTheirOwnDay() = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:sales_report_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val session = client.post("/api/v1/cash-sessions") {
            contentType(ContentType.Application.Json)
            setBody(OpenSessionRequest("caja-1", "usr-admin", 5000))
        }.body<CashSessionSummary>().sessionId
        val single = product(client, "PACK-R", 101, factor = 6000, department = "dept-despensa")
        val ingredient = product(client, "ING-R", 303, unit = "KG")
        val kit = product(client, "KIT-R", 0, tracked = false)
        val recipe = listOf(KitComponent(single.id, 2000), KitComponent(ingredient.id, 500))
        assertEquals(HttpStatusCode.OK, client.put("/api/v1/products/${kit.id}/kit") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductKitRequest(recipe))
        }.status)

        val sold = sell(client, session, single, 2000)
        val kitSale = sell(client, session, kit, 1000)
        val cancelled = sell(client, session, single, 1000)
        val legacy = sell(client, session, single, 1000)
        val beforeChanges = client.get("/api/v1/reports/daily?sessionId=$session").body<DailyFinancialReportDto>()
        assertEquals(2778L, beforeChanges.totalCostCents) // 1212 + 354 (202 + 151.5) + 606 + 606
        assertEquals(0L, beforeChanges.estimatedCostLines)

        db.inTransaction { conn ->
            conn.prepareStatement("UPDATE products SET cost_cents = 400, department_id = 'dept-bebidas' WHERE id = ?").use {
                it.setString(1, single.id)
                it.executeUpdate()
                it.setString(1, ingredient.id)
                it.executeUpdate()
            }
            conn.prepareStatement("UPDATE sale_lines SET cost_snapshot_cents = NULL, base_factor_millis = NULL WHERE sale_id = ?").use {
                it.setString(1, legacy.id)
                it.executeUpdate()
            }
        }
        val afterChanges = client.get("/api/v1/reports/daily?sessionId=$session").body<DailyFinancialReportDto>()
        assertEquals(4572L, afterChanges.totalCostCents) // known snapshot costs plus 6 x 400 estimated for the legacy line
        assertEquals(1L, afterChanges.estimatedCostLines)

        suspend fun returnPart(restock: Boolean) = client.post("/api/v1/sales/${sold.id}/refund") {
            contentType(ContentType.Application.Json)
            setBody(CreateSaleRefundRequest(
                session, "usr-admin", reason = "DEFECTO",
                lines = listOf(RefundLineItemRequest(sold.lines.single().id, 1000, restock))
            ))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<SaleRefundResponse>()
        val firstReturn = returnPart(true)
        val secondReturn = returnPart(false)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/sales/${cancelled.id}/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelSaleRequest("Duplicada"))
        }.status)

        val dayOne = Instant.parse("2026-11-24T06:00:00Z").toEpochMilli()
        val dayTwo = Instant.parse("2026-11-25T06:00:00Z").toEpochMilli()
        db.inTransaction { conn ->
            conn.prepareStatement("UPDATE sales SET created_at = ? WHERE id IN (?, ?, ?, ?)").use {
                it.setLong(1, dayOne)
                listOf(sold, kitSale, cancelled, legacy).forEachIndexed { index, sale -> it.setString(index + 2, sale.id) }
                it.executeUpdate()
            }
            conn.prepareStatement("UPDATE sales SET cancelled_at = ? WHERE id = ?").use {
                it.setLong(1, dayTwo)
                it.setString(2, cancelled.id)
                it.executeUpdate()
            }
            conn.prepareStatement("UPDATE sale_refunds SET created_at = ? WHERE id IN (?, ?)").use {
                it.setLong(1, dayTwo)
                it.setString(2, firstReturn.id)
                it.setString(3, secondReturn.id)
                it.executeUpdate()
            }
        }

        val request = SalesAnalysisRequest("1234", "2026-11-24", "2026-11-25", "America/Mexico_City")
        assertEquals(HttpStatusCode.Forbidden, report(client, request.copy(pin = "0000")).status)
        assertEquals(HttpStatusCode.BadRequest, report(client, request.copy(fromDate = "2026-11-26")).status)
        val byDay = report(client, request).body<SalesAnalysisReport>()
        assertEquals(listOf("2026-11-24", "2026-11-25"), byDay.rows.map { it.label })
        assertEquals(15000L, byDay.rows[0].totals.salesCents)
        assertEquals(6000L, byDay.rows[1].totals.refundsCents)
        assertEquals(3000L, byDay.rows[1].totals.cancellationsCents)
        assertEquals(-1212L, byDay.rows[1].totals.costCents)
        assertEquals(SalesAnalysisTotals(15000, 3000, 6000, 6000, 3360, 2640, 1), byDay.totals)

        val periodBeforeAdjustments = report(client, request.copy(toDate = "2026-11-24")).body<SalesAnalysisReport>()
        assertEquals(15000L, periodBeforeAdjustments.totals.netSalesCents)
        assertEquals(4572L, periodBeforeAdjustments.totals.costCents)
        val byProduct = report(client, request.copy(groupBy = SalesAnalysisGroup.PRODUCT)).body<SalesAnalysisReport>()
        assertEquals(byDay.totals, byProduct.totals)
        assertEquals(6000L, byProduct.rows.single { it.sku == single.sku }.netQuantityMillis)
        assertEquals(354L, byProduct.rows.single { it.sku == kit.sku }.totals.costCents)
        val byDept = report(client, request.copy(groupBy = SalesAnalysisGroup.DEPARTMENT)).body<SalesAnalysisReport>()
        assertEquals(byDay.totals, byDept.totals)
        assertEquals(606L, byDept.rows.single { it.key == "dept-despensa" }.totals.costCents)
        assertEquals(2400L, byDept.rows.single { it.key == "dept-bebidas" }.totals.costCents)
    }

    private suspend fun product(
        client: HttpClient,
        sku: String,
        cost: Long,
        factor: Long = 1000,
        unit: String = "PZA",
        department: String? = null,
        tracked: Boolean = true
    ): ProductDto = client.post("/api/v1/products") {
        contentType(ContentType.Application.Json)
        setBody(CreateProductRequest(
            sku = sku, name = sku, baseUnit = unit, departmentId = department,
            costCents = cost, usesInventory = tracked, initialStockMillis = if (tracked) 200000 else 0,
            presentations = listOf(CreatePresentationRequest(barcode = sku, name = sku, factorMillis = factor, priceCents = 3000))
        ))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun sell(client: HttpClient, session: String, product: ProductDto, quantity: Long): SaleResponse =
        client.post("/api/v1/sales") {
            contentType(ContentType.Application.Json)
            val total = quantity * 3
            setBody(CreateSaleRequest(
                UUID.randomUUID().toString(), session, "usr-admin",
                lines = listOf(SaleLineItemRequest(
                    presentationId = product.presentations.single().id, description = product.name,
                    quantityMillis = quantity, expectedUnitPriceCents = 3000, expectedTotalCents = total
                )),
                payments = listOf(SalePaymentRequest("CASH", total)), expectedTotalCents = total
            ))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun report(client: HttpClient, request: SalesAnalysisRequest) = client.post("/api/v1/reports/sales-analysis") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}

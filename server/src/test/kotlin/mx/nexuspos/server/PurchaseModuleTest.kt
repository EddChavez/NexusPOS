package mx.nexuspos.server

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.catalog.CreatePresentationRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.contracts.purchases.*
import mx.nexuspos.server.persistence.DatabaseFactory
import java.util.UUID
import kotlin.test.*

class PurchaseModuleTest {
    @Test
    fun receiptAndPartialReturnsReconcileInventoryLotsAndAmounts() = purchasesTest { client ->
        val denied = client.post("/api/v1/purchases/suppliers") {
            contentType(ContentType.Application.Json)
            setBody(PurchaseAuthorization("0000", SaveSupplierRequest("Proveedor")))
        }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        val supplier = createSupplier(client)
        val product = createProduct(client, "YOGUR", perishable = true)
        val suggested = client.get("/api/v1/inventory/reorder-suggestions").body<ReorderSuggestionsReportDto>()
        assertTrue(suggested.items.single { it.productId == product.id }.isPerishable)
        val request = ReceivePurchaseRequest(
            "receipt-1", supplier.id, PurchaseDocumentType.INVOICE, "Fac-25",
            listOf(ReceivePurchaseLine(product.id, 1500, 1251, "LOT-7", "2028-05-04"))
        )

        val receipts = coroutineScope {
            List(2) { async { receive(client, request) } }.awaitAll()
        }
        assertTrue(receipts.all { it.status == HttpStatusCode.Created })
        val purchase = receipts.first().body<PurchaseDetails>()
        assertEquals(purchase, receipts.last().body<PurchaseDetails>())
        assertEquals("FAC-25", purchase.summary.documentNumber)
        assertEquals(1877L, purchase.summary.totalCents)
        assertEquals("Administrador", purchase.receivedBy)
        assertEquals(HttpStatusCode.Conflict, receive(client, request.copy(documentNumber = "FAC-26")).status)
        val duplicate = receive(client, request.copy(idempotencyKey = "second-key"))
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("PURCHASE_DOCUMENT_EXISTS", duplicate.body<ApiError>().code)
        assertEquals(1500L, product(client, product.id).currentStockMillis)
        assertEquals(1251L, product(client, product.id).costCents)
        assertEquals(1500L, lots(client, product.id).single().quantityMillis)
        assertEquals(1500L, ledger(client, product.id).single().quantityMillis)
        assertEquals("usr-admin", ledger(client, product.id).single().userId)
        assertEquals(purchase.summary.id, ledger(client, product.id).single().reference)

        val retired = client.put("/api/v1/purchases/suppliers/${supplier.id}") {
            contentType(ContentType.Application.Json)
            setBody(PurchaseAuthorization("1234", SaveSupplierRequest("Proveedor archivado", isActive = false)))
        }.body<SupplierDto>()
        assertFalse(retired.isActive)
        assertEquals(HttpStatusCode.BadRequest, receive(client, request.copy(
            idempotencyKey = "after-deactivation", documentNumber = "FAC-27"
        )).status)

        val line = purchase.lines.single()
        val first = ReturnPurchaseRequest("return-1", "Empaque dañado", listOf(ReturnPurchaseLine(line.id, 500)))
        val firstResult = returnStock(client, purchase.summary.id, first).body<PurchaseReturn>()
        assertEquals(626L, firstResult.totalCents)
        assertEquals(firstResult, returnStock(client, purchase.summary.id, first).body<PurchaseReturn>())
        assertEquals(1000L, product(client, product.id).currentStockMillis)
        assertEquals(1000L, lots(client, product.id).single().quantityMillis)

        val second = ReturnPurchaseRequest("return-2", "Resto del lote", listOf(ReturnPurchaseLine(line.id, 1000)))
        assertEquals(1251L, returnStock(client, purchase.summary.id, second).body<PurchaseReturn>().totalCents)
        val detail = client.get("/api/v1/purchases/receipts/${purchase.summary.id}").body<PurchaseDetails>()
        assertEquals(1500L, detail.lines.single().returnedMillis)
        assertEquals(detail.summary.totalCents, detail.summary.returnedCents)
        assertEquals(supplier.name, detail.summary.supplierName)
        assertEquals(2, detail.returns.size)
        val history = client.get("/api/v1/purchases/receipts").body<List<PurchaseSummary>>()
        assertEquals(detail.summary, history.single())
        assertEquals(0L, product(client, product.id).currentStockMillis)
        assertTrue(lots(client, product.id).isEmpty())
        assertEquals(2, ledger(client, product.id).count { it.type == "PURCHASE_RETURN" })
        assertEquals(HttpStatusCode.Conflict, returnStock(client, purchase.summary.id, second.copy(idempotencyKey = "another-return")).status)
    }

    @Test
    fun invalidReceiptAndInsufficientReturnLeaveAllPartiesUnchanged() = purchasesTest { client ->
        val supplier = createSupplier(client)
        val units = createProduct(client, "ARROZ")
        val chilled = createProduct(client, "LECHE", perishable = true)
        val request = ReceivePurchaseRequest(
            "receipt-2", supplier.id, PurchaseDocumentType.DELIVERY_NOTE, "REM-7",
            listOf(ReceivePurchaseLine(units.id, 2000, 880), ReceivePurchaseLine(chilled.id, 500, 1270))
        )
        assertEquals(HttpStatusCode.BadRequest, receive(client, request).status)
        assertEquals(0L, product(client, units.id).currentStockMillis)
        assertTrue(ledger(client, units.id).isEmpty())
        val corrected = request.copy(lines = listOf(
            request.lines.first(), request.lines.last().copy(lotNumber = "LOT-2", expirationDate = "2028-03-05")
        ))
        val purchase = receive(client, corrected).body<PurchaseDetails>()
        val manualLoss = client.post("/api/v1/inventory/movements") {
            contentType(ContentType.Application.Json)
            setBody(RegisterStockMovementRequest(units.id, "LOSS", 1000, "Caja dañada"))
        }
        assertEquals(HttpStatusCode.Created, manualLoss.status)

        val tooMuch = ReturnPurchaseRequest("return-3", "Rechazo", purchase.lines.map { ReturnPurchaseLine(it.id, it.quantityMillis) })
        assertEquals(HttpStatusCode.UnprocessableEntity, returnStock(client, purchase.summary.id, tooMuch).status)
        assertEquals(500L, lots(client, chilled.id).single().quantityMillis)
        assertEquals(500L, product(client, chilled.id).currentStockMillis)
        assertTrue(client.get("/api/v1/purchases/receipts/${purchase.summary.id}").body<PurchaseDetails>().returns.isEmpty())
        assertTrue(ledger(client, chilled.id).none { it.type == "PURCHASE_RETURN" })

        val partial = tooMuch.copy(lines = listOf(ReturnPurchaseLine(purchase.lines.first().id, 1000)))
        assertEquals(HttpStatusCode.Created, returnStock(client, purchase.summary.id, partial).status)
        assertEquals(0L, product(client, units.id).currentStockMillis)
    }

    private fun purchasesTest(block: suspend (HttpClient) -> Unit) = testApplication {
        val db = DatabaseFactory(
            "jdbc:h2:mem:test_${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa", ""
        )
        application { nexusModule(db) }
        val http = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        block(http)
    }

    private suspend fun createSupplier(client: HttpClient): SupplierDto = client.post("/api/v1/purchases/suppliers") {
        contentType(ContentType.Application.Json)
        setBody(PurchaseAuthorization("1234", SaveSupplierRequest("Distribuidora Central")))
    }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun createProduct(client: HttpClient, sku: String, perishable: Boolean = false): ProductDto =
        client.post("/api/v1/products") {
            contentType(ContentType.Application.Json)
            setBody(CreateProductRequest(
                sku, sku, baseUnit = if (perishable) "KG" else "PZA", isPerishable = perishable,
                presentations = listOf(CreatePresentationRequest(barcode = "TEST-$sku", name = "Unidad", priceCents = 2000))
            ))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun receive(client: HttpClient, request: ReceivePurchaseRequest) = client.post("/api/v1/purchases/receipts") {
        contentType(ContentType.Application.Json)
        setBody(PurchaseAuthorization("1234", request))
    }

    private suspend fun returnStock(client: HttpClient, id: String, request: ReturnPurchaseRequest) =
        client.post("/api/v1/purchases/receipts/$id/returns") {
            contentType(ContentType.Application.Json)
            setBody(PurchaseAuthorization("1234", request))
        }

    private suspend fun product(client: HttpClient, id: String): ProductDto = client.get("/api/v1/products/$id").body()
    private suspend fun lots(client: HttpClient, id: String): List<ProductLotDto> = client.get("/api/v1/inventory/lots") {
        parameter("productId", id)
    }.body()
    private suspend fun ledger(client: HttpClient, id: String): List<StockMovementDto> = client.get("/api/v1/inventory/movements") {
        parameter("productId", id)
    }.body()
}

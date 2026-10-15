package mx.nexuspos.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemRequest
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.contracts.system.SettingsDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContractsSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testProductDtoSerialization() {
        val presentation = ProductPresentationDto(
            id = "pres-1",
            productId = "prod-1",
            barcode = "7501000111204",
            name = "Arroz 1kg",
            factorMillis = 1000L,
            priceCents = 2850L
        )
        val product = ProductDto(
            id = "prod-1",
            sku = "AB-001",
            name = "Arroz Morelos 1kg",
            baseUnit = "PZA",
            costCents = 2100L,
            taxRate = 0.0,
            usesInventory = true,
            currentStockMillis = 45000L,
            presentations = listOf(presentation)
        )

        val encoded = json.encodeToString(product)
        assertTrue(encoded.contains("2850"))
        assertTrue(encoded.contains("7501000111204"))

        val decoded = json.decodeFromString<ProductDto>(encoded)
        assertEquals(product.name, decoded.name)
        assertEquals(2850L, decoded.presentations.first().priceCents)
    }

    @Test
    fun testCreateSaleRequestSerialization() {
        val request = CreateSaleRequest(
            idempotencyKey = "uuid-1234",
            cashSessionId = "session-1",
            cashierUserId = "user-1",
            lines = listOf(
                SaleLineItemRequest(
                    productId = "prod-1",
                    description = "Arroz 1kg",
                    quantityMillis = 1000L,
                    expectedUnitPriceCents = 2850L,
                    expectedTotalCents = 2850L
                )
            ),
            payments = listOf(
                SalePaymentRequest(
                    method = "CASH",
                    amountCents = 2850L,
                    receivedCents = 5000L
                )
            ),
            expectedTotalCents = 2850L
        )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CreateSaleRequest>(encoded)
        assertEquals("uuid-1234", decoded.idempotencyKey)
        assertEquals(2850L, decoded.expectedTotalCents)
    }

    @Test
    fun testSettingsDtoNegativeStockFlag() {
        val settings = SettingsDto(allowNegativeStock = false)
        val encoded = json.encodeToString(settings)
        val decoded = json.decodeFromString<SettingsDto>(encoded)
        assertEquals(false, decoded.allowNegativeStock)
    }
}

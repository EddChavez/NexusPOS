package mx.nexuspos.desktop.sales

import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.test.*

class CartGridTest {

    @Test
    fun testPieceProductCartLineProperties() {
        val line = CartLine(
            id = "c1",
            productId = "p1",
            presentationId = "pr1",
            barcode = "7501055300076",
            name = "Coca Cola 600ml",
            category = "Refrescos y Bebidas",
            unit = ProductUnit.PIECE,
            quantity = Quantity.pieces(3),
            unitPrice = Money.fromCents(1850),
            taxRate = 0.16,
            total = Money.fromCents(5550),
            currentStockMillis = 28000L,
            isWeighed = false
        )

        assertFalse(line.isWeighed)
        assertEquals("Coca Cola 600ml", line.name)
        assertEquals("Refrescos y Bebidas", line.category)
        assertEquals(3L, line.quantity.millis / 1000)
        assertEquals("$18.50", line.unitPrice.format())
        assertEquals("$55.50", line.total.format())
        assertEquals(28L, (line.currentStockMillis ?: 0L) / 1000)
    }

    @Test
    fun testWeighedProductCartLineProperties() {
        val line = CartLine(
            id = "c2",
            productId = "p2",
            presentationId = "pr2",
            barcode = "200000101250",
            name = "Manzana Gala Nacional",
            category = "Frutas y Verduras",
            unit = ProductUnit.KILOGRAM,
            quantity = Quantity.fromDouble(1.250),
            unitPrice = Money.fromCents(4500),
            taxRate = 0.0,
            total = Money.fromCents(5625),
            currentStockMillis = 14500L,
            isWeighed = true
        )

        assertTrue(line.isWeighed)
        assertEquals("1.250 kg", line.quantity.format(line.unit))
        assertEquals("$45.00", line.unitPrice.format())
        assertEquals("$56.25", line.total.format())
        assertEquals("14.5 kg", "${(line.currentStockMillis ?: 0L) / 1000.0} kg")
    }

    @Test
    fun testAddProductPresentationPopulatesCategoryAndStock() {
        val vm = SalesViewModel()

        val prod = ProductDto(
            id = "p-100",
            sku = "MANZ-01",
            name = "Manzana Roja",
            departmentName = "Frutería",
            baseUnit = "KG",
            usesInventory = true,
            currentStockMillis = 35000L
        )

        val pres = ProductPresentationDto(
            id = "pres-100",
            productId = "p-100",
            barcode = "2000100",
            name = "Manzana Roja por Kilo",
            factorMillis = 1000L,
            priceCents = 3800L
        )

        vm.addProductPresentation(prod, pres, Quantity.fromDouble(2.500))

        assertEquals(1, vm.uiState.value.cartLines.size)
        val addedLine = vm.uiState.value.cartLines.first()

        assertEquals("Frutería", addedLine.category)
        assertTrue(addedLine.isWeighed)
        assertEquals(35000L, addedLine.currentStockMillis)
        assertEquals(Money.fromCents(9500), addedLine.total)
    }

    @Test
    fun testUnlimitedStockIndicator() {
        val line = CartLine(
            id = "c3",
            productId = null,
            presentationId = null,
            barcode = "VAR-GEN",
            name = "Servicio de Copias",
            category = "Servicios",
            unit = ProductUnit.PIECE,
            quantity = Quantity.ONE,
            unitPrice = Money.fromCents(200),
            taxRate = 0.0,
            total = Money.fromCents(200),
            currentStockMillis = null,
            isWeighed = false
        )

        assertNull(line.currentStockMillis)
        assertFalse(line.isWeighed)
    }

    @Test
    fun testUpdateCartLineQuantityRecalculatesTotals() {
        val vm = SalesViewModel()
        val prod = ProductDto(
            id = "p-coca",
            sku = "COCA-600",
            name = "Coca-Cola Original 600ml",
            departmentName = "Bebidas",
            baseUnit = "PZA",
            usesInventory = true,
            currentStockMillis = 51000L
        )
        val pres = ProductPresentationDto(
            id = "pres-coca",
            productId = "p-coca",
            barcode = "7501055300078",
            name = "Coca-Cola Original 600ml",
            factorMillis = 1000L,
            priceCents = 1850L
        )

        vm.addProductPresentation(prod, pres, Quantity.pieces(1))
        val initialLine = vm.uiState.value.cartLines.first()
        assertEquals(Money.fromCents(1850), initialLine.total)
        assertEquals(Money.fromCents(1850), vm.uiState.value.total)

        // Actualizar a 3 piezas
        vm.updateCartLineQuantity(initialLine.id, Quantity.pieces(3))

        val updatedLine = vm.uiState.value.cartLines.first()
        assertEquals(3L, updatedLine.quantity.millis / 1000L)
        assertEquals(Money.fromCents(5550), updatedLine.total)
        assertEquals(Money.fromCents(5550), vm.uiState.value.total)
    }

    @Test
    fun testUpdateCartLineQuantityRejectsZeroOrNegative() {
        val vm = SalesViewModel()
        val prod = ProductDto(
            id = "p-1",
            sku = "SKU-1",
            name = "Producto 1",
            departmentName = "General",
            baseUnit = "PZA"
        )
        val pres = ProductPresentationDto(
            id = "pres-1",
            productId = "p-1",
            barcode = "12345",
            name = "Pres 1",
            factorMillis = 1000L,
            priceCents = 1000L
        )
        vm.addProductPresentation(prod, pres, Quantity.pieces(2))
        val line = vm.uiState.value.cartLines.first()

        vm.updateCartLineQuantity(line.id, Quantity.ZERO)
        assertEquals(2L, vm.uiState.value.cartLines.first().quantity.millis / 1000L)

        vm.updateCartLineQuantity(line.id, Quantity(-1000L))
        assertEquals(2L, vm.uiState.value.cartLines.first().quantity.millis / 1000L)
    }

    @Test
    fun testUpdateCartLineQuantityStockWarning() {
        val vm = SalesViewModel()
        val prod = ProductDto(
            id = "p-stock",
            sku = "SKU-STK",
            name = "Galletas",
            departmentName = "Abarrotes",
            baseUnit = "PZA",
            usesInventory = true,
            currentStockMillis = 5000L // 5 piezas disponibles
        )
        val pres = ProductPresentationDto(
            id = "pres-stk",
            productId = "p-stock",
            barcode = "98765",
            name = "Galletas",
            factorMillis = 1000L,
            priceCents = 1500L
        )
        vm.addProductPresentation(prod, pres, Quantity.pieces(2))
        val line = vm.uiState.value.cartLines.first()

        // Pedir 10 piezas (supera stock de 5)
        vm.updateCartLineQuantity(line.id, Quantity.pieces(10))

        assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage!!.contains("excede la existencia"))
        assertEquals(10L, vm.uiState.value.cartLines.first().quantity.millis / 1000L)
    }
}

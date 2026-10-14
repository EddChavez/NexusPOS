package org.ozafry.nexuspos

import org.ozafry.nexuspos.domain.CartItem
import org.ozafry.nexuspos.domain.POSCheckoutService
import org.ozafry.nexuspos.models.ProductDto
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCommonTest {

    @Test
    fun testCartCalculationWithoutDiscounts() {
        val prod1 = ProductDto(
            id = "p1",
            sku = "SKU-1",
            name = "Café",
            price = 50.0,
            taxRate = 0.16,
            stockQuantity = 10.0,
            updatedAt = 0L
        )
        val prod2 = ProductDto(
            id = "p2",
            sku = "SKU-2",
            name = "Galleta",
            price = 20.0,
            taxRate = 0.16,
            stockQuantity = 5.0,
            updatedAt = 0L
        )

        val items = listOf(
            CartItem(product = prod1, quantity = 2.0),
            CartItem(product = prod2, quantity = 1.0)
        )

        val calc = POSCheckoutService.calculateCart(items)
        assertEquals(120.0, calc.subtotal)
        assertEquals(0.0, calc.discountTotal)
        assertEquals(19.20, calc.taxTotal)
        assertEquals(139.20, calc.total)
        assertEquals(3.0, calc.itemCount)
    }

    @Test
    fun testCartCalculationWithItemDiscount() {
        val prod = ProductDto(
            id = "p1",
            sku = "SKU-1",
            name = "Combo Almuerzo",
            price = 100.0,
            taxRate = 0.16,
            stockQuantity = 10.0,
            updatedAt = 0L
        )

        val items = listOf(
            CartItem(product = prod, quantity = 1.0, discountPercent = 10.0)
        )

        val calc = POSCheckoutService.calculateCart(items)
        assertEquals(90.0, calc.subtotal)
        assertEquals(10.0, calc.discountTotal)
        assertEquals(14.40, calc.taxTotal)
        assertEquals(104.40, calc.total)
    }

    @Test
    fun testChangeCalculation() {
        val changeExact = POSCheckoutService.calculateChange(total = 85.50, amountPaid = 100.0)
        assertEquals(14.50, changeExact)

        val changeInsufficient = POSCheckoutService.calculateChange(total = 85.50, amountPaid = 50.0)
        assertEquals(0.0, changeInsufficient)
    }
}
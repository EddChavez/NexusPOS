package org.ozafry.nexuspos.domain

import org.ozafry.nexuspos.models.ProductDto
import org.ozafry.nexuspos.models.SaleDto
import org.ozafry.nexuspos.models.SaleItemDto
import kotlin.math.round

data class CartItem(
    val product: ProductDto,
    val quantity: Double = 1.0,
    val discountPercent: Double = 0.0
) {
    val unitPrice: Double get() = product.price
    val rawSubtotal: Double get() = round2(unitPrice * quantity)
    val discountAmount: Double get() = round2(rawSubtotal * (discountPercent / 100.0))
    val taxableSubtotal: Double get() = round2(rawSubtotal - discountAmount)
    val taxAmount: Double get() = round2(taxableSubtotal * product.taxRate)
    val total: Double get() = round2(taxableSubtotal + taxAmount)

    fun toSaleItemDto(saleId: String, itemId: String): SaleItemDto {
        return SaleItemDto(
            id = itemId,
            saleId = saleId,
            productId = product.id,
            productName = product.name,
            unitPrice = unitPrice,
            quantity = quantity,
            subtotal = taxableSubtotal,
            taxRate = product.taxRate,
            taxAmount = taxAmount,
            discountAmount = discountAmount,
            total = total
        )
    }

    private fun round2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}

data class CartCalculation(
    val subtotal: Double,
    val discountTotal: Double,
    val taxTotal: Double,
    val total: Double,
    val itemCount: Double
)

object POSCheckoutService {
    fun calculateCart(items: List<CartItem>): CartCalculation {
        var subtotal = 0.0
        var discountTotal = 0.0
        var taxTotal = 0.0
        var itemCount = 0.0

        for (item in items) {
            subtotal += item.taxableSubtotal
            discountTotal += item.discountAmount
            taxTotal += item.taxAmount
            itemCount += item.quantity
        }

        val total = round2(subtotal + taxTotal)
        return CartCalculation(
            subtotal = round2(subtotal),
            discountTotal = round2(discountTotal),
            taxTotal = round2(taxTotal),
            total = total,
            itemCount = itemCount
        )
    }

    fun calculateChange(total: Double, amountPaid: Double): Double {
        val change = amountPaid - total
        return if (change < 0.0) 0.0 else round2(change)
    }

    private fun round2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}

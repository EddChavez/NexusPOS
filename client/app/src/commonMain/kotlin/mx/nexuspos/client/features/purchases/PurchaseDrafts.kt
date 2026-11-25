package mx.nexuspos.client.features.purchases

import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.purchases.*
import kotlinx.datetime.LocalDate
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun purchaseKey(): String = Uuid.random().toString()

internal data class ReceiptDraft(
    val key: String = purchaseKey(),
    val supplierId: String = "",
    val documentType: PurchaseDocumentType = PurchaseDocumentType.INVOICE,
    val documentNumber: String = "",
    val notes: String = "",
    val lines: List<ReceiptLineDraft> = emptyList()
) {
    fun toRequest(): ReceivePurchaseRequest? {
        if (supplierId.isBlank() || documentNumber.isBlank() || documentNumber.trim().length > 64 ||
            notes.trim().length > 1000 || lines.isEmpty()
        ) return null
        val parsed = lines.map { it.parse() ?: return null }
        return ReceivePurchaseRequest(key, supplierId, documentType, documentNumber, parsed, notes)
    }
}

internal data class ReceiptLineDraft(
    val key: String = purchaseKey(),
    val productId: String,
    val name: String,
    val sku: String,
    val unit: String,
    val perishable: Boolean? = null,
    val quantity: String = "1",
    val cost: String,
    val lot: String = "",
    val expiry: String = ""
) {
    fun parse(): ReceivePurchaseLine? {
        val quantityMillis = parsePurchaseDecimal(quantity, 3)
            ?.takeIf { it > 0 && (unit != "PZA" || it % 1000L == 0L) } ?: return null
        val unitCost = parsePurchaseDecimal(cost, 2) ?: return null
        if (purchaseLineTotal(quantityMillis, unitCost) == null) return null
        val hasLot = lot.isNotBlank() || expiry.isNotBlank()
        if (hasLot || perishable == true) {
            if (lot.isBlank() || lot.trim().length > 64 || expiry.trim().length != 10 ||
                runCatching { LocalDate.parse(expiry.trim()) }.isFailure
            ) return null
        }
        return ReceivePurchaseLine(productId, quantityMillis, unitCost, lot.ifBlank { null }, expiry.ifBlank { null })
    }

    companion object {
        fun fromProduct(product: ProductDto) = ReceiptLineDraft(
            productId = product.id, name = product.name, sku = product.sku, unit = product.baseUnit,
            perishable = product.isPerishable, cost = purchaseDecimal(product.costCents, 2)
        )

        fun fromSuggestion(item: ReorderSuggestionItemDto) = ReceiptLineDraft(
            productId = item.productId, name = item.productName, sku = item.sku, unit = item.baseUnit,
            perishable = item.isPerishable, quantity = purchaseDecimal(item.suggestedOrderMillis, 3),
            cost = purchaseDecimal(item.costCents, 2)
        )
    }
}

internal data class SupplierDraft(
    val id: String? = null,
    val name: String = "",
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val active: Boolean = true
) {
    fun toRequest() = SaveSupplierRequest(name, taxId, phone, email, active)
}

internal data class ReturnDraft(
    val key: String = purchaseKey(),
    val reason: String = "",
    val quantities: Map<String, String> = emptyMap()
) {
    fun toRequest(purchase: PurchaseDetails): ReturnPurchaseRequest? {
        if (reason.isBlank() || reason.trim().length > 256) return null
        val lines = purchase.lines.mapNotNull { line ->
            val text = quantities[line.id].orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val quantity = parsePurchaseDecimal(text, 3) ?: return null
            if (quantity == 0L) return@mapNotNull null
            if (quantity > line.quantityMillis - line.returnedMillis ||
                (line.baseUnit == "PZA" && quantity % 1000L != 0L)
            ) return null
            ReturnPurchaseLine(line.id, quantity)
        }
        return if (lines.isEmpty()) null else ReturnPurchaseRequest(key, reason, lines)
    }
}

internal fun parsePurchaseDecimal(raw: String, scale: Int): Long? {
    val normalized = raw.trim().replace(',', '.')
    if (!normalized.matches(Regex("[0-9]+(\\.[0-9]{1,$scale})?"))) return null
    val digits = normalized.substringBefore('.') + normalized.substringAfter('.', "").padEnd(scale, '0')
    return digits.trimStart('0').ifEmpty { "0" }.toLongOrNull()
}

internal fun purchaseDecimal(value: Long, scale: Int): String {
    val digits = value.toString().padStart(scale + 1, '0')
    val fraction = digits.takeLast(scale).trimEnd('0')
    return digits.dropLast(scale) + if (fraction.isEmpty()) "" else ".$fraction"
}

internal fun PurchaseDocumentType.label(): String = when (this) {
    PurchaseDocumentType.INVOICE -> "Factura"
    PurchaseDocumentType.DELIVERY_NOTE -> "Remisión"
}

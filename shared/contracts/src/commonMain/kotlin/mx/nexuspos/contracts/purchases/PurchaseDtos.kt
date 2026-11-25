package mx.nexuspos.contracts.purchases

import kotlinx.serialization.Serializable

@Serializable
data class SupplierDto(
    val id: String,
    val name: String,
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val isActive: Boolean = true
)

@Serializable
data class SaveSupplierRequest(
    val name: String,
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val isActive: Boolean = true
)

@Serializable
data class PurchaseAuthorization<T>(val pin: String, val data: T)

@Serializable
enum class PurchaseDocumentType { INVOICE, DELIVERY_NOTE }

@Serializable
data class ReceivePurchaseRequest(
    val idempotencyKey: String,
    val supplierId: String,
    val documentType: PurchaseDocumentType,
    val documentNumber: String,
    val lines: List<ReceivePurchaseLine>,
    val notes: String = ""
)

@Serializable
data class ReceivePurchaseLine(
    val productId: String,
    val quantityMillis: Long,
    val unitCostCents: Long,
    val lotNumber: String? = null,
    val expirationDate: String? = null
)

@Serializable
data class PurchaseSummary(
    val id: String,
    val supplierId: String,
    val supplierName: String,
    val documentType: PurchaseDocumentType,
    val documentNumber: String,
    val totalCents: Long,
    val returnedCents: Long = 0,
    val createdAt: Long
)

@Serializable
data class PurchaseLine(
    val id: String,
    val productId: String,
    val productName: String,
    val sku: String,
    val baseUnit: String,
    val quantityMillis: Long,
    val unitCostCents: Long,
    val totalCents: Long,
    val lotNumber: String? = null,
    val expirationDate: String? = null,
    val returnedMillis: Long = 0
)

@Serializable
data class PurchaseDetails(
    val summary: PurchaseSummary,
    val receivedBy: String,
    val notes: String,
    val lines: List<PurchaseLine>,
    val returns: List<PurchaseReturn> = emptyList()
)

@Serializable
data class ReturnPurchaseRequest(
    val idempotencyKey: String,
    val reason: String,
    val lines: List<ReturnPurchaseLine>
)

@Serializable
data class ReturnPurchaseLine(val purchaseLineId: String, val quantityMillis: Long)

@Serializable
data class PurchaseReturn(
    val id: String,
    val purchaseId: String,
    val reason: String,
    val returnedBy: String,
    val totalCents: Long,
    val createdAt: Long,
    val lines: List<PurchaseReturnLine>
)

@Serializable
data class PurchaseReturnLine(
    val purchaseLineId: String,
    val quantityMillis: Long,
    val totalCents: Long
)

fun purchaseLineTotal(quantityMillis: Long, unitCostCents: Long): Long? {
    if (quantityMillis < 0 || unitCostCents < 0) return null
    if (unitCostCents != 0L && quantityMillis > Long.MAX_VALUE / unitCostCents) return null
    val milliCents = quantityMillis * unitCostCents
    return milliCents / 1000 + if (milliCents % 1000 >= 500) 1 else 0
}

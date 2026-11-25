package mx.nexuspos.contracts.purchases

import kotlinx.serialization.Serializable

@Serializable
enum class SupplierPaymentMethod { CASH, TRANSFER, CARD }

@Serializable
data class RegisterSupplierPayableRequest(val idempotencyKey: String, val dueDate: String)

@Serializable
data class RecordSupplierPaymentRequest(
    val idempotencyKey: String,
    val amountCents: Long,
    val method: SupplierPaymentMethod,
    val reference: String = ""
)

@Serializable
data class SupplierPayable(
    val receiptId: String,
    val supplierName: String,
    val documentNumber: String,
    val dueDate: String,
    val totalCents: Long,
    val returnedCents: Long,
    val paidCents: Long
) {
    val balanceCents: Long get() = (totalCents - returnedCents - paidCents).coerceAtLeast(0L)
    val supplierCreditCents: Long get() = (paidCents - (totalCents - returnedCents)).coerceAtLeast(0L)
}

@Serializable
data class SupplierPayment(
    val id: String,
    val amountCents: Long,
    val method: SupplierPaymentMethod,
    val reference: String,
    val operatorName: String,
    val createdAt: Long
)

@Serializable
data class SupplierPayableDetail(val account: SupplierPayable, val payments: List<SupplierPayment> = emptyList())

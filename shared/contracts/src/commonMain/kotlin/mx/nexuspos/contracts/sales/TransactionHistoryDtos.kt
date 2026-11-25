package mx.nexuspos.contracts.sales

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType { SALE, CASH_IN, CASH_OUT, OPENING_FUND, CREDIT_PAYMENT }

@Serializable
enum class TransactionPeriod { TODAY, YESTERDAY, LAST_SEVEN_DAYS, CUSTOM }

@Serializable
data class TransactionHistoryRequest(
    val period: TransactionPeriod = TransactionPeriod.TODAY,
    val from: String? = null,
    val through: String? = null,
    val type: TransactionType? = null,
    val registerId: String? = null,
    val query: String = "",
    val offset: Int = 0
)

@Serializable
data class TransactionRecord(
    val id: String,
    val type: TransactionType,
    val createdAt: Long,
    val registerName: String?,
    val operatorName: String?,
    val description: String?,
    val amountCents: Long,
    val folio: Long? = null,
    val status: String? = null,
    val customerName: String? = null,
    val paymentMethod: String? = null,
    val sessionOpen: Boolean = false,
    val reference: String? = null
)

@Serializable
data class TransactionRegister(val id: String, val name: String)

@Serializable
data class TransactionHistoryPage(
    val records: List<TransactionRecord>,
    val registers: List<TransactionRegister>,
    val nextOffset: Int? = null
)

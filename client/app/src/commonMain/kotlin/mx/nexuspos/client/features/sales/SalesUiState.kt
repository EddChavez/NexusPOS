package mx.nexuspos.client.features.sales

import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

data class CartLine(
    val id: String,
    val productId: String?,
    val presentationId: String?,
    val barcode: String,
    val name: String,
    val unit: ProductUnit,
    val quantity: Quantity,
    val unitPrice: Money,
    val taxRate: Double,
    val total: Money
)

data class SalesUiState(
    val cartLines: List<CartLine> = emptyList(),
    val subtotal: Money = Money.ZERO,
    val tax: Money = Money.ZERO,
    val total: Money = Money.ZERO,
    val connectionState: ServerConnectionState = ServerConnectionState.CONNECTED,
    val activeSession: CashSessionSummary? = null,
    val activeCashierName: String = "Cajero Principal",
    val activeCashierId: String = "usr-cajero",
    val selectedCustomer: CustomerDto? = null,
    val scannerInput: String = "",
    val isWeighingProduct: Pair<ProductDto, ProductPresentationDto>? = null,
    val isShowingCashPayment: Boolean = false,
    val isShowingCustomerDialog: Boolean = false,
    val isShowingSessionDialog: Boolean = false,
    val lastCompletedSale: SaleResponse? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

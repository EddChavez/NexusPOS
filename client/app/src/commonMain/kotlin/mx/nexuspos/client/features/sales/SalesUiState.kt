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
    val category: String = "Abarrotes",
    val unit: ProductUnit,
    val quantity: Quantity,
    val unitPrice: Money,
    val taxRate: Double,
    val total: Money,
    val currentStockMillis: Long? = null,
    val isWeighed: Boolean = unit == ProductUnit.KILOGRAM
)

/**
 * Representa el estado de una venta o ticket individual en memoria para soporte multi-ticket.
 */
data class TicketState(
    val id: String = "ticket-1",
    val title: String = "Ticket 1",
    val cartLines: List<CartLine> = emptyList(),
    val subtotal: Money = Money.ZERO,
    val tax: Money = Money.ZERO,
    val total: Money = Money.ZERO,
    val selectedCustomer: CustomerDto? = null
)

data class SalesUiState(
    val tickets: List<TicketState> = listOf(TicketState()),
    val activeTicketId: String = "ticket-1",
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
    val scannerStatus: ScannerStatus = ScannerStatus.READY,
    val selectedCartLineId: String? = null,
    val isWeighingProduct: Pair<ProductDto, ProductPresentationDto>? = null,
    val isShowingCashPayment: Boolean = false,
    val isShowingCustomerDialog: Boolean = false,
    val isShowingSessionDialog: Boolean = false,
    val isShowingGenericProductDialog: Boolean = false,
    val isShowingCommonArticlesDialog: Boolean = false,
    val isShowingSearchCatalogDialog: Boolean = false,
    val isShowingCashInDialog: Boolean = false,
    val isShowingCashOutDialog: Boolean = false,
    val lastCompletedSale: SaleResponse? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * Convierte el estado de ventas a la proyección requerida por [CaptureBar].
 */
fun SalesUiState.toCaptureBarState(): CaptureBarState {
    val pendingCount = tickets.count { it.id != activeTicketId && it.cartLines.isNotEmpty() }
    val tabItems = tickets.map { t ->
        TicketTabItem(
            id = t.id,
            label = t.title,
            totalFormatted = t.total.format(),
            itemCount = t.cartLines.size,
            isActive = t.id == activeTicketId,
            canClose = tickets.size > 1 || t.cartLines.isNotEmpty()
        )
    }
    return CaptureBarState(
        tickets = tabItems,
        activeTicketId = activeTicketId,
        pendingTicketsCount = pendingCount,
        currentCustomerName = selectedCustomer?.name ?: "Público en General",
        currentCustomerCode = selectedCustomer?.let { "00${it.id.takeLast(2)}" } ?: "001",
        scannerInput = scannerInput,
        scannerStatus = scannerStatus,
        isDeleteEnabled = cartLines.isNotEmpty()
    )
}

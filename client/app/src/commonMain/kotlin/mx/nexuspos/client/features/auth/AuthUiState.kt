package mx.nexuspos.client.features.auth

import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.identity.LoginResponse

data class AuthUiState(
    val isAuthenticated: Boolean = false,
    val currentUser: LoginResponse? = null,
    val pinInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val useRootPin: Boolean = false
)

sealed interface SupervisorAction {
    data object AccessSettings : SupervisorAction
    data class AdjustInventory(val product: ProductDto) : SupervisorAction
    data class CancelSale(val saleId: String, val folio: Long, val reason: String) : SupervisorAction
    data class ApplyDiscount(
        val lineId: String?,
        val discountPercent: Double,
        val discountCents: Long,
        val reason: String = "Descuento especial"
    ) : SupervisorAction
    data class RefundSale(
        val saleId: String,
        val folio: Long,
        val request: mx.nexuspos.contracts.sales.CreateSaleRefundRequest
    ) : SupervisorAction
}

data class SupervisorAuthState(
    val isVisible: Boolean = false,
    val targetAction: SupervisorAction? = null,
    val pinInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val useRootPin: Boolean = false
)

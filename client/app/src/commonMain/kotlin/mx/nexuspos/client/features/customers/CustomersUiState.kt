package mx.nexuspos.client.features.customers

import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.CustomerStatementDto
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto

data class CustomersUiState(
    val customers: List<CustomerDto> = emptyList(),
    val selectedCustomer: CustomerDto? = null,
    val statement: CustomerStatementDto? = null,
    val loyaltyMovements: List<LoyaltyMovementDto> = emptyList(),
    val isLoyaltyLoading: Boolean = false,
    val loyaltyErrorMessage: String? = null,
    val showLoyaltyDialog: Boolean = false,
    val showAdjustLoyaltyDialog: Boolean = false,
    val searchQuery: String = "",
    val includeInactive: Boolean = false,
    val isLoading: Boolean = false,
    val isStatementLoading: Boolean = false,
    val statementErrorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val showFormDialog: Boolean = false,
    val customerToEdit: CustomerDto? = null,
    val showPaymentDialog: Boolean = false,
    val isFullLiquidation: Boolean = false,
    val showWhatsAppDialog: Boolean = false,
    val whatsAppMessageText: String = "",
    val whatsAppInitialPhone: String = "",
    val whatsAppDialogTitle: String = "Enviar por WhatsApp",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    internal val statementRequestId: Long = 0,
    internal val loyaltyRequestId: Long = 0
)

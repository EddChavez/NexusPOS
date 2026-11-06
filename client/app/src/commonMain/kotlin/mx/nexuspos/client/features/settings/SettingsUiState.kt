package mx.nexuspos.client.features.settings

import mx.nexuspos.contracts.identity.UserDto

enum class SettingsTab { STORE_TICKET, POLICIES, PRINTER, USERS }

data class SettingsUiState(
    val isLoading: Boolean = true,
    val selectedTab: SettingsTab = SettingsTab.STORE_TICKET,
    // Store / Ticket fields
    val storeName: String = "",
    val storeAddress: String = "",
    val storePhone: String = "",
    val storeTaxId: String = "",
    val ticketHeader: String = "",
    val ticketFooter: String = "",
    val currencySymbol: String = "$",
    // Policies
    val allowNegativeStock: Boolean = true,
    // Users
    val users: List<UserDto> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val userFilter: String = "",
    val includeInactiveUsers: Boolean = false,
    // User form dialog
    val showUserDialog: Boolean = false,
    val editingUser: UserDto? = null,
    val userFormName: String = "",
    val userFormRole: String = "CASHIER",
    val userFormPin: String = "",
    val userFormError: String? = null,
    // Printer Hardware settings
    val printerConnectionType: String = "NONE", // "NONE", "USB_OS", "ETHERNET_TCP"
    val osPrinterName: String = "",
    val availableOsPrinters: List<String> = emptyList(),
    val tcpHost: String = "192.168.1.200",
    val tcpPort: String = "9100",
    val paperWidth: String = "80mm", // "80mm", "58mm"
    val autoPrintOnSale: Boolean = true,
    val openDrawerOnCashSale: Boolean = true,
    val isTestingPrinter: Boolean = false,
    // Feedback
    val isSaving: Boolean = false,
    val successBanner: String? = null,
    val errorBanner: String? = null
)

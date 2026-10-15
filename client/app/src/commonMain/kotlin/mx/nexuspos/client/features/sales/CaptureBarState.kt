package mx.nexuspos.client.features.sales

/**
 * Modelo de una pestaña individual en el gestor multi-ticket.
 */
data class TicketTabItem(
    val id: String,
    val label: String,
    val totalFormatted: String,
    val itemCount: Int,
    val isActive: Boolean,
    val canClose: Boolean = true
)

/**
 * Estados visuales de disponibilidad del lector y línea de comandos.
 */
enum class ScannerStatus(val label: String) {
    READY("LISTO"),
    READING("LEYENDO"),
    PROCESSING("BUSCANDO...")
}

/**
 * Estado inmutable que alimenta al componente [CaptureBar].
 */
data class CaptureBarState(
    val tickets: List<TicketTabItem> = emptyList(),
    val activeTicketId: String = "",
    val pendingTicketsCount: Int = 0,
    val currentCustomerName: String = "Público en General",
    val currentCustomerCode: String = "001",
    val scannerInput: String = "",
    val scannerStatus: ScannerStatus = ScannerStatus.READY,
    val isDeleteEnabled: Boolean = false
)

/**
 * Eventos emitidos por las interacciones y atajos en [CaptureBar].
 */
sealed interface CaptureBarEvent {
    data class SelectTicket(val ticketId: String) : CaptureBarEvent
    data object NewTicket : CaptureBarEvent
    data class CloseTicket(val ticketId: String) : CaptureBarEvent
    data object OpenCustomerSelector : CaptureBarEvent
    data class ScannerInputChanged(val text: String) : CaptureBarEvent
    data object SubmitScanner : CaptureBarEvent

    // Cinta de atajos auxiliares
    data object InsertGeneric : CaptureBarEvent       // [INS]
    data object CommonArticles : CaptureBarEvent      // [Ctrl+P]
    data object SearchCatalog : CaptureBarEvent       // [F10]
    data object ApplyWholesale : CaptureBarEvent      // [F11]
    data object CashIn : CaptureBarEvent              // [F7]
    data object CashOut : CaptureBarEvent             // [F8]
    data object WeighBulk : CaptureBarEvent           // [F9]
    data object DeleteItem : CaptureBarEvent          // [DEL]
}

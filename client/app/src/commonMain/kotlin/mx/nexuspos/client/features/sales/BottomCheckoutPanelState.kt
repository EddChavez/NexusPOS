package mx.nexuspos.client.features.sales

import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.core.Money

/**
 * Modelo de estado inmutable para el componente [BottomCheckoutPanel].
 *
 * Consolida la información requerida por las tres tarjetas inferiores:
 * 1. Gestión de tickets en curso y acciones de caja.
 * 2. Datos históricos de la última venta completada (post-venta).
 * 3. Total general a cobrar y acción primaria de cobro.
 */
data class BottomCheckoutPanelState(
    // ==========================================
    // 1. TARJETA IZQUIERDA: GESTIÓN DE TICKET
    // ==========================================
    val activeTicketLabel: String = "Ticket 1",
    val statusBadgeText: String = "En Curso",
    val hasMultipleTickets: Boolean = false,
    val canCancelTicket: Boolean = false,
    val canDelete: Boolean = canCancelTicket,

    // ==========================================
    // 2. TARJETA CENTRAL: VENTA ANTERIOR
    // ==========================================
    val lastSaleFolio: String = "Ticket #---",
    val lastSaleTotalFormatted: String = "$0.00",
    val lastSaleReceivedFormatted: String = "$0.00",
    val lastSaleChangeFormatted: String = "$0.00",
    val hasLastSale: Boolean = false,

    // ==========================================
    // 3. TARJETA DERECHA: TOTAL A COBRAR
    // ==========================================
    val currentTotalFormatted: String = "$0.00",
    val currencyLabel: String = "Moneda: MXN ($)",
    val canCheckout: Boolean = false,
    val isServerConnected: Boolean = true
)

/**
 * Mapea el estado global de ventas [SalesUiState] a la proyección [BottomCheckoutPanelState].
 */
fun SalesUiState.toBottomCheckoutPanelState(): BottomCheckoutPanelState {
    val activeTicket = tickets.firstOrNull { it.id == activeTicketId }
    val lastSale = lastCompletedSale
    val changeCents = lastSale?.payments?.firstOrNull()?.changeCents ?: 0L
    val receivedCents = lastSale?.payments?.firstOrNull()?.receivedCents ?: (lastSale?.totalCents ?: 0L)

    val canCancel = cartLines.isNotEmpty() || tickets.size > 1

    return BottomCheckoutPanelState(
        activeTicketLabel = activeTicket?.title ?: "Ticket 1",
        statusBadgeText = "En Curso",
        hasMultipleTickets = tickets.size > 1,
        canCancelTicket = canCancel,
        canDelete = canCancel,
        lastSaleFolio = lastSale?.let { "Ticket #${it.folio}" } ?: "Ticket #---",
        lastSaleTotalFormatted = lastSale?.let { Money.fromCents(it.totalCents).format() } ?: "$0.00",
        lastSaleReceivedFormatted = lastSale?.let { Money.fromCents(receivedCents).format() } ?: "$0.00",
        lastSaleChangeFormatted = lastSale?.let { Money.fromCents(changeCents).format() } ?: "$0.00",
        hasLastSale = lastSale != null,
        currentTotalFormatted = total.format(),
        currencyLabel = "Moneda: MXN ($)",
        canCheckout = cartLines.isNotEmpty() && activeSession != null && connectionState == ServerConnectionState.CONNECTED,
        isServerConnected = connectionState == ServerConnectionState.CONNECTED
    )
}

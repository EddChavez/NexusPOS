package mx.nexuspos.client.features.sales.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.sales.CashMovementDialog
import mx.nexuspos.client.features.cash.CashSessionDialog
import mx.nexuspos.client.features.credit.CustomerSelectionDialog
import mx.nexuspos.client.features.header.AppModule
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.sales.*
import mx.nexuspos.client.features.sales.v2.components.*
import mx.nexuspos.client.features.sales.v2.dialogs.*
import mx.nexuspos.client.features.sales.v2.shortcuts.*
import mx.nexuspos.client.features.whatsapp.WhatsAppSendDialog
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

/**
 * Pantalla Principal de Ventas V2 (Pure Material Design 3) para NexusPOS.
 *
 * Arquitectura visual:
 * 1. Columna Izquierda: Navigation Rail vertical M3 (72.dp) con iconos oficiales, selector de módulos,
 *    acceso a Historial de Turno/Devoluciones (Requisito 4.7) y perfil de cajero.
 * 2. Columna Central (weight 1f):
 *    - Pestañas Multi-ticket dinámicas M3 con badge de ventas en espera en red (Requisito 4.2).
 *    - Buscador Omnibox con autocompletado flotante predictivo ListItem y detección de láser.
 *    - Carrito de compras virtualizado con stepper M3, re-pesaje RS-232 (Requisito 4.3) y menú [⋮].
 *    - Franja de Cobro con botón masivo [F12 / ⌘↵], notas [📝] y reimpresión oficial [🖨️] (Requisito 4.1).
 * 3. Columna Derecha (340.dp):
 *    - Tarjeta de contexto del producto seleccionado (ElevatedCard M3) con selector de precio [Precio ▾].
 *    - Cuadrícula de 8 Acciones Rápidas (FilledTonalButton M3) con saldo de cliente/monedero (Requisito 4.4).
 * 4. Capas Flotantes: Modales oficiales AlertDialog (Requisito 4.5 cancelación, historial 4.7, etc.) y SnackbarHost M3.
 */
@Composable
fun SalesScreenV2(
    viewModel: SalesViewModel = remember { SalesViewModel() },
    scaleReading: ScaleReading = ScaleReading(),
    onRequestCancelSale: ((sale: SaleResponse, reason: String) -> Unit)? = null,
    onOpenWhatsApp: (url: String) -> Unit = {},
    onCopyText: (text: String) -> Unit = {},
    onNavigateToModule: (AppModule) -> Unit = {},
    onLockTerminal: () -> Unit = {},
    onOpenCashDrawerHardware: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val omniboxFocusRequester = remember { FocusRequester() }
    val isOmniboxFocused = remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    val autocompleteMatches = remember(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            viewModel.catalogCache.searchProducts(searchQuery.trim()).take(6)
        } else {
            emptyList()
        }
    }

    val currentTicket = state.tickets.firstOrNull { it.id == state.activeTicketId }
        ?: state.tickets.firstOrNull() ?: TicketState()

    val selectedLine = state.cartLines.firstOrNull { it.id == state.selectedCartLineId }
        ?: state.cartLines.lastOrNull()

    // Verificador de modales activos para la Capa 1 de prioridad de teclado
    val hasActiveModal = {
        state.isShowingCashPayment ||
                state.isShowingCustomerDialog ||
                state.isShowingSessionDialog ||
                state.isShowingDiscountDialog ||
                state.isWeighingProduct != null ||
                state.isShowingGenericProductDialog ||
                state.isShowingCommonArticlesDialog ||
                state.isShowingCashInDialog ||
                state.isShowingCashOutDialog ||
                state.isShowingSalesHistory ||
                state.isShowingCancelReasonDialog ||
                state.isShowingCancelTicketDialog ||
                state.isShowingRefundDialog ||
                state.reprintTicketPreview != null ||
                state.isShowingTicketNoteDialog ||
                state.isShowingRenameTicketDialog ||
                state.isShowingPriceCheckerDialog ||
                state.isShowingParkSaleDialog ||
                state.isShowingParkedSalesListDialog ||
                state.isShowingOfflineSyncDialog ||
                state.isShowingWhatsAppDialog
    }

    // Gestor Global de Atajos Multiplataforma
    val shortcutManager = remember(hasActiveModal) {
        PosShortcutManager(
            hasActiveModal = hasActiveModal,
            hasDropdownOpen = { searchQuery.trim().length >= 2 && autocompleteMatches.isNotEmpty() },
            isInputFocused = { isOmniboxFocused.value },
            onAction = { action ->
                when (action) {
                    PosAction.SWITCH_TICKET -> viewModel.cycleNextTicket()
                    PosAction.CHECKOUT -> {
                        if (state.activeSession == null) {
                            viewModel.showSessionDialog(true)
                        } else if (state.cartLines.isEmpty()) {
                            viewModel.setErrorMessage("Agrega al menos un producto al carrito para cobrar.")
                        } else {
                            viewModel.showCashPaymentDialog(true)
                        }
                    }
                    PosAction.FAST_SALE -> viewModel.showGenericProductDialog(true)
                    PosAction.PRICE_CHECKER -> viewModel.showPriceCheckerDialog(true)
                    PosAction.REPRINT_TICKET -> viewModel.reprintLastTicket()
                    PosAction.FOCUS_SEARCH -> {
                        try {
                            omniboxFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                    PosAction.NAVIGATE_UP, PosAction.NAVIGATE_DOWN -> { /* Gestionado internamente en dropdown */ }
                    PosAction.CONFIRM_SELECTION -> {
                        if (autocompleteMatches.isNotEmpty()) {
                            val prod = autocompleteMatches.first()
                            val pres = prod.presentations.firstOrNull()
                            if (pres != null) {
                                viewModel.addProductPresentation(prod, pres, Quantity.ONE)
                                searchQuery = ""
                            }
                        }
                    }
                    PosAction.ESCAPE -> {
                        viewModel.showCashPaymentDialog(false)
                        viewModel.showCustomerDialog(false)
                        viewModel.showDiscountDialog(false)
                        viewModel.dismissWeighingDialog()
                        viewModel.showSessionDialog(false)
                        viewModel.showGenericProductDialog(false)
                        viewModel.showCommonArticlesDialog(false)
                        viewModel.showCashInDialog(false)
                        viewModel.showCashOutDialog(false)
                        viewModel.showSearchCatalogDialog(false)
                        viewModel.closeSalesHistoryDialog()
                        viewModel.dismissCancelReasonDialog()
                        viewModel.dismissCancelTicketDialog()
                        viewModel.dismissRefundDialog()
                        viewModel.closeReprintPreview()
                        viewModel.showTicketNoteDialog(false)
                        viewModel.showRenameTicketDialog(false)
                        viewModel.showPriceCheckerDialog(false)
                        viewModel.closeParkSaleDialog()
                        viewModel.closeParkedSalesListDialog()
                        viewModel.closeOfflineSyncDialog()
                        viewModel.closeWhatsAppDialog()
                        viewModel.clearMessages()
                        searchQuery = ""
                        try {
                            omniboxFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                    PosAction.QTY_INCREMENT -> {
                        selectedLine?.let { line ->
                            val step = if (line.unit == ProductUnit.KILOGRAM) 100L else 1000L
                            viewModel.updateCartLineQuantity(line.id, Quantity(line.quantity.millis + step))
                        }
                    }
                    PosAction.QTY_DECREMENT -> {
                        selectedLine?.let { line ->
                            val step = if (line.unit == ProductUnit.KILOGRAM) 100L else 1000L
                            if (line.quantity.millis > step) {
                                viewModel.updateCartLineQuantity(line.id, Quantity(line.quantity.millis - step))
                            }
                        }
                    }
                    PosAction.DELETE_ITEM -> viewModel.deleteActiveCartLine()
                    PosAction.ASSIGN_CUSTOMER -> viewModel.showCustomerDialog(true)
                    PosAction.GLOBAL_DISCOUNT -> viewModel.showDiscountDialog(true)
                    PosAction.CASH_IN -> viewModel.showCashInDialog(true)
                    PosAction.CASH_OUT -> viewModel.showCashOutDialog(true)
                    PosAction.OPEN_DRAWER -> {
                        onOpenCashDrawerHardware()
                        viewModel.openCashDrawer()
                        viewModel.recordManualDrawerOpen(state.activeCashierName)
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { event -> shortcutManager.handleKeyEvent(event) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // =================================================================
            // 1. BARRA LATERAL IZQUIERDA (NAVIGATION RAIL M3)
            // =================================================================
            SalesNavigationRail(
                activeModule = AppModule.SALES,
                onModuleSelected = onNavigateToModule,
                onOpenDailySalesAndReturns = { viewModel.openDailySalesAndReturns() },
                cashierName = state.activeCashierName,
                cashierRole = state.activeCashierRole,
                onLockTerminal = onLockTerminal
            )

            // =================================================================
            // 2. ESPACIO PRINCIPAL DE TRABAJO (ÁREA CENTRAL + PANEL DERECHO)
            // =================================================================
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // COLUMNA CENTRAL (ÁREA DE CAPTURA Y PRODUCTOS)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pestañas Multi-ticket (InputChip M3) con badge Parked Sales (Requisito 4.2)
                    MultiTicketTabBar(
                        tickets = state.tickets,
                        activeTicketId = state.activeTicketId,
                        parkedSalesCount = state.parkedSalesCount,
                        onSelectTicket = { viewModel.selectTicket(it) },
                        onNewTicket = { viewModel.newTicket() },
                        onCloseTicket = { viewModel.requestCloseTicket(it) },
                        onRenameTicket = { viewModel.showRenameTicketDialog(true) },
                        onOpenParkedSales = { viewModel.openParkedSalesListDialog() }
                    )

                    // Banner de Modo Contingencia Offline
                    if (state.connectionState == ServerConnectionState.NO_SERVER) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("⚠️", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = "MODO CONTINGENCIA (Sin conexión LAN al servidor central)",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Cobros en Efectivo/Tarjeta habilitados. Se sincronizan al volver la red.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (state.offlinePendingSalesCount > 0) {
                                    Button(
                                        onClick = { viewModel.openOfflineSyncDialog() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = MaterialTheme.shapes.small,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("${state.offlinePendingSalesCount} pendientes [Sincronizar]", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Mensajes de Alerta / Éxito
                    FeedbackMessageHost(
                        errorMessage = state.errorMessage,
                        successMessage = state.successMessage,
                        onDismissError = viewModel::clearMessages,
                        onDismissSuccess = viewModel::clearMessages
                    )

                    // Banner Post-Cobro para Envío por WhatsApp
                    if (state.lastCompletedSale != null && state.successMessage != null) {
                        Surface(
                            color = Color(0xFF25D366).copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ticket #${state.lastCompletedSale!!.folio} cobrado exitosamente",
                                    color = Color(0xFF1E7E34),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = { viewModel.openSaleWhatsAppDialog(state.lastCompletedSale!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("📲 Enviar Ticket por WhatsApp", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Buscador Omnibox con Autocompletado Predictivo ListItem M3
                    OmniboxSearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            viewModel.onScannerInputChanged(it)
                        },
                        onSubmit = {
                            viewModel.onScannerSubmitted()
                            searchQuery = ""
                        },
                        matches = autocompleteMatches,
                        onSelectMatch = { prod ->
                            val pres = prod.presentations.firstOrNull()
                            if (pres != null) {
                                if (prod.baseUnit.equals("KG", ignoreCase = true)) {
                                    viewModel.onScannerInputChanged(pres.barcode.ifBlank { prod.sku })
                                    viewModel.onScannerSubmitted()
                                } else {
                                    viewModel.addProductPresentation(prod, pres, Quantity.ONE)
                                }
                            }
                            searchQuery = ""
                        },
                        focusRequester = omniboxFocusRequester,
                        isFocused = isOmniboxFocused
                    )

                    // Lista de Productos en el Ticket con Re-pesaje RS-232 (Requisito 4.3)
                    CartProductList(
                        items = state.cartLines,
                        selectedLineId = state.selectedCartLineId,
                        onSelectLine = { viewModel.selectCartLine(it) },
                        onRemoveLine = { viewModel.removeCartLine(it) },
                        onUpdateQuantity = { lineId, qty -> viewModel.updateCartLineQuantity(lineId, qty) },
                        onReweighLine = { line ->
                            viewModel.reweighCartLine(line)
                        },
                        onDiscountLine = { viewModel.showDiscountDialog(true) },
                        onChangePriceLine = { line ->
                            viewModel.onApplyWholesalePrice()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Franja Inferior de Cierre y Cobro [F12] con Reimpresión (Requisito 4.1)
                    CheckoutSummaryBar(
                        cartLines = state.cartLines,
                        total = state.total,
                        ticketNote = state.activeTicketNote,
                        onOpenNoteDialog = { viewModel.showTicketNoteDialog(true) },
                        onReprintTicket = { viewModel.reprintLastTicket() },
                        onCheckout = {
                            if (state.activeSession == null) {
                                viewModel.showSessionDialog(true)
                            } else if (state.cartLines.isEmpty()) {
                                viewModel.setErrorMessage("Agrega al menos un producto al carrito para cobrar.")
                            } else {
                                viewModel.showCashPaymentDialog(true)
                            }
                        }
                    )
                }

                // COLUMNA DERECHA: CONTEXTO Y ACCIONES RÁPIDAS (340.dp)
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tarjeta del Producto Seleccionado (ElevatedCard M3)
                    SelectedProductCard(
                        selectedLine = selectedLine,
                        onApplyPriceTier = { newPrice ->
                            selectedLine?.let { line ->
                                viewModel.updateCartLinePrice(line.id, newPrice)
                            }
                        },
                        onInspectCatalog = { viewModel.showSearchCatalogDialog(true) }
                    )

                    // Cuadrícula de 8 Acciones Rápidas con Monedero/Crédito (Requisito 4.4)
                    QuickActionsGrid(
                        selectedCustomer = state.selectedCustomer,
                        onFastSale = { viewModel.showGenericProductDialog(true) },
                        onPriceChecker = { viewModel.showPriceCheckerDialog(true) },
                        onCashIn = { viewModel.showCashInDialog(true) },
                        onCashOut = { viewModel.showCashOutDialog(true) },
                        onAssignCustomer = { viewModel.showCustomerDialog(true) },
                        onGlobalDiscount = { viewModel.showDiscountDialog(true) },
                        onWhatsApp = {
                            state.lastCompletedSale?.let { viewModel.openSaleWhatsAppDialog(it) }
                                ?: viewModel.setErrorMessage("No hay venta reciente para enviar por WhatsApp")
                        },
                        onOpenDrawer = {
                            onOpenCashDrawerHardware()
                            viewModel.openCashDrawer()
                            viewModel.recordManualDrawerOpen(state.activeCashierName)
                        }
                    )
                }
            }
        }

        // =====================================================================
        // NOTIFICACIONES FLOTANTES M3 (SNACKBAR)
        // =====================================================================
        AuditToastHost(
            message = state.auditToastMessage,
            onDismiss = { viewModel.clearAuditToast() },
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // =====================================================================
        // MODALES CONTEXTUALES OFICIALES MATERIAL DESIGN 3
        // =====================================================================
        // 1. Pesaje a granel con báscula
        state.isWeighingProduct?.let { (prod, pres) ->
            WeightQuantityDialog(
                product = prod,
                presentation = pres,
                scaleReading = scaleReading,
                onConfirm = { qty -> viewModel.confirmWeighedProduct(qty) },
                onDismiss = { viewModel.dismissWeighingDialog() }
            )
        }

        // 2. Pasarela de pagos multidivisa (Monedero / Crédito Requisito 4.4)
        if (state.isShowingCashPayment) {
            MultiPaymentCheckoutDialog(
                isOpen = true,
                total = state.total,
                selectedCustomer = state.selectedCustomer,
                onDismiss = { viewModel.showCashPaymentDialog(false) },
                onRequestSelectCustomer = {
                    viewModel.showCashPaymentDialog(false)
                    viewModel.showCustomerDialog(true)
                },
                onConfirmCheckout = { payments ->
                    viewModel.checkoutMultiPayment(payments)
                }
            )
        }

        // 3. Asignación de cliente
        if (state.isShowingCustomerDialog) {
            CustomerSelectionDialog(
                apiClient = viewModel.apiClient,
                currentCustomer = state.selectedCustomer,
                onSelectCustomer = { cust -> viewModel.selectCustomer(cust) },
                onDismiss = { viewModel.showCustomerDialog(false) }
            )
        }

        // 4. Apertura / Arqueo de caja
        if (state.isShowingSessionDialog) {
            CashSessionDialog(
                activeSession = state.activeSession,
                onOpenSession = { fund -> viewModel.openCashSession(fund) },
                onCloseSession = { counted, notes -> viewModel.closeCashSession(counted, notes) },
                onDismiss = { viewModel.showSessionDialog(false) }
            )
        }

        // 5. Venta rápida (artículo genérico)
        if (state.isShowingGenericProductDialog) {
            GenericProductDialog(
                onConfirm = { name, priceCents -> viewModel.addGenericProduct(name, priceCents) },
                onDismiss = { viewModel.showGenericProductDialog(false) }
            )
        }

        // 6. Descuento global o por partida
        if (state.isShowingDiscountDialog) {
            ApplyDiscountDialog(
                isOpen = true,
                selectedLine = selectedLine,
                cartLines = state.cartLines,
                maxCashierDiscountPercent = state.maxCashierDiscountPercent,
                isCashierRole = state.activeCashierRole == "CASHIER",
                onDismiss = { viewModel.showDiscountDialog(false) },
                onApplyDiscount = { targetLineId, percent, fixedCents ->
                    viewModel.applyDiscount(targetLineId, percent, fixedCents)
                }
            )
        }

        // 7. Entradas y Salidas de efectivo
        if (state.isShowingCashInDialog) {
            CashMovementDialog(
                isCashIn = true,
                onConfirm = { _, _ -> viewModel.showCashInDialog(false) },
                onDismiss = { viewModel.showCashInDialog(false) }
            )
        }

        if (state.isShowingCashOutDialog) {
            CashMovementDialog(
                isCashIn = false,
                onConfirm = { _, _ -> viewModel.showCashOutDialog(false) },
                onDismiss = { viewModel.showCashOutDialog(false) }
            )
        }

        // 8. Diálogo oficial M3 de Notas al Ticket
        TicketNoteDialog(
            isOpen = state.isShowingTicketNoteDialog,
            initialNote = state.activeTicketNote,
            onConfirm = { note ->
                viewModel.setTicketNote(note)
                viewModel.showTicketNoteDialog(false)
            },
            onDismiss = { viewModel.showTicketNoteDialog(false) }
        )

        // 9. Diálogo oficial M3 de Renombrar Ticket
        RenameTicketDialog(
            isOpen = state.isShowingRenameTicketDialog,
            currentTitle = currentTicket.title,
            onConfirm = { newTitle ->
                viewModel.renameTicket(currentTicket.id, newTitle)
                viewModel.showRenameTicketDialog(false)
            },
            onDismiss = { viewModel.showRenameTicketDialog(false) }
        )

        // 10. Diálogo oficial M3 Verificador de Precios
        PriceCheckerDialog(
            isOpen = state.isShowingPriceCheckerDialog,
            apiClient = viewModel.apiClient,
            onDismiss = { viewModel.showPriceCheckerDialog(false) }
        )

        // 11. REQUISITO 4.5: Cancelación formal de ticket en progreso (AlertDialog oficial)
        val pendingCancelTicket = state.tickets.firstOrNull { it.id == state.ticketPendingCancelId }
        CancelTicketPromptDialog(
            isOpen = state.isShowingCancelTicketDialog && pendingCancelTicket != null,
            ticketTitle = pendingCancelTicket?.title ?: "Venta",
            itemsCount = pendingCancelTicket?.cartLines?.size ?: 0,
            onConfirm = { reason ->
                state.ticketPendingCancelId?.let { ticketId ->
                    viewModel.confirmCancelTicket(ticketId, reason)
                }
            },
            onDismiss = { viewModel.dismissCancelTicketDialog() }
        )

        // 12. REQUISITO 4.5 & 4.7: Cancelación de venta completada con supervisor
        state.salePendingCancel?.let { sale ->
            if (state.isShowingCancelReasonDialog) {
                CancelSaleReasonDialog(
                    sale = sale,
                    onConfirm = { reason ->
                        if (onRequestCancelSale != null) {
                            onRequestCancelSale(sale, reason)
                        } else {
                            viewModel.executeCancelSale(sale.id, reason)
                        }
                    },
                    onDismiss = { viewModel.dismissCancelReasonDialog() }
                )
            }
        }

        // 13. REQUISITO 4.7: Historial de Turno y Ventas del Día
        if (state.isShowingSalesHistory) {
            SalesHistoryDialog(
                sales = state.shiftSales,
                selectedSale = state.selectedShiftSale,
                isLoading = state.isLoadingShiftSales,
                searchFolio = state.salesHistorySearchFolio,
                statusFilter = state.salesHistoryStatusFilter,
                cashSessionId = state.activeSession?.sessionId,
                onSearchFolioChanged = { viewModel.onSalesSearchFolioChanged(it) },
                onStatusFilterChanged = { viewModel.onSalesStatusFilterChanged(it) },
                onSelectSale = { viewModel.selectShiftSale(it) },
                onReload = { viewModel.loadShiftSales() },
                onReprintTicket = { viewModel.reprintTicket(it) },
                onRefundSale = { viewModel.promptRefundSale(it) },
                onCancelSale = { viewModel.startCancelSaleFlow(it) },
                onSendWhatsApp = { viewModel.openSaleWhatsAppDialog(it) },
                onDismiss = { viewModel.closeSalesHistoryDialog() }
            )
        }

        // 14. REQUISITO 4.7: Devoluciones por partida de venta
        if (state.isShowingRefundDialog && state.salePendingRefund != null) {
            RefundSaleLinesDialog(
                sale = state.salePendingRefund!!,
                activeCashSessionId = state.activeSession?.sessionId ?: "",
                currentUserId = state.activeCashierId,
                isCashier = state.activeCashierRole == "CASHIER",
                onDismiss = { viewModel.dismissRefundDialog() },
                onConfirmRefund = { req -> viewModel.executeRefundSale(state.salePendingRefund!!.id, req) }
            )
        }

        // 15. Ventas en espera en red (Parked Sales - Requisito 4.2)
        if (state.isShowingParkSaleDialog) {
            ParkSalePromptDialog(
                isOpen = true,
                itemsCount = state.cartLines.size,
                total = state.total,
                onConfirm = { label -> viewModel.confirmParkCurrentSale(label) },
                onDismiss = { viewModel.closeParkSaleDialog() }
            )
        }

        if (state.isShowingParkedSalesListDialog) {
            ParkedSalesListDialog(
                isOpen = true,
                parkedSales = state.parkedSales,
                selectedSale = state.selectedParkedSale,
                isLoading = state.isLoadingParkedSales,
                onSelectSale = { viewModel.selectParkedSale(it) },
                onRetrieve = { viewModel.retrieveParkedSale(it) },
                onDiscard = { viewModel.discardParkedSale(it) },
                onDismiss = { viewModel.closeParkedSalesListDialog() }
            )
        }

        // 16. Sincronización de contingencia offline (Requisito 4.6)
        if (state.isShowingOfflineSyncDialog) {
            mx.nexuspos.client.features.offline.OfflineSyncDialog(
                isOpen = true,
                queuedSales = viewModel.saleQueue.getAll(),
                connectionState = state.connectionState,
                isSyncing = state.isSyncingOfflineSales,
                syncMessage = state.offlineSyncMessage,
                onSyncNow = { viewModel.triggerOfflineSync() },
                onClearSynced = { viewModel.clearSyncedOfflineSales() },
                onDismiss = { viewModel.closeOfflineSyncDialog() }
            )
        }

        // 17. Envío de ticket por WhatsApp
        if (state.isShowingWhatsAppDialog) {
            mx.nexuspos.client.features.whatsapp.WhatsAppSendDialog(
                isOpen = true,
                title = state.whatsAppDialogTitle,
                initialPhone = state.whatsAppInitialPhone,
                messageText = state.whatsAppMessageText,
                defaultCountryCode = viewModel.currentSettings?.whatsappCountryCode ?: "52",
                onSend = onOpenWhatsApp,
                onCopyText = onCopyText,
                onDismiss = { viewModel.closeWhatsAppDialog() }
            )
        }

        // 18. REQUISITO 4.1: Reimpresión de Ticket con Vista Previa
        state.reprintTicketPreview?.let { receiptText ->
            TicketReceiptPreviewDialog(
                receiptText = receiptText,
                onDismiss = { viewModel.closeReprintPreview() },
                onPrintPhysical = {
                    val saleToPrint = state.selectedShiftSale ?: state.lastCompletedSale
                    if (saleToPrint != null) {
                        viewModel.reprintTicket(saleToPrint)
                    }
                }
            )
        }
    }
}

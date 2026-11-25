package mx.nexuspos.client.features.sales.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.sales.CashMovementDialog
import mx.nexuspos.client.features.cash.CashSessionDialog
import mx.nexuspos.client.features.header.AppModule
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.sales.*
import mx.nexuspos.client.features.sales.v2.components.*
import mx.nexuspos.client.features.sales.v2.dialogs.*
import mx.nexuspos.client.features.sales.v2.shortcuts.*
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

/** Pantalla de ventas para caja Desktop con carrito, cobro y panel lateral adaptables. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalesScreenV2(
    viewModel: SalesViewModel = remember { SalesViewModel() },
    scaleReading: ScaleReading = ScaleReading(),
    onOpenWhatsApp: (url: String) -> Result<Unit> = {
        Result.failure(IllegalStateException("WhatsApp no está disponible en esta terminal."))
    },
    onCopyText: (text: String) -> Result<Unit> = {
        Result.failure(IllegalStateException("El portapapeles no está disponible en esta terminal."))
    },
    onNavigateToModule: (AppModule) -> Unit = {},
    onLockTerminal: () -> Unit = {},
    onOpenCashDrawerHardware: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    if (state.barcodeChoices.isNotEmpty()) {
        BarcodeChoicesDialog(state.barcodeChoices, viewModel::confirmBarcodeChoice)
    }
    val omniboxFocusRequester = remember { FocusRequester() }
    val isOmniboxFocused = remember { mutableStateOf(false) }
    var isQuantityFocused by remember { mutableStateOf(false) }

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

    fun submitSearch(input: String) {
        viewModel.onScannerInputChanged(input)
        viewModel.onScannerSubmitted()
        searchQuery = ""
    }

    fun selectSearchMatch(product: ProductDto) {
        val presentation = product.presentations.firstOrNull() ?: return
        submitSearch(presentation.barcode.ifBlank { product.sku })
    }

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
                state.isShowingCancelTicketDialog ||
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
            hasDropdownOpen = { isOmniboxFocused.value && autocompleteMatches.isNotEmpty() },
            isInputFocused = { isOmniboxFocused.value || isQuantityFocused },
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
                    PosAction.PARK_SALE -> viewModel.openParkSaleDialog()
                    PosAction.FOCUS_SEARCH -> {
                        try {
                            omniboxFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                    PosAction.NAVIGATE_UP, PosAction.NAVIGATE_DOWN, PosAction.CONFIRM_SELECTION -> Unit
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
                        viewModel.dismissCancelTicketDialog()
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
                        viewModel.onScannerInputChanged("")
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
                        viewModel.openCashDrawer(onOpenCashDrawerHardware)
                        viewModel.recordManualDrawerOpen(state.activeCashierName)
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                event.key == Key.Enter && event.isMetaPressed && shortcutManager.handleKeyEvent(event)
            }
            .onKeyEvent { event ->
                shortcutManager.handleKeyEvent(event) ||
                    (event.key == Key.Delete && (isOmniboxFocused.value || isQuantityFocused))
            }
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
                cashierName = state.activeCashierName,
                cashierRole = state.activeCashierRole,
                canViewTransactions = "transactions.own" in state.activeCashierPermissions,
                canViewPurchases = "purchases.view" in state.activeCashierPermissions ||
                    state.activeCashierRole in setOf("ADMIN", "SUPERVISOR"),
                onLockTerminal = onLockTerminal
            )

            // =================================================================
            // 2. ESPACIO PRINCIPAL DE TRABAJO (ÁREA CENTRAL + PANEL DERECHO)
            // =================================================================
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val panelWidth = (maxWidth * 0.34f).coerceIn(198.dp, 440.dp)
                    .coerceAtMost(maxWidth * 0.43f)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // COLUMNA CENTRAL (ÁREA DE CAPTURA Y PRODUCTOS)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pestañas, nueva venta y acceso a tickets en espera.
                        MultiTicketTabBar(
                            tickets = state.tickets,
                            activeTicketId = state.activeTicketId,
                            parkedSalesCount = state.parkedSalesCount,
                            hasActiveCartLines = state.cartLines.isNotEmpty(),
                            onSelectTicket = { viewModel.selectTicket(it) },
                            onNewTicket = { viewModel.newTicket() },
                            onCloseTicket = { viewModel.requestCloseTicket(it) },
                            onRenameTicket = { viewModel.showRenameTicketDialog(true) },
                            onParkCurrentSale = { viewModel.openParkSaleDialog() },
                            onOpenParkedSales = { viewModel.openParkedSalesListDialog() }
                        )

                        if (state.connectionState == ServerConnectionState.NO_SERVER) {
                            ContingencyBanner(
                                pendingCount = state.offlinePendingSalesCount,
                                onOpenSync = viewModel::openOfflineSyncDialog
                            )
                        }

                        // Mensajes de Alerta / Éxito
                        FeedbackMessageHost(
                            errorMessage = state.errorMessage,
                            successMessage = state.successMessage,
                            onDismissError = viewModel::clearMessages,
                            onDismissSuccess = viewModel::clearMessages
                        )

                        val completedSale = state.lastCompletedSale
                        if (completedSale != null && state.successMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Ticket #${completedSale.folio} cobrado",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(onClick = { viewModel.openSaleWhatsAppDialog(completedSale) }) {
                                        Text("Enviar por WhatsApp")
                                    }
                                }
                            }
                        }

                        OmniboxSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSubmit = { submitSearch(searchQuery.trim()) },
                            matches = autocompleteMatches,
                            onSelectMatch = ::selectSearchMatch,
                            focusRequester = omniboxFocusRequester,
                            isFocused = isOmniboxFocused
                        )

                        if (selectedLine?.presentationId != null && (selectedLine.listPrices.isNotEmpty() || selectedLine.priceListLevel != null || viewModel.canPerform("sales.price.manual"))) {
                            CartPriceListChoice(viewModel, selectedLine)
                        }

                        val shift = state.activeSession
                        if (shift != null && shift.operatorUserId != state.activeCashierId) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Turno a cargo de ${shift.operatorName}.", style = MaterialTheme.typography.bodySmall)
                                if (state.allowShiftHandoff && "cash.handoff" in state.activeCashierPermissions) {
                                    TextButton(viewModel::handoffCurrentShift, enabled = !state.isHandingOffShift) { Text("Asumir turno") }
                                }
                            }
                        }

                        // Lista de Productos en el Ticket con Re-pesaje RS-232 (Requisito 4.3)
                        CartProductList(
                            items = state.cartLines,
                            selectedLineId = selectedLine?.id,
                            onSelectLine = { viewModel.selectCartLine(it) },
                            onRemoveLine = { viewModel.removeCartLine(it) },
                            onUpdateQuantity = { lineId, qty -> viewModel.updateCartLineQuantity(lineId, qty) },
                            onReweighLine = { line ->
                                viewModel.reweighCartLine(line)
                            },
                            onDiscountLine = { line ->
                                if (state.selectedCartLineId != line.id) viewModel.selectCartLine(line.id)
                                viewModel.showDiscountDialog(true)
                            },
                            onQuantityFocusChanged = { isQuantityFocused = it },
                            onFinishQuantityEdit = { omniboxFocusRequester.requestFocus() },
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

                    // COLUMNA DERECHA: CONTEXTO Y ACCIONES RÁPIDAS
                    Column(
                        modifier = Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Producto seleccionado en el ticket.
                        SelectedProductCard(
                            selectedLine = selectedLine,
                            onInspectCatalog = { onNavigateToModule(AppModule.PRODUCTS) }
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
                                viewModel.openCashDrawer(onOpenCashDrawerHardware)
                                viewModel.recordManualDrawerOpen(state.activeCashierName)
                            }
                        )
                    }
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
            WeightQuantityDialogV2(
                product = prod,
                presentation = pres,
                scaleReading = scaleReading,
                currentLine = state.cartLines.firstOrNull { it.id == state.weighingCartLineId },
                onConfirm = { qty -> viewModel.confirmWeighedProduct(qty) },
                onDismiss = { viewModel.dismissWeighingDialog() }
            )
        }

        // 2. Pasarela de pagos multidivisa (Monedero / Crédito Requisito 4.4)
        if (state.isShowingCashPayment) {
            CheckoutDialogV2(
                total = state.total,
                selectedCustomer = state.selectedCustomer,
                isOffline = state.connectionState == ServerConnectionState.NO_SERVER,
                isSubmitting = state.isCheckoutInProgress,
                errorMessage = state.checkoutErrorMessage,
                onDismiss = { viewModel.showCashPaymentDialog(false) },
                onRequestSelectCustomer = { viewModel.showCustomerDialog(true) },
                onConfirmCheckout = viewModel::checkoutMultiPayment,
                allowCashLimitApproval = state.cashDrawerLimitEnabled,
                onConfirmWithApproval = viewModel::checkoutMultiPayment,
                allowedMethods = CheckoutPaymentMethod.entries.filter {
                    viewModel.canPerform("sales.pay.${it.methodCode.lowercase()}") &&
                        (it != CheckoutPaymentMethod.CREDIT || viewModel.currentSettings?.commerce?.creditSalesEnabled != false)
                }.toSet(),
                allowMixedPayments = viewModel.canPerform("sales.pay.mixed")
            )
        }

        // 3. Asignación de cliente
        if (state.isShowingCustomerDialog) {
            CustomerSelectionDialogV2(
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
                onConfirm = { cents, reason -> viewModel.submitCashMovement(true, cents, reason) },
                approvalRequired = state.cashDrawerLimitEnabled,
                onConfirmWithApproval = { cents, reason, pin -> viewModel.submitCashMovement(true, cents, reason, pin) },
                isSubmitting = state.isCashMovementSubmitting,
                serverError = state.cashMovementError,
                onDismiss = { viewModel.showCashInDialog(false) }
            )
        }

        if (state.isShowingCashOutDialog) {
            CashMovementDialog(
                isCashIn = false,
                onConfirm = { cents, reason -> viewModel.submitCashMovement(false, cents, reason) },
                isSubmitting = state.isCashMovementSubmitting,
                serverError = state.cashMovementError,
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

        if (state.isShowingPriceCheckerDialog) {
            PriceCheckerDialogV2(
                apiClient = viewModel.apiClient,
                onDismiss = { viewModel.showPriceCheckerDialog(false) }
            )
        }

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

        // 15. Ventas en espera en red (Parked Sales - Requisito 4.2)
        if (state.isShowingParkSaleDialog) {
            ParkSalePromptDialog(
                isOpen = true,
                itemsCount = state.cartLines.size,
                total = state.total,
                defaultLabel = state.selectedCustomer?.name.orEmpty().take(128),
                ticketNote = state.activeTicketNote,
                isSaving = state.isParkingSale,
                saveError = state.errorMessage,
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
            OfflineSyncDialogV2(
                queuedSales = viewModel.saleQueue.getAll(),
                connectionState = state.connectionState,
                serverUrl = viewModel.apiClient.baseUrl,
                isSyncing = state.isSyncingOfflineSales,
                syncMessage = state.offlineSyncMessage,
                onSyncNow = { viewModel.triggerOfflineSync() },
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
                onSend = { onOpenWhatsApp(it) },
                onCopyText = { onCopyText(it) },
                onDismiss = { viewModel.closeWhatsAppDialog() }
            )
        }

        // 18. REQUISITO 4.1: Reimpresión de Ticket con Vista Previa
        state.reprintTicketPreview?.let { receiptText ->
            TicketReceiptPreviewDialog(
                receiptText = receiptText,
                onDismiss = { viewModel.closeReprintPreview() },
                onPrintPhysical = {
                    state.reprintTicketSale?.let(viewModel::printPhysicalTicket)
                }
            )
        }
    }
}

@Composable
private fun ContingencyBanner(pendingCount: Int, onOpenSync: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.WarningAmber, null)
            Column {
                Text(
                    "Modo contingencia · Sin conexión LAN",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Los cobros en efectivo y tarjeta se sincronizan al volver la conexión.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (pendingCount > 0) {
                    TextButton(onClick = onOpenSync) {
                        Text(if (pendingCount == 1) "1 venta pendiente" else "$pendingCount ventas pendientes")
                    }
                }
            }
        }
    }
}


@Composable
private fun CartPriceListChoice(viewModel: SalesViewModel, line: mx.nexuspos.client.features.sales.CartLine) {
    val scope = rememberCoroutineScope()
    var choices by remember(line.id) { mutableStateOf<List<mx.nexuspos.contracts.catalog.CatalogPriceList>?>(null) }
    var error by remember(line.id) { mutableStateOf<String?>(null) }
    var manual by remember(line.id) { mutableStateOf<String?>(null) }
    manual?.let { amount ->
        val cents = parseCheckoutCents(amount)
        AlertDialog(onDismissRequest = { manual = null }, title = { Text("Precio manual") },
            text = { OutlinedTextField(amount, { manual = it.take(16) }, singleLine = true, label = { Text("Precio unitario") }) },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateCartLinePrice(line.id, mx.nexuspos.core.Money(checkNotNull(cents)))
                    manual = null
                }, enabled = cents != null && cents >= 0) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { manual = null }) { Text("Cancelar") } }
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = {
                scope.launch {
                    viewModel.activePriceLists().onSuccess { choices = it; error = null }.onFailure { error = it.message }
                }
            }) { Text(if (line.manualPriceCents != null) "Precio manual" else line.priceListLevel?.let { "Precio $it" } ?: "Precio público") }
            DropdownMenu(choices != null, onDismissRequest = { choices = null }) {
                DropdownMenuItem({ Text("Precio público") }, onClick = {
                    viewModel.chooseLinePrice(line.id, null)
                    choices = null
                })
                if (viewModel.canPerform("sales.price.manual")) DropdownMenuItem({ Text("Precio manual") }, onClick = {
                    choices = null
                    manual = checkoutAmountText(line.unitPrice.cents)
                })
                choices.orEmpty().forEach { choice ->
                    val amount = line.listPrices[choice.level]
                    DropdownMenuItem(
                        text = { Text(choice.name + (amount?.let { " · ${mx.nexuspos.core.Money(it).format()}" } ?: " · Sin precio")) },
                        enabled = amount != null,
                        onClick = { viewModel.chooseLinePrice(line.id, choice.level); choices = null }
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

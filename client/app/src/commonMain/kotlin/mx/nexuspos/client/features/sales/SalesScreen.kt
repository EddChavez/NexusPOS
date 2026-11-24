package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.cash.CashSessionDialog
import mx.nexuspos.client.features.credit.CustomerSelectionDialog
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.core.ProductUnit

@Composable
fun SalesScreen(
    viewModel: SalesViewModel = remember { SalesViewModel() },
    scaleReading: mx.nexuspos.client.features.header.ScaleReading = mx.nexuspos.client.features.header.ScaleReading(),
    onRequestCancelSale: ((sale: SaleResponse, reason: String) -> Unit)? = null,
    onOpenWhatsApp: (url: String) -> Unit = {},
    onCopyText: (text: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Barra de Control de Venta y Captura Rápida
            CaptureBar(
                state = state.toCaptureBarState(),
                onEvent = { event ->
                    when (event) {
                        is CaptureBarEvent.SelectTicket -> viewModel.selectTicket(event.ticketId)
                        is CaptureBarEvent.NewTicket -> viewModel.newTicket()
                        is CaptureBarEvent.CloseTicket -> viewModel.closeTicket(event.ticketId)
                        is CaptureBarEvent.OpenCustomerSelector -> viewModel.showCustomerDialog(true)
                        is CaptureBarEvent.ScannerInputChanged -> viewModel.onScannerInputChanged(event.text)
                        is CaptureBarEvent.SubmitScanner -> viewModel.onScannerSubmitted()
                        is CaptureBarEvent.InsertGeneric -> viewModel.showGenericProductDialog(true)
                        is CaptureBarEvent.CommonArticles -> viewModel.showCommonArticlesDialog(true)
                        is CaptureBarEvent.SearchCatalog -> viewModel.showSearchCatalogDialog(true)
                        is CaptureBarEvent.ApplyWholesale -> viewModel.onApplyWholesalePrice()
                        is CaptureBarEvent.OpenDiscount -> viewModel.showDiscountDialog(true)
                        is CaptureBarEvent.CashIn -> viewModel.showCashInDialog(true)
                        is CaptureBarEvent.CashOut -> viewModel.showCashOutDialog(true)
                        is CaptureBarEvent.WeighBulk -> {
                            val bulkProd = state.cartLines.firstOrNull { it.unit.code == "KG" }
                            if (bulkProd != null) {
                                viewModel.onScannerInputChanged(bulkProd.barcode)
                                viewModel.onScannerSubmitted()
                            } else {
                                viewModel.showGenericProductDialog(true)
                            }
                        }
                        is CaptureBarEvent.DeleteItem -> viewModel.deleteActiveCartLine()
                        is CaptureBarEvent.OpenParkedSales -> viewModel.openParkedSalesListDialog()
                        is CaptureBarEvent.ParkCurrentSale -> viewModel.openParkSaleDialog()
                    }
                }
            )

            // Banner de Modo Contingencia (Offline) cuando no hay conexión al servidor central LAN
            if (state.connectionState == ServerConnectionState.NO_SERVER) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
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
                                    text = "MODO CONTINGENCIA (Sin conexión al servidor central)",
                                    color = Color(0xFFB45309),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cobros en Efectivo o Tarjeta permitidos. Las ventas se guardan localmente.",
                                    color = Color(0xFF92400E),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        if (state.offlinePendingSalesCount > 0) {
                            Button(
                                onClick = { viewModel.openOfflineSyncDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("${state.offlinePendingSalesCount} pendientes [Ver]", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Alerts / Messages
            FeedbackMessageHost(
                errorMessage = state.errorMessage,
                successMessage = state.successMessage,
                onDismissError = viewModel::clearMessages,
                onDismissSuccess = viewModel::clearMessages
            )

            // Banner de Envío Rápido por WhatsApp para última venta completada
            if (state.lastCompletedSale != null && state.successMessage != null) {
                Surface(
                    color = Color(0xFF25D366).copy(alpha = 0.12f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ticket #${state.lastCompletedSale!!.folio} listo para envío digital",
                            color = Color(0xFF1E7E34),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { viewModel.openSaleWhatsAppDialog(state.lastCompletedSale!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        ) {
                            Text("📲 Enviar Ticket por WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Parrilla de Productos en Venta (Data Grid Central - Scroll Independiente con LazyColumn)
            CartGrid(
                items = state.cartLines,
                selectedLineId = state.selectedCartLineId,
                onSelectItem = { lineId -> viewModel.selectCartLine(lineId) },
                onRemoveItem = { lineId -> viewModel.removeCartLine(lineId) },
                onUpdateQuantity = { lineId, newQty -> viewModel.updateCartLineQuantity(lineId, newQty) },
                onEditQuantity = { line ->
                    if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        viewModel.onScannerInputChanged(line.barcode)
                        viewModel.onScannerSubmitted()
                    }
                },
                onSearchCatalog = { viewModel.showSearchCatalogDialog(true) },
                onCommonArticles = { viewModel.showCommonArticlesDialog(true) },
                onInsertGeneric = { viewModel.showGenericProductDialog(true) },
                onWeighBulk = {
                    val bulkProd = state.cartLines.firstOrNull { it.unit.code == "KG" }
                    if (bulkProd != null) {
                        viewModel.onScannerInputChanged(bulkProd.barcode)
                        viewModel.onScannerSubmitted()
                    } else {
                        viewModel.showGenericProductDialog(true)
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                footer = {
                    CartSummaryBar(
                        state = state.toCartSummaryBarState(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            // Panel Inferior de Totales y Gestión (Bottom Checkout Panel)
            BottomCheckoutPanel(
                state = state.toBottomCheckoutPanelState(),
                onSwitchTicket = { viewModel.cycleNextTicket() },
                onHoldTicket = { viewModel.openParkSaleDialog() },
                // Este evento elimina por completo el ticket actual en foco, a diferencia de la eliminación de un producto individual de la parrilla
                onCancelCurrentTicket = { viewModel.closeTicket(state.activeTicketId) },
                onOpenCashDrawer = { viewModel.openCashDrawer() },
                onReprintTicket = { viewModel.reprintLastTicket() },
                onDailySalesAndReturns = { viewModel.openDailySalesAndReturns() },
                onCheckout = {
                    if (state.activeSession == null) {
                        viewModel.showSessionDialog(true)
                    } else if (state.cartLines.isEmpty()) {
                        viewModel.setErrorMessage("Agrega al menos un producto al carrito para cobrar.")
                    } else {
                        viewModel.showCashPaymentDialog(true)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Modals
        state.isWeighingProduct?.let { (prod, pres) ->
            WeightQuantityDialog(
                product = prod,
                presentation = pres,
                scaleReading = scaleReading,
                onConfirm = { qty -> viewModel.confirmWeighedProduct(qty) },
                onDismiss = { viewModel.dismissWeighingDialog() }
            )
        }

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

        if (state.isShowingCustomerDialog) {
            CustomerSelectionDialog(
                apiClient = viewModel.apiClient,
                currentCustomer = state.selectedCustomer,
                onSelectCustomer = { cust -> viewModel.selectCustomer(cust) },
                onDismiss = { viewModel.showCustomerDialog(false) }
            )
        }

        if (state.isShowingSessionDialog) {
            CashSessionDialog(
                activeSession = state.activeSession,
                onOpenSession = { fund -> viewModel.openCashSession(fund) },
                onCloseSession = { counted, notes -> viewModel.closeCashSession(counted, notes) },
                onDismiss = { viewModel.showSessionDialog(false) }
            )
        }

        if (state.isShowingGenericProductDialog) {
            GenericProductDialog(
                onConfirm = { name, priceCents -> viewModel.addGenericProduct(name, priceCents) },
                onDismiss = { viewModel.showGenericProductDialog(false) }
            )
        }

        if (state.isShowingDiscountDialog) {
            val selectedLine = state.cartLines.firstOrNull { it.id == state.selectedCartLineId }
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

        if (state.isShowingCommonArticlesDialog) {
            CommonArticlesDialog(
                onSelectArticle = { name, priceCents ->
                    viewModel.addGenericProduct(name, priceCents)
                    viewModel.showCommonArticlesDialog(false)
                },
                onDismiss = { viewModel.showCommonArticlesDialog(false) }
            )
        }

        if (state.isShowingCashInDialog) {
            CashMovementDialog(
                isCashIn = true,
                onConfirm = { amount, reason ->
                    viewModel.showCashInDialog(false)
                    viewModel.clearMessages()
                },
                onDismiss = { viewModel.showCashInDialog(false) }
            )
        }

        if (state.isShowingCashOutDialog) {
            CashMovementDialog(
                isCashIn = false,
                onConfirm = { amount, reason ->
                    viewModel.showCashOutDialog(false)
                    viewModel.clearMessages()
                },
                onDismiss = { viewModel.showCashOutDialog(false) }
            )
        }

        if (state.isShowingSearchCatalogDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showSearchCatalogDialog(false) },
                title = { Text("Búsqueda en Catálogo [F10]") },
                text = { Text("Escriba el nombre o SKU en la barra de captura y presione [ENTER] para agregar directamente el producto.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.showSearchCatalogDialog(false) }) {
                        Text("Entendido [Enter]")
                    }
                }
            )
        }

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

        state.reprintTicketPreview?.let { receiptText ->
            TicketReceiptPreviewDialog(
                receiptText = receiptText,
                onDismiss = { viewModel.closeReprintPreview() },
                onPrintPhysical = {
                    val saleToPrint = state.selectedShiftSale ?: state.lastCompletedSale
                    if (saleToPrint != null) {
                        viewModel.printPhysicalTicket(saleToPrint)
                    } else {
                        viewModel.closeReprintPreview()
                    }
                }
            )
        }

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
    }
}

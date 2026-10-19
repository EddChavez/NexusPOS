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
import mx.nexuspos.core.ProductUnit

@Composable
fun SalesScreen(
    viewModel: SalesViewModel = remember { SalesViewModel() },
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
                    }
                }
            )

            // Alerts / Messages
            state.errorMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            state.successMessage?.let { msg ->
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF2E7D32))
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
                onEditQuantity = { line ->
                    if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                        viewModel.onScannerInputChanged(line.barcode)
                        viewModel.onScannerSubmitted()
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            // Panel Inferior de Totales y Gestión (Bottom Checkout Panel)
            BottomCheckoutPanel(
                state = state.toBottomCheckoutPanelState(),
                onSwitchTicket = { viewModel.cycleNextTicket() },
                onHoldTicket = { viewModel.holdTicketAndNew() },
                onDelete = { viewModel.deleteActiveCartLine() },
                onOpenCashDrawer = { viewModel.openCashDrawer() },
                onReprintTicket = { viewModel.reprintLastTicket() },
                onDailySalesAndReturns = { viewModel.openDailySalesAndReturns() },
                onCheckout = {
                    if (state.activeSession == null) {
                        viewModel.showSessionDialog(true)
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
                onConfirm = { qty -> viewModel.confirmWeighedProduct(qty) },
                onDismiss = { viewModel.dismissWeighingDialog() }
            )
        }

        if (state.isShowingCashPayment) {
            CashPaymentDialog(
                total = state.total,
                onConfirm = { receivedCents -> viewModel.checkoutCash(receivedCents) },
                onDismiss = { viewModel.showCashPaymentDialog(false) }
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
    }
}

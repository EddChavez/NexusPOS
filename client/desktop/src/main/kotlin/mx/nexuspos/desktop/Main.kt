package mx.nexuspos.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import mx.nexuspos.client.features.customers.CustomersScreen
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.client.features.footer.SystemFooter
import mx.nexuspos.client.features.header.AppModule
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.header.TopHeader
import mx.nexuspos.client.features.header.TopHeaderUiState
import mx.nexuspos.client.features.sales.SalesScreen
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.desktop.hardware.KeyboardWedgeBarcodeInput

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
    val viewModel = remember { SalesViewModel() }
    val customersViewModel = remember { CustomersViewModel(viewModel.apiClient) }
    val salesState by viewModel.uiState.collectAsState()

    var activeModule by remember { mutableStateOf(AppModule.SALES) }
    var scaleReading by remember { mutableStateOf(ScaleReading("0.000 kg", ScaleStatus.STABLE)) }

    val headerState = remember(activeModule, scaleReading, salesState.connectionState, salesState.activeCashierName) {
        TopHeaderUiState(
            brandTitle = "NexusPOS",
            brandSubtitle = "Abarrotes & Miscelánea",
            activeModule = activeModule,
            scaleReading = scaleReading,
            connectionState = salesState.connectionState,
            registerId = "01",
            cashierName = salesState.activeCashierName
        )
    }

    // Hardware Keyboard Wedge Barcode Scanner Adapter
    val barcodeWedge = remember {
        KeyboardWedgeBarcodeInput { scannedCode ->
            if (activeModule == AppModule.SALES) {
                viewModel.onScannerInputChanged(scannedCode)
                viewModel.onScannerSubmitted()
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NexusPOS - Terminal de Punto de Venta (LAN-First)",
        state = windowState,
        onKeyEvent = { keyEvent ->
            // First check if this is an incoming barcode scan from USB hardware scanner
            if (barcodeWedge.onKeyEvent(keyEvent)) {
                true
            } else if (keyEvent.type == KeyEventType.KeyDown) {
                // Function Key Shortcuts
                when (keyEvent.key) {
                    // Global Module Navigation Shortcuts F1 - F6
                    Key.F1 -> {
                        activeModule = AppModule.SALES
                        true
                    }
                    Key.F2 -> {
                        activeModule = AppModule.CUSTOMERS
                        true
                    }
                    Key.F3 -> {
                        activeModule = AppModule.PRODUCTS
                        true
                    }
                    Key.F4 -> {
                        activeModule = AppModule.INVENTORY
                        true
                    }
                    Key.F5 -> {
                        activeModule = AppModule.SETTINGS
                        true
                    }
                    Key.F6 -> {
                        // Acceso rápido a Cortes de Turno
                        activeModule = AppModule.CASH_CUT
                        viewModel.showSessionDialog(true)
                        true
                    }

                    // Local Module Shortcuts for Sales
                    Key.F12 -> {
                        if (activeModule == AppModule.SALES &&
                            salesState.cartLines.isNotEmpty() &&
                            salesState.activeSession != null
                        ) {
                            viewModel.showCashPaymentDialog(true)
                            true
                        } else false
                    }

                    Key.Insert -> {
                        if (activeModule == AppModule.SALES) {
                            viewModel.showGenericProductDialog(true)
                            true
                        } else if (activeModule == AppModule.CUSTOMERS) {
                            customersViewModel.openCreateDialog()
                            true
                        } else false
                    }

                    Key.P -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.showCommonArticlesDialog(true)
                            true
                        } else false
                    }

                    Key.F10 -> {
                        if (activeModule == AppModule.SALES) {
                            viewModel.showSearchCatalogDialog(true)
                            true
                        } else false
                    }

                    Key.F11 -> {
                        if (activeModule == AppModule.SALES) {
                            viewModel.onApplyWholesalePrice()
                            true
                        } else false
                    }

                    Key.F7 -> {
                        if (activeModule == AppModule.SALES) {
                            viewModel.showCashInDialog(true)
                            true
                        } else false
                    }

                    Key.F8 -> {
                        if (activeModule == AppModule.SALES) {
                            viewModel.showCashOutDialog(true)
                            true
                        } else false
                    }

                    Key.F9 -> {
                        if (activeModule == AppModule.SALES) {
                            val bulkProd = salesState.cartLines.firstOrNull { it.unit.code == "KG" }
                            if (bulkProd != null) {
                                viewModel.onScannerInputChanged(bulkProd.barcode)
                                viewModel.onScannerSubmitted()
                            } else {
                                viewModel.showGenericProductDialog(true)
                            }
                            true
                        } else false
                    }

                    Key.Delete -> {
                        if (activeModule == AppModule.SALES && salesState.cartLines.isNotEmpty()) {
                            viewModel.deleteActiveCartLine()
                            true
                        } else false
                    }

                    // Escape key handling
                    Key.Escape -> {
                        val hasOpenDialogs = salesState.isShowingCashPayment ||
                                salesState.isShowingCustomerDialog ||
                                salesState.isShowingSessionDialog ||
                                salesState.isWeighingProduct != null ||
                                salesState.isShowingGenericProductDialog ||
                                salesState.isShowingCommonArticlesDialog ||
                                salesState.isShowingCashInDialog ||
                                salesState.isShowingCashOutDialog ||
                                salesState.isShowingSearchCatalogDialog

                        if (hasOpenDialogs) {
                            viewModel.showCashPaymentDialog(false)
                            viewModel.showCustomerDialog(false)
                            viewModel.dismissWeighingDialog()
                            viewModel.showSessionDialog(false)
                            viewModel.showGenericProductDialog(false)
                            viewModel.showCommonArticlesDialog(false)
                            viewModel.showCashInDialog(false)
                            viewModel.showCashOutDialog(false)
                            viewModel.showSearchCatalogDialog(false)
                            true
                        } else if (activeModule != AppModule.SALES) {
                            // Si estamos en otro módulo, Escape regresa a Ventas
                            activeModule = AppModule.SALES
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        NexusTheme {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopHeader(
                        state = headerState,
                        onModuleSelected = { selected ->
                            activeModule = selected
                            if (selected == AppModule.CASH_CUT) {
                                viewModel.showSessionDialog(true)
                            }
                        },
                        onLogout = {
                            // Acción de Salir [Esc]
                            if (activeModule != AppModule.SALES) {
                                activeModule = AppModule.SALES
                            } else {
                                viewModel.showSessionDialog(true)
                            }
                        }
                    )
                },
                bottomBar = {
                    SystemFooter(
                        networkStatus = salesState.connectionState,
                        scaleReading = scaleReading,
                        versionText = "NexusPOS v2.4 Desktop • © 2026"
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (activeModule) {
                        AppModule.SALES, AppModule.CASH_CUT -> {
                            SalesScreen(viewModel = viewModel)
                        }
                        AppModule.CUSTOMERS -> {
                            CustomersScreen(
                                viewModel = customersViewModel,
                                cashSessionId = salesState.activeSession?.sessionId ?: "caja-1",
                                cashierUserId = salesState.activeSession?.cashierId ?: "usr-cajero",
                                onBackToSales = { activeModule = AppModule.SALES }
                            )
                        }
                        AppModule.PRODUCTS -> {
                            ModulePlaceholderScreen(
                                module = AppModule.PRODUCTS,
                                onBackToSales = { activeModule = AppModule.SALES }
                            )
                        }
                        AppModule.INVENTORY -> {
                            ModulePlaceholderScreen(
                                module = AppModule.INVENTORY,
                                onBackToSales = { activeModule = AppModule.SALES }
                            )
                        }
                        AppModule.SETTINGS -> {
                            ModulePlaceholderScreen(
                                module = AppModule.SETTINGS,
                                onBackToSales = { activeModule = AppModule.SALES }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModulePlaceholderScreen(
    module: AppModule,
    onBackToSales: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = module.keyShortcut,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = "Módulo: ${module.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Acceso directo mediante la tecla [${module.keyShortcut}]. La vista administrativa se conectará en este panel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onBackToSales,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Volver a Ventas [F1]", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

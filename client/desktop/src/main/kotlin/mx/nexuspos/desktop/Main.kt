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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.nexuspos.client.features.auth.AuthViewModel
import mx.nexuspos.client.features.auth.LoginScreen
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.auth.SupervisorAuthDialog
import mx.nexuspos.client.features.cash.CashCutScreen
import mx.nexuspos.client.features.cash.CashCutViewModel
import mx.nexuspos.client.features.customers.CustomersScreen
import mx.nexuspos.client.features.customers.CustomersViewModel
import mx.nexuspos.desktop.hardware.WhatsAppDispatcher
import mx.nexuspos.client.features.inventory.InventoryScreen
import mx.nexuspos.client.features.inventory.InventoryViewModel
import mx.nexuspos.desktop.inventory.ReorderExportHelper
import mx.nexuspos.client.features.products.ProductsScreen
import mx.nexuspos.client.features.products.ProductsViewModel
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.settings.NetworkUiConfig
import mx.nexuspos.client.features.settings.PrinterUiConfig
import mx.nexuspos.client.features.settings.ServerConnectionDialog
import mx.nexuspos.client.features.settings.SettingsScreen
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.client.features.footer.SystemFooter
import mx.nexuspos.client.features.header.AppModule
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.header.TopHeader
import mx.nexuspos.client.features.header.TopHeaderUiState
import mx.nexuspos.client.features.sales.SalesScreen
import mx.nexuspos.client.features.sales.v2.SalesScreenV2
import mx.nexuspos.client.features.sales.SalesViewModel
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.client.features.scale.ScaleConfig
import mx.nexuspos.client.features.scale.ScaleProtocol
import mx.nexuspos.client.features.scale.ScaleUiConfig
import mx.nexuspos.desktop.hardware.scale.ScaleConfigRepository
import mx.nexuspos.desktop.hardware.scale.ScaleManager
import mx.nexuspos.desktop.config.AppConfig
import mx.nexuspos.desktop.config.AppConfigRepository
import mx.nexuspos.desktop.hardware.DefaultPrinterDispatcher
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import mx.nexuspos.desktop.hardware.KeyboardWedgeBarcodeInput
import mx.nexuspos.desktop.hardware.PaperWidth
import mx.nexuspos.desktop.hardware.PrinterConfig
import mx.nexuspos.desktop.hardware.PrinterConfigRepository
import mx.nexuspos.desktop.hardware.PrinterConnectionType
import mx.nexuspos.desktop.hardware.PrinterDispatcher

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)
    val appConfigRepo = remember { AppConfigRepository() }
    var appConfig by remember { mutableStateOf(appConfigRepo.load()) }
    var isShowingServerDialog by remember { mutableStateOf(false) }

    val viewModel = remember { SalesViewModel() }
    val authViewModel = remember { AuthViewModel(viewModel.apiClient) }
    val customersViewModel = remember { CustomersViewModel(viewModel.apiClient) }
    val productsViewModel = remember { ProductsViewModel(viewModel.apiClient) }
    val inventoryViewModel = remember { InventoryViewModel(viewModel.apiClient) }
    val settingsViewModel = remember { SettingsViewModel(viewModel.apiClient) }
    val cashCutViewModel = remember { CashCutViewModel(viewModel.apiClient, registerId = appConfig.registerId) }
    val salesState by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val supervisorState by authViewModel.supervisorState.collectAsState()
    val printerRepo = remember { PrinterConfigRepository() }
    val printerDispatcher: PrinterDispatcher = remember { DefaultPrinterDispatcher() }
    var printerConfig by remember { mutableStateOf(printerRepo.load()) }
    val scaleRepo = remember { ScaleConfigRepository() }
    val scaleManager = remember { ScaleManager(scaleRepo) }

    var activeModule by remember { mutableStateOf(AppModule.SALES) }
    var scaleReading by remember { mutableStateOf(scaleManager.getCurrentReading()) }

    // Wire auth and hardware printer callbacks
    LaunchedEffect(Unit) {
        // 0. Base URL and server health check
        viewModel.apiClient.baseUrl = appConfig.baseUrl
        val isHealthy = viewModel.apiClient.checkHealth()
        if (!isHealthy) {
            isShowingServerDialog = true
        } else {
            viewModel.loadActiveSession(appConfig.registerId)
        }

        // 1. SettingsViewModel network & server LAN bridge
        settingsViewModel.networkConfigLoader = {
            appConfig = appConfigRepo.load()
            NetworkUiConfig(
                serverHost = appConfig.serverHost,
                serverPort = appConfig.serverPort,
                serverScheme = appConfig.serverScheme,
                registerId = appConfig.registerId,
                registerName = appConfig.registerName
            )
        }

        settingsViewModel.networkConfigSaver = { netCfg ->
            val newCfg = AppConfig(
                serverHost = netCfg.serverHost,
                serverPort = netCfg.serverPort,
                serverScheme = netCfg.serverScheme,
                registerId = netCfg.registerId,
                registerName = netCfg.registerName,
                connectTimeoutMs = appConfig.connectTimeoutMs
            )
            appConfigRepo.save(newCfg)
            appConfig = newCfg
            viewModel.apiClient.baseUrl = newCfg.baseUrl
            viewModel.checkServerConnection()
            viewModel.loadActiveSession(newCfg.registerId)
            cashCutViewModel.loadCurrentSession(newCfg.registerId)
        }

        settingsViewModel.testNetworkAction = { host, port ->
            val testClient = ApiClient(baseUrl = "http://$host:$port")
            val ok = testClient.checkHealth()
            if (ok) {
                Result.success("Servidor responde OK (HTTP 200 /health)")
            } else {
                Result.failure(Exception("Host inaccesible en http://$host:$port"))
            }
        }
        settingsViewModel.loadNetworkConfig()

        // 2. SettingsViewModel printer bridge
        settingsViewModel.printerConfigLoader = {
            printerConfig = printerRepo.load()
            PrinterUiConfig(
                connectionType = printerConfig.connectionType.name,
                osPrinterName = printerConfig.osPrinterName,
                tcpHost = printerConfig.tcpHost,
                tcpPort = printerConfig.tcpPort,
                paperWidth = if (printerConfig.paperWidth == PaperWidth.MM_58) "58mm" else "80mm",
                autoPrintOnSale = printerConfig.autoPrintOnSale,
                openDrawerOnCashSale = printerConfig.openDrawerOnCashSale
            )
        }

        settingsViewModel.printerConfigSaver = { uiCfg ->
            val newCfg = PrinterConfig(
                connectionType = try { PrinterConnectionType.valueOf(uiCfg.connectionType) } catch (e: Exception) { PrinterConnectionType.NONE },
                osPrinterName = uiCfg.osPrinterName,
                tcpHost = uiCfg.tcpHost,
                tcpPort = uiCfg.tcpPort,
                paperWidth = PaperWidth.fromString(uiCfg.paperWidth),
                autoPrintOnSale = uiCfg.autoPrintOnSale,
                openDrawerOnCashSale = uiCfg.openDrawerOnCashSale
            )
            printerRepo.save(newCfg)
            printerConfig = newCfg
        }

        settingsViewModel.osPrintersProvider = {
            printerDispatcher.getAvailableOsPrinters()
        }

        settingsViewModel.testPrintAction = { uiCfg ->
            val testCfg = PrinterConfig(
                connectionType = try { PrinterConnectionType.valueOf(uiCfg.connectionType) } catch (e: Exception) { PrinterConnectionType.NONE },
                osPrinterName = uiCfg.osPrinterName,
                tcpHost = uiCfg.tcpHost,
                tcpPort = uiCfg.tcpPort,
                paperWidth = PaperWidth.fromString(uiCfg.paperWidth)
            )
            printerDispatcher.printTestReceipt(
                storeName = settingsViewModel.uiState.value.storeName.ifBlank { "NEXUS POS" },
                config = testCfg
            )
        }

        settingsViewModel.testDrawerAction = { uiCfg ->
            val testCfg = PrinterConfig(
                connectionType = try { PrinterConnectionType.valueOf(uiCfg.connectionType) } catch (e: Exception) { PrinterConnectionType.NONE },
                osPrinterName = uiCfg.osPrinterName,
                tcpHost = uiCfg.tcpHost,
                tcpPort = uiCfg.tcpPort
            )
            printerDispatcher.openCashDrawer(testCfg)
        }

        settingsViewModel.loadPrinterConfig()

        // 1.5 SettingsViewModel scale bridge
        launch {
            scaleManager.readingFlow.collect { reading ->
                scaleReading = reading
            }
        }

        viewModel.scaleReadingProvider = { scaleManager.getCurrentReading() }
        viewModel.autoAcceptScaleWeightProvider = { scaleManager.getConfig().autoAcceptStableWeight }

        settingsViewModel.scaleConfigLoader = {
            val cfg = scaleManager.getConfig()
            ScaleUiConfig(
                enabled = cfg.enabled,
                portName = cfg.portName,
                baudRate = cfg.baudRate,
                protocol = cfg.protocol.code,
                autoAcceptStableWeight = cfg.autoAcceptStableWeight,
                pollIntervalMs = cfg.pollIntervalMs
            )
        }

        settingsViewModel.scaleConfigSaver = { uiCfg ->
            val newCfg = ScaleConfig(
                enabled = uiCfg.enabled,
                portName = uiCfg.portName,
                baudRate = uiCfg.baudRate,
                protocol = ScaleProtocol.fromString(uiCfg.protocol),
                autoAcceptStableWeight = uiCfg.autoAcceptStableWeight,
                pollIntervalMs = uiCfg.pollIntervalMs
            )
            scaleManager.updateConfig(newCfg)
        }

        settingsViewModel.serialPortsProvider = {
            scaleManager.getAvailablePorts()
        }

        settingsViewModel.testScaleAction = { uiCfg ->
            val reading = scaleManager.getCurrentReading()
            if (scaleManager.isConnected()) {
                Result.success(reading)
            } else {
                Result.failure(IllegalStateException("Báscula no conectada en puerto ${uiCfg.portName}"))
            }
        }

        settingsViewModel.tareScaleAction = {
            scaleManager.tare()
        }

        settingsViewModel.zeroScaleAction = {
            scaleManager.zero()
        }

        settingsViewModel.loadScaleConfig()

        // 2. Receipt formatting hook
        viewModel.receiptFormatter = { sale, isReprint ->
            val printer = EscPosReceiptPrinter(printerConfig.paperWidth)
            printer.formatSaleReceiptText(sale, isReprint = isReprint)
        }

        // 3. Sales auto-print and cash drawer hooks
        viewModel.onSaleCompleted = { sale ->
            CoroutineScope(Dispatchers.IO).launch {
                if (printerConfig.autoPrintOnSale && printerConfig.connectionType != PrinterConnectionType.NONE) {
                    printerDispatcher.printSaleReceipt(
                        sale = sale,
                        settings = null,
                        config = printerConfig,
                        isReprint = false
                    )
                }
                val isCash = sale.payments.any { it.method == "CASH" }
                if (isCash && printerConfig.openDrawerOnCashSale && printerConfig.connectionType != PrinterConnectionType.NONE) {
                    printerDispatcher.openCashDrawer(printerConfig)
                }
            }
        }

        viewModel.onOpenCashDrawer = {
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.openCashDrawer(printerConfig)
            }
        }

        viewModel.onPrintPhysicalReceipt = { sale ->
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.printSaleReceipt(
                    sale = sale,
                    settings = null,
                    config = printerConfig,
                    isReprint = true
                )
            }
        }

        authViewModel.onLoginSuccess = { user ->
            viewModel.setActiveCashier(
                userId = user.userId,
                name = user.name,
                role = user.role,
                permissions = user.permissions
            )
        }
        authViewModel.onSupervisorSuccess = { action ->
            when (action) {
                is SupervisorAction.AccessSettings -> {
                    activeModule = AppModule.SETTINGS
                }
                is SupervisorAction.AdjustInventory -> {
                    inventoryViewModel.openMovementDialog(action.product)
                }
                is SupervisorAction.CancelSale -> {
                    viewModel.executeCancelSale(action.saleId, action.reason, supervisorUserId = "usr-admin")
                }
                is SupervisorAction.ApplyDiscount -> {
                    viewModel.applyDiscount(
                        targetLineId = action.lineId,
                        discountPercent = action.discountPercent,
                        discountCents = action.discountCents,
                        isSupervisorApproved = true
                    )
                }
                is SupervisorAction.RefundSale -> {
                    viewModel.executeRefundSale(action.saleId, action.request, isSupervisorApproved = true)
                }
            }
        }

        viewModel.onDiscountSupervisorRequired = { action ->
            authViewModel.requestSupervisorElevation(action)
        }

        viewModel.onRefundSupervisorRequired = { action ->
            authViewModel.requestSupervisorElevation(action)
        }

        viewModel.onPrintSaleRefund = { refund ->
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.printSaleRefund(refund, null, printerConfig)
            }
        }

        // 4. Cash Cut & Shift reports hooks
        cashCutViewModel.cashCutFormatter = { session, isFinal ->
            val printer = EscPosReceiptPrinter(printerConfig.paperWidth)
            printer.formatCashCutText(session, isFinalZCut = isFinal)
        }

        cashCutViewModel.dailyReportFormatter = { report ->
            val printer = EscPosReceiptPrinter(printerConfig.paperWidth)
            printer.formatDailyReportText(report)
        }

        cashCutViewModel.onPrintCashCut = { session, isFinal ->
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.printCashCut(session, null, printerConfig, isFinalZCut = isFinal)
            }
        }

        cashCutViewModel.onPrintDailyReport = { report ->
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.printDailyReport(report, null, printerConfig)
            }
        }

        cashCutViewModel.onOpenCashDrawer = {
            CoroutineScope(Dispatchers.IO).launch {
                printerDispatcher.openCashDrawer(printerConfig)
            }
        }

        cashCutViewModel.onSessionClosedCallback = {
            viewModel.loadActiveSession()
        }
    }

    val headerState = remember(activeModule, scaleReading, salesState.connectionState, salesState.activeCashierName, salesState.activeCashierRole, appConfig.registerId, salesState.offlinePendingSalesCount) {
        TopHeaderUiState(
            brandTitle = "NexusPOS",
            brandSubtitle = "Abarrotes & Miscelánea",
            activeModule = activeModule,
            scaleReading = scaleReading,
            connectionState = salesState.connectionState,
            offlinePendingSalesCount = salesState.offlinePendingSalesCount,
            registerId = appConfig.registerId,
            cashierName = salesState.activeCashierName,
            cashierRole = salesState.activeCashierRole
        )
    }

    // Hardware Keyboard Wedge Barcode Scanner Adapter
    val barcodeWedge = remember {
        KeyboardWedgeBarcodeInput { scannedCode ->
            if (authState.isAuthenticated && activeModule == AppModule.SALES) {
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
            // 1. First check if terminal is locked (Login Screen input)
            if (!authState.isAuthenticated) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Zero, Key.NumPad0 -> { authViewModel.onDigit('0'); true }
                        Key.One, Key.NumPad1 -> { authViewModel.onDigit('1'); true }
                        Key.Two, Key.NumPad2 -> { authViewModel.onDigit('2'); true }
                        Key.Three, Key.NumPad3 -> { authViewModel.onDigit('3'); true }
                        Key.Four, Key.NumPad4 -> { authViewModel.onDigit('4'); true }
                        Key.Five, Key.NumPad5 -> { authViewModel.onDigit('5'); true }
                        Key.Six, Key.NumPad6 -> { authViewModel.onDigit('6'); true }
                        Key.Seven, Key.NumPad7 -> { authViewModel.onDigit('7'); true }
                        Key.Eight, Key.NumPad8 -> { authViewModel.onDigit('8'); true }
                        Key.Nine, Key.NumPad9 -> { authViewModel.onDigit('9'); true }
                        Key.Backspace -> { authViewModel.onBackspace(); true }
                        Key.Enter, Key.NumPadEnter -> { authViewModel.submitLogin(); true }
                        Key.Escape -> { authViewModel.onClear(); true }
                        else -> false
                    }
                } else false
            } else if (supervisorState.isVisible) {
                // 2. Supervisor authorization elevation dialog input
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Zero, Key.NumPad0 -> { authViewModel.onSupervisorDigit('0'); true }
                        Key.One, Key.NumPad1 -> { authViewModel.onSupervisorDigit('1'); true }
                        Key.Two, Key.NumPad2 -> { authViewModel.onSupervisorDigit('2'); true }
                        Key.Three, Key.NumPad3 -> { authViewModel.onSupervisorDigit('3'); true }
                        Key.Four, Key.NumPad4 -> { authViewModel.onSupervisorDigit('4'); true }
                        Key.Five, Key.NumPad5 -> { authViewModel.onSupervisorDigit('5'); true }
                        Key.Six, Key.NumPad6 -> { authViewModel.onSupervisorDigit('6'); true }
                        Key.Seven, Key.NumPad7 -> { authViewModel.onSupervisorDigit('7'); true }
                        Key.Eight, Key.NumPad8 -> { authViewModel.onSupervisorDigit('8'); true }
                        Key.Nine, Key.NumPad9 -> { authViewModel.onSupervisorDigit('9'); true }
                        Key.Backspace -> { authViewModel.onSupervisorBackspace(); true }
                        Key.Enter, Key.NumPadEnter -> { authViewModel.submitSupervisorElevation(); true }
                        Key.Escape -> { authViewModel.cancelSupervisorElevation(); true }
                        else -> false
                    }
                } else false
            } else if (barcodeWedge.onKeyEvent(keyEvent)) {
                // 3. Incoming barcode scan from USB hardware scanner
                true
            } else if (keyEvent.type == KeyEventType.KeyDown) {
                // 4. Quick Cashier Switch shortcut Ctrl+L or Cmd+L
                if ((keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.L) {
                    authViewModel.lockTerminal()
                    true
                } else when (keyEvent.key) {
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
                        if (salesState.activeCashierRole == "CASHIER") {
                            authViewModel.requestSupervisorElevation(SupervisorAction.AccessSettings)
                        } else {
                            activeModule = AppModule.SETTINGS
                        }
                        true
                    }
                    Key.F6 -> {
                        // Acceso rápido a Cortes de Turno
                        activeModule = AppModule.CASH_CUT
                        cashCutViewModel.loadCurrentSession()
                        true
                    }

                    // Local Module Shortcuts for Sales
                    Key.F12 -> {
                        if (activeModule == AppModule.SALES) {
                            if (salesState.activeSession == null) {
                                viewModel.showSessionDialog(true)
                            } else if (salesState.cartLines.isEmpty()) {
                                viewModel.setErrorMessage("Agrega al menos un producto al carrito para cobrar.")
                            } else {
                                viewModel.showCashPaymentDialog(true)
                            }
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
                        } else if (activeModule == AppModule.PRODUCTS) {
                            productsViewModel.openCreateDialog()
                            true
                        } else if (activeModule == AppModule.INVENTORY) {
                            val sel = inventoryViewModel.uiState.value.selectedProduct
                            if (sel != null) {
                                if (salesState.activeCashierRole == "CASHIER") {
                                    authViewModel.requestSupervisorElevation(SupervisorAction.AdjustInventory(sel))
                                } else {
                                    inventoryViewModel.openMovementDialog(sel)
                                }
                                true
                            } else false
                        } else if (activeModule == AppModule.SETTINGS &&
                            settingsViewModel.uiState.value.selectedTab == SettingsTab.USERS) {
                            settingsViewModel.openCreateUserDialog()
                            true
                        } else false
                    }

                    Key.P -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.showCommonArticlesDialog(true)
                            true
                        } else false
                    }

                    Key.H -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.openDailySalesAndReturns()
                            true
                        } else false
                    }

                    Key.T -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.cycleNextTicket()
                            true
                        } else false
                    }

                    Key.E -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.openParkSaleDialog()
                            true
                        } else false
                    }

                    Key.W -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.closeTicket(salesState.activeTicketId)
                            true
                        } else false
                    }

                    Key.B -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.openCashDrawer()
                            true
                        } else false
                    }

                    Key.R -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.reprintLastTicket()
                            true
                        } else false
                    }

                    Key.D -> {
                        if (activeModule == AppModule.SALES && (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)) {
                            viewModel.showDiscountDialog(true)
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
                                salesState.isShowingDiscountDialog ||
                                salesState.isWeighingProduct != null ||
                                salesState.isShowingGenericProductDialog ||
                                salesState.isShowingCommonArticlesDialog ||
                                salesState.isShowingCashInDialog ||
                                salesState.isShowingCashOutDialog ||
                                salesState.isShowingSearchCatalogDialog ||
                                salesState.isShowingSalesHistory ||
                                salesState.isShowingCancelReasonDialog ||
                                salesState.reprintTicketPreview != null

                        if (hasOpenDialogs) {
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
                            viewModel.closeReprintPreview()
                            true
                        } else if (inventoryViewModel.uiState.value.showMovementDialog) {
                            inventoryViewModel.closeMovementDialog()
                            true
                        } else if (settingsViewModel.uiState.value.showUserDialog) {
                            settingsViewModel.closeUserDialog()
                            true
                        } else if (productsViewModel.uiState.value.showDepartmentsDialog) {
                            productsViewModel.closeDepartmentsDialog()
                            true
                        } else if (productsViewModel.uiState.value.showFormDialog) {
                            productsViewModel.closeFormDialog()
                            true
                        } else if (customersViewModel.uiState.value.showFormDialog) {
                            customersViewModel.closeFormDialog()
                            true
                        } else if (customersViewModel.uiState.value.showPaymentDialog) {
                            customersViewModel.closePaymentDialog()
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
            if (!authState.isAuthenticated) {
                LoginScreen(
                    viewModel = authViewModel,
                    connectionState = salesState.connectionState,
                    registerId = appConfig.registerId,
                    onConfigureServerConnection = { isShowingServerDialog = true }
                )
            } else {
                val isV2SalesActive = appConfig.salesUiVersion == "V2" && activeModule == AppModule.SALES

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (!isV2SalesActive) {
                            TopHeader(
                                state = headerState,
                                onModuleSelected = { selected ->
                                    if (selected == AppModule.SETTINGS && salesState.activeCashierRole == "CASHIER") {
                                        authViewModel.requestSupervisorElevation(SupervisorAction.AccessSettings)
                                    } else {
                                        activeModule = selected
                                        if (selected == AppModule.CASH_CUT) {
                                            cashCutViewModel.loadCurrentSession()
                                        }
                                    }
                                },
                                onLogout = { authViewModel.lockTerminal() },
                                onSwitchCashier = { authViewModel.lockTerminal() },
                                onSyncOffline = { viewModel.openOfflineSyncDialog() }
                            )
                        }
                    },
                    bottomBar = {
                        SystemFooter(
                            networkStatus = salesState.connectionState,
                            offlinePendingCount = salesState.offlinePendingSalesCount,
                            scaleReading = scaleReading,
                            versionText = "NexusPOS v2.4 Desktop • © 2026",
                            onSyncClick = { viewModel.openOfflineSyncDialog() }
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
                            AppModule.SALES -> {
                                if (appConfig.salesUiVersion == "V2") {
                                    SalesScreenV2(
                                        viewModel = viewModel,
                                        scaleReading = scaleReading,
                                        onPrintShiftReceipt = { sale ->
                                            if (printerConfig.connectionType == PrinterConnectionType.NONE) {
                                                Result.failure(IllegalStateException("Configura una impresora para reimprimir el ticket."))
                                            } else {
                                                withContext(Dispatchers.IO) {
                                                    printerDispatcher.printSaleReceipt(sale, null, printerConfig, isReprint = true)
                                                }
                                            }
                                        },
                                        onOpenWhatsApp = { WhatsAppDispatcher.openWhatsApp(it) },
                                        onCopyText = { WhatsAppDispatcher.copyToClipboard(it) },
                                        onNavigateToModule = { activeModule = it },
                                        onLockTerminal = { authViewModel.lockTerminal() },
                                        onOpenCashDrawerHardware = {
                                            if (printerConfig.connectionType != PrinterConnectionType.NONE) {
                                                printerDispatcher.openCashDrawer(printerConfig)
                                            }
                                        }
                                    )
                                } else {
                                    SalesScreen(
                                        viewModel = viewModel,
                                        scaleReading = scaleReading,
                                        onRequestCancelSale = { sale, reason ->
                                            if (salesState.activeCashierRole == "CASHIER") {
                                                authViewModel.requestSupervisorElevation(
                                                    SupervisorAction.CancelSale(sale.id, sale.folio, reason)
                                                )
                                            } else {
                                                viewModel.executeCancelSale(sale.id, reason, supervisorUserId = null)
                                            }
                                        },
                                        onOpenWhatsApp = { WhatsAppDispatcher.openWhatsApp(it) },
                                        onCopyText = { WhatsAppDispatcher.copyToClipboard(it) }
                                    )
                                }
                            }
                            AppModule.CASH_CUT -> {
                                CashCutScreen(
                                    viewModel = cashCutViewModel,
                                    onBackToSales = { activeModule = AppModule.SALES },
                                    activeCashierUserId = salesState.activeCashierId
                                )
                            }
                            AppModule.CUSTOMERS -> {
                                CustomersScreen(
                                    viewModel = customersViewModel,
                                    cashSessionId = salesState.activeSession?.sessionId ?: "caja-1",
                                    cashierUserId = salesState.activeCashierId,
                                    onOpenWhatsApp = { WhatsAppDispatcher.openWhatsApp(it) },
                                    onCopyText = { WhatsAppDispatcher.copyToClipboard(it) },
                                    onBackToSales = { activeModule = AppModule.SALES }
                                )
                            }
                            AppModule.PRODUCTS -> {
                                ProductsScreen(
                                    viewModel = productsViewModel,
                                    onBackToSales = { activeModule = AppModule.SALES },
                                    onPrintShelfLabel = { prod, copies ->
                                        CoroutineScope(Dispatchers.IO).launch {
                                            if (printerConfig.connectionType != PrinterConnectionType.NONE) {
                                                val printer = EscPosReceiptPrinter(printerConfig.paperWidth)
                                                val pres = prod.presentations.firstOrNull()
                                                val barcode = pres?.barcode?.ifBlank { prod.sku } ?: prod.sku
                                                val price = pres?.priceCents ?: 0L
                                                repeat(copies) {
                                                    val bytes = printer.generateShelfLabelBytes(
                                                        productName = prod.name,
                                                        barcode = barcode,
                                                        priceCents = price,
                                                        presentationName = prod.baseUnit,
                                                        storeName = settingsViewModel.uiState.value.storeName.ifBlank { "NEXUS POS" }
                                                    )
                                                    printerDispatcher.printBytes(bytes, printerConfig)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            AppModule.INVENTORY -> {
                                InventoryScreen(
                                    viewModel = inventoryViewModel,
                                    onRequestAdjustStock = { product ->
                                        if (salesState.activeCashierRole == "CASHIER") {
                                            authViewModel.requestSupervisorElevation(SupervisorAction.AdjustInventory(product))
                                        } else {
                                            inventoryViewModel.openMovementDialog(product)
                                        }
                                    },
                                    onExportReorderCsv = { items ->
                                        val csv = ReorderExportHelper.generateCsvContent(items)
                                        try {
                                            val file = java.io.File(System.getProperty("user.home"), "pedido_sugerido_${System.currentTimeMillis()}.csv")
                                            file.writeText(csv)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    onCopyReorderWhatsApp = { items ->
                                        val text = ReorderExportHelper.generateWhatsAppText(
                                            items = items,
                                            storeName = settingsViewModel.uiState.value.storeName.ifBlank { "NEXUS POS" }
                                        )
                                        try {
                                            val selection = java.awt.datatransfer.StringSelection(text)
                                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    onPrintReorderTicket = { items ->
                                        CoroutineScope(Dispatchers.IO).launch {
                                            printerDispatcher.printReorderList(
                                                items = items,
                                                settings = null,
                                                departmentName = null,
                                                config = printerConfig
                                            )
                                        }
                                    },
                                    onPrintLotsReport = {
                                        val rep = inventoryViewModel.uiState.value.lotsReport
                                        if (rep != null) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                printerDispatcher.printExpirationsReport(
                                                    report = rep,
                                                    settings = null,
                                                    config = printerConfig
                                                )
                                            }
                                        }
                                    },
                                    onBackToSales = { activeModule = AppModule.SALES }
                                )
                            }
                            AppModule.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBackToSales = { activeModule = AppModule.SALES }
                                )
                            }
                        }

                        // Supervisor authorization modal when needed
                        if (supervisorState.isVisible) {
                            SupervisorAuthDialog(
                                state = supervisorState,
                                onDigit = { authViewModel.onSupervisorDigit(it) },
                                onBackspace = { authViewModel.onSupervisorBackspace() },
                                onSubmit = { authViewModel.submitSupervisorElevation() },
                                onDismiss = { authViewModel.cancelSupervisorElevation() }
                            )
                        }
                    }
                }
            }

            // Server LAN Connection configuration dialog
            ServerConnectionDialog(
                isOpen = isShowingServerDialog,
                initialHost = appConfig.serverHost,
                initialPort = appConfig.serverPort,
                initialRegisterId = appConfig.registerId,
                initialRegisterName = appConfig.registerName,
                onTestConnection = { host, port ->
                    val testClient = ApiClient(baseUrl = "http://$host:$port")
                    val ok = testClient.checkHealth()
                    if (ok) {
                        Result.success("Servidor NexusPOS responde OK (HTTP 200 /health)")
                    } else {
                        Result.failure(Exception("Host inaccesible en http://$host:$port"))
                    }
                },
                onSaveAndConnect = { host, port, regId, regName ->
                    val newCfg = AppConfig(
                        serverHost = host,
                        serverPort = port,
                        serverScheme = "http",
                        registerId = regId,
                        registerName = regName
                    )
                    appConfigRepo.save(newCfg)
                    appConfig = newCfg
                    viewModel.apiClient.baseUrl = newCfg.baseUrl
                    viewModel.checkServerConnection()
                    viewModel.loadActiveSession(regId)
                    cashCutViewModel.loadCurrentSession(regId)
                },
                onDismiss = { isShowingServerDialog = false }
            )
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

package mx.nexuspos.client.features.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.scale.ScaleUiConfig
import mx.nexuspos.contracts.identity.CreateUserRequest
import mx.nexuspos.contracts.identity.UpdateUserRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.system.SettingsDto

data class PrinterUiConfig(
    val connectionType: String = "NONE",
    val osPrinterName: String = "",
    val tcpHost: String = "192.168.1.200",
    val tcpPort: Int = 9100,
    val paperWidth: String = "80mm",
    val autoPrintOnSale: Boolean = true,
    val openDrawerOnCashSale: Boolean = true
)

data class NetworkUiConfig(
    val serverHost: String = "localhost",
    val serverPort: Int = 8080,
    val serverScheme: String = "http",
    val registerId: String = "caja-1",
    val registerName: String = "Caja Principal"
)

class SettingsViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Hardware printer hooks wired by desktop launcher
    var printerConfigLoader: (() -> PrinterUiConfig)? = null
    var printerConfigSaver: ((PrinterUiConfig) -> Unit)? = null
    var osPrintersProvider: (() -> List<String>)? = null
    var testPrintAction: ((PrinterUiConfig) -> Result<Unit>)? = null
    var testDrawerAction: ((PrinterUiConfig) -> Result<Unit>)? = null

    // Hardware scale hooks wired by desktop launcher
    var scaleConfigLoader: (() -> ScaleUiConfig)? = null
    var scaleConfigSaver: ((ScaleUiConfig) -> Unit)? = null
    var serialPortsProvider: (() -> List<String>)? = null
    var testScaleAction: ((ScaleUiConfig) -> Result<ScaleReading>)? = null
    var tareScaleAction: (() -> Result<Unit>)? = null
    var zeroScaleAction: (() -> Result<Unit>)? = null

    // Network / Server LAN hooks wired by desktop launcher
    var networkConfigLoader: (() -> NetworkUiConfig)? = null
    var networkConfigSaver: ((NetworkUiConfig) -> Unit)? = null
    var testNetworkAction: (suspend (host: String, port: Int) -> Result<String>)? = null

    init {
        loadAll()
    }

    private fun loadAll() {
        loadSettings()
        loadUsers()
    }

    fun loadSettings() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            val res = apiClient.getSettings()
            res.onSuccess { dto ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        storeName = dto.storeName,
                        storeAddress = dto.storeAddress,
                        storePhone = dto.storePhone,
                        storeTaxId = dto.storeTaxId,
                        ticketHeader = dto.ticketHeader,
                        ticketFooter = dto.ticketFooter,
                        currencySymbol = dto.currencySymbol,
                        allowNegativeStock = dto.allowNegativeStock,
                        blindCashCountEnabled = dto.blindCashCountEnabled,
                        maxCashierDiscountPercent = dto.maxCashierDiscountPercent,
                        loyaltyEnabled = dto.loyaltyEnabled,
                        loyaltyCashbackPercent = dto.loyaltyCashbackPercent,
                        loyaltyMinRedeemCents = dto.loyaltyMinRedeemCents
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorBanner = err.message) }
            }
        }
    }

    fun loadUsers(includeInactive: Boolean = _uiState.value.includeInactiveUsers) {
        scope.launch {
            _uiState.update { it.copy(isLoadingUsers = true) }
            val res = apiClient.getUsers(includeInactive)
            res.onSuccess { list ->
                _uiState.update { it.copy(users = list, isLoadingUsers = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoadingUsers = false, errorBanner = err.message) }
            }
        }
    }

    fun selectTab(tab: SettingsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == SettingsTab.PRINTER) {
            refreshAvailableOsPrinters()
        } else if (tab == SettingsTab.NETWORK) {
            loadNetworkConfig()
        }
    }

    // --- Store / Ticket field updates ---

    fun onStoreNameChanged(v: String) = _uiState.update { it.copy(storeName = v) }
    fun onStoreAddressChanged(v: String) = _uiState.update { it.copy(storeAddress = v) }
    fun onStorePhoneChanged(v: String) = _uiState.update { it.copy(storePhone = v) }
    fun onStoreTaxIdChanged(v: String) = _uiState.update { it.copy(storeTaxId = v) }
    fun onTicketHeaderChanged(v: String) = _uiState.update { it.copy(ticketHeader = v) }
    fun onTicketFooterChanged(v: String) = _uiState.update { it.copy(ticketFooter = v) }
    fun onCurrencySymbolChanged(v: String) = _uiState.update { it.copy(currencySymbol = v) }

    fun saveStoreSettings() {
        val s = _uiState.value
        scope.launch {
            _uiState.update { it.copy(isSaving = true, errorBanner = null) }
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = s.allowNegativeStock,
                blindCashCountEnabled = s.blindCashCountEnabled,
                maxCashierDiscountPercent = s.maxCashierDiscountPercent,
                loyaltyEnabled = s.loyaltyEnabled,
                loyaltyCashbackPercent = s.loyaltyCashbackPercent,
                loyaltyMinRedeemCents = s.loyaltyMinRedeemCents
            )
            val res = apiClient.updateSettings(dto)
            res.onSuccess {
                _uiState.update { it.copy(isSaving = false, successBanner = "Configuración de tienda guardada correctamente") }
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorBanner = err.message) }
            }
        }
    }

    // --- Policies ---

    fun toggleNegativeStock() {
        val newValue = !_uiState.value.allowNegativeStock
        _uiState.update { it.copy(allowNegativeStock = newValue) }
        // Persist immediately
        val s = _uiState.value
        scope.launch {
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = newValue,
                blindCashCountEnabled = s.blindCashCountEnabled,
                maxCashierDiscountPercent = s.maxCashierDiscountPercent,
                loyaltyEnabled = s.loyaltyEnabled,
                loyaltyCashbackPercent = s.loyaltyCashbackPercent,
                loyaltyMinRedeemCents = s.loyaltyMinRedeemCents
            )
            apiClient.updateSettings(dto).onSuccess {
                _uiState.update { it.copy(successBanner = if (newValue) "Política cambiada a Flexible (stock negativo permitido)" else "Política cambiada a Estricto (stock negativo bloqueado)") }
                autoDismissBanner()
            }.onFailure { err ->
                // Revert on failure
                _uiState.update { it.copy(allowNegativeStock = !newValue, errorBanner = err.message) }
            }
        }
    }

    fun toggleBlindCashCount() {
        val newValue = !_uiState.value.blindCashCountEnabled
        _uiState.update { it.copy(blindCashCountEnabled = newValue) }
        val s = _uiState.value
        scope.launch {
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = s.allowNegativeStock,
                blindCashCountEnabled = newValue,
                maxCashierDiscountPercent = s.maxCashierDiscountPercent,
                loyaltyEnabled = s.loyaltyEnabled,
                loyaltyCashbackPercent = s.loyaltyCashbackPercent,
                loyaltyMinRedeemCents = s.loyaltyMinRedeemCents
            )
            apiClient.updateSettings(dto).onSuccess {
                _uiState.update { it.copy(successBanner = if (newValue) "Modo Arqueo Ciego ACTIVADO (esperado oculto en caja)" else "Modo Arqueo Ciego DESACTIVADO") }
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(blindCashCountEnabled = !newValue, errorBanner = err.message) }
            }
        }
    }

    fun onMaxCashierDiscountPercentChanged(v: Double) {
        val safe = v.coerceIn(0.0, 100.0)
        _uiState.update { it.copy(maxCashierDiscountPercent = safe) }
        val s = _uiState.value
        scope.launch {
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = s.allowNegativeStock,
                blindCashCountEnabled = s.blindCashCountEnabled,
                maxCashierDiscountPercent = safe,
                loyaltyEnabled = s.loyaltyEnabled,
                loyaltyCashbackPercent = s.loyaltyCashbackPercent,
                loyaltyMinRedeemCents = s.loyaltyMinRedeemCents
            )
            apiClient.updateSettings(dto).onSuccess {
                _uiState.update { it.copy(successBanner = "Límite de descuento para cajeros actualizado a $safe%") }
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    fun toggleLoyaltyEnabled() {
        val newValue = !_uiState.value.loyaltyEnabled
        _uiState.update { it.copy(loyaltyEnabled = newValue) }
        saveLoyaltySettings(newValue, _uiState.value.loyaltyCashbackPercent, _uiState.value.loyaltyMinRedeemCents)
    }

    fun onLoyaltyCashbackPercentChanged(percent: Double) {
        val safe = percent.coerceIn(0.0, 100.0)
        _uiState.update { it.copy(loyaltyCashbackPercent = safe) }
        saveLoyaltySettings(_uiState.value.loyaltyEnabled, safe, _uiState.value.loyaltyMinRedeemCents)
    }

    fun onLoyaltyMinRedeemCentsChanged(cents: Long) {
        val safe = cents.coerceAtLeast(0L)
        _uiState.update { it.copy(loyaltyMinRedeemCents = safe) }
        saveLoyaltySettings(_uiState.value.loyaltyEnabled, _uiState.value.loyaltyCashbackPercent, safe)
    }

    private fun saveLoyaltySettings(enabled: Boolean, percent: Double, minRedeem: Long) {
        val s = _uiState.value
        scope.launch {
            val dto = SettingsDto(
                storeName = s.storeName,
                storeAddress = s.storeAddress,
                storePhone = s.storePhone,
                storeTaxId = s.storeTaxId,
                ticketHeader = s.ticketHeader,
                ticketFooter = s.ticketFooter,
                currencySymbol = s.currencySymbol,
                allowNegativeStock = s.allowNegativeStock,
                blindCashCountEnabled = s.blindCashCountEnabled,
                maxCashierDiscountPercent = s.maxCashierDiscountPercent,
                loyaltyEnabled = enabled,
                loyaltyCashbackPercent = percent,
                loyaltyMinRedeemCents = minRedeem
            )
            apiClient.updateSettings(dto).onSuccess {
                _uiState.update { it.copy(successBanner = if (enabled) "Monedero Electrónico ACTIVADO ($percent% cashback)" else "Monedero Electrónico DESACTIVADO") }
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    // --- User Filter ---

    fun onUserFilterChanged(v: String) = _uiState.update { it.copy(userFilter = v) }

    fun onToggleIncludeInactiveUsers() {
        val newVal = !_uiState.value.includeInactiveUsers
        _uiState.update { it.copy(includeInactiveUsers = newVal) }
        loadUsers(newVal)
    }

    // --- User Form Dialog ---

    fun openCreateUserDialog() {
        _uiState.update {
            it.copy(
                showUserDialog = true,
                editingUser = null,
                userFormName = "",
                userFormRole = "CASHIER",
                userFormPin = "",
                userFormError = null
            )
        }
    }

    fun openEditUserDialog(user: UserDto) {
        _uiState.update {
            it.copy(
                showUserDialog = true,
                editingUser = user,
                userFormName = user.name,
                userFormRole = user.role,
                userFormPin = "",
                userFormError = null
            )
        }
    }

    fun closeUserDialog() {
        _uiState.update { it.copy(showUserDialog = false, editingUser = null, userFormError = null) }
    }

    fun onUserFormNameChanged(v: String) = _uiState.update { it.copy(userFormName = v) }
    fun onUserFormRoleChanged(v: String) = _uiState.update { it.copy(userFormRole = v) }
    fun onUserFormPinChanged(v: String) {
        if (v.length <= 4 && v.all { it.isDigit() }) {
            _uiState.update { it.copy(userFormPin = v) }
        }
    }

    fun saveUser() {
        val s = _uiState.value
        val name = s.userFormName.trim()
        val role = s.userFormRole
        val pin = s.userFormPin

        // Validate
        if (name.isBlank()) {
            _uiState.update { it.copy(userFormError = "El nombre es obligatorio") }
            return
        }
        val isCreating = s.editingUser == null
        if (isCreating && pin.length != 4) {
            _uiState.update { it.copy(userFormError = "El PIN debe ser exactamente 4 dígitos") }
            return
        }
        if (pin.isNotBlank() && pin.length != 4) {
            _uiState.update { it.copy(userFormError = "El PIN debe ser exactamente 4 dígitos") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSaving = true, userFormError = null) }

            if (isCreating) {
                val req = CreateUserRequest(name = name, role = role, pin = pin)
                val res = apiClient.createUser(req)
                res.onSuccess { created ->
                    _uiState.update {
                        it.copy(isSaving = false, showUserDialog = false, editingUser = null,
                            successBanner = "Usuario '${created.name}' creado correctamente")
                    }
                    loadUsers()
                    autoDismissBanner()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, userFormError = err.message) }
                }
            } else {
                val editing = s.editingUser!!
                val req = UpdateUserRequest(name = name, role = role, pin = pin.ifBlank { null })
                val res = apiClient.updateUser(editing.id, req)
                res.onSuccess { updated ->
                    _uiState.update {
                        it.copy(isSaving = false, showUserDialog = false, editingUser = null,
                            successBanner = "Usuario '${updated.name}' actualizado correctamente")
                    }
                    loadUsers()
                    autoDismissBanner()
                }.onFailure { err ->
                    _uiState.update { it.copy(isSaving = false, userFormError = err.message) }
                }
            }
        }
    }

    fun deactivateUser(user: UserDto) {
        scope.launch {
            val res = apiClient.deactivateUser(user.id)
            res.onSuccess {
                _uiState.update { it.copy(successBanner = "Usuario '${user.name}' desactivado") }
                loadUsers()
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    fun reactivateUser(user: UserDto) {
        scope.launch {
            val res = apiClient.reactivateUser(user.id)
            res.onSuccess {
                _uiState.update { it.copy(successBanner = "Usuario '${user.name}' reactivado") }
                loadUsers()
                autoDismissBanner()
            }.onFailure { err ->
                _uiState.update { it.copy(errorBanner = err.message) }
            }
        }
    }

    // --- Hardware / Printer settings ---

    fun loadPrinterConfig() {
        val loaded = printerConfigLoader?.invoke() ?: PrinterUiConfig()
        val osPrinters = osPrintersProvider?.invoke() ?: emptyList()
        _uiState.update {
            it.copy(
                printerConnectionType = loaded.connectionType,
                osPrinterName = loaded.osPrinterName,
                availableOsPrinters = osPrinters,
                tcpHost = loaded.tcpHost,
                tcpPort = loaded.tcpPort.toString(),
                paperWidth = loaded.paperWidth,
                autoPrintOnSale = loaded.autoPrintOnSale,
                openDrawerOnCashSale = loaded.openDrawerOnCashSale
            )
        }
    }

    fun onPrinterConnectionTypeChanged(v: String) = _uiState.update { it.copy(printerConnectionType = v) }
    fun onOsPrinterNameChanged(v: String) = _uiState.update { it.copy(osPrinterName = v) }
    fun onTcpHostChanged(v: String) = _uiState.update { it.copy(tcpHost = v) }
    fun onTcpPortChanged(v: String) = _uiState.update { it.copy(tcpPort = v) }
    fun onPaperWidthChanged(v: String) = _uiState.update { it.copy(paperWidth = v) }
    fun onAutoPrintOnSaleChanged(v: Boolean) = _uiState.update { it.copy(autoPrintOnSale = v) }
    fun onOpenDrawerOnCashSaleChanged(v: Boolean) = _uiState.update { it.copy(openDrawerOnCashSale = v) }

    fun refreshAvailableOsPrinters() {
        val printers = osPrintersProvider?.invoke() ?: emptyList()
        _uiState.update { it.copy(availableOsPrinters = printers) }
    }

    fun getCurrentPrinterConfig(): PrinterUiConfig {
        val s = _uiState.value
        return PrinterUiConfig(
            connectionType = s.printerConnectionType,
            osPrinterName = s.osPrinterName,
            tcpHost = s.tcpHost.trim(),
            tcpPort = s.tcpPort.toIntOrNull() ?: 9100,
            paperWidth = s.paperWidth,
            autoPrintOnSale = s.autoPrintOnSale,
            openDrawerOnCashSale = s.openDrawerOnCashSale
        )
    }

    fun savePrinterConfig() {
        val cfg = getCurrentPrinterConfig()
        printerConfigSaver?.invoke(cfg)
        _uiState.update {
            it.copy(
                successBanner = "Configuración de impresora guardada correctamente.",
                errorBanner = null
            )
        }
        autoDismissBanner()
    }

    fun testPrinter() {
        val s = _uiState.value
        if (s.printerConnectionType == "NONE") {
            _uiState.update { it.copy(errorBanner = "La impresora está desactivada (NONE). Seleccione USB o Red Ethernet antes de probar.") }
            return
        }
        val cfg = getCurrentPrinterConfig()
        _uiState.update { it.copy(isTestingPrinter = true, errorBanner = null) }
        scope.launch {
            val res = testPrintAction?.invoke(cfg) ?: Result.failure(IllegalStateException("Servicio de impresión no disponible"))
            _uiState.update { state ->
                state.copy(
                    isTestingPrinter = false,
                    successBanner = if (res.isSuccess) "Ticket de prueba enviado exitosamente a la impresora." else null,
                    errorBanner = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) autoDismissBanner()
        }
    }

    fun testCashDrawer() {
        val s = _uiState.value
        if (s.printerConnectionType == "NONE") {
            _uiState.update { it.copy(errorBanner = "La impresora está desactivada (NONE). Seleccione USB o Red Ethernet para enviar el pulso del cajón.") }
            return
        }
        val cfg = getCurrentPrinterConfig()
        scope.launch {
            val res = testDrawerAction?.invoke(cfg) ?: Result.failure(IllegalStateException("Servicio de hardware no disponible"))
            _uiState.update { state ->
                state.copy(
                    successBanner = if (res.isSuccess) "Comando de apertura de cajón enviado exitosamente." else null,
                    errorBanner = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) autoDismissBanner()
        }
    }

    // --- Hardware / Scale settings ---

    fun loadScaleConfig() {
        val loaded = scaleConfigLoader?.invoke() ?: ScaleUiConfig()
        val ports = serialPortsProvider?.invoke() ?: emptyList()
        _uiState.update {
            it.copy(
                scaleEnabled = loaded.enabled,
                scalePortName = loaded.portName,
                availableScalePorts = ports,
                scaleBaudRate = loaded.baudRate.toString(),
                scaleProtocol = loaded.protocol,
                scaleAutoAcceptWeight = loaded.autoAcceptStableWeight
            )
        }
    }

    fun onScaleEnabledChanged(v: Boolean) = _uiState.update { it.copy(scaleEnabled = v) }
    fun onScalePortNameChanged(v: String) = _uiState.update { it.copy(scalePortName = v) }
    fun onScaleBaudRateChanged(v: String) = _uiState.update { it.copy(scaleBaudRate = v) }
    fun onScaleProtocolChanged(v: String) = _uiState.update { it.copy(scaleProtocol = v) }
    fun onScaleAutoAcceptWeightChanged(v: Boolean) = _uiState.update { it.copy(scaleAutoAcceptWeight = v) }

    fun refreshAvailableScalePorts() {
        val ports = serialPortsProvider?.invoke() ?: emptyList()
        _uiState.update { it.copy(availableScalePorts = ports) }
    }

    fun getCurrentScaleConfig(): ScaleUiConfig {
        val s = _uiState.value
        return ScaleUiConfig(
            enabled = s.scaleEnabled,
            portName = s.scalePortName,
            baudRate = s.scaleBaudRate.toIntOrNull() ?: 9600,
            protocol = s.scaleProtocol,
            autoAcceptStableWeight = s.scaleAutoAcceptWeight
        )
    }

    fun saveScaleConfig() {
        val cfg = getCurrentScaleConfig()
        scaleConfigSaver?.invoke(cfg)
        _uiState.update {
            it.copy(
                successBanner = "Configuración de báscula guardada correctamente.",
                errorBanner = null
            )
        }
        autoDismissBanner()
    }

    fun testScale() {
        val cfg = getCurrentScaleConfig()
        _uiState.update { it.copy(isTestingScale = true, scaleTestResult = null) }
        scope.launch {
            val res = testScaleAction?.invoke(cfg) ?: Result.failure(IllegalStateException("Driver de báscula no disponible"))
            _uiState.update { state ->
                state.copy(
                    isTestingScale = false,
                    isScaleTestSuccess = res.isSuccess,
                    scaleTestResult = if (res.isSuccess) {
                        "Lectura: ${res.getOrNull()?.weightText} [${res.getOrNull()?.status?.label}]"
                    } else {
                        "Error: ${res.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    fun tareScale() {
        scope.launch {
            val res = tareScaleAction?.invoke() ?: Result.failure(IllegalStateException("Driver de báscula no disponible"))
            _uiState.update { state ->
                state.copy(
                    successBanner = if (res.isSuccess) "Comando de TARA enviado a la báscula." else null,
                    errorBanner = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) autoDismissBanner()
        }
    }

    fun zeroScale() {
        scope.launch {
            val res = zeroScaleAction?.invoke() ?: Result.failure(IllegalStateException("Driver de báscula no disponible"))
            _uiState.update { state ->
                state.copy(
                    successBanner = if (res.isSuccess) "Comando Poner a CERO enviado a la báscula." else null,
                    errorBanner = res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) autoDismissBanner()
        }
    }

    // --- Network / Server LAN settings ---

    fun loadNetworkConfig() {
        val loaded = networkConfigLoader?.invoke() ?: NetworkUiConfig()
        _uiState.update {
            it.copy(
                serverHost = loaded.serverHost,
                serverPort = loaded.serverPort.toString(),
                serverScheme = loaded.serverScheme,
                registerId = loaded.registerId,
                registerName = loaded.registerName,
                networkTestResult = null
            )
        }
    }

    fun onServerHostChanged(v: String) = _uiState.update { it.copy(serverHost = v.trim(), networkTestResult = null) }
    fun onServerPortChanged(v: String) = _uiState.update { it.copy(serverPort = v.filter { ch -> ch.isDigit() }, networkTestResult = null) }
    fun onServerSchemeChanged(v: String) = _uiState.update { it.copy(serverScheme = v.trim(), networkTestResult = null) }
    fun onRegisterIdChanged(v: String) = _uiState.update { it.copy(registerId = v.trim()) }
    fun onRegisterNameChanged(v: String) = _uiState.update { it.copy(registerName = v) }

    fun getCurrentNetworkConfig(): NetworkUiConfig {
        val s = _uiState.value
        return NetworkUiConfig(
            serverHost = s.serverHost.ifBlank { "localhost" },
            serverPort = s.serverPort.toIntOrNull() ?: 8080,
            serverScheme = s.serverScheme.ifBlank { "http" },
            registerId = s.registerId.ifBlank { "caja-1" },
            registerName = s.registerName.ifBlank { "Caja Principal" }
        )
    }

    fun saveNetworkConfig() {
        val cfg = getCurrentNetworkConfig()
        networkConfigSaver?.invoke(cfg)
        _uiState.update {
            it.copy(
                successBanner = "Configuración de red y servidor guardada correctamente.",
                errorBanner = null
            )
        }
        autoDismissBanner()
    }

    fun testNetwork() {
        val s = _uiState.value
        val host = s.serverHost.trim()
        val port = s.serverPort.toIntOrNull() ?: 8080
        if (host.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Debe ingresar una IP o Host de servidor válido.") }
            return
        }
        _uiState.update { it.copy(isTestingNetwork = true, networkTestResult = null, errorBanner = null) }
        scope.launch {
            val res = testNetworkAction?.invoke(host, port)
                ?: Result.failure(IllegalStateException("Acción de prueba de red no configurada"))
            _uiState.update { state ->
                state.copy(
                    isTestingNetwork = false,
                    isNetworkTestSuccess = res.isSuccess,
                    networkTestResult = if (res.isSuccess) {
                        "Conexión exitosa con el servidor (${res.getOrNull() ?: "OK"})"
                    } else {
                        "Error de conexión: ${res.exceptionOrNull()?.message ?: "Host inaccesible"}"
                    }
                )
            }
        }
    }

    fun clearBanners() {
        _uiState.update { it.copy(successBanner = null, errorBanner = null) }
    }

    private fun autoDismissBanner() {
        scope.launch {
            delay(4000)
            _uiState.update { it.copy(successBanner = null) }
        }
    }
}

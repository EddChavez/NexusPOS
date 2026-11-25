package mx.nexuspos.client.features.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.SaleSubmissionRejected
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.core.saleOutcomeIsUncertain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.offline.LocalPersistenceProvider
import mx.nexuspos.client.features.offline.OfflineCatalogCache
import mx.nexuspos.client.features.offline.OfflineSaleQueue
import mx.nexuspos.client.features.offline.OfflineSyncManager
import mx.nexuspos.client.features.offline.getDefaultPersistenceProvider
import mx.nexuspos.client.features.whatsapp.WhatsAppMessageFormatter
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.catalog.presentationPromotionDiscount
import mx.nexuspos.contracts.catalog.resolvePresentationPrice
import mx.nexuspos.contracts.cash.CashMovementRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.ParkedSaleItemDto
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemRequest
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleRefundResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import java.util.UUID

class SalesViewModel(
    val apiClient: ApiClient = ApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    val persistenceProvider: LocalPersistenceProvider = getDefaultPersistenceProvider(),
    initialRegisterId: String = "caja-1"
) {
    val catalogCache = OfflineCatalogCache(persistenceProvider)
    val saleQueue = OfflineSaleQueue(persistenceProvider)
    val syncManager = OfflineSyncManager(apiClient, saleQueue, scope)

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private var activeRegisterId = initialRegisterId
    private var activeSessionServer = apiClient.baseUrl
    private var activeSessionRequest = 0L
    private val connectionCheck = Mutex()

    var currentSettings: SettingsDto? = null

    init {
        _uiState.update { it.copy(offlinePendingSalesCount = saleQueue.pendingCount()) }
        checkServerConnection()
        loadActiveSession()
        loadParkedSales()
        loadSettings()
        preloadCatalogCache()
    }

    fun preloadCatalogCache() {
        scope.launch {
            apiClient.searchProducts().onSuccess(catalogCache::saveCatalog)
        }
    }

    fun loadSettings() {
        scope.launch {
            apiClient.getSettings().onSuccess(::applySettings)
        }
    }

    fun applySettings(settings: SettingsDto) {
        currentSettings = settings
        _uiState.update { it.copy(
            maxCashierDiscountPercent = settings.maxCashierDiscountPercent,
            allowShiftHandoff = settings.commerce.allowShiftHandoff,
            cashDrawerLimitEnabled = settings.commerce.cashDrawerLimitCents != null
        ) }
    }

    fun openSaleWhatsAppDialog(sale: SaleResponse) {
        if (!allowAction("transactions.share")) return
        val phone = _uiState.value.selectedCustomer?.phone.orEmpty()
        val text = formatWhatsAppTicket(sale)
        _uiState.update {
            it.copy(
                isShowingWhatsAppDialog = true,
                whatsAppMessageText = text,
                whatsAppInitialPhone = phone,
                whatsAppDialogTitle = "Enviar Ticket Folio #${sale.folio} por WhatsApp"
            )
        }
    }

    fun closeWhatsAppDialog() {
        _uiState.update { it.copy(isShowingWhatsAppDialog = false) }
    }

    fun openOfflineSyncDialog() {
        _uiState.update { it.copy(isShowingOfflineSyncDialog = true) }
    }

    fun closeOfflineSyncDialog() {
        _uiState.update { it.copy(isShowingOfflineSyncDialog = false) }
    }

    fun triggerOfflineSync(automatic: Boolean = false) {
        if (_uiState.value.isSyncingOfflineSales) return
        _uiState.update { it.copy(isSyncingOfflineSales = true, offlineSyncMessage = "Sincronizando...") }
        syncManager.triggerSync(automatic) { result ->
            _uiState.update {
                it.copy(
                    isSyncingOfflineSales = false,
                    offlinePendingSalesCount = saleQueue.pendingCount(),
                    offlineSyncMessage = if (result.failed == 0) {
                        "¡Sincronización completada! ${result.succeeded} venta(s) enviadas."
                    } else {
                        "Sincronizadas: ${result.succeeded}, Fallidas: ${result.failed}."
                    },
                    successMessage = if (result.succeeded > 0 && result.failed == 0) {
                        "¡Sincronización exitosa! Se transmitieron ${result.succeeded} venta(s) de contingencia al servidor central."
                    } else it.successMessage
                )
            }
            if (result.succeeded > 0) {
                loadShiftSales()
                loadActiveSession()
            }
        }
    }

    fun clearSyncedOfflineSales() {
        runCatching { saleQueue.clearSynced() }.onFailure { error ->
            _uiState.update { it.copy(offlineSyncMessage = error.message ?: "No se pudo actualizar la cola local.") }
        }
        _uiState.update { it.copy(offlinePendingSalesCount = saleQueue.pendingCount()) }
    }

    fun checkServerConnection() {
        scope.launch { refreshServerConnection() }
    }

    suspend fun refreshServerConnection() {
        if (!connectionCheck.tryLock()) return
        try {
            val server = apiClient.baseUrl
            val isHealthy = apiClient.checkHealth()
            if (apiClient.baseUrl != server) return
            val wasOffline = _uiState.value.connectionState == ServerConnectionState.NO_SERVER
            _uiState.update {
                it.copy(connectionState = if (isHealthy) ServerConnectionState.CONNECTED else ServerConnectionState.NO_SERVER)
            }
            if (!isHealthy) return
            if (wasOffline) {
                preloadCatalogCache()
                loadActiveSession()
            }
            if (saleQueue.getPending(automatic = true).any { it.belongsTo(server) }) {
                triggerOfflineSync(automatic = true)
            }
        } finally {
            connectionCheck.unlock()
        }
    }

    fun loadActiveSession(registerId: String = activeRegisterId) {
        val server = apiClient.baseUrl
        val request = ++activeSessionRequest
        if (activeRegisterId != registerId || activeSessionServer != server) {
            activeRegisterId = registerId
            activeSessionServer = server
            _uiState.update { it.copy(activeSession = null) }
        }
        scope.launch {
            val res = apiClient.getCurrentSession(registerId)
            if (request != activeSessionRequest || server != apiClient.baseUrl) return@launch
            res.onSuccess { session ->
                _uiState.update { it.copy(activeSession = session) }
            }.onFailure {
                _uiState.update { it.copy(connectionState = ServerConnectionState.NO_SERVER) }
            }
        }
    }

    fun setLoginCashSession(session: CashSessionSummary) {
        activeSessionRequest++
        activeRegisterId = session.registerId
        activeSessionServer = apiClient.baseUrl
        _uiState.update { it.copy(activeSession = session, connectionState = ServerConnectionState.CONNECTED) }
    }

    fun setActiveCashier(userId: String, name: String, role: String, permissions: List<String> = emptyList()) {
        _uiState.update {
            it.copy(
                activeCashierId = userId,
                activeCashierName = name,
                activeCashierRole = role,
                activeCashierPermissions = permissions
            )
        }
    }

    fun onScannerInputChanged(value: String) {
        _uiState.update { it.copy(scannerInput = value) }
    }

    fun onScannerSubmitted() {
        val scannerState = _uiState.value
        if (scannerState.isParkingSale || scannerState.isCheckoutInProgress) return
        val input = scannerState.scannerInput.trim()
        if (input.isBlank()) return

        _uiState.update { it.copy(scannerStatus = ScannerStatus.PROCESSING) }

        // Búsqueda inmediata en caché local si estamos sin conexión al servidor central
        if (_uiState.value.connectionState == ServerConnectionState.NO_SERVER) {
            val cachedProd = catalogCache.findProductByBarcode(input) ?: catalogCache.searchProducts(input).firstOrNull()
            if (cachedProd != null) {
                val pres = cachedProd.presentations.firstOrNull { it.barcode == input } ?: cachedProd.presentations.firstOrNull()
                if (pres != null) {
                    if (cachedProd.baseUnit.equals("KG", ignoreCase = true)) {
                        val scale = scaleReadingProvider?.invoke()
                        val autoAccept = autoAcceptScaleWeightProvider?.invoke() == true
                        if (scale != null && scale.isStableWeight && autoAccept) {
                            addProductPresentation(cachedProd, pres, Quantity.fromDouble(scale.weightKg))
                            _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    scannerInput = "",
                                    isWeighingProduct = Pair(cachedProd, pres),
                                    weighingCartLineId = null,
                                    errorMessage = null
                                )
                            }
                        }
                    } else {
                        addProductPresentation(cachedProd, pres, Quantity.ONE)
                        _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                    }
                    _uiState.update { it.copy(scannerStatus = ScannerStatus.READY) }
                    return
                }
            }
        }

        scope.launch {
            try {
                val res = apiClient.searchProducts(barcode = input)
                res.onSuccess { prods ->
                    if (prods.isNotEmpty()) {
                        val exactMatches = prods.flatMap { product -> product.presentations.filter { it.barcode == input }.map { product to it } }
                        val matches = exactMatches.ifEmpty { prods.flatMap { product -> product.presentations.map { product to it } } }
                        if (matches.size == 1) confirmBarcodeChoice(matches.single())
                        else if (matches.isNotEmpty()) _uiState.update { it.copy(barcodeChoices = matches, scannerInput = "") }
                    } else {
                        // Try searching by name/query
                        val queryRes = apiClient.searchProducts(query = input)
                        queryRes.onSuccess { queryProds ->
                            if (queryProds.isNotEmpty()) {
                                val prod = queryProds.first()
                                val pres = prod.presentations.firstOrNull()
                                if (pres != null) {
                                    if (prod.baseUnit.equals("KG", ignoreCase = true)) {
                                        val scale = scaleReadingProvider?.invoke()
                                        val autoAccept = autoAcceptScaleWeightProvider?.invoke() == true
                                        if (scale != null && scale.isStableWeight && autoAccept) {
                                            addProductPresentation(prod, pres, mx.nexuspos.core.Quantity.fromDouble(scale.weightKg))
                                            _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                                        } else {
                                            _uiState.update {
                                                it.copy(
                                                    scannerInput = "",
                                                    isWeighingProduct = Pair(prod, pres),
                                                    weighingCartLineId = null,
                                                    errorMessage = null
                                                )
                                            }
                                        }
                                    } else {
                                        addProductPresentation(prod, pres, Quantity.ONE)
                                        _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                                    }
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        errorMessage = "Producto con código '$input' no encontrado",
                                        scannerInput = ""
                                    )
                                }
                            }
                        }
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(connectionState = ServerConnectionState.NO_SERVER) }
                    // Respaldo de contingencia: buscar en caché local
                    val cachedProd = catalogCache.findProductByBarcode(input) ?: catalogCache.searchProducts(input).firstOrNull()
                    if (cachedProd != null) {
                        val pres = cachedProd.presentations.firstOrNull { it.barcode == input } ?: cachedProd.presentations.firstOrNull()
                        if (pres != null) {
                            if (cachedProd.baseUnit.equals("KG", ignoreCase = true)) {
                                val scale = scaleReadingProvider?.invoke()
                                val autoAccept = autoAcceptScaleWeightProvider?.invoke() == true
                                if (scale != null && scale.isStableWeight && autoAccept) {
                                    addProductPresentation(cachedProd, pres, Quantity.fromDouble(scale.weightKg))
                                    _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                                } else {
                                    _uiState.update {
                                        it.copy(
                                            scannerInput = "",
                                            isWeighingProduct = Pair(cachedProd, pres),
                                            weighingCartLineId = null,
                                            errorMessage = null
                                        )
                                    }
                                }
                            } else {
                                addProductPresentation(cachedProd, pres, Quantity.ONE)
                                _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                            }
                            return@launch
                        }
                    }
                    _uiState.update {
                        it.copy(
                            errorMessage = "Sin conexión al servidor central y producto '$input' no encontrado en caché local"
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(scannerStatus = ScannerStatus.READY) }
            }
        }
    }

    fun confirmBarcodeChoice(choice: Pair<ProductDto, ProductPresentationDto>?) {
        _uiState.update { it.copy(barcodeChoices = emptyList(), scannerInput = "", errorMessage = null) }
        val (product, presentation) = choice ?: return
        val unit = ProductUnit.fromCode(product.baseUnit)
        if (unit == ProductUnit.KILOGRAM) {
            val scale = scaleReadingProvider?.invoke()
            if (scale != null && scale.isStableWeight && autoAcceptScaleWeightProvider?.invoke() == true) {
                addProductPresentation(product, presentation, Quantity.fromDouble(scale.weightKg))
            } else _uiState.update { it.copy(isWeighingProduct = choice, weighingCartLineId = null) }
        } else addProductPresentation(product, presentation, Quantity.ONE)
    }

    fun canPerform(permission: String): Boolean {
        val state = _uiState.value
        return permission in state.activeCashierPermissions ||
            (state.activeCashierPermissions.isEmpty() && !state.activeCashierRole.startsWith("ROLE-"))
    }

    private fun allowAction(permission: String): Boolean {
        if (canPerform(permission)) return true
        setErrorMessage("Tu rol no permite realizar esta acción.")
        return false
    }

    fun addProductPresentation(prod: ProductDto, pres: ProductPresentationDto, qty: Quantity) {
        if (!allowAction("cart.add")) return
        val unit = ProductUnit.fromCode(prod.baseUnit)
        val unitPrice = Money.fromCents(pres.priceFor(qty.millis))
        val lineTotal = PricingRules.calculateLineTotal(unitPrice, qty)

        val itemDescription = if (
            pres.name.isNotBlank() &&
            pres.name != "Pieza" &&
            pres.name != "Base" &&
            pres.name != prod.name &&
            pres.factorMillis != 1000L
        ) {
            "${prod.name} (${pres.name})"
        } else {
            prod.name
        }

        _uiState.update { state ->
            val existingIndex = state.cartLines.indexOfFirst { it.presentationId == pres.id && it.priceListLevel == null && it.manualPriceCents == null }
            val updatedLines = if (existingIndex >= 0 && unit == ProductUnit.PIECE) {
                state.cartLines.mapIndexed { idx, line ->
                    if (idx == existingIndex) {
                        val newQty = line.quantity + qty
                        line.copy(publicPriceCents = pres.priceCents, priceTiers = pres.priceTiers, kitComponents = prod.kitComponents, promotion = pres.promotion)
                            .pricedAt(newQty, fixedDiscount = line.fixedDiscount())
                    } else line
                }
            } else {
                state.cartLines + CartLine(
                    id = "cart-" + UUID.randomUUID().toString().take(6),
                    productId = prod.id,
                    presentationId = pres.id,
                    barcode = pres.barcode,
                    name = itemDescription,
                    category = prod.departmentName ?: "General",
                    unit = unit,
                    quantity = qty,
                    unitPrice = unitPrice,
                    taxRate = prod.taxRate,
                    total = lineTotal,
                    currentStockMillis = if (prod.usesInventory) prod.currentStockMillis else null,
                    isWeighed = unit == ProductUnit.KILOGRAM,
                    publicPriceCents = pres.priceCents,
                    priceTiers = pres.priceTiers,
                    listPrices = pres.listPrices,
                    kitComponents = prod.kitComponents,
                    promotion = pres.promotion
                ).pricedAt(qty, 0)
            }
            recalculateTotals(state.copy(cartLines = updatedLines))
        }
    }

    fun confirmWeighedProduct(qty: Quantity) {
        val state = _uiState.value
        val (product, presentation) = state.isWeighingProduct ?: return
        val lineId = state.weighingCartLineId
        if (lineId == null) {
            addProductPresentation(product, presentation, qty)
        } else {
            updateCartLineQuantity(lineId, qty)
        }
        dismissWeighingDialog()
    }

    fun dismissWeighingDialog() {
        _uiState.update { it.copy(isWeighingProduct = null, weighingCartLineId = null) }
    }

    fun updateCartLineQuantity(lineId: String, newQuantity: Quantity) {
        if (newQuantity.millis <= 0L) return
        val current = _uiState.value.cartLines.firstOrNull { it.id == lineId } ?: return
        if (newQuantity > current.quantity && !allowAction("cart.increase")) return
        if (newQuantity < current.quantity && !allowAction("cart.decrease")) return
        if (newQuantity.millis % 1000 != 0L &&
            _uiState.value.cartLines.any { it.id == lineId && !it.kitComponents.isNullOrEmpty() }
        ) {
            setErrorMessage("Los kits se venden en cantidades enteras.")
            return
        }
        _uiState.update { state ->
            var stockWarning: String? = null
            val updatedLines = state.cartLines.map { line ->
                if (line.id == lineId) {
                    if (line.currentStockMillis != null && newQuantity.millis > line.currentStockMillis) {
                        val stockUnits = if (line.isWeighed || line.unit == ProductUnit.KILOGRAM) {
                            "${line.currentStockMillis / 1000.0} kg"
                        } else {
                            "${line.currentStockMillis / 1000L} pza"
                        }
                        stockWarning = "Atención: La cantidad excede la existencia disponible ($stockUnits) para '${line.name}'."
                    }
                    line.pricedAt(newQuantity)
                } else line
            }
            recalculateTotals(
                state.copy(
                    cartLines = updatedLines,
                    errorMessage = stockWarning ?: state.errorMessage
                )
            )
        }
    }

    private fun CartLine.fixedDiscount(): Long = fixedDiscountCents ?: (
        discountCents - promotionDiscountCents - PricingRules.calculateLineTotalWithDiscount(unitPrice, quantity, discountPercent).second.cents
    ).coerceAtLeast(0)

    private fun CartLine.pricedAt(newQuantity: Quantity, fixedDiscount: Long = fixedDiscount()): CartLine {
        val price = Money(
            manualPriceCents ?: priceListLevel?.let { listPrices[it] ?: unitPrice.cents }
                ?: resolvePresentationPrice(publicPriceCents ?: unitPrice.cents, priceTiers, newQuantity.millis)
        )
        val automatic = if (unit == ProductUnit.PIECE && manualPriceCents == null && priceListLevel == null &&
            discountPercent == 0.0 && fixedDiscount == 0L
        ) presentationPromotionDiscount(promotion, System.currentTimeMillis(), newQuantity.millis, publicPriceCents ?: price.cents, price.cents)
        else 0L
        val (total, discount) = PricingRules.calculateLineTotalWithDiscount(
            price, newQuantity, discountPercent, if (automatic > 0L) automatic else fixedDiscount
        )
        return copy(
            quantity = newQuantity, unitPrice = price, total = total, discountCents = discount.cents,
            fixedDiscountCents = fixedDiscount, promotionDiscountCents = automatic
        )
    }

    suspend fun activePriceLists() = apiClient.priceLists().map { lists ->
        lists.filter { it.enabled && "sales.price.${it.level}" in _uiState.value.activeCashierPermissions }
    }

    fun chooseLinePrice(lineId: String, level: Int?) {
        _uiState.update { state ->
            val updated = state.cartLines.map { line ->
                if (line.id != lineId || (level != null && (level !in line.listPrices || "sales.price.$level" !in state.activeCashierPermissions))) {
                    line
                } else line.copy(priceListLevel = level, manualPriceCents = null).pricedAt(line.quantity, line.fixedDiscount())
            }
            recalculateTotals(state.copy(cartLines = updated))
        }
    }

    fun removeCartLine(lineId: String) {
        if (!allowAction("cart.remove")) return
        _uiState.update { state ->
            val updated = state.cartLines.filterNot { it.id == lineId }
            recalculateTotals(state.copy(cartLines = updated))
        }
    }

    fun clearCart() {
        _uiState.update { recalculateTotals(it.copy(cartLines = emptyList())) }
    }

    fun selectCustomer(customer: CustomerDto?) {
        _uiState.update { state ->
            val updatedTickets = state.tickets.map { t ->
                if (t.id == state.activeTicketId) {
                    t.copy(selectedCustomer = customer)
                } else t
            }
            state.copy(
                selectedCustomer = customer,
                tickets = updatedTickets,
                isShowingCustomerDialog = false
            )
        }
    }

    // ==========================================
    // MULTI-TICKET MANAGEMENT
    // ==========================================

    fun newTicket() {
        if (!allowAction("cart.create")) return
        _uiState.update { state ->
            val newId = "ticket-${UUID.randomUUID().toString().take(6)}"
            val newTicket = TicketState(id = newId, title = "Ticket ${state.tickets.size + 1}")
            activateTicket(state.copy(tickets = state.tickets + newTicket), newTicket)
        }
    }

    fun selectTicket(ticketId: String) {
        _uiState.update { state ->
            if (state.activeTicketId == ticketId) return@update state
            val target = state.tickets.firstOrNull { it.id == ticketId } ?: return@update state
            activateTicket(state, target)
        }
    }

    private fun activateTicket(state: SalesUiState, ticket: TicketState): SalesUiState = state.copy(
        activeTicketId = ticket.id,
        cartLines = ticket.cartLines,
        subtotal = ticket.subtotal,
        tax = ticket.tax,
        total = ticket.total,
        discount = Money(ticket.cartLines.sumOf { it.discountCents }),
        selectedCustomer = ticket.selectedCustomer,
        activeTicketNote = ticket.note,
        selectedCartLineId = null,
        scannerInput = ""
    )

    fun cycleNextTicket() {
        val state = _uiState.value
        if (state.tickets.size <= 1) return
        val currentIndex = state.tickets.indexOfFirst { it.id == state.activeTicketId }
        val nextIndex = if (currentIndex == -1 || currentIndex >= state.tickets.size - 1) 0 else currentIndex + 1
        val targetTicketId = state.tickets[nextIndex].id
        selectTicket(targetTicketId)
    }

    fun holdTicketAndNew() {
        newTicket()
        _uiState.update { it.copy(successMessage = "Ticket anterior puesto en espera. Nuevo ticket listo.") }
    }

    fun setTicketNote(note: String) {
        _uiState.update { state ->
            val updatedTickets = state.tickets.map { t ->
                if (t.id == state.activeTicketId) t.copy(note = note) else t
            }
            state.copy(activeTicketNote = note, tickets = updatedTickets)
        }
    }

    fun showTicketNoteDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingTicketNoteDialog = show) }
    }

    fun renameTicket(ticketId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        _uiState.update { state ->
            val updated = state.tickets.map { t ->
                if (t.id == ticketId) t.copy(title = trimmed) else t
            }
            state.copy(tickets = updated)
        }
    }

    fun showRenameTicketDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingRenameTicketDialog = show) }
    }

    fun showPriceCheckerDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingPriceCheckerDialog = show) }
    }

    fun recordManualDrawerOpen(cashierName: String = _uiState.value.activeCashierName) {
        val msg = "✓ Cajón de dinero abierto (Registrado en bitácora - Cajero: $cashierName)"
        _uiState.update { it.copy(auditToastMessage = msg) }
        scope.launch {
            kotlinx.coroutines.delay(4000)
            _uiState.update { if (it.auditToastMessage == msg) it.copy(auditToastMessage = null) else it }
        }
    }

    fun clearAuditToast() {
        _uiState.update { it.copy(auditToastMessage = null) }
    }

    fun updateCartLinePrice(lineId: String, newPrice: Money) {
        if (newPrice.cents < 0 || !allowAction("sales.price.manual")) return
        _uiState.update { state ->
            val lines = state.cartLines.map { line ->
                if (line.id == lineId && line.presentationId != null) {
                    line.copy(priceListLevel = null, manualPriceCents = newPrice.cents).pricedAt(line.quantity, line.fixedDiscount())
                } else line
            }
            recalculateTotals(state.copy(cartLines = lines))
        }
    }

    fun reweighCartLine(line: CartLine) {
        val reading = scaleReadingProvider?.invoke()
        if (reading != null && reading.isStableWeight) {
            updateCartLineQuantity(line.id, Quantity.fromDouble(reading.weightKg))
            _uiState.update { it.copy(successMessage = "Peso actualizado desde báscula RS-232: ${reading.weightText}") }
        } else {
            // Si la báscula no tiene peso estable o está desconectada, abrir diálogo de pesaje
            // para permitir pesaje guiado o captura manual sin alterar la cantidad existente si se cancela
            val prod = ProductDto(
                id = line.productId ?: "custom",
                sku = line.barcode,
                name = line.name,
                departmentName = line.category,
                baseUnit = "KG"
            )
            val pres = ProductPresentationDto(
                id = line.presentationId ?: "pres-custom",
                productId = prod.id,
                barcode = line.barcode,
                name = line.name,
                factorMillis = 1000L,
                priceCents = line.publicPriceCents ?: line.unitPrice.cents,
                priceTiers = line.priceTiers
            )
            _uiState.update {
                it.copy(
                    isWeighingProduct = Pair(prod, pres),
                    weighingCartLineId = line.id,
                    errorMessage = if (reading == null || reading.status == mx.nexuspos.client.features.header.ScaleStatus.DISCONNECTED)
                        "Báscula no detectada. Verifique conexión serial o introduzca peso manual."
                    else null
                )
            }
        }
    }

    fun requestCloseTicket(ticketId: String) {
        val ticket = _uiState.value.tickets.firstOrNull { it.id == ticketId } ?: return
        if (ticket.cartLines.isEmpty()) {
            closeTicket(ticketId)
        } else {
            _uiState.update {
                it.copy(
                    isShowingCancelTicketDialog = true,
                    ticketPendingCancelId = ticketId
                )
            }
        }
    }

    fun dismissCancelTicketDialog() {
        _uiState.update { it.copy(isShowingCancelTicketDialog = false, ticketPendingCancelId = null) }
    }

    fun confirmCancelTicket(ticketId: String, reason: String, supervisorUserId: String? = null) {
        closeTicket(ticketId)
        _uiState.update {
            it.copy(
                isShowingCancelTicketDialog = false,
                ticketPendingCancelId = null,
                auditToastMessage = "✓ Venta cancelada (Auditado - Motivo: $reason)"
            )
        }
    }

    // ==========================================
    // PERSISTENT PARKED SALES (BASE DE DATOS)
    // ==========================================

    fun loadParkedSales(registerId: String? = null) {
        scope.launch {
            _uiState.update { it.copy(isLoadingParkedSales = true) }
            val res = apiClient.getParkedSales(registerId)
            res.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        parkedSales = list,
                        parkedSalesCount = list.size,
                        isLoadingParkedSales = false,
                        selectedParkedSale = it.selectedParkedSale?.let { sel -> list.firstOrNull { p -> p.id == sel.id } } ?: list.firstOrNull()
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingParkedSales = false) }
            }
        }
    }

    fun openParkSaleDialog() {
        val state = _uiState.value
        if (state.cartLines.isEmpty()) {
            openParkedSalesListDialog()
        } else {
            _uiState.update { it.copy(isShowingParkSaleDialog = true, errorMessage = null) }
        }
    }

    fun closeParkSaleDialog() {
        _uiState.update { if (it.isParkingSale) it else it.copy(isShowingParkSaleDialog = false) }
    }

    fun openParkedSalesListDialog() {
        loadParkedSales()
        _uiState.update { it.copy(isShowingParkedSalesListDialog = true) }
    }

    fun closeParkedSalesListDialog() {
        _uiState.update { it.copy(isShowingParkedSalesListDialog = false, selectedParkedSale = null) }
    }

    fun selectParkedSale(parked: ParkedSaleResponse?) {
        _uiState.update { it.copy(selectedParkedSale = parked) }
    }

    fun confirmParkCurrentSale(label: String) {
        val state = _uiState.getAndUpdate { current ->
            if (current.isParkingSale || current.cartLines.isEmpty()) current
            else current.copy(isParkingSale = true, errorMessage = null)
        }
        if (state.isParkingSale) return
        if (state.cartLines.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "El carrito está vacío. Agregue productos antes de poner en espera.") }
            return
        }

        val effectiveLabel = label.trim().ifBlank {
            val activeTicket = state.tickets.firstOrNull { it.id == state.activeTicketId }
            state.selectedCustomer?.name?.takeIf { it.isNotBlank() }
                ?: activeTicket?.title ?: "Ticket ${state.tickets.size}"
        }.take(128)

        val itemsDto = state.cartLines.map { line ->
            ParkedSaleItemDto(
                id = line.id,
                productId = line.productId,
                presentationId = line.presentationId,
                barcode = line.barcode,
                name = line.name,
                unitCode = line.unit.code,
                quantityMillis = line.quantity.millis,
                unitPriceCents = line.unitPrice.cents,
                taxRate = line.taxRate,
                totalCents = line.total.cents,
                isWeighed = line.isWeighed,
                discountCents = line.discountCents,
                discountPercent = line.discountPercent,
                fixedDiscountCents = line.fixedDiscount(),
                publicPriceCents = line.publicPriceCents,
                priceTiers = line.priceTiers,
                promotion = line.promotion,
                kitComponents = line.kitComponents,
                listPrices = line.listPrices,
                priceListLevel = line.priceListLevel,
                manualPriceCents = line.manualPriceCents
            )
        }

        val req = ParkSaleRequest(
            customLabel = effectiveLabel,
            cashierUserId = state.activeCashierId,
            cashierName = state.activeCashierName,
            registerId = state.activeSession?.registerId ?: activeRegisterId,
            customerId = state.selectedCustomer?.id,
            customerName = state.selectedCustomer?.name,
            items = itemsDto,
            totalCents = state.total.cents,
            note = state.activeTicketNote
        )

        scope.launch {
            apiClient.parkSale(req).onSuccess { parked ->
                _uiState.update { current ->
                    val updated = removeTicket(current, state.activeTicketId)
                    updated.copy(
                        isParkingSale = false,
                        isShowingParkSaleDialog = false,
                        successMessage = "Ticket '${parked.customLabel}' guardado en espera (${parked.itemsCount} productos).",
                        errorMessage = if (parked.note != req.note) {
                            "El servidor no guardó la nota del ticket en espera. Nota: ${req.note}"
                        } else null
                    )
                }
                loadParkedSales()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isParkingSale = false, errorMessage = "Error al poner ticket en espera: ${err.message}")
                }
            }
        }
    }

    fun retrieveParkedSale(parkedSale: ParkedSaleResponse) {
        val state = _uiState.value
        val req = RetrieveParkedSaleRequest(
            retrievedByUserId = state.activeCashierId,
            registerId = state.activeSession?.registerId ?: activeRegisterId
        )

        scope.launch {
            apiClient.retrieveParkedSale(parkedSale.id, req).onSuccess { retrieved ->
                val restoredLines = retrieved.items.map { item ->
                    CartLine(
                        id = item.id.ifBlank { "cart-${UUID.randomUUID().toString().take(6)}" },
                        productId = item.productId,
                        presentationId = item.presentationId,
                        barcode = item.barcode,
                        name = item.name,
                        unit = ProductUnit.fromCode(item.unitCode),
                        quantity = Quantity(item.quantityMillis),
                        unitPrice = Money.fromCents(item.unitPriceCents),
                        taxRate = item.taxRate,
                        total = Money.fromCents(item.totalCents),
                        isWeighed = item.isWeighed,
                        discountCents = item.discountCents,
                        discountPercent = item.discountPercent,
                        publicPriceCents = item.publicPriceCents,
                        priceTiers = item.priceTiers,
                        fixedDiscountCents = item.fixedDiscountCents,
                        kitComponents = item.kitComponents,
                        listPrices = item.listPrices,
                        priceListLevel = item.priceListLevel,
                        manualPriceCents = item.manualPriceCents,
                        promotion = item.promotion
                    ).let { line -> if (line.publicPriceCents == null) line else line.pricedAt(line.quantity) }
                }

                val customerResult = retrieved.customerId?.let { apiClient.getCustomer(it) }
                val recoveredCustomer = customerResult?.getOrNull()?.takeIf { it.isActive }
                _uiState.update { current ->
                    val active = current.tickets.first { it.id == current.activeTicketId }
                    val reuseActive = active.cartLines.isEmpty() && active.note.isBlank() && active.selectedCustomer == null
                    val restored = TicketState(
                        id = if (reuseActive) active.id else "ticket-${UUID.randomUUID().toString().take(6)}",
                        title = retrieved.customLabel,
                        cartLines = restoredLines,
                        selectedCustomer = recoveredCustomer,
                        note = retrieved.note
                    )
                    val tickets = if (reuseActive) {
                        current.tickets.map { if (it.id == active.id) restored else it }
                    } else {
                        current.tickets + restored
                    }
                    recalculateTotals(activateTicket(current.copy(tickets = tickets), restored)).copy(
                        isShowingParkedSalesListDialog = false,
                        selectedParkedSale = null,
                        successMessage = "Ticket '${retrieved.customLabel}' recuperado.",
                        errorMessage = if (retrieved.customerId != null && recoveredCustomer == null) {
                            "No se pudo recuperar el cliente del ticket. Asígnalo antes de cobrar."
                        } else null
                    )
                }
                loadParkedSales()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = "Error al recuperar ticket: ${err.message}")
                }
            }
        }
    }

    fun discardParkedSale(parkedSaleId: String) {
        val state = _uiState.value
        scope.launch {
            apiClient.discardParkedSale(parkedSaleId, userId = state.activeCashierId).onSuccess {
                _uiState.update { it.copy(successMessage = "Ticket en espera descartado correctamente.") }
                loadParkedSales()
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = "Error al descartar ticket: ${err.message}") }
            }
        }
    }

    var receiptFormatter: ((SaleResponse, Boolean) -> String)? = null
    var onSaleCompleted: ((SaleResponse) -> Unit)? = null
    var onOpenCashDrawer: (() -> Unit)? = null
    var onPrintPhysicalReceipt: ((SaleResponse) -> Unit)? = null
    var onCashSessionClosed: ((CashSessionSummary) -> Unit)? = null
    var scaleReadingProvider: (() -> ScaleReading)? = null
    var autoAcceptScaleWeightProvider: (() -> Boolean)? = null

    fun openCashDrawer(hardware: (() -> Unit)? = null) {
        if (!allowAction("cash.drawer.open")) return
        (hardware ?: onOpenCashDrawer)?.invoke()
        _uiState.update { it.copy(successMessage = "Comando de apertura enviado al cajón de dinero.") }
    }

    fun printPhysicalTicket(sale: SaleResponse) {
        if (!allowAction("transactions.reprint")) return
        onPrintPhysicalReceipt?.invoke(sale)
        _uiState.update {
            it.copy(
                reprintTicketPreview = null,
                reprintTicketSale = null,
                successMessage = "Comando de impresión para folio #${sale.folio} enviado a la impresora física."
            )
        }
    }

    fun reprintLastTicket() {
        val lastSale = _uiState.value.lastCompletedSale
        if (lastSale != null) {
            reprintTicket(lastSale)
        } else {
            _uiState.update { it.copy(errorMessage = "No hay ninguna venta completada recientemente para reimprimir.") }
        }
    }

    fun openDailySalesAndReturns() {
        _uiState.update { it.copy(isShowingSalesHistory = true) }
        loadShiftSales()
    }

    fun closeSalesHistoryDialog() {
        _uiState.update { it.copy(isShowingSalesHistory = false, selectedShiftSale = null) }
    }

    fun loadShiftSales(sessionId: String? = _uiState.value.activeSession?.sessionId) {
        _uiState.update { it.copy(isLoadingShiftSales = true) }
        scope.launch {
            val res = apiClient.getSales(sessionId = sessionId, limit = 200)
            res.onSuccess { sales ->
                _uiState.update {
                    it.copy(
                        shiftSales = sales,
                        selectedShiftSale = it.selectedShiftSale?.let { sel -> sales.firstOrNull { s -> s.id == sel.id } } ?: sales.firstOrNull(),
                        isLoadingShiftSales = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingShiftSales = false,
                        errorMessage = "Error al cargar ventas del turno: ${err.message}"
                    )
                }
            }
        }
    }

    fun selectShiftSale(sale: SaleResponse?) {
        _uiState.update { it.copy(selectedShiftSale = sale) }
    }

    fun onSalesSearchFolioChanged(query: String) {
        _uiState.update { it.copy(salesHistorySearchFolio = query) }
    }

    fun onSalesStatusFilterChanged(status: String?) {
        _uiState.update { it.copy(salesHistoryStatusFilter = status) }
    }

    fun formatReprint(sale: SaleResponse): String =
        receiptFormatter?.invoke(sale, true) ?: defaultFormatReceipt(sale, true)

    fun formatWhatsAppTicket(sale: SaleResponse): String =
        WhatsAppMessageFormatter.formatSaleTicket(sale, currentSettings)

    suspend fun cancelShiftSale(sale: SaleResponse, reason: String, supervisorPin: String?): Result<SaleResponse> {
        if (!supervisorPin.isNullOrBlank() && apiClient.sessionToken != null) {
            return apiClient.cancelSaleWithPin(sale.id, reason, supervisorPin).onSuccess {
                updateShiftSale(it)
                loadActiveSession()
            }
        }
        if (_uiState.value.activeCashierRole == "CASHIER" && supervisorPin.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Se requiere autorización de encargado."))
        }
        val supervisorId = if (supervisorPin != null) {
            val auth = apiClient.authorizeSupervisor(supervisorPin, action = "CANCEL_SALE")
                .getOrElse { return Result.failure(it) }
            if (!auth.authorized || auth.supervisorId.isBlank()) {
                return Result.failure(IllegalArgumentException("PIN incorrecto o sin permisos de encargado."))
            }
            auth.supervisorId
        } else null

        return apiClient.cancelSale(sale.id, reason, supervisorId).onSuccess { cancelled ->
            updateShiftSale(cancelled)
            loadActiveSession()
        }
    }

    suspend fun refundShiftSale(sale: SaleResponse, request: CreateSaleRefundRequest): Result<SaleRefundResponse> =
        apiClient.refundSale(sale.id, request).onSuccess { refund ->
            val current = _uiState.value.shiftSales.firstOrNull { it.id == sale.id } ?: sale
            if (current.refunds.none { it.id == refund.id }) {
                val quantities = refund.lines.groupingBy { it.saleLineId }.fold(0L) { total, line ->
                    total + line.quantityMillis
                }
                val lines = current.lines.map {
                    it.copy(refundedQuantityMillis = it.refundedQuantityMillis + (quantities[it.id] ?: 0L))
                }
                updateShiftSale(current.copy(
                    status = if (lines.all { it.returnableQuantityMillis == 0L }) "REFUNDED" else "PARTIALLY_REFUNDED",
                    lines = lines,
                    refunds = listOf(refund) + current.refunds
                ))
            }
            onPrintSaleRefund?.invoke(refund)
            loadShiftSales()
            loadActiveSession()
        }

    private fun updateShiftSale(sale: SaleResponse) {
        _uiState.update { state ->
            state.copy(
                shiftSales = state.shiftSales.map { if (it.id == sale.id) sale else it },
                selectedShiftSale = if (state.selectedShiftSale?.id == sale.id) sale else state.selectedShiftSale,
                lastCompletedSale = if (state.lastCompletedSale?.id == sale.id) sale else state.lastCompletedSale
            )
        }
    }

    fun reprintTicket(sale: SaleResponse) {
        if (!allowAction("transactions.reprint")) return
        val formatted = formatReprint(sale)
        _uiState.update {
            it.copy(
                reprintTicketPreview = formatted,
                reprintTicketSale = sale,
                successMessage = "Ticket #${sale.folio} listo para reimpresión (*** COPIA DE REIMPRESIÓN ***)"
            )
        }
    }

    fun closeReprintPreview() {
        _uiState.update { it.copy(reprintTicketPreview = null, reprintTicketSale = null) }
    }

    fun startCancelSaleFlow(sale: SaleResponse) {
        if (sale.status == "CANCELLED") {
            _uiState.update { it.copy(errorMessage = "El ticket #${sale.folio} ya se encuentra cancelado.") }
            return
        }
        _uiState.update { it.copy(isShowingCancelReasonDialog = true, salePendingCancel = sale) }
    }

    fun dismissCancelReasonDialog() {
        _uiState.update { it.copy(isShowingCancelReasonDialog = false, salePendingCancel = null) }
    }

    fun executeCancelSale(saleId: String, reason: String, supervisorUserId: String? = null) {
        scope.launch {
            val res = apiClient.cancelSale(saleId, reason, supervisorUserId)
            res.onSuccess { cancelledSale ->
                _uiState.update { state ->
                    val updatedList = state.shiftSales.map { if (it.id == cancelledSale.id) cancelledSale else it }
                    state.copy(
                        shiftSales = updatedList,
                        selectedShiftSale = if (state.selectedShiftSale?.id == cancelledSale.id) cancelledSale else state.selectedShiftSale,
                        isShowingCancelReasonDialog = false,
                        salePendingCancel = null,
                        successMessage = "Venta #${cancelledSale.folio} cancelada exitosamente. Stock y caja revertidos."
                    )
                }
                loadActiveSession()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isShowingCancelReasonDialog = false,
                        errorMessage = "Error al cancelar la venta: ${err.message}"
                    )
                }
            }
        }
    }

    var onRefundSupervisorRequired: ((SupervisorAction.RefundSale) -> Unit)? = null
    var onPrintSaleRefund: ((refund: SaleRefundResponse) -> Unit)? = null

    fun promptRefundSale(sale: SaleResponse) {
        if (sale.status == "CANCELLED") {
            _uiState.update { it.copy(errorMessage = "No se puede devolver mercancía de un ticket cancelado.") }
            return
        }
        if (sale.status == "REFUNDED") {
            _uiState.update { it.copy(errorMessage = "El ticket #${sale.folio} ya ha sido devuelto en su totalidad.") }
            return
        }
        val returnableCount = sale.lines.sumOf { it.returnableQuantityMillis }
        if (returnableCount <= 0L) {
            _uiState.update { it.copy(errorMessage = "Todas las partidas del ticket #${sale.folio} ya han sido devueltas.") }
            return
        }
        _uiState.update { it.copy(isShowingRefundDialog = true, salePendingRefund = sale) }
    }

    fun dismissRefundDialog() {
        _uiState.update { it.copy(isShowingRefundDialog = false, salePendingRefund = null) }
    }

    fun executeRefundSale(saleId: String, request: CreateSaleRefundRequest, isSupervisorApproved: Boolean = false) {
        val state = _uiState.value
        val isCashier = state.activeCashierRole == "CASHIER"

        if (isCashier && !isSupervisorApproved) {
            dismissRefundDialog()
            onRefundSupervisorRequired?.invoke(
                SupervisorAction.RefundSale(
                    saleId = saleId,
                    folio = state.salePendingRefund?.folio ?: 0L,
                    request = request
                )
            )
            return
        }

        scope.launch {
            val res = apiClient.refundSale(saleId, request)
            res.onSuccess { refund ->
                _uiState.update { s ->
                    s.copy(
                        isShowingRefundDialog = false,
                        salePendingRefund = null,
                        successMessage = "Devolución Nota #${refund.folio} procesada exitosamente."
                    )
                }
                onPrintSaleRefund?.invoke(refund)
                loadShiftSales()
                loadActiveSession()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isShowingRefundDialog = false,
                        salePendingRefund = null,
                        errorMessage = "Error al procesar devolución: ${err.message}"
                    )
                }
            }
        }
    }

    private fun defaultFormatReceipt(sale: SaleResponse, isReprint: Boolean = false): String {
        val sb = StringBuilder()
        val sep = "=".repeat(40)
        val thinSep = "-".repeat(40)
        sb.appendLine("           NEXUS POS           ")
        sb.appendLine("     Abarrotes & Miscelánea    ")
        sb.appendLine(sep)
        if (isReprint) {
            sb.appendLine("   *** COPIA DE REIMPRESIÓN ***   ")
            sb.appendLine(thinSep)
        }
        sb.appendLine("Folio: #${sale.folio}")
        sb.appendLine("Cajero: ${sale.cashierName}")
        if (sale.customerName != null) {
            sb.appendLine("Cliente: ${sale.customerName}")
        }
        sb.appendLine("Estatus: ${sale.status}")
        sb.appendLine(thinSep)
        for (line in sale.lines) {
            val qty = (line.quantityMillis / 1000.0).toString().removeSuffix(".0")
            sb.appendLine("$qty x ${line.description}")
            sb.appendLine("         ${Money(line.totalCents).format()}")
        }
        sb.appendLine(sep)
        sb.appendLine("SUBTOTAL: ${Money(sale.subtotalCents).format()}")
        sb.appendLine("IVA:      ${Money(sale.taxCents).format()}")
        sb.appendLine("TOTAL:    ${Money(sale.totalCents).format()}")
        sb.appendLine(thinSep)
        if (sale.payments.size > 1) {
            sb.appendLine("       -- FORMAS DE PAGO --      ")
        }
        for (p in sale.payments) {
            val refStr = if (p.reference.isNotBlank()) " [${p.reference}]" else ""
            when (p.method) {
                "CASH" -> {
                    sb.appendLine("Pago (Efectivo): ${Money(p.amountCents).format()}")
                    if (p.receivedCents > 0L) {
                        sb.appendLine("  Recibido: ${Money(p.receivedCents).format()}")
                        sb.appendLine("  Cambio:   ${Money(p.changeCents).format()}")
                    }
                }
                "CARD" -> sb.appendLine("Pago (Tarjeta$refStr): ${Money(p.amountCents).format()}")
                "TRANSFER" -> sb.appendLine("Pago (Transf$refStr): ${Money(p.amountCents).format()}")
                "CREDIT" -> {
                    sb.appendLine("Pago (A Crédito): ${Money(p.amountCents).format()}")
                    val remCredit = sale.customerRemainingCreditCents
                    if (remCredit != null) {
                        sb.appendLine("  Crédito Restante: ${Money(remCredit).format()}")
                    }
                }
                else -> sb.appendLine("Pago (${p.method}$refStr): ${Money(p.amountCents).format()}")
            }
        }
        sb.appendLine(sep)
        sb.appendLine("     ¡Gracias por su compra!    ")
        return sb.toString()
    }

    fun closeTicket(ticketId: String) {
        _uiState.update { removeTicket(it, ticketId) }
    }

    private fun removeTicket(state: SalesUiState, ticketId: String): SalesUiState {
        if (state.tickets.none { it.id == ticketId }) return state
        val remaining = state.tickets.filterNot { it.id == ticketId }
        if (state.activeTicketId != ticketId) return state.copy(tickets = remaining)
        val next = remaining.lastOrNull() ?: TicketState(id = ticketId)
        return activateTicket(state.copy(tickets = remaining.ifEmpty { listOf(next) }), next)
    }

    // ==========================================
    // SELECTION & OPERATIONS ON CART LINES
    // ==========================================

    fun selectCartLine(lineId: String?) {
        _uiState.update { it.copy(selectedCartLineId = if (it.selectedCartLineId == lineId) null else lineId) }
    }

    fun deleteActiveCartLine() {
        val state = _uiState.value
        val lineToDelete = state.selectedCartLineId ?: state.cartLines.lastOrNull()?.id ?: return
        removeCartLine(lineToDelete)
        _uiState.update { it.copy(selectedCartLineId = null) }
    }

    fun onApplyWholesalePrice() {
        _uiState.update { state ->
            val targetId = state.selectedCartLineId ?: state.cartLines.lastOrNull()?.id
            if (targetId == null) {
                state.copy(errorMessage = "Seleccione un artículo para aplicar precio de mayoreo.")
            } else {
                val updatedLines = state.cartLines.map { line ->
                    if (line.id == targetId) {
                        // Descuento de mayoreo del 10%
                        val discountedUnitPrice = Money.fromCents((line.unitPrice.cents * 0.90).toLong())
                        val newTotal = PricingRules.calculateLineTotal(discountedUnitPrice, line.quantity)
                        line.copy(unitPrice = discountedUnitPrice, total = newTotal)
                    } else line
                }
                recalculateTotals(state.copy(cartLines = updatedLines, successMessage = "Precio de mayoreo aplicado."))
            }
        }
    }

    fun addGenericProduct(name: String, priceCents: Long, qty: Quantity = Quantity.ONE) {
        if (currentSettings?.commerce?.quickSaleEnabled == false || !allowAction("sales.quick")) return
        val unitPrice = Money.fromCents(priceCents)
        val lineTotal = PricingRules.calculateLineTotal(unitPrice, qty)
        _uiState.update { state ->
            val genericLine = CartLine(
                id = "cart-gen-" + UUID.randomUUID().toString().take(6),
                productId = null,
                presentationId = null,
                barcode = "VAR-GEN",
                name = name.ifBlank { "Artículo Varios" },
                unit = ProductUnit.PIECE,
                quantity = qty,
                unitPrice = unitPrice,
                taxRate = 0.0,
                total = lineTotal
            )
            recalculateTotals(state.copy(
                cartLines = state.cartLines + genericLine,
                isShowingGenericProductDialog = false,
                successMessage = "Artículo '$name' agregado."
            ))
        }
    }

    fun showGenericProductDialog(show: Boolean) {
        if (show && !allowAction("sales.quick")) return
        if (show && currentSettings?.commerce?.quickSaleEnabled == false) {
            _uiState.update { it.copy(errorMessage = "La venta rápida está deshabilitada.") }
            return
        }
        _uiState.update { it.copy(isShowingGenericProductDialog = show) }
    }

    fun showCommonArticlesDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingCommonArticlesDialog = show) }
    }

    fun showSearchCatalogDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingSearchCatalogDialog = show) }
    }

    fun showCashInDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingCashInDialog = show) }
    }

    fun showCashOutDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingCashOutDialog = show) }
    }

    fun showCashPaymentDialog(show: Boolean) {
        _uiState.update { state ->
            if (state.isCheckoutInProgress || state.isShowingCashPayment == show) state
            else state.copy(isShowingCashPayment = show, checkoutErrorMessage = null)
        }
    }

    fun showCustomerDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingCustomerDialog = show) }
    }

    fun submitCashMovement(isCashIn: Boolean, amountCents: Long, reason: String, approvalPin: String? = null) {
        val state = _uiState.value
        if (state.isCashMovementSubmitting) return
        val session = state.activeSession
        if (session == null) {
            _uiState.update { it.copy(cashMovementError = "Abre un turno para registrar el movimiento.") }
            return
        }
        _uiState.update { it.copy(isCashMovementSubmitting = true, cashMovementError = null) }
        scope.launch {
            val request = CashMovementRequest(if (isCashIn) "CASH_IN" else "CASH_OUT", amountCents, reason, state.activeCashierId)
            apiClient.recordCashMovement(session.sessionId, request, approvalPin).onSuccess {
                _uiState.update { it.copy(isCashMovementSubmitting = false, isShowingCashInDialog = false, isShowingCashOutDialog = false) }
                loadActiveSession()
            }.onFailure { failure ->
                _uiState.update { it.copy(isCashMovementSubmitting = false, cashMovementError = failure.message) }
            }
        }
    }

    fun handoffCurrentShift() {
        val state = _uiState.value
        val session = state.activeSession ?: return
        if (!state.allowShiftHandoff || state.isHandingOffShift) return
        if (hasUnconfirmedSales(session.sessionId)) {
            _uiState.update { it.copy(errorMessage = "Sincroniza las ventas pendientes antes del relevo.") }
            return
        }
        _uiState.update { it.copy(isHandingOffShift = true, errorMessage = null) }
        scope.launch {
            apiClient.handoffShift(session.sessionId).onSuccess { updated ->
                _uiState.update { it.copy(activeSession = updated, isHandingOffShift = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isHandingOffShift = false, errorMessage = error.message) }
            }
        }
    }

    fun showSessionDialog(show: Boolean) {
        if (show && _uiState.value.activeSession == null && currentSettings?.commerce?.requireOpeningFund == false) {
            openCashSession(0)
            return
        }
        _uiState.update { it.copy(isShowingSessionDialog = show) }
    }

    fun openCashSession(fundCents: Long) {
        scope.launch {
            val req = OpenSessionRequest(
                registerId = activeRegisterId,
                userId = _uiState.value.activeCashierId,
                initialFundCents = fundCents
            )
            apiClient.openSession(req).onSuccess { session ->
                _uiState.update {
                    it.copy(
                        activeSession = session,
                        isShowingSessionDialog = false,
                        successMessage = "Sesión de caja abierta con éxito"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun closeCashSession(countedCashCents: Long, notes: String = "") {
        val state = _uiState.value
        if (state.isClosingCashSession) return
        val sess = state.activeSession ?: return
        if (hasUnconfirmedSales(sess.sessionId)) {
            setErrorMessage("Sincroniza las ventas pendientes de este turno antes de cerrarlo.")
            return
        }
        if (!_uiState.compareAndSet(state, state.copy(isClosingCashSession = true, errorMessage = null))) return
        val token = apiClient.sessionToken
        scope.launch {
            val req = CloseSessionRequest(countedCashCents = countedCashCents, notes = notes)
            apiClient.closeSession(sess.sessionId, req).onSuccess { closed ->
                if (_uiState.value.activeSession?.sessionId == closed.sessionId) activeSessionRequest++
                _uiState.update {
                    it.copy(
                        activeSession = it.activeSession?.takeUnless { active -> active.sessionId == closed.sessionId },
                        isClosingCashSession = false,
                        isShowingSessionDialog = false,
                        successMessage = "Corte de caja completado. Diferencia: $${(closed.differenceCents ?: 0L) / 100.0}"
                    )
                }
                if (token == apiClient.sessionToken) onCashSessionClosed?.invoke(closed)
            }.onFailure { err ->
                _uiState.update { it.copy(isClosingCashSession = false, errorMessage = err.message) }
            }
        }
    }

    fun hasUnconfirmedSales(sessionId: String): Boolean =
        _uiState.value.isCheckoutInProgress || saleQueue.getPending().any { it.request.cashSessionId == sessionId }

    fun checkoutCash(receivedCents: Long) {
        val state = _uiState.value
        checkoutMultiPayment(
            listOf(
                SalePaymentRequest(
                    method = "CASH",
                    amountCents = state.total.cents,
                    receivedCents = receivedCents
                )
            )
        )
    }

    fun checkoutCredit() {
        val state = _uiState.value
        if (state.connectionState == ServerConnectionState.NO_SERVER) {
            _uiState.update {
                it.copy(errorMessage = "En Modo Contingencia (sin servidor LAN) solo se permiten pagos en Efectivo o Tarjeta.")
            }
            return
        }
        if (state.selectedCustomer == null) {
            _uiState.update { it.copy(errorMessage = "Debe seleccionar un cliente para venta a crédito (fiado).") }
            return
        }
        checkoutMultiPayment(
            listOf(
                SalePaymentRequest(
                    method = "CREDIT",
                    amountCents = state.total.cents
                )
            )
        )
    }

    fun checkoutMultiPayment(payments: List<SalePaymentRequest>) = checkoutMultiPayment(payments, null)

    fun checkoutMultiPayment(payments: List<SalePaymentRequest>, approvalPin: String?) {
        val state = _uiState.getAndUpdate {
            if (it.isCheckoutInProgress) it else it.copy(isCheckoutInProgress = true, checkoutErrorMessage = null)
        }
        if (state.isCheckoutInProgress) return

        val refreshed = state.cartLines.map { line ->
            if (line.promotion == null) line else line.pricedAt(line.quantity)
        }
        if (refreshed.zip(state.cartLines).any { (quoted, prior) -> quoted.total != prior.total }) {
            _uiState.update { recalculateTotals(it.copy(cartLines = refreshed, isShowingCashPayment = false)) }
            reportCheckoutError("Cambió la promoción de un artículo. Revisa el nuevo total antes de cobrar.")
            return
        }
        val isOffline = state.connectionState == ServerConnectionState.NO_SERVER
        val server = activeSessionServer
        val validationError = when {
            payments.isEmpty() -> "Debe especificar al menos una forma de pago."
            isOffline && state.cartLines.any { it.promotionDiscountCents > 0 } ->
                "Conéctate al servidor para validar la promoción antes de cobrar."
            state.cartLines.any { !it.kitComponents.isNullOrEmpty() && it.quantity.millis % 1000 != 0L } ->
                "Los kits se venden en cantidades enteras."
            currentSettings?.commerce?.creditSalesEnabled == false && payments.any { it.method == "CREDIT" } ->
                "Las nuevas ventas a crédito están deshabilitadas."
            isOffline && state.cashDrawerLimitEnabled && payments.any { it.method == "CASH" } ->
                "Conéctate al servidor para validar el límite de efectivo antes de cobrar."
            isOffline && payments.any { it.method == "CREDIT" || it.method == "LOYALTY" } ->
                "En Modo Contingencia (sin servidor LAN) solo se permiten pagos en Efectivo o Tarjeta."
            state.activeSession?.status != "OPEN" || server != apiClient.baseUrl -> if (isOffline) {
                "Conéctate al servidor para confirmar un turno abierto antes de cobrar sin red."
            } else "Debe abrir una sesión de caja antes de realizar ventas."
            payments.any { it.method == "CREDIT" } && state.selectedCustomer == null ->
                "Debe seleccionar un cliente para registrar pago a crédito (fiado)."
            else -> null
        }
        if (validationError != null) {
            reportCheckoutError(validationError)
            return
        }

        scope.launch {
            if (server != apiClient.baseUrl) {
                reportCheckoutError("Cambió el servidor durante el cobro. Confirma el turno antes de continuar.")
                return@launch
            }
            val linesReq = state.cartLines.map { line ->
                SaleLineItemRequest(
                    productId = line.productId,
                    presentationId = line.presentationId,
                    description = line.name,
                    quantityMillis = line.quantity.millis,
                    expectedUnitPriceCents = line.unitPrice.cents,
                    expectedTotalCents = line.total.cents,
                    taxRate = line.taxRate,
                    discountCents = line.fixedDiscount(),
                    discountPercent = line.discountPercent,
                    expectedKitComponents = line.kitComponents,
                    priceListLevel = line.priceListLevel,
                    manualPriceCents = line.manualPriceCents,
                    expectedPromotionDiscountCents = line.promotionDiscountCents
                )
            }

            val totalDiscountCents = state.cartLines.sumOf { it.discountCents - it.promotionDiscountCents }
            val sessionId = checkNotNull(state.activeSession).sessionId

            val saleReq = CreateSaleRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                cashSessionId = sessionId,
                cashierUserId = state.activeCashierId,
                customerId = state.selectedCustomer?.id,
                lines = linesReq,
                payments = payments,
                expectedTotalCents = state.total.cents,
                discountCents = totalDiscountCents
            )

            if (isOffline) {
                val queuedSale = try {
                    saleQueue.enqueue(saleReq, serverUrl = server)
                } catch (error: Exception) {
                    reportCheckoutError(error.message ?: "No se pudo guardar la venta en contingencia.")
                    return@launch
                }
                val localFolio = queuedSale.localFolio

                val cashPay = payments.firstOrNull { it.method == "CASH" }
                val cashChangeCents = if (cashPay != null && cashPay.receivedCents > cashPay.amountCents) {
                    cashPay.receivedCents - cashPay.amountCents
                } else 0L
                val changeMsg = if (cashChangeCents > 0L) {
                    " | Cambio: $${cashChangeCents / 100.0}"
                } else ""

                val offlineSale = SaleResponse(
                    id = "offline-$localFolio",
                    folio = localFolio.replace("OFF-", "").toLongOrNull() ?: 9999L,
                    cashSessionId = sessionId,
                    cashierName = state.activeCashierName,
                    customerName = state.selectedCustomer?.name,
                    status = "COMPLETED",
                    subtotalCents = state.subtotal.cents,
                    taxCents = state.tax.cents,
                    totalCents = state.total.cents,
                    lines = linesReq.mapIndexed { idx, l ->
                        SaleLineItemResponse(
                            id = "line-off-$idx",
                            productId = l.productId,
                            presentationId = l.presentationId,
                            description = l.description,
                            quantityMillis = l.quantityMillis,
                            unitPriceCents = l.expectedUnitPriceCents,
                            taxRate = l.taxRate,
                            taxCents = ((l.expectedTotalCents * l.taxRate) / (1 + l.taxRate)).toLong(),
                            totalCents = l.expectedTotalCents,
                            discountCents = state.cartLines[idx].discountCents,
                            discountPercent = l.discountPercent,
                            isKit = !l.expectedKitComponents.isNullOrEmpty()
                        )
                    },
                    payments = payments.mapIndexed { idx, p ->
                        val payChange = (p.receivedCents - p.amountCents).coerceAtLeast(0L)
                        SalePaymentResponse(
                            id = "pay-off-$idx",
                            method = p.method,
                            amountCents = p.amountCents,
                            receivedCents = p.receivedCents,
                            changeCents = payChange,
                            reference = p.reference
                        )
                    },
                    createdAt = queuedSale.createdAt,
                    loyaltyReward = null
                )

                _uiState.update { current ->
                    resetCheckedOutTicket(current, state.activeTicketId).copy(
                        isShowingCashPayment = false,
                        isCheckoutInProgress = false,
                        checkoutErrorMessage = null,
                        lastCompletedSale = offlineSale,
                        offlinePendingSalesCount = saleQueue.pendingCount(),
                        successMessage = "Venta registrada en Modo Contingencia (#$localFolio)$changeMsg. Se sincronizará al volver la red.",
                        errorMessage = null
                    )
                }
                onSaleCompleted?.invoke(offlineSale)
                return@launch
            }

            val submission = try {
                saleQueue.stageOnline(saleReq, serverUrl = server)
            } catch (error: Exception) {
                reportCheckoutError(error.message ?: "No se pudo guardar el intento de cobro.")
                return@launch
            }
            val response = try {
                if (approvalPin.isNullOrBlank()) apiClient.completeSale(saleReq) else apiClient.completeSaleWithCashApproval(saleReq, approvalPin)
            } catch (error: CancellationException) {
                throw error
            } finally {
                saleQueue.releaseOnline(submission.localFolio)
            }
            response.onSuccess { sale ->
                runCatching { saleQueue.removeOnline(submission.localFolio) }
                _uiState.update { current ->
                    val cashPay = sale.payments.firstOrNull { it.method == "CASH" }
                    val changeMsg = if (cashPay != null && cashPay.changeCents > 0L) {
                        " | Cambio: $${cashPay.changeCents / 100.0}"
                    } else ""

                    val creditPay = sale.payments.firstOrNull { it.method == "CREDIT" }
                    val remCredit = sale.customerRemainingCreditCents
                    val creditMsg = if (creditPay != null && remCredit != null) {
                        " | Crédito restante: $${remCredit / 100.0}"
                    } else ""

                    val loyaltyReward = sale.loyaltyReward
                    val loyaltyMsg = if (loyaltyReward != null && loyaltyReward.earnedCents > 0L) {
                        " | ¡Ganó $${"%.2f".format(loyaltyReward.earnedCents / 100.0)} en monedero! (Saldo: $${"%.2f".format(loyaltyReward.finalBalanceCents / 100.0)})"
                    } else if (loyaltyReward != null && loyaltyReward.redeemedCents > 0L) {
                        " | Redimió $${"%.2f".format(loyaltyReward.redeemedCents / 100.0)} de monedero (Saldo: $${"%.2f".format(loyaltyReward.finalBalanceCents / 100.0)})"
                    } else ""

                    resetCheckedOutTicket(current, state.activeTicketId).copy(
                        isShowingCashPayment = false,
                        isCheckoutInProgress = false,
                        checkoutErrorMessage = null,
                        lastCompletedSale = sale,
                        offlinePendingSalesCount = saleQueue.pendingCount(),
                        successMessage = "Venta folio #${sale.folio} completada con éxito$changeMsg$creditMsg$loyaltyMsg",
                        errorMessage = null
                    )
                }
                onSaleCompleted?.invoke(sale)
            }.onFailure { error ->
                if (error.saleOutcomeIsUncertain()) {
                    _uiState.update { current ->
                        resetCheckedOutTicket(current, state.activeTicketId).copy(
                            isShowingCashPayment = false,
                            isCheckoutInProgress = false,
                            offlinePendingSalesCount = saleQueue.pendingCount(),
                            errorMessage = "El cobro quedó pendiente de confirmación. Consulta la cola de ventas antes de volver a cobrar."
                        )
                    }
                } else {
                    runCatching { saleQueue.removeOnline(submission.localFolio) }.onFailure {
                        runCatching { saleQueue.markFailed(submission.localFolio, error.message.orEmpty(), automaticRetry = false) }
                    }
                    _uiState.update { it.copy(offlinePendingSalesCount = saleQueue.pendingCount()) }
                    if ((error as? SaleSubmissionRejected)?.code == "PRICE_CHANGED" && server == apiClient.baseUrl) {
                        refreshCheckoutPrices(state.activeTicketId)
                    }
                    reportCheckoutError(error.message ?: "No se pudo completar el cobro.")
                }
            }
        }
    }

    private suspend fun refreshCheckoutPrices(ticketId: String) {
        val server = apiClient.baseUrl
        val lines = _uiState.value.takeIf { it.activeTicketId == ticketId }?.cartLines.orEmpty()
        val latest = lines.mapNotNull { it.productId }.distinct().mapNotNull { id -> apiClient.getProduct(id).getOrNull() }
            .flatMap { it.presentations }.associateBy { it.id }
        if (apiClient.baseUrl != server) return
        _uiState.update { current ->
            if (current.activeTicketId != ticketId) current else recalculateTotals(current.copy(
                isShowingCashPayment = false,
                cartLines = current.cartLines.map { line ->
                    val p = latest[line.presentationId] ?: return@map line
                    line.copy(publicPriceCents = p.priceCents, priceTiers = p.priceTiers, promotion = p.promotion, listPrices = p.listPrices)
                        .pricedAt(line.quantity)
                }
            ))
        }
    }

    private fun reportCheckoutError(message: String) {
        _uiState.update {
            it.copy(isCheckoutInProgress = false, checkoutErrorMessage = message, errorMessage = message)
        }
    }

    private fun resetCheckedOutTicket(state: SalesUiState, ticketId: String): SalesUiState {
        val ticket = state.tickets.firstOrNull { it.id == ticketId } ?: return state
        val cleared = TicketState(id = ticket.id, title = ticket.title, createdAtMillis = ticket.createdAtMillis)
        val updated = state.copy(tickets = state.tickets.map { if (it.id == ticketId) cleared else it })
        return if (state.activeTicketId == ticketId) activateTicket(updated, cleared) else updated
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    var onDiscountSupervisorRequired: ((SupervisorAction.ApplyDiscount) -> Unit)? = null

    fun showDiscountDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingDiscountDialog = show) }
    }

    fun applyDiscount(
        targetLineId: String?,
        discountPercent: Double,
        discountCents: Long,
        isSupervisorApproved: Boolean = false
    ) {
        val state = _uiState.value
        val isCashier = state.activeCashierRole == "CASHIER"

        if (discountPercent > state.maxCashierDiscountPercent && isCashier && !isSupervisorApproved) {
            showDiscountDialog(false)
            onDiscountSupervisorRequired?.invoke(
                SupervisorAction.ApplyDiscount(
                    lineId = targetLineId,
                    discountPercent = discountPercent,
                    discountCents = discountCents
                )
            )
            return
        }

        if (targetLineId != null) {
            val updatedLines = state.cartLines.map { line ->
                if (line.id == targetLineId) {
                    line.copy(discountPercent = discountPercent).pricedAt(line.quantity, discountCents)
                } else line
            }
            _uiState.update {
                recalculateTotals(it.copy(
                    cartLines = updatedLines,
                    isShowingDiscountDialog = false,
                    successMessage = "Descuento aplicado correctamente."
                ))
            }
        } else {
            if (state.cartLines.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "El carrito está vacío para aplicar descuento.") }
                return
            }
            val updatedLines = state.cartLines.map { line ->
                line.copy(discountPercent = discountPercent).pricedAt(line.quantity, discountCents)
            }
            _uiState.update {
                recalculateTotals(it.copy(
                    cartLines = updatedLines,
                    isShowingDiscountDialog = false,
                    successMessage = "Descuento global aplicado a la venta."
                ))
            }
        }
    }

    private fun recalculateTotals(state: SalesUiState): SalesUiState {
        var subtotal = Money.ZERO
        var tax = Money.ZERO
        var discount = Money.ZERO

        for (line in state.cartLines) {
            val lineTax = PricingRules.extractTaxFromTotal(line.total, line.taxRate)
            val lineNet = line.total - lineTax
            subtotal += lineNet
            tax += lineTax
            discount += Money(line.discountCents)
        }

        val total = subtotal + tax

        val updatedTickets = state.tickets.map { t ->
            if (t.id == state.activeTicketId) {
                t.copy(
                    cartLines = state.cartLines,
                    subtotal = subtotal,
                    tax = tax,
                    total = total,
                    selectedCustomer = state.selectedCustomer
                )
            } else t
        }

        return state.copy(
            subtotal = subtotal,
            tax = tax,
            total = total,
            discount = discount,
            tickets = updatedTickets
        )
    }
}

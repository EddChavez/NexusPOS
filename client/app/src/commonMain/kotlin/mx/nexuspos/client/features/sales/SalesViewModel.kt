package mx.nexuspos.client.features.sales

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.features.auth.SupervisorAction
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.offline.LocalPersistenceProvider
import mx.nexuspos.client.features.offline.OfflineCatalogCache
import mx.nexuspos.client.features.offline.OfflineSaleQueue
import mx.nexuspos.client.features.offline.OfflineSyncManager
import mx.nexuspos.client.features.offline.getDefaultPersistenceProvider
import mx.nexuspos.client.features.whatsapp.WhatsAppMessageFormatter
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
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
    val persistenceProvider: LocalPersistenceProvider = getDefaultPersistenceProvider()
) {
    val catalogCache = OfflineCatalogCache(persistenceProvider)
    val saleQueue = OfflineSaleQueue(persistenceProvider)
    val syncManager = OfflineSyncManager(apiClient, saleQueue, scope)

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

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
            apiClient.searchProducts().onSuccess { prods ->
                if (prods.isNotEmpty()) {
                    catalogCache.saveCatalog(prods)
                }
            }
        }
    }

    fun loadSettings() {
        scope.launch {
            apiClient.getSettings().onSuccess { s ->
                currentSettings = s
                _uiState.update { it.copy(maxCashierDiscountPercent = s.maxCashierDiscountPercent) }
            }
        }
    }

    fun openSaleWhatsAppDialog(sale: SaleResponse) {
        val phone = _uiState.value.selectedCustomer?.phone?.ifBlank { "" } ?: ""
        val text = WhatsAppMessageFormatter.formatSaleTicket(sale, currentSettings)
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

    fun triggerOfflineSync() {
        if (_uiState.value.isSyncingOfflineSales) return
        _uiState.update { it.copy(isSyncingOfflineSales = true, offlineSyncMessage = "Sincronizando...") }
        syncManager.triggerSync { result ->
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
        saleQueue.clearSynced()
        _uiState.update { it.copy(offlinePendingSalesCount = saleQueue.pendingCount()) }
    }

    fun checkServerConnection() {
        scope.launch {
            val isHealthy = apiClient.checkHealth()
            val wasOffline = _uiState.value.connectionState == ServerConnectionState.NO_SERVER
            _uiState.update {
                it.copy(
                    connectionState = if (isHealthy) ServerConnectionState.CONNECTED else ServerConnectionState.NO_SERVER
                )
            }
            if (isHealthy && (wasOffline || saleQueue.pendingCount() > 0)) {
                preloadCatalogCache()
                if (saleQueue.pendingCount() > 0) {
                    triggerOfflineSync()
                }
            }
        }
    }

    fun loadActiveSession(registerId: String = "caja-1") {
        scope.launch {
            val res = apiClient.getCurrentSession(registerId)
            res.onSuccess { session ->
                _uiState.update { it.copy(activeSession = session) }
            }.onFailure {
                _uiState.update { it.copy(connectionState = ServerConnectionState.NO_SERVER) }
            }
        }
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
        val input = _uiState.value.scannerInput.trim()
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
                        val prod = prods.first()
                        val pres = prod.presentations.firstOrNull { it.barcode == input } ?: prod.presentations.firstOrNull()
                        if (pres != null) {
                            if (prod.baseUnit.equals("KG", ignoreCase = true)) {
                                val scale = scaleReadingProvider?.invoke()
                                val autoAccept = autoAcceptScaleWeightProvider?.invoke() == true
                                if (scale != null && scale.isStableWeight && autoAccept) {
                                    addProductPresentation(prod, pres, mx.nexuspos.core.Quantity.fromDouble(scale.weightKg))
                                    _uiState.update { it.copy(scannerInput = "", errorMessage = null) }
                                } else {
                                    // Open bulk weight dialog
                                    _uiState.update {
                                        it.copy(
                                            scannerInput = "",
                                            isWeighingProduct = Pair(prod, pres),
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

    fun addProductPresentation(prod: ProductDto, pres: ProductPresentationDto, qty: Quantity) {
        val unit = ProductUnit.fromCode(prod.baseUnit)
        val unitPrice = Money.fromCents(pres.priceCents)
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
            val existingIndex = state.cartLines.indexOfFirst { it.presentationId == pres.id }
            val updatedLines = if (existingIndex >= 0 && unit == ProductUnit.PIECE) {
                state.cartLines.mapIndexed { idx, line ->
                    if (idx == existingIndex) {
                        val newQty = line.quantity + qty
                        val newTotal = PricingRules.calculateLineTotal(line.unitPrice, newQty)
                        line.copy(quantity = newQty, total = newTotal)
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
                    isWeighed = unit == ProductUnit.KILOGRAM
                )
            }
            recalculateTotals(state.copy(cartLines = updatedLines))
        }
    }

    fun confirmWeighedProduct(qty: Quantity) {
        val weighing = _uiState.value.isWeighingProduct
        if (weighing != null) {
            addProductPresentation(weighing.first, weighing.second, qty)
            _uiState.update { it.copy(isWeighingProduct = null) }
        }
    }

    fun dismissWeighingDialog() {
        _uiState.update { it.copy(isWeighingProduct = null) }
    }

    fun updateCartLineQuantity(lineId: String, newQuantity: Quantity) {
        if (newQuantity.millis <= 0L) return
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
                    val (newTotal, totalDisc) = if (line.discountPercent > 0.0 || line.discountCents > 0L) {
                        PricingRules.calculateLineTotalWithDiscount(
                            line.unitPrice,
                            newQuantity,
                            line.discountPercent,
                            line.discountCents
                        )
                    } else {
                        Pair(PricingRules.calculateLineTotal(line.unitPrice, newQuantity), Money(line.discountCents))
                    }
                    line.copy(
                        quantity = newQuantity,
                        total = newTotal,
                        discountCents = totalDisc.cents
                    )
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

    fun removeCartLine(lineId: String) {
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
        _uiState.update { state ->
            val nextNum = state.tickets.size + 1
            val newId = "ticket-${UUID.randomUUID().toString().take(6)}"
            val newTicket = TicketState(id = newId, title = "Ticket $nextNum")

            // Sincronizar el ticket actual saliente
            val syncedTickets = state.tickets.map { t ->
                if (t.id == state.activeTicketId) {
                    t.copy(
                        cartLines = state.cartLines,
                        subtotal = state.subtotal,
                        tax = state.tax,
                        total = state.total,
                        selectedCustomer = state.selectedCustomer
                    )
                } else t
            }

            state.copy(
                tickets = syncedTickets + newTicket,
                activeTicketId = newId,
                cartLines = emptyList(),
                subtotal = Money.ZERO,
                tax = Money.ZERO,
                total = Money.ZERO,
                selectedCustomer = null,
                selectedCartLineId = null,
                scannerInput = ""
            )
        }
    }

    fun selectTicket(ticketId: String) {
        _uiState.update { state ->
            if (state.activeTicketId == ticketId) return@update state
            val target = state.tickets.firstOrNull { it.id == ticketId } ?: return@update state

            val syncedTickets = state.tickets.map { t ->
                if (t.id == state.activeTicketId) {
                    t.copy(
                        cartLines = state.cartLines,
                        subtotal = state.subtotal,
                        tax = state.tax,
                        total = state.total,
                        selectedCustomer = state.selectedCustomer
                    )
                } else t
            }

            state.copy(
                tickets = syncedTickets,
                activeTicketId = target.id,
                cartLines = target.cartLines,
                subtotal = target.subtotal,
                tax = target.tax,
                total = target.total,
                selectedCustomer = target.selectedCustomer,
                selectedCartLineId = null,
                scannerInput = ""
            )
        }
    }

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
            _uiState.update { it.copy(isShowingParkSaleDialog = true) }
        }
    }

    fun closeParkSaleDialog() {
        _uiState.update { it.copy(isShowingParkSaleDialog = false) }
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
        val state = _uiState.value
        if (state.cartLines.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "El carrito está vacío. Agregue productos antes de poner en espera.") }
            return
        }

        val effectiveLabel = label.trim().ifBlank {
            val activeTicket = state.tickets.firstOrNull { it.id == state.activeTicketId }
            activeTicket?.title ?: "Ticket ${state.tickets.size}"
        }

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
                isWeighed = line.isWeighed
            )
        }

        val req = ParkSaleRequest(
            customLabel = effectiveLabel,
            cashierUserId = state.activeCashierId,
            cashierName = state.activeCashierName,
            registerId = state.activeSession?.registerId ?: "caja-1",
            customerId = state.selectedCustomer?.id,
            customerName = state.selectedCustomer?.name,
            items = itemsDto,
            totalCents = state.total.cents
        )

        scope.launch {
            apiClient.parkSale(req).onSuccess { parked ->
                _uiState.update { s ->
                    val clearedTickets = s.tickets.map { t ->
                        if (t.id == s.activeTicketId) {
                            t.copy(
                                cartLines = emptyList(),
                                subtotal = Money.ZERO,
                                tax = Money.ZERO,
                                total = Money.ZERO,
                                selectedCustomer = null
                            )
                        } else t
                    }
                    s.copy(
                        tickets = clearedTickets,
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
                        selectedCartLineId = null,
                        scannerInput = "",
                        isShowingParkSaleDialog = false,
                        successMessage = "Ticket '$effectiveLabel' guardado en espera (${parked.itemsCount} productos).",
                        errorMessage = null
                    )
                }
                loadParkedSales()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = "Error al poner ticket en espera: ${err.message}")
                }
            }
        }
    }

    fun retrieveParkedSale(parkedSale: ParkedSaleResponse) {
        val state = _uiState.value
        val req = RetrieveParkedSaleRequest(
            retrievedByUserId = state.activeCashierId,
            registerId = state.activeSession?.registerId ?: "caja-1"
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
                        isWeighed = item.isWeighed
                    )
                }

                _uiState.update { s ->
                    if (s.cartLines.isEmpty()) {
                        val updatedState = s.copy(
                            cartLines = restoredLines,
                            isShowingParkedSalesListDialog = false,
                            selectedParkedSale = null,
                            successMessage = "Ticket '${retrieved.customLabel}' recuperado al carrito."
                        )
                        val recalculated = recalculateTotals(updatedState)
                        val syncedTickets = recalculated.tickets.map { t ->
                            if (t.id == recalculated.activeTicketId) {
                                t.copy(
                                    cartLines = recalculated.cartLines,
                                    subtotal = recalculated.subtotal,
                                    tax = recalculated.tax,
                                    total = recalculated.total
                                )
                            } else t
                        }
                        recalculated.copy(tickets = syncedTickets)
                    } else {
                        val newId = "ticket-${UUID.randomUUID().toString().take(6)}"
                        val newTicket = TicketState(id = newId, title = retrieved.customLabel.take(15))
                        val stateWithNewTicket = s.copy(
                            tickets = s.tickets + newTicket,
                            activeTicketId = newId,
                            cartLines = restoredLines,
                            isShowingParkedSalesListDialog = false,
                            selectedParkedSale = null,
                            successMessage = "Ticket '${retrieved.customLabel}' recuperado en nueva pestaña."
                        )
                        val recalculated = recalculateTotals(stateWithNewTicket)
                        val syncedTickets = recalculated.tickets.map { t ->
                            if (t.id == newId) {
                                t.copy(
                                    cartLines = recalculated.cartLines,
                                    subtotal = recalculated.subtotal,
                                    tax = recalculated.tax,
                                    total = recalculated.total
                                )
                            } else t
                        }
                        recalculated.copy(tickets = syncedTickets)
                    }
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
    var scaleReadingProvider: (() -> ScaleReading)? = null
    var autoAcceptScaleWeightProvider: (() -> Boolean)? = null

    fun openCashDrawer() {
        onOpenCashDrawer?.invoke()
        _uiState.update { it.copy(successMessage = "Comando de apertura enviado al cajón de dinero.") }
    }

    fun printPhysicalTicket(sale: SaleResponse) {
        onPrintPhysicalReceipt?.invoke(sale)
        _uiState.update {
            it.copy(
                reprintTicketPreview = null,
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

    fun reprintTicket(sale: SaleResponse) {
        val formatted = receiptFormatter?.invoke(sale, true) ?: defaultFormatReceipt(sale, true)
        _uiState.update {
            it.copy(
                reprintTicketPreview = formatted,
                successMessage = "Ticket #${sale.folio} listo para reimpresión (*** COPIA DE REIMPRESIÓN ***)"
            )
        }
    }

    fun closeReprintPreview() {
        _uiState.update { it.copy(reprintTicketPreview = null) }
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
        _uiState.update { state ->
            if (state.tickets.size <= 1) {
                val reset = state.tickets.first().copy(
                    cartLines = emptyList(),
                    subtotal = Money.ZERO,
                    tax = Money.ZERO,
                    total = Money.ZERO,
                    selectedCustomer = null
                )
                state.copy(
                    tickets = listOf(reset),
                    cartLines = emptyList(),
                    subtotal = Money.ZERO,
                    tax = Money.ZERO,
                    total = Money.ZERO,
                    selectedCustomer = null,
                    selectedCartLineId = null,
                    scannerInput = ""
                )
            } else {
                val remaining = state.tickets.filterNot { it.id == ticketId }
                val nextActive = if (state.activeTicketId == ticketId) {
                    remaining.last()
                } else {
                    remaining.firstOrNull { it.id == state.activeTicketId } ?: remaining.first()
                }

                state.copy(
                    tickets = remaining,
                    activeTicketId = nextActive.id,
                    cartLines = nextActive.cartLines,
                    subtotal = nextActive.subtotal,
                    tax = nextActive.tax,
                    total = nextActive.total,
                    selectedCustomer = nextActive.selectedCustomer,
                    selectedCartLineId = null,
                    scannerInput = ""
                )
            }
        }
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
        _uiState.update { it.copy(isShowingCashPayment = show) }
    }

    fun showCustomerDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingCustomerDialog = show) }
    }

    fun showSessionDialog(show: Boolean) {
        _uiState.update { it.copy(isShowingSessionDialog = show) }
    }

    fun openCashSession(fundCents: Long) {
        scope.launch {
            val req = OpenSessionRequest(
                registerId = "caja-1",
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
        val sess = _uiState.value.activeSession ?: return
        scope.launch {
            val req = CloseSessionRequest(countedCashCents = countedCashCents, notes = notes)
            apiClient.closeSession(sess.sessionId, req).onSuccess { closed ->
                _uiState.update {
                    it.copy(
                        activeSession = null,
                        isShowingSessionDialog = false,
                        successMessage = "Corte de caja completado. Diferencia: $${(closed.differenceCents ?: 0L) / 100.0}"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

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

    fun checkoutMultiPayment(payments: List<SalePaymentRequest>) {
        val state = _uiState.value
        val isOffline = state.connectionState == ServerConnectionState.NO_SERVER

        if (payments.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Debe especificar al menos una forma de pago.") }
            return
        }

        if (isOffline) {
            val hasRestricted = payments.any { it.method == "CREDIT" || it.method == "LOYALTY" }
            if (hasRestricted) {
                _uiState.update {
                    it.copy(errorMessage = "En Modo Contingencia (sin servidor LAN) solo se permiten pagos en Efectivo o Tarjeta.")
                }
                return
            }
        } else {
            val sess = state.activeSession
            if (sess == null || sess.status != "OPEN") {
                _uiState.update {
                    it.copy(errorMessage = "Debe abrir una sesión de caja antes de realizar ventas.")
                }
                return
            }
        }

        val hasCredit = payments.any { it.method == "CREDIT" }
        if (hasCredit && state.selectedCustomer == null) {
            _uiState.update { it.copy(errorMessage = "Debe seleccionar un cliente para registrar pago a crédito (fiado).") }
            return
        }

        scope.launch {
            val linesReq = state.cartLines.map { line ->
                SaleLineItemRequest(
                    productId = line.productId,
                    presentationId = line.presentationId,
                    description = line.name,
                    quantityMillis = line.quantity.millis,
                    expectedUnitPriceCents = line.unitPrice.cents,
                    expectedTotalCents = line.total.cents,
                    taxRate = line.taxRate,
                    discountCents = line.discountCents,
                    discountPercent = line.discountPercent
                )
            }

            val totalDiscountCents = state.cartLines.sumOf { it.discountCents }
            val sessionId = state.activeSession?.sessionId ?: "contingency-session"

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
                val queuedSale = saleQueue.enqueue(saleReq)
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
                            discountCents = l.discountCents,
                            discountPercent = l.discountPercent
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

                onSaleCompleted?.invoke(offlineSale)

                _uiState.update { s ->
                    val clearedTickets = s.tickets.map { t ->
                        if (t.id == s.activeTicketId) {
                            t.copy(
                                cartLines = emptyList(),
                                subtotal = Money.ZERO,
                                tax = Money.ZERO,
                                total = Money.ZERO,
                                selectedCustomer = null
                            )
                        } else t
                    }

                    s.copy(
                        tickets = clearedTickets,
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
                        selectedCartLineId = null,
                        isShowingCashPayment = false,
                        lastCompletedSale = offlineSale,
                        offlinePendingSalesCount = saleQueue.pendingCount(),
                        successMessage = "Venta registrada en Modo Contingencia (#$localFolio)$changeMsg. Se sincronizará al volver la red.",
                        errorMessage = null
                    )
                }
                return@launch
            }

            apiClient.completeSale(saleReq).onSuccess { sale ->
                onSaleCompleted?.invoke(sale)
                _uiState.update { s ->
                    val clearedTickets = s.tickets.map { t ->
                        if (t.id == s.activeTicketId) {
                            t.copy(
                                cartLines = emptyList(),
                                subtotal = Money.ZERO,
                                tax = Money.ZERO,
                                total = Money.ZERO,
                                selectedCustomer = null
                            )
                        } else t
                    }

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

                    s.copy(
                        tickets = clearedTickets,
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
                        selectedCartLineId = null,
                        isShowingCashPayment = false,
                        lastCompletedSale = sale,
                        successMessage = "Venta folio #${sale.folio} completada con éxito$changeMsg$creditMsg$loyaltyMsg",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message)
                }
            }
        }
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
                    val (newTotal, totalDisc) = PricingRules.calculateLineTotalWithDiscount(
                        line.unitPrice,
                        line.quantity,
                        discountPercent,
                        discountCents
                    )
                    line.copy(
                        total = newTotal,
                        discountCents = totalDisc.cents,
                        discountPercent = discountPercent
                    )
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
                val (newTotal, totalDisc) = PricingRules.calculateLineTotalWithDiscount(
                    line.unitPrice,
                    line.quantity,
                    discountPercent,
                    discountCents
                )
                line.copy(
                    total = newTotal,
                    discountCents = totalDisc.cents,
                    discountPercent = discountPercent
                )
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

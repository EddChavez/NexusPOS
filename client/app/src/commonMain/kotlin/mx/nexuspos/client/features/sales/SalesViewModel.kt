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
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleLineItemRequest
import mx.nexuspos.contracts.sales.SalePaymentRequest
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import java.util.UUID

class SalesViewModel(
    val apiClient: ApiClient = ApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    init {
        checkServerConnection()
        loadActiveSession()
    }

    fun checkServerConnection() {
        scope.launch {
            val isHealthy = apiClient.checkHealth()
            _uiState.update {
                it.copy(
                    connectionState = if (isHealthy) ServerConnectionState.CONNECTED else ServerConnectionState.NO_SERVER
                )
            }
        }
    }

    fun loadActiveSession() {
        scope.launch {
            val res = apiClient.getCurrentSession("caja-1")
            res.onSuccess { session ->
                _uiState.update { it.copy(activeSession = session) }
            }.onFailure {
                _uiState.update { it.copy(connectionState = ServerConnectionState.NO_SERVER) }
            }
        }
    }

    fun onScannerInputChanged(value: String) {
        _uiState.update { it.copy(scannerInput = value) }
    }

    fun onScannerSubmitted() {
        val input = _uiState.value.scannerInput.trim()
        if (input.isBlank()) return

        _uiState.update { it.copy(scannerStatus = ScannerStatus.PROCESSING) }

        scope.launch {
            try {
                val res = apiClient.searchProducts(barcode = input)
                res.onSuccess { prods ->
                    if (prods.isNotEmpty()) {
                        val prod = prods.first()
                        val pres = prod.presentations.firstOrNull { it.barcode == input } ?: prod.presentations.firstOrNull()
                        if (pres != null) {
                            if (prod.baseUnit.equals("KG", ignoreCase = true)) {
                                // Open bulk weight dialog
                                _uiState.update {
                                    it.copy(
                                        scannerInput = "",
                                        isWeighingProduct = Pair(prod, pres),
                                        errorMessage = null
                                    )
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
                                        _uiState.update {
                                            it.copy(
                                                scannerInput = "",
                                                isWeighingProduct = Pair(prod, pres),
                                                errorMessage = null
                                            )
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
                    _uiState.update {
                        it.copy(
                            errorMessage = "Error al buscar producto: ${err.message}",
                            connectionState = ServerConnectionState.NO_SERVER
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

    fun openCashDrawer() {
        _uiState.update { it.copy(successMessage = "Comando de apertura enviado al cajón de dinero.") }
    }

    fun reprintLastTicket() {
        val lastSale = _uiState.value.lastCompletedSale
        if (lastSale != null) {
            _uiState.update { it.copy(successMessage = "Reimprimiendo ticket #${lastSale.folio}...") }
        } else {
            _uiState.update { it.copy(errorMessage = "No hay ninguna venta completada recientemente para reimprimir.") }
        }
    }

    fun openDailySalesAndReturns() {
        _uiState.update { it.copy(successMessage = "Consultando registro de ventas y devoluciones del turno...") }
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
        if (state.connectionState == ServerConnectionState.NO_SERVER) {
            _uiState.update {
                it.copy(errorMessage = "Sin servidor: No es posible cobrar mientras el servidor central no esté disponible en la LAN. El carrito se conserva intacto.")
            }
            return
        }

        val sess = state.activeSession
        if (sess == null || sess.status != "OPEN") {
            _uiState.update {
                it.copy(errorMessage = "Debe abrir una sesión de caja antes de realizar ventas.")
            }
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
                    taxRate = line.taxRate
                )
            }

            val payReq = listOf(
                SalePaymentRequest(
                    method = "CASH",
                    amountCents = state.total.cents,
                    receivedCents = receivedCents
                )
            )

            val saleReq = CreateSaleRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                cashSessionId = sess.sessionId,
                cashierUserId = state.activeCashierId,
                customerId = state.selectedCustomer?.id,
                lines = linesReq,
                payments = payReq,
                expectedTotalCents = state.total.cents
            )

            apiClient.completeSale(saleReq).onSuccess { sale ->
                _uiState.update { state ->
                    val clearedTickets = state.tickets.map { t ->
                        if (t.id == state.activeTicketId) {
                            t.copy(
                                cartLines = emptyList(),
                                subtotal = Money.ZERO,
                                tax = Money.ZERO,
                                total = Money.ZERO,
                                selectedCustomer = null
                            )
                        } else t
                    }
                    state.copy(
                        tickets = clearedTickets,
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
                        selectedCartLineId = null,
                        isShowingCashPayment = false,
                        lastCompletedSale = sale,
                        successMessage = "Venta folio #${sale.folio} completada. Cambio: $${(sale.payments.firstOrNull()?.changeCents ?: 0L) / 100.0}",
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

    fun checkoutCredit() {
        val state = _uiState.value
        val customer = state.selectedCustomer
        if (customer == null) {
            _uiState.update { it.copy(errorMessage = "Debe seleccionar un cliente para venta a crédito (fiado).") }
            return
        }

        val sess = state.activeSession
        if (sess == null || sess.status != "OPEN") {
            _uiState.update { it.copy(errorMessage = "Debe abrir una sesión de caja antes de realizar ventas.") }
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
                    taxRate = line.taxRate
                )
            }

            val payReq = listOf(
                SalePaymentRequest(
                    method = "CREDIT",
                    amountCents = state.total.cents
                )
            )

            val saleReq = CreateSaleRequest(
                idempotencyKey = UUID.randomUUID().toString(),
                cashSessionId = sess.sessionId,
                cashierUserId = state.activeCashierId,
                customerId = customer.id,
                lines = linesReq,
                payments = payReq,
                expectedTotalCents = state.total.cents
            )

            apiClient.completeSale(saleReq).onSuccess { sale ->
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
                        lastCompletedSale = sale,
                        successMessage = "Venta a crédito folio #${sale.folio} para ${customer.name}. Saldo restante: $${(sale.customerRemainingCreditCents ?: 0L) / 100.0}",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun recalculateTotals(state: SalesUiState): SalesUiState {
        var subtotal = Money.ZERO
        var tax = Money.ZERO

        for (line in state.cartLines) {
            val lineTax = PricingRules.extractTaxFromTotal(line.total, line.taxRate)
            val lineNet = line.total - lineTax
            subtotal += lineNet
            tax += lineTax
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
            tickets = updatedTickets
        )
    }
}

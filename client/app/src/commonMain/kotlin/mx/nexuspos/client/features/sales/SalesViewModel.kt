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

        scope.launch {
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
        }
    }

    fun addProductPresentation(prod: ProductDto, pres: ProductPresentationDto, qty: Quantity) {
        val unit = ProductUnit.fromCode(prod.baseUnit)
        val unitPrice = Money.fromCents(pres.priceCents)
        val lineTotal = PricingRules.calculateLineTotal(unitPrice, qty)

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
                    name = pres.name,
                    unit = unit,
                    quantity = qty,
                    unitPrice = unitPrice,
                    taxRate = prod.taxRate,
                    total = lineTotal
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
        _uiState.update { it.copy(selectedCustomer = customer, isShowingCustomerDialog = false) }
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
                _uiState.update {
                    it.copy(
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
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
                _uiState.update {
                    it.copy(
                        cartLines = emptyList(),
                        subtotal = Money.ZERO,
                        tax = Money.ZERO,
                        total = Money.ZERO,
                        selectedCustomer = null,
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
        return state.copy(subtotal = subtotal, tax = tax, total = total)
    }
}

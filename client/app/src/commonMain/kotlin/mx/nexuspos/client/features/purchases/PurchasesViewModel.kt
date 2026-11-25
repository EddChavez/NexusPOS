package mx.nexuspos.client.features.purchases

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.PurchaseRequestException
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.purchases.*

internal enum class PurchasesPage { HISTORY, SUPPLIERS, SUPPLIER_FORM, RECEIVE, DETAIL, RETURN }

internal data class PurchasesState(
    val page: PurchasesPage = PurchasesPage.HISTORY,
    val suppliers: List<SupplierDto> = emptyList(),
    val history: List<PurchaseSummary> = emptyList(),
    val historyOffset: Int = 0,
    val hasMoreHistory: Boolean = false,
    val receipt: ReceiptDraft? = null,
    val supplier: SupplierDraft? = null,
    val returnDraft: ReturnDraft? = null,
    val detailId: String? = null,
    val details: PurchaseDetails? = null,
    val productQuery: String = "",
    val productResults: List<ProductDto> = emptyList(),
    val searching: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

internal const val PURCHASE_HISTORY_PAGE_SIZE = 30

class PurchasesViewModel(
    private val api: ApiClient,
    private val onInventoryChanged: () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val mutableState = MutableStateFlow(PurchasesState())
    internal val state = mutableState.asStateFlow()
    private var load: Job? = null
    private var search: Job? = null

    fun dispose() = scope.cancel()

    fun refresh() {
        when (state.value.page) {
            PurchasesPage.HISTORY -> openHistory(state.value.historyOffset)
            PurchasesPage.SUPPLIERS -> openSuppliers()
            PurchasesPage.RECEIVE -> loadSuppliers()
            PurchasesPage.DETAIL -> state.value.detailId?.let { openDetails(it) }
            else -> Unit
        }
    }

    internal fun openHistory(offset: Int = 0) {
        val start = offset.coerceAtLeast(0)
        open(PurchasesPage.HISTORY) { copy(historyOffset = start, history = emptyList(), hasMoreHistory = false) }
        fetch(
            request = { api.listPurchases(PURCHASE_HISTORY_PAGE_SIZE + 1, start) },
            onSuccess = { copy(history = it.take(PURCHASE_HISTORY_PAGE_SIZE), hasMoreHistory = it.size > PURCHASE_HISTORY_PAGE_SIZE) }
        )
    }

    fun openSupplierWorkspace() = openSuppliers()

    internal fun openSuppliers() {
        open(PurchasesPage.SUPPLIERS)
        loadSuppliers()
    }

    private fun loadSuppliers() = fetch({ api.listSuppliers() }) { copy(suppliers = it) }

    fun startReceipt(suggestions: List<ReorderSuggestionItemDto> = emptyList()) {
        if (state.value.saving || state.value.awaitingConfirmation) return
        val current = state.value.receipt
        val fromReorder = suggestions.filter { it.suggestedOrderMillis > 0 }
        open(PurchasesPage.RECEIVE) {
            copy(
                receipt = current ?: ReceiptDraft(lines = fromReorder.take(200).map(ReceiptLineDraft::fromSuggestion)),
                productQuery = "",
                productResults = emptyList(),
                error = when {
                    current != null && suggestions.isNotEmpty() -> "Ya hay una recepción en edición. Termínala o cancélala antes de usar el resurtido."
                    fromReorder.size > 200 -> "Se copiaron 200 partidas. Divide el resto del resurtido en otro documento."
                    else -> null
                }
            )
        }
        loadSuppliers()
    }

    internal fun editSupplier(supplier: SupplierDto? = null) {
        val draft = supplier?.let { SupplierDraft(it.id, it.name, it.taxId, it.phone, it.email, it.isActive) } ?: SupplierDraft()
        open(PurchasesPage.SUPPLIER_FORM) { copy(supplier = draft) }
    }

    internal fun changeSupplier(draft: SupplierDraft) {
        edit { copy(supplier = draft) }
    }

    internal fun saveSupplier(pin: String) {
        val draft = state.value.supplier ?: return
        save(
            request = { api.saveSupplier(draft.id, pin, draft.toRequest()) },
            onSuccess = {
                mutableState.update { it.copy(supplier = null) }
                if (state.value.receipt != null) {
                    startReceipt()
                    editReceipt { copy(supplierId = it.id) }
                } else openSuppliers()
                mutableState.update { current -> current.copy(success = "Proveedor guardado.") }
            }
        )
    }

    internal fun editReceipt(change: ReceiptDraft.() -> ReceiptDraft) {
        edit { copy(receipt = receipt?.change()) }
    }

    internal fun searchProducts(query: String) {
        if (state.value.saving || state.value.awaitingConfirmation) return
        search?.cancel()
        val term = query.trim()
        edit { copy(productQuery = query, productResults = emptyList(), searching = term.isNotEmpty()) }
        if (term.isEmpty()) return
        search = scope.launch {
            delay(200)
            val result = api.searchProducts(query = term)
            ensureActive()
            mutableState.update { current ->
                result.fold(
                    onSuccess = { products ->
                        val valid = products.filter { it.isActive && it.usesInventory && it.kitComponents.isEmpty() }.take(8)
                        current.copy(productResults = valid, searching = false)
                    },
                    onFailure = { current.copy(searching = false, error = it.message ?: "No se pudieron buscar productos.") }
                )
            }
        }
    }

    internal fun addProduct(product: ProductDto) {
        editReceipt {
            if (lines.size >= 200) this else copy(lines = lines + ReceiptLineDraft.fromProduct(product))
        }
        searchProducts("")
    }

    internal fun confirmReceipt(pin: String) {
        val request = state.value.receipt?.toRequest() ?: return
        save({ api.receivePurchase(pin, request) }, reconcileOnFailure = true) { saved ->
            open(PurchasesPage.DETAIL) {
                copy(receipt = null, detailId = saved.summary.id, details = saved, success = "Recepción registrada. Se actualizaron las existencias.")
            }
            onInventoryChanged()
        }
    }

    internal fun openDetails(id: String, success: String? = null) {
        open(PurchasesPage.DETAIL) { copy(detailId = id, details = details?.takeIf { it.summary.id == id }, success = success) }
        fetch({ api.getPurchase(id) }) { copy(details = it) }
    }

    internal fun startReturn() {
        val purchase = state.value.details ?: return
        if (purchase.lines.none { it.returnedMillis < it.quantityMillis }) return
        open(PurchasesPage.RETURN) { copy(returnDraft = ReturnDraft()) }
    }

    internal fun changeReturn(draft: ReturnDraft) {
        edit { copy(returnDraft = draft) }
    }

    internal fun confirmReturn(pin: String) {
        val purchase = state.value.details ?: return
        val request = state.value.returnDraft?.toRequest(purchase) ?: return
        save({ api.returnPurchase(purchase.summary.id, pin, request) }, reconcileOnFailure = true) {
            mutableState.update { it.copy(returnDraft = null, details = null) }
            openDetails(purchase.summary.id, "Devolución registrada.")
            onInventoryChanged()
        }
    }

    fun back(): Boolean {
        val current = state.value
        if (current.saving || current.awaitingConfirmation) return true
        when (current.page) {
            PurchasesPage.HISTORY -> return false
            PurchasesPage.RECEIVE -> {
                mutableState.update { it.copy(receipt = null) }
                openHistory()
            }
            PurchasesPage.SUPPLIER_FORM -> {
                mutableState.update { it.copy(supplier = null) }
                if (current.receipt == null) openSuppliers() else startReceipt()
            }
            PurchasesPage.RETURN -> {
                mutableState.update { it.copy(returnDraft = null) }
                openDetails(checkNotNull(current.details).summary.id)
            }
            else -> openHistory()
        }
        return true
    }

    internal fun dismissMessage() = mutableState.update { it.copy(error = null, success = null) }

    private fun open(page: PurchasesPage, update: PurchasesState.() -> PurchasesState = { this }) {
        if (state.value.saving || state.value.awaitingConfirmation) return
        load?.cancel()
        search?.cancel()
        mutableState.update { it.copy(page = page, searching = false, loading = false, error = null, success = null).update() }
    }

    private fun edit(update: PurchasesState.() -> PurchasesState) =
        mutableState.update { if (it.saving || it.awaitingConfirmation) it else it.update() }

    private fun <T> fetch(request: suspend () -> Result<T>, onSuccess: PurchasesState.(T) -> PurchasesState) {
        if (state.value.saving) return
        load?.cancel()
        mutableState.update { it.copy(loading = true) }
        load = scope.launch {
            val result = request()
            ensureActive()
            mutableState.update { current ->
                val next = result.fold(
                    onSuccess = { current.onSuccess(it) },
                    onFailure = { current.copy(error = it.message ?: "No se pudo cargar la información.") }
                )
                next.copy(loading = false)
            }
        }
    }

    private fun <T> save(
        request: suspend () -> Result<T>,
        reconcileOnFailure: Boolean = false,
        onSuccess: (T) -> Unit
    ) {
        val snapshot = state.value
        if (snapshot.saving || snapshot.loading) return
        if (!mutableState.compareAndSet(snapshot, snapshot.copy(saving = true, error = null, success = null))) return
        scope.launch {
            val result = request()
            result.onSuccess { saved ->
                mutableState.update { it.copy(saving = false, awaitingConfirmation = false) }
                onSuccess(saved)
            }.onFailure { error ->
                val rejection = error as? PurchaseRequestException
                val uncertain = reconcileOnFailure && (rejection == null || rejection.status >= 500 || rejection.status == 408)
                mutableState.update { current ->
                    val stillUnverified = current.awaitingConfirmation && (
                        rejection?.status in listOf(401, 403) || rejection?.error?.code == "IDEMPOTENCY_MISMATCH"
                    )
                    current.copy(
                        saving = false,
                        awaitingConfirmation = uncertain || stillUnverified,
                        error = error.message ?: "No se pudo guardar la operación."
                    )
                }
            }
        }
    }
}

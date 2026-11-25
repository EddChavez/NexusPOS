package org.ozafry.nexuspos

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.nexuspos.contracts.mobile.*
import java.io.IOException
import javax.net.ssl.SSLException

internal enum class MobileTab(val label: String) { HOME("Inicio"), PRODUCTS("Productos"), SHIFTS("Turnos") }

internal data class MobileState(
    val loaded: Boolean = false,
    val address: String? = null,
    val user: String? = null,
    val selected: MobileTab = MobileTab.HOME,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val overviewUpdatedAt: Long? = null,
    val productsUpdatedAt: Long? = null,
    val shiftsUpdatedAt: Long? = null,
    val salesUpdatedAt: Long? = null,
    val overview: MobileOverview? = null,
    val products: List<MobileProduct>? = null,
    val productId: String? = null,
    val shifts: MobileShifts? = null,
    val sales: MobileShiftSales? = null,
    val shiftId: String? = null
) {
    val updatedAt: Long? get() = when (selected) {
        MobileTab.HOME -> overviewUpdatedAt
        MobileTab.PRODUCTS -> productsUpdatedAt
        MobileTab.SHIFTS -> if (shiftId == null) shiftsUpdatedAt else salesUpdatedAt
    }
}

internal class MobileModel(application: Application) : AndroidViewModel(application) {
    private val store = EnrollmentStore(application)
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var enrollment: MobileEnrollment? = null
    private var session: MobileSession? = null
    private var running: Job? = null
    private var timeout: Job? = null
    private var reconnecting: Job? = null
    private var lastInteraction = 0L
    private var query: Pair<String, Boolean>? = null
    private var shiftsFilter: Pair<String?, String?> = null to null
    private var folioFilter: Long? = null

    init {
        viewModelScope.launch {
            enrollment = withContext(Dispatchers.IO) { store.load() }
            update { copy(loaded = true, address = enrollment?.address) }
        }
    }

    fun pair(address: String, code: String, name: String) {
        val host = storeAddress(address)
        if (host == null) {
            update { copy(error = "Captura la dirección HTTPS de la tienda, sin una ruta.") }
            return
        }
        perform {
            val credential = MobileApi(host).pair(code.trim(), name.trim())
            val saved = MobileEnrollment(host, credential.token)
            withContext(Dispatchers.IO) { store.save(saved) }
            enrollment = saved
            update { copy(address = host) }
        }
    }

    fun login(pin: String) {
        val current = enrollment ?: return
        perform {
            session = MobileApi(current.address).login(current.token, pin)
            update { copy(user = session?.userName) }
            interaction()
            fetchVisible()
        }
    }

    fun forget() {
        lock()
        store.clear()
        enrollment = null
        update { copy(address = null) }
    }

    fun lock() {
        running?.cancel()
        running = null
        timeout?.cancel()
        val old = session
        val host = enrollment?.address
        session = null
        lastInteraction = 0L
        query = null
        shiftsFilter = null to null
        folioFilter = null
        mutable.value = MobileState(loaded = true, address = host)
        if (old != null && host != null) viewModelScope.launch {
            try { MobileApi(host).logout(old.token) } catch (_: IOException) { } catch (_: MobileHttpError) { }
        }
    }

    fun interaction() {
        val current = session ?: return
        val elapsed = SystemClock.elapsedRealtime()
        if (lastInteraction != 0L && elapsed - lastInteraction >= current.idleMinutes * 60_000L) {
            lock()
            return
        }
        lastInteraction = elapsed
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(current.idleMinutes * 60_000L)
            lock()
        }
    }

    fun select(tab: MobileTab) {
        if (mutable.value.loading) return
        update { copy(selected = tab, error = null) }
        refresh()
    }

    fun search(value: String, barcode: Boolean = false) {
        if (mutable.value.loading || value.isBlank()) return
        query = value.trim() to barcode
        update { copy(productId = null) }
        refresh()
    }

    fun selectProduct(id: String?) { update { copy(productId = id) } }

    fun filterShifts(date: String?, register: String?) {
        if (mutable.value.loading) return
        shiftsFilter = date?.trim()?.takeIf { it.isNotEmpty() } to register
        update { copy(shiftId = null, sales = null, salesUpdatedAt = null) }
        refresh()
    }

    fun selectShift(id: String?) {
        if (mutable.value.loading) return
        folioFilter = null
        update { copy(shiftId = id, sales = null, salesUpdatedAt = null) }
        if (id != null) refresh()
    }

    fun searchFolio(folio: Long?) {
        if (mutable.value.loading) return
        folioFilter = folio
        refresh()
    }

    fun moreSales() {
        val shift = mutable.value.shiftId ?: return
        val current = mutable.value.sales ?: return
        val next = current.nextBeforeFolio ?: return
        perform {
            val (api, token) = client()
            val result = api.shiftSales(token, shift, folioFilter, next)
            publishSales(result.copy(sales = current.sales + result.sales))
        }
    }

    fun refresh() {
        val current = session ?: return
        if (SystemClock.elapsedRealtime() - lastInteraction >= current.idleMinutes * 60_000L) {
            lock()
            return
        }
        if (mutable.value.selected == MobileTab.PRODUCTS && query == null) return
        perform { fetchVisible() }
    }

    fun connectionLost() {
        update { copy(offline = true) }
    }

    fun connectionRestored() {
        reconnecting?.cancel()
        reconnecting = viewModelScope.launch {
            running?.join()
            update { copy(offline = false) }
            refresh()
        }
    }

    private suspend fun fetchVisible() {
        val (api, token) = client()
        when (mutable.value.selected) {
            MobileTab.HOME -> {
                val result = api.overview(token)
                update { copy(overview = result, overviewUpdatedAt = System.currentTimeMillis()) }
            }
            MobileTab.PRODUCTS -> query?.let { (value, barcode) ->
                val result = api.products(token, value, barcode)
                val matched = result.singleOrNull { it.matchedPresentationId != null }
                update {
                    copy(
                        products = result,
                        productsUpdatedAt = System.currentTimeMillis(),
                        productId = matched?.id ?: productId?.takeIf { id -> result.any { it.id == id } }
                    )
                }
            }
            MobileTab.SHIFTS -> {
                val id = mutable.value.shiftId
                if (id == null) {
                    val (date, register) = shiftsFilter
                    val result = api.shifts(token, date, register)
                    update { copy(shifts = result, shiftsUpdatedAt = System.currentTimeMillis()) }
                } else {
                    publishSales(api.shiftSales(token, id, folioFilter, null))
                }
            }
        }
    }

    private fun publishSales(result: MobileShiftSales) {
        update {
            copy(
                shifts = shifts?.let { list ->
                    list.copy(shifts = list.shifts.map { if (it.id == result.shift.id) result.shift else it })
                },
                sales = result,
                salesUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun client(): Pair<MobileApi, String> = MobileApi(checkNotNull(enrollment).address) to checkNotNull(session).token

    private fun perform(action: suspend () -> Unit) {
        if (mutable.value.loading) return
        update { copy(loading = true, error = null) }
        running = viewModelScope.launch {
            try {
                action()
                update { copy(offline = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: MobileHttpError) {
                if (error.status == 401 && session != null) lock()
                update { copy(error = error.message, offline = false) }
            } catch (_: SSLException) {
                update { copy(error = "No se pudo verificar el certificado HTTPS de la tienda.", offline = true) }
            } catch (_: IOException) {
                update { copy(error = "No se pudo conectar con la tienda.", offline = true) }
            } catch (_: Exception) {
                update { copy(error = "No se pudo completar la consulta.") }
            } finally {
                update { copy(loading = false) }
            }
        }
    }

    private fun update(change: MobileState.() -> MobileState) { mutable.value = mutable.value.change() }
}

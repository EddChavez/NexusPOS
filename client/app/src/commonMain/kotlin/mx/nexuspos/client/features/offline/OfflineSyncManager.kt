package mx.nexuspos.client.features.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.saleOutcomeIsUncertain

data class SyncResult(
    val totalProcessed: Int,
    val succeeded: Int,
    val failed: Int,
    val errors: List<String> = emptyList()
)

class OfflineSyncManager(
    private val apiClient: ApiClient,
    private val queue: OfflineSaleQueue,
    private val scope: CoroutineScope
) {
    private val syncMutex = Mutex()
    private val _summary = MutableStateFlow(
        OfflineSyncSummary(totalPending = queue.pendingCount())
    )
    val summary: StateFlow<OfflineSyncSummary> = _summary.asStateFlow()

    fun updatePendingCount() {
        _summary.update { it.copy(totalPending = queue.pendingCount()) }
    }

    suspend fun syncPendingSales(automatic: Boolean = false): SyncResult = syncMutex.withLock {
        val endpoint = apiClient.baseUrl
        val pending = queue.getPending(automatic).filter { it.belongsTo(endpoint) }
        if (pending.isEmpty()) {
            _summary.update {
                it.copy(totalPending = queue.pendingCount(), isSyncing = false, lastSyncMessage = "No hay ventas para sincronizar en este intento.")
            }
            return@withLock SyncResult(0, 0, 0)
        }

        _summary.update { it.copy(isSyncing = true, lastSyncMessage = "Sincronizando ${pending.size} ventas...") }

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        try {
            for (sale in pending) {
                if (apiClient.baseUrl != endpoint) break
                val res = apiClient.completeSale(sale.request)
                coroutineContext.ensureActive()
                try {
                    res.onSuccess { completedSale ->
                        queue.markSynced(sale.localFolio, completedSale.folio, completedSale.id)
                        successCount++
                    }.onFailure { error ->
                        val message = error.message ?: "Error de conexión"
                        queue.markFailed(sale.localFolio, message, automaticRetry = error.saleOutcomeIsUncertain())
                        errors.add("Venta ${sale.localFolio}: $message")
                        failCount++
                    }
                } catch (error: Exception) {
                    errors.add("Venta ${sale.localFolio}: no se pudo actualizar la cola local.")
                    failCount++
                }
            }
        } finally {
            _summary.update { it.copy(isSyncing = false, totalPending = queue.pendingCount()) }
        }

        val finalPending = queue.pendingCount()
        val msg = if (failCount == 0) {
            "Sincronización exitosa: $successCount venta(s) enviadas."
        } else {
            "Sincronizadas: $successCount, Fallidas: $failCount."
        }

        _summary.update {
            it.copy(
                totalPending = finalPending,
                totalSynced = it.totalSynced + successCount,
                totalFailed = it.totalFailed + failCount,
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis(),
                lastSyncMessage = msg
            )
        }

        SyncResult(successCount + failCount, successCount, failCount, errors)
    }

    fun triggerSync(automatic: Boolean = false, onCompleted: ((SyncResult) -> Unit)? = null) {
        if (_summary.value.isSyncing) return
        scope.launch {
            val result = syncPendingSales(automatic)
            onCompleted?.invoke(result)
        }
    }
}

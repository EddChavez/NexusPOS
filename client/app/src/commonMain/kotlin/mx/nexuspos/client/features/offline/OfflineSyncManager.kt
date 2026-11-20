package mx.nexuspos.client.features.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient

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
    private val _summary = MutableStateFlow(
        OfflineSyncSummary(totalPending = queue.pendingCount())
    )
    val summary: StateFlow<OfflineSyncSummary> = _summary.asStateFlow()

    fun updatePendingCount() {
        _summary.update { it.copy(totalPending = queue.pendingCount()) }
    }

    suspend fun syncPendingSales(): SyncResult {
        val pending = queue.getPending()
        if (pending.isEmpty()) {
            _summary.update {
                it.copy(totalPending = 0, isSyncing = false, lastSyncMessage = "No hay ventas pendientes.")
            }
            return SyncResult(0, 0, 0)
        }

        _summary.update { it.copy(isSyncing = true, lastSyncMessage = "Sincronizando ${pending.size} ventas...") }

        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        for (sale in pending) {
            val res = apiClient.completeSale(sale.request)
            res.onSuccess { completedSale ->
                queue.markSynced(
                    localFolio = sale.localFolio,
                    centralFolio = completedSale.folio,
                    centralSaleId = completedSale.id
                )
                successCount++
            }.onFailure { err ->
                val errorMsg = err.message ?: "Error de conexión"
                queue.markFailed(sale.localFolio, errorMsg)
                errors.add("Venta ${sale.localFolio}: $errorMsg")
                failCount++
            }
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

        return SyncResult(pending.size, successCount, failCount, errors)
    }

    fun triggerSync(onCompleted: ((SyncResult) -> Unit)? = null) {
        if (_summary.value.isSyncing) return
        scope.launch {
            val result = syncPendingSales()
            onCompleted?.invoke(result)
        }
    }
}

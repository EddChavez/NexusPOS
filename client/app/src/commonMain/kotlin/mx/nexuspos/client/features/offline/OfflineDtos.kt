package mx.nexuspos.client.features.offline

import kotlinx.serialization.Serializable
import mx.nexuspos.contracts.sales.CreateSaleRequest

@Serializable
enum class OfflineSaleStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Serializable
data class QueuedOfflineSale(
    val localFolio: String,
    val request: CreateSaleRequest,
    val createdAt: Long,
    val status: OfflineSaleStatus = OfflineSaleStatus.PENDING,
    val syncedFolio: Long? = null,
    val syncedSaleId: String? = null,
    val lastError: String? = null,
    val attempts: Int = 0,
    val automaticRetry: Boolean = true,
    val serverUrl: String? = null
) {
    fun belongsTo(endpoint: String): Boolean = serverUrl == null || serverUrl.trimEnd('/') == endpoint.trimEnd('/')
}

@Serializable
data class OfflineSyncSummary(
    val totalPending: Int = 0,
    val totalSynced: Int = 0,
    val totalFailed: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val lastSyncMessage: String? = null
)

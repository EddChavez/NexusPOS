package mx.nexuspos.client.features.offline

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.sales.CreateSaleRequest

class OfflineSaleQueue(
    private val storage: LocalPersistenceProvider = getDefaultPersistenceProvider()
) {
    private val queueFileName = "offline_sales_queue.json"
    private val seqFileName = "offline_sequence.txt"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val sales = mutableListOf<QueuedOfflineSale>()
    private var nextSequence: Long = 1L

    init {
        loadSequence()
        loadQueue()
    }

    private fun loadSequence() {
        val raw = storage.readText(seqFileName)
        nextSequence = raw?.trim()?.toLongOrNull() ?: 1L
    }

    private fun saveSequence() {
        storage.writeText(seqFileName, nextSequence.toString())
    }

    private fun loadQueue() {
        val raw = storage.readText(queueFileName)
        sales.clear()
        if (!raw.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<QueuedOfflineSale>>(raw)
                sales.addAll(list)
            } catch (e: Exception) {
                // ignore corrupted
            }
        }
    }

    private fun persistQueue() {
        try {
            val encoded = json.encodeToString(sales.toList())
            storage.writeText(queueFileName, encoded)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun nextFolio(): String {
        val folio = "OFF-" + nextSequence.toString().padStart(4, '0')
        nextSequence++
        saveSequence()
        return folio
    }

    fun enqueue(request: CreateSaleRequest, customFolio: String? = null): QueuedOfflineSale {
        val folio = customFolio ?: nextFolio()
        val queued = QueuedOfflineSale(
            localFolio = folio,
            request = request,
            createdAt = System.currentTimeMillis(),
            status = OfflineSaleStatus.PENDING
        )
        sales.add(queued)
        persistQueue()
        return queued
    }

    fun getPending(): List<QueuedOfflineSale> {
        return sales.filter { it.status == OfflineSaleStatus.PENDING || it.status == OfflineSaleStatus.FAILED }
    }

    fun getAll(): List<QueuedOfflineSale> {
        return sales.toList()
    }

    fun markSynced(localFolio: String, centralFolio: Long, centralSaleId: String? = null) {
        val idx = sales.indexOfFirst { it.localFolio == localFolio }
        if (idx >= 0) {
            val current = sales[idx]
            sales[idx] = current.copy(
                status = OfflineSaleStatus.SYNCED,
                syncedFolio = centralFolio,
                syncedSaleId = centralSaleId,
                lastError = null
            )
            persistQueue()
        }
    }

    fun markFailed(localFolio: String, error: String) {
        val idx = sales.indexOfFirst { it.localFolio == localFolio }
        if (idx >= 0) {
            val current = sales[idx]
            sales[idx] = current.copy(
                status = OfflineSaleStatus.FAILED,
                lastError = error,
                attempts = current.attempts + 1
            )
            persistQueue()
        }
    }

    fun pendingCount(): Int = sales.count { it.status == OfflineSaleStatus.PENDING || it.status == OfflineSaleStatus.FAILED }

    fun clearSynced() {
        sales.removeAll { it.status == OfflineSaleStatus.SYNCED }
        persistQueue()
    }

    fun clearAll() {
        sales.clear()
        nextSequence = 1L
        storage.delete(queueFileName)
        storage.delete(seqFileName)
    }
}

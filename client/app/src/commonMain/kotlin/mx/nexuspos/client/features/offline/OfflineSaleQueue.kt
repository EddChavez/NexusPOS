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
    private val onlineSubmissions = mutableSetOf<String>()
    private var loadError: Exception? = null

    init {
        loadSequence()
        loadQueue()
    }

    private fun loadSequence() {
        val raw = runCatching { storage.readText(seqFileName) }.getOrNull()
        nextSequence = raw?.trim()?.toLongOrNull() ?: 1L
    }

    private fun saveSequence() {
        storage.writeText(seqFileName, nextSequence.toString())
    }

    private fun loadQueue() {
        try {
            val raw = storage.readText(queueFileName)
            if (!raw.isNullOrBlank()) sales.addAll(json.decodeFromString<List<QueuedOfflineSale>>(raw))
        } catch (error: Exception) {
            loadError = error
        }
    }

    private fun persistQueue(replacement: List<QueuedOfflineSale> = sales.toList()) {
        check(loadError == null) { "La cola de ventas local no se puede leer. Conserva el archivo y solicita soporte." }
        storage.writeText(queueFileName, json.encodeToString(replacement))
        sales.clear()
        sales.addAll(replacement)
    }

    fun nextFolio(): String {
        var folio: String
        do {
            folio = "OFF-" + nextSequence.toString().padStart(4, '0')
            nextSequence++
        } while (sales.any { it.localFolio == folio })
        saveSequence()
        return folio
    }

    fun enqueue(
        request: CreateSaleRequest,
        customFolio: String? = null,
        serverUrl: String? = null
    ): QueuedOfflineSale {
        val folio = customFolio ?: nextFolio()
        val queued = QueuedOfflineSale(
            localFolio = folio,
            request = request,
            createdAt = System.currentTimeMillis(),
            status = OfflineSaleStatus.PENDING,
            serverUrl = serverUrl
        )
        persistQueue(sales + queued)
        return queued
    }

    fun stageOnline(request: CreateSaleRequest, serverUrl: String? = null): QueuedOfflineSale {
        val entry = enqueue(request, "ENV-${request.idempotencyKey}", serverUrl)
        onlineSubmissions.add(entry.localFolio)
        return entry
    }

    fun releaseOnline(localFolio: String) {
        onlineSubmissions.remove(localFolio)
    }

    fun removeOnline(localFolio: String) {
        persistQueue(sales.filterNot { it.localFolio == localFolio })
    }

    fun getPending(automatic: Boolean = false): List<QueuedOfflineSale> = sales.filter {
        it.status != OfflineSaleStatus.SYNCED && it.localFolio !in onlineSubmissions &&
            (!automatic || it.automaticRetry)
    }

    fun getAll(): List<QueuedOfflineSale> {
        return sales.filterNot { it.localFolio in onlineSubmissions }
    }

    fun markSynced(localFolio: String, centralFolio: Long, centralSaleId: String? = null) {
        persistQueue(sales.map { entry ->
            if (entry.localFolio == localFolio) entry.copy(
                status = OfflineSaleStatus.SYNCED,
                syncedFolio = centralFolio,
                syncedSaleId = centralSaleId,
                lastError = null
            ) else entry
        })
    }

    fun markFailed(localFolio: String, error: String, automaticRetry: Boolean = true) {
        persistQueue(sales.map { entry ->
            if (entry.localFolio == localFolio) entry.copy(
                status = OfflineSaleStatus.FAILED,
                lastError = error,
                attempts = entry.attempts + 1,
                automaticRetry = automaticRetry
            ) else entry
        })
    }

    fun pendingCount(): Int = getPending().size

    fun clearSynced() {
        persistQueue(sales.filter { it.status != OfflineSaleStatus.SYNCED })
    }

    fun clearAll() {
        sales.clear()
        onlineSubmissions.clear()
        loadError = null
        nextSequence = 1L
        storage.delete(queueFileName)
        storage.delete(seqFileName)
    }
}

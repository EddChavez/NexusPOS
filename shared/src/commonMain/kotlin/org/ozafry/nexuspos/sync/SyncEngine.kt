package org.ozafry.nexuspos.sync

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.ozafry.nexuspos.models.*
import org.ozafry.nexuspos.repository.OfflineSaleRepository

data class SyncSummary(
    val pushedEventsCount: Int,
    val pulledProductsCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class SyncEngine(
    private val repository: OfflineSaleRepository,
    private val registerId: String,
    private val serverBaseUrl: String = "http://localhost:8080",
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) {
    private var lastSyncTimestamp: Long = 0L

    suspend fun syncNow(): SyncSummary {
        var pushedCount = 0
        var pulledCount = 0

        try {
            // 1. PUSH: Enviar eventos fuera de línea locales pendientes
            val pendingEvents = repository.getPendingSyncEvents(limit = 100)
            if (pendingEvents.isNotEmpty()) {
                val pushRequest = SyncPushRequest(
                    registerId = registerId,
                    events = pendingEvents
                )

                val response: SyncPushResponse = httpClient.post("$serverBaseUrl/api/sync/push") {
                    contentType(ContentType.Application.Json)
                    setBody(pushRequest)
                }.body()

                for (acceptedId in response.acceptedEventIds) {
                    repository.markSyncEventSuccess(acceptedId, response.serverTimestamp)
                    pushedCount++
                }

                for ((rejectedId, error) in response.rejectedEventIds) {
                    repository.markSyncEventFailed(rejectedId, error)
                }

                lastSyncTimestamp = response.serverTimestamp
            }

            // 2. PULL: Descargar actualizaciones de productos y categorías desde el servidor
            val pullResponse: SyncPullResponse = httpClient.get("$serverBaseUrl/api/catalog") {
                parameter("since", lastSyncTimestamp)
            }.body()

            if (pullResponse.products.isNotEmpty() || pullResponse.categories.isNotEmpty()) {
                repository.upsertSyncedProducts(pullResponse.products, pullResponse.categories)
                pulledCount = pullResponse.products.size
            }

            lastSyncTimestamp = pullResponse.serverTimestamp
            return SyncSummary(
                pushedEventsCount = pushedCount,
                pulledProductsCount = pulledCount,
                isSuccess = true
            )
        } catch (e: Exception) {
            return SyncSummary(
                pushedEventsCount = pushedCount,
                pulledProductsCount = pulledCount,
                isSuccess = false,
                errorMessage = e.message ?: "Error de conexión con el servidor"
            )
        }
    }
}

package mx.nexuspos.client.core

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto

class ApiClient(
    var baseUrl: String = "http://localhost:8080",
    client: HttpClient? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val http = client ?: HttpClient {
        install(ContentNegotiation) {
            json(this@ApiClient.json)
        }
    }

    suspend fun checkHealth(): Boolean {
        return try {
            val res = http.get("$baseUrl/health")
            res.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun login(pin: String): Result<LoginResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(pin = pin))
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProducts(barcode: String? = null, query: String? = null): Result<List<ProductDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/products") {
                if (!barcode.isNullOrBlank()) parameter("barcode", barcode)
                if (!query.isNullOrBlank()) parameter("query", query)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> {
        return try {
            val res = http.get("$baseUrl/api/v1/cash-sessions/current") {
                parameter("registerId", registerId)
            }
            if (res.status == HttpStatusCode.NotFound) {
                Result.success(null)
            } else if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun openSession(req: OpenSessionRequest): Result<CashSessionSummary> {
        return try {
            val res = http.post("$baseUrl/api/v1/cash-sessions") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeSession(sessionId: String, req: CloseSessionRequest): Result<CashSessionSummary> {
        return try {
            val res = http.post("$baseUrl/api/v1/cash-sessions/$sessionId/close") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchCustomers(query: String? = null): Result<List<CustomerDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers") {
                if (!query.isNullOrBlank()) parameter("query", query)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerCreditPayment(req: RegisterCreditPaymentRequest): Result<CreditMovementDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/customers/${req.customerId}/credit/payments") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/sales") {
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSettings(): Result<SettingsDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/system/settings")
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun parseError(response: HttpResponse): ApiError {
        return try {
            val text = response.bodyAsText()
            json.decodeFromString<ApiError>(text)
        } catch (e: Exception) {
            ApiError("HTTP_ERROR", "Error del servidor (${response.status.value})")
        }
    }
}

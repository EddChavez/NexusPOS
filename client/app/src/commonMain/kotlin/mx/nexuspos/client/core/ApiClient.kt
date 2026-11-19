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
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.CustomerStatementDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.identity.AuthorizeRequest
import mx.nexuspos.contracts.identity.AuthorizeResponse
import mx.nexuspos.contracts.identity.LoginRequest
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.identity.CreateUserRequest
import mx.nexuspos.contracts.identity.UpdateUserRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.LotWriteOffRequest
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.contracts.sales.SaleRefundResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto

open class ApiClient(
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

    open suspend fun checkHealth(): Boolean {
        return try {
            val res = http.get("$baseUrl/health")
            res.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    open suspend fun login(pin: String): Result<LoginResponse> {
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

    open suspend fun authorizeSupervisor(pin: String, action: String = "AUTHORIZE"): Result<AuthorizeResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/auth/authorize") {
                contentType(ContentType.Application.Json)
                setBody(AuthorizeRequest(pin = pin, action = action))
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

    open suspend fun searchProducts(
        barcode: String? = null,
        query: String? = null,
        departmentId: String? = null,
        includeInactive: Boolean = false
    ): Result<List<ProductDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/products") {
                if (!barcode.isNullOrBlank()) parameter("barcode", barcode)
                if (!query.isNullOrBlank()) parameter("query", query)
                if (!departmentId.isNullOrBlank()) parameter("departmentId", departmentId)
                if (includeInactive) parameter("includeInactive", "true")
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

    suspend fun getProduct(id: String): Result<ProductDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/products/$id")
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

    suspend fun createProduct(req: CreateProductRequest): Result<ProductDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/products") {
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

    suspend fun updateProduct(id: String, req: UpdateProductRequest): Result<ProductDto> {
        return try {
            val res = http.put("$baseUrl/api/v1/products/$id") {
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

    suspend fun deactivateProduct(id: String): Result<ProductDto> {
        return try {
            val res = http.delete("$baseUrl/api/v1/products/$id")
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

    suspend fun reactivateProduct(id: String): Result<ProductDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/products/$id/reactivate")
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

    suspend fun getDepartments(): Result<List<DepartmentDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/departments")
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

    suspend fun createDepartment(req: CreateDepartmentRequest): Result<DepartmentDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/departments") {
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

    suspend fun updateDepartment(id: String, req: UpdateDepartmentRequest): Result<DepartmentDto> {
        return try {
            val res = http.put("$baseUrl/api/v1/departments/$id") {
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

    suspend fun deleteDepartment(id: String): Result<Boolean> {
        return try {
            val res = http.delete("$baseUrl/api/v1/departments/$id")
            if (res.status.isSuccess()) {
                Result.success(true)
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun exportCatalogCsv(): Result<String> {
        return try {
            val res = http.get("$baseUrl/api/v1/catalog/export")
            if (res.status.isSuccess()) {
                Result.success(res.bodyAsText())
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun importCatalog(req: CatalogImportRequest): Result<CatalogImportResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/catalog/import") {
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

    suspend fun getInventorySummary(): Result<InventorySummaryDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/summary")
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

    open suspend fun getReorderSuggestions(
        departmentId: String? = null,
        onlyOutOfStock: Boolean = false
    ): Result<ReorderSuggestionsReportDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/reorder-suggestions") {
                if (!departmentId.isNullOrBlank()) parameter("departmentId", departmentId)
                if (onlyOutOfStock) parameter("onlyOutOfStock", true)
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

    suspend fun getStockMovements(
        productId: String? = null,
        type: String? = null,
        limit: Int = 100
    ): Result<List<StockMovementDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/movements") {
                if (!productId.isNullOrBlank()) parameter("productId", productId)
                if (!type.isNullOrBlank()) parameter("type", type)
                parameter("limit", limit)
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

    suspend fun registerStockMovement(req: RegisterStockMovementRequest): Result<StockMovementDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/inventory/movements") {
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

    open suspend fun getProductLots(
        productId: String? = null,
        status: LotExpirationStatus? = null
    ): Result<List<ProductLotDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/lots") {
                if (!productId.isNullOrBlank()) parameter("productId", productId)
                if (status != null) parameter("status", status.name)
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

    open suspend fun createProductLot(req: CreateProductLotRequest): Result<ProductLotDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/inventory/lots") {
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

    open suspend fun getExpirationsReport(): Result<ExpirationsReportDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/lots/report")
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

    open suspend fun writeOffProductLot(lotId: String, req: LotWriteOffRequest): Result<ProductLotDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/inventory/lots/$lotId/write-off") {
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

    open suspend fun getExpiringLotForProduct(productId: String): Result<ProductLotDto?> {
        return try {
            val res = http.get("$baseUrl/api/v1/inventory/lots/expiring/$productId")
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else if (res.status == HttpStatusCode.NotFound) {
                Result.success(null)
            } else {
                val err = parseError(res)
                Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getCurrentSession(registerId: String): Result<CashSessionSummary?> {
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

    open suspend fun openSession(req: OpenSessionRequest): Result<CashSessionSummary> {
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

    open suspend fun closeSession(sessionId: String, req: CloseSessionRequest): Result<CashSessionSummary> {
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

    suspend fun searchCustomers(query: String? = null, includeInactive: Boolean = false): Result<List<CustomerDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers") {
                if (!query.isNullOrBlank()) parameter("query", query)
                if (includeInactive) parameter("includeInactive", "true")
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

    suspend fun getCustomer(id: String): Result<CustomerDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers/$id")
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

    suspend fun createCustomer(req: CreateCustomerRequest): Result<CustomerDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/customers") {
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

    suspend fun updateCustomer(id: String, req: UpdateCustomerRequest): Result<CustomerDto> {
        return try {
            val res = http.put("$baseUrl/api/v1/customers/$id") {
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

    suspend fun deactivateCustomer(id: String): Result<CustomerDto> {
        return try {
            val res = http.delete("$baseUrl/api/v1/customers/$id")
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

    suspend fun getCustomerStatement(id: String): Result<CustomerStatementDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers/$id/statement")
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

    open suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> {
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

    open suspend fun getSales(
        sessionId: String? = null,
        folio: Long? = null,
        status: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        limit: Int = 100
    ): Result<List<SaleResponse>> {
        return try {
            val res = http.get("$baseUrl/api/v1/sales") {
                if (!sessionId.isNullOrBlank()) parameter("sessionId", sessionId)
                if (folio != null) parameter("folio", folio)
                if (!status.isNullOrBlank()) parameter("status", status)
                if (startDate != null) parameter("startDate", startDate)
                if (endDate != null) parameter("endDate", endDate)
                parameter("limit", limit)
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

    open suspend fun getSale(id: String): Result<SaleResponse> {
        return try {
            val res = http.get("$baseUrl/api/v1/sales/$id")
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

    open suspend fun cancelSale(id: String, reason: String, authorizedByUserId: String? = null): Result<SaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/sales/$id/cancel") {
                contentType(ContentType.Application.Json)
                setBody(CancelSaleRequest(reason = reason, authorizedByUserId = authorizedByUserId))
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

    open suspend fun refundSale(id: String, req: CreateSaleRefundRequest): Result<SaleRefundResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/sales/$id/refund") {
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

    open suspend fun getSaleRefunds(id: String): Result<List<SaleRefundResponse>> {
        return try {
            val res = http.get("$baseUrl/api/v1/sales/$id/refunds")
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

    open suspend fun parkSale(req: ParkSaleRequest): Result<ParkedSaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/parked-sales") {
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

    open suspend fun getParkedSales(registerId: String? = null): Result<List<ParkedSaleResponse>> {
        return try {
            val res = http.get("$baseUrl/api/v1/parked-sales") {
                if (!registerId.isNullOrBlank()) parameter("registerId", registerId)
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

    open suspend fun retrieveParkedSale(id: String, req: RetrieveParkedSaleRequest): Result<ParkedSaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/parked-sales/$id/retrieve") {
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

    open suspend fun discardParkedSale(id: String, userId: String = "cajero"): Result<ParkedSaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/parked-sales/$id/discard") {
                parameter("userId", userId)
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

    open suspend fun getSettings(): Result<SettingsDto> {
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

    open suspend fun updateSettings(settings: SettingsDto): Result<SettingsDto> {
        return try {
            val res = http.put("$baseUrl/api/v1/system/settings") {
                contentType(ContentType.Application.Json)
                setBody(settings)
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

    open suspend fun getDailyReport(
        dateEpochMs: Long? = null,
        sessionId: String? = null
    ): Result<DailyFinancialReportDto> {
        return try {
            val url = buildString {
                append("$baseUrl/api/v1/reports/daily")
                val params = mutableListOf<String>()
                dateEpochMs?.let { params.add("date=$it") }
                sessionId?.let { params.add("sessionId=$it") }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
            val res = http.get(url)
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

    suspend fun getUsers(includeInactive: Boolean = true): Result<List<UserDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/users") {
                parameter("includeInactive", includeInactive)
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

    suspend fun createUser(req: CreateUserRequest): Result<UserDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/users") {
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

    suspend fun updateUser(id: String, req: UpdateUserRequest): Result<UserDto> {
        return try {
            val res = http.put("$baseUrl/api/v1/users/$id") {
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

    suspend fun deactivateUser(id: String): Result<UserDto> {
        return try {
            val res = http.delete("$baseUrl/api/v1/users/$id")
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

    suspend fun reactivateUser(id: String): Result<UserDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/users/$id/reactivate")
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

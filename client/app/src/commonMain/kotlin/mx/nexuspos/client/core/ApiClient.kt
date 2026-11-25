package mx.nexuspos.client.core

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.cash.AuditedCashMovement
import mx.nexuspos.contracts.cash.CashMovementHistoryPage
import mx.nexuspos.contracts.cash.CashMovementHistoryRequest
import mx.nexuspos.contracts.cash.CashMovementRequest
import mx.nexuspos.contracts.cash.ReverseCashMovementRequest
import mx.nexuspos.contracts.cash.CashCutDetailRequest
import mx.nexuspos.contracts.cash.CashCutHistoryDetail
import mx.nexuspos.contracts.cash.CashCutHistoryPage
import mx.nexuspos.contracts.cash.CashCutHistoryRequest
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.CashRegisterDto
import mx.nexuspos.contracts.cash.SaveCashRegisterRequest
import mx.nexuspos.contracts.cash.CloseSessionRequest
import mx.nexuspos.contracts.cash.ClosedShiftSaleDetail
import mx.nexuspos.contracts.cash.ClosedShiftSalesPage
import mx.nexuspos.contracts.cash.ClosedShiftSalesRequest
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.cash.OpenSessionRequest
import mx.nexuspos.contracts.catalog.CatalogPriceList
import mx.nexuspos.contracts.catalog.UpdateCatalogPriceList
import mx.nexuspos.contracts.catalog.UpdatePresentationListPrice
import mx.nexuspos.contracts.catalog.CatalogImportRequest
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CreateDepartmentRequest
import mx.nexuspos.contracts.catalog.CreateProductRequest
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.UpdateDepartmentRequest
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.contracts.catalog.UpdatePresentationPricesRequest
import mx.nexuspos.contracts.catalog.UpdateProductKitRequest
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
import mx.nexuspos.contracts.identity.RoleDto
import mx.nexuspos.contracts.identity.PermissionDto
import mx.nexuspos.contracts.identity.SaveRoleRequest
import mx.nexuspos.contracts.inventory.CreateProductLotRequest
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.InventorySummaryDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.LotWriteOffRequest
import mx.nexuspos.contracts.inventory.ProductLotDto
import mx.nexuspos.contracts.inventory.RegisterStockMovementRequest
import mx.nexuspos.contracts.inventory.ReorderSuggestionsReportDto
import mx.nexuspos.contracts.inventory.StockMovementDto
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto
import mx.nexuspos.contracts.loyalty.LoyaltySummaryDto
import mx.nexuspos.contracts.mobile.MobileAdminRequest
import mx.nexuspos.contracts.mobile.MobileDevice
import mx.nexuspos.contracts.mobile.MobilePairingCode
import mx.nexuspos.contracts.reports.SalesAnalysisReport
import mx.nexuspos.contracts.reports.SalesAnalysisRequest
import mx.nexuspos.contracts.sales.CancelSaleRequest
import mx.nexuspos.contracts.sales.CreateSaleRefundRequest
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.contracts.sales.SaleRefundResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.purchases.*
import mx.nexuspos.contracts.system.*

internal class CashCutHistoryAccessDenied(message: String) : Exception(message)

internal class PurchaseRequestException(val status: Int, val error: ApiError) : Exception(error.message)

open class ApiClient(
    baseUrl: String = "http://localhost:8080",
    client: HttpClient? = null
) {
    internal var sessionToken: String? = null
    var baseUrl: String = baseUrl
        set(value) {
            if (value != field) sessionToken = null
            field = value
        }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val http = client ?: HttpClient {
        followRedirects = false
        install(createClientPlugin("DesktopSession") {
            onRequest { request, _ ->
                val expected = Url(this@ApiClient.baseUrl)
                val target = request.url.build()
                if (target.host == expected.host && target.port == expected.port && target.protocol == expected.protocol &&
                    !request.headers.contains(HttpHeaders.Authorization)
                ) {
                    this@ApiClient.sessionToken?.let { request.bearerAuth(it) }
                }
            }
        })
        install(ContentNegotiation) {
            json(this@ApiClient.json)
        }
    }

    suspend fun setupStatus(): Result<SetupStatus> = setupCall {
        http.get("$baseUrl/api/v1/setup/status")
    }

    suspend fun completeSetup(request: StoreSetupRequest): Result<StoreSetupResult> = setupCall {
        http.post("$baseUrl/api/v1/setup/complete") { contentType(ContentType.Application.Json); setBody(request) }
    }

    suspend fun getStoreProfile(): Result<StoreProfile> = setupCall {
        http.get("$baseUrl/api/v1/system/store-profile")
    }

    suspend fun updateStoreProfile(profile: StoreProfile): Result<StoreProfile> = setupCall {
        http.put("$baseUrl/api/v1/system/store-profile") { contentType(ContentType.Application.Json); setBody(profile) }
    }

    suspend fun adoptRoot(request: AdoptRootRequest): Result<Unit> = setupCommand {
        http.post("$baseUrl/api/v1/setup/adopt-root") { contentType(ContentType.Application.Json); setBody(request) }
    }

    suspend fun recoverRoot(request: RecoverRootRequest): Result<Unit> = setupCommand {
        http.post("$baseUrl/api/v1/setup/recover-root") { contentType(ContentType.Application.Json); setBody(request) }
    }

    suspend fun saleFolioStatus(): Result<SaleFolioStatus> = setupCall { http.get("$baseUrl/api/v1/system/sale-folio") }

    suspend fun cashRegisters(): Result<List<CashRegisterDto>> = setupCall { http.get("$baseUrl/api/v1/registers") }

    suspend fun saveCashRegister(id: String?, change: SaveCashRegisterRequest): Result<CashRegisterDto> = setupCall {
        val url = "$baseUrl/api/v1/registers"
        val body: HttpRequestBuilder.() -> Unit = {
            contentType(ContentType.Application.Json)
            setBody(change)
        }
        if (id == null) http.post(url, body) else http.put("$url/$id", body)
    }

    suspend fun handoffShift(id: String): Result<CashSessionSummary> = setupCall {
        http.post("$baseUrl/api/v1/cash-sessions/$id/handoff")
    }

    suspend fun priceLists(): Result<List<CatalogPriceList>> = setupCall { http.get("$baseUrl/api/v1/price-lists") }

    suspend fun updatePriceList(level: Int, change: UpdateCatalogPriceList): Result<CatalogPriceList> = setupCall {
        http.put("$baseUrl/api/v1/price-lists/$level") { contentType(ContentType.Application.Json); setBody(change) }
    }

    suspend fun updateListPrice(product: String, presentation: String, level: Int, cents: Long?): Result<ProductDto> = setupCall {
        http.put("$baseUrl/api/v1/products/$product/presentations/$presentation/list-prices/$level") {
            contentType(ContentType.Application.Json)
            setBody(UpdatePresentationListPrice(cents))
        }
    }

    suspend fun listRoles(): Result<List<RoleDto>> = setupCall { http.get("$baseUrl/api/v1/roles") }

    suspend fun permissionCatalog(): Result<List<PermissionDto>> = setupCall { http.get("$baseUrl/api/v1/roles/permissions") }

    suspend fun saveRole(id: String?, request: SaveRoleRequest): Result<RoleDto> = setupCall {
        val path = "$baseUrl/api/v1/roles"
        val body: HttpRequestBuilder.() -> Unit = {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (id == null) http.post(path, body) else http.put("$path/$id", body)
    }

    suspend fun deleteRole(id: String): Result<Unit> = setupCommand { http.delete("$baseUrl/api/v1/roles/$id") }

    suspend fun updateCommerce(preferences: CommercialPreferences): Result<CommercialPreferences> = setupCall {
        http.put("$baseUrl/api/v1/system/settings/commerce") {
            contentType(ContentType.Application.Json)
            setBody(preferences)
        }
    }

    suspend fun closeDesktopSession(token: String, serverUrl: String) {
        setupCommand { http.delete("$serverUrl/api/v1/auth/session") { bearerAuth(token) } }
    }

    private suspend fun setupCommand(call: suspend () -> HttpResponse): Result<Unit> = setupCall(call) { Unit }

    private suspend inline fun <reified T> setupCall(noinline call: suspend () -> HttpResponse): Result<T> =
        setupCall(call) { it.body<T>() }

    private suspend fun <T> setupCall(call: suspend () -> HttpResponse, decode: suspend (HttpResponse) -> T): Result<T> =
        try {
            val response = call()
            if (response.status.isSuccess()) Result.success(decode(response))
            else Result.failure(Exception(parseError(response).message))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    open suspend fun checkHealth(): Boolean {
        return try {
            val res = http.get("$baseUrl/health")
            res.status.isSuccess()
        } catch (e: CancellationException) {
            throw e
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

    open suspend fun getProduct(id: String): Result<ProductDto> {
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

    suspend fun updateProductKit(productId: String, request: UpdateProductKitRequest): Result<ProductDto> = try {
        val response = http.put("$baseUrl/api/v1/products/$productId/kit") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) Result.success(response.body())
        else Result.failure(Exception(parseError(response).message))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun updatePresentationPrices(
        productId: String,
        presentationId: String,
        request: UpdatePresentationPricesRequest
    ): Result<ProductDto> = try {
        val response = http.put("$baseUrl/api/v1/products/$productId/presentations/$presentationId/prices") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) Result.success(response.body())
        else Result.failure(Exception(parseError(response).message))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
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

    open suspend fun getDepartments(): Result<List<DepartmentDto>> {
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

    open suspend fun getInventorySummary(): Result<InventorySummaryDto> {
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

    open suspend fun getStockMovements(
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

    open suspend fun registerStockMovement(req: RegisterStockMovementRequest): Result<StockMovementDto> {
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
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                if (res.status == HttpStatusCode.NotFound && err.code == "NO_OPEN_SESSION") Result.success(null)
                else Result.failure(Exception(err.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun searchCashCutHistory(request: CashCutHistoryRequest): Result<CashCutHistoryPage> =
        supervisedReportCall("cash-sessions/history/search", request)

    open suspend fun getClosedCashCut(id: String, pin: String): Result<CashCutHistoryDetail> =
        supervisedReportCall("cash-sessions/history/${id.encodeURLPathPart()}", CashCutDetailRequest(pin))

    open suspend fun getClosedShiftSales(id: String, request: ClosedShiftSalesRequest): Result<ClosedShiftSalesPage> =
        supervisedReportCall("cash-sessions/history/${id.encodeURLPathPart()}/sales", request)

    open suspend fun getClosedShiftSale(id: String, saleId: String, pin: String): Result<ClosedShiftSaleDetail> =
        supervisedReportCall("cash-sessions/history/${id.encodeURLPathPart()}/sales/${saleId.encodeURLPathPart()}", CashCutDetailRequest(pin))

    open suspend fun getCashMovements(sessionId: String, request: CashMovementHistoryRequest): Result<CashMovementHistoryPage> =
        supervisedReportCall("cash-sessions/${sessionId.encodeURLPathPart()}/movements/history", request)

    open suspend fun reverseCashMovement(sessionId: String, movementId: String, request: ReverseCashMovementRequest): Result<AuditedCashMovement> =
        supervisedReportCall("cash-sessions/${sessionId.encodeURLPathPart()}/movements/${movementId.encodeURLPathPart()}/reverse", request)

    open suspend fun getSalesAnalysis(request: SalesAnalysisRequest): Result<SalesAnalysisReport> =
        supervisedReportCall("reports/sales-analysis", request)

    suspend fun issueMobilePairing(pin: String): Result<MobilePairingCode> =
        supervisedReportCall("mobile-admin/pairing", MobileAdminRequest(pin))

    suspend fun listMobileDevices(pin: String): Result<List<MobileDevice>> =
        supervisedReportCall("mobile-admin/devices", MobileAdminRequest(pin))

    suspend fun revokeMobileDevice(pin: String, id: String): Result<Boolean> =
        supervisedReportCall("mobile-admin/devices/${id.encodeURLPathPart()}/revoke", MobileAdminRequest(pin))

    private suspend inline fun <reified Request, reified Response> supervisedReportCall(
        path: String, request: Request
    ): Result<Response> = try {
        val response = http.post("$baseUrl/api/v1/$path") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.isSuccess()) Result.success(response.body())
        else {
            val message = parseError(response).message
            val denied = response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.Unauthorized
            Result.failure(if (denied) CashCutHistoryAccessDenied(message) else Exception(message))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
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

    open suspend fun searchCustomers(query: String? = null, includeInactive: Boolean = false): Result<List<CustomerDto>> {
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

    open suspend fun getCustomer(id: String): Result<CustomerDto> {
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

    suspend fun deactivateCustomer(id: String): Result<CustomerDto> =
        changeCustomerStatus { http.delete("$baseUrl/api/v1/customers/$id") }

    suspend fun reactivateCustomer(id: String): Result<CustomerDto> =
        changeCustomerStatus { http.post("$baseUrl/api/v1/customers/$id/reactivate") }

    private suspend fun changeCustomerStatus(request: suspend () -> HttpResponse): Result<CustomerDto> {
        return try {
            val response = request()
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(Exception(parseError(response).message))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    open suspend fun getCustomerStatement(id: String): Result<CustomerStatementDto> {
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

    open suspend fun registerCreditPayment(req: RegisterCreditPaymentRequest): Result<CreditMovementDto> {
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

    open suspend fun getCustomerLoyaltyMovements(customerId: String): Result<List<LoyaltyMovementDto>> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers/$customerId/loyalty")
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

    open suspend fun getCustomerLoyaltySummary(customerId: String): Result<LoyaltySummaryDto> {
        return try {
            val res = http.get("$baseUrl/api/v1/customers/$customerId/loyalty/summary")
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

    open suspend fun adjustCustomerLoyalty(customerId: String, req: AdjustLoyaltyBalanceRequest): Result<CustomerDto> {
        return try {
            val res = http.post("$baseUrl/api/v1/customers/$customerId/loyalty/adjust") {
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

    suspend fun recordCashMovement(sessionId: String, request: CashMovementRequest, approvalPin: String?): Result<Unit> = setupCommand {
        http.post("$baseUrl/api/v1/cash-sessions/$sessionId/movements") {
            contentType(ContentType.Application.Json)
            setBody(request)
            if (!approvalPin.isNullOrBlank()) header("X-Nexus-Cash-Approval", approvalPin)
        }
    }

    open suspend fun completeSale(req: CreateSaleRequest): Result<SaleResponse> = submitSale(req, null)

    suspend fun completeSaleWithCashApproval(req: CreateSaleRequest, pin: String): Result<SaleResponse> = submitSale(req, pin)

    private suspend fun submitSale(req: CreateSaleRequest, approvalPin: String?): Result<SaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/sales") {
                contentType(ContentType.Application.Json)
                setBody(req)
                if (!approvalPin.isNullOrBlank()) header("X-Nexus-Cash-Approval", approvalPin)
            }
            if (res.status.isSuccess()) {
                Result.success(res.body())
            } else {
                val err = parseError(res)
                Result.failure(SaleSubmissionRejected(err.message, res.status.value))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(SaleSubmissionUncertain(e))
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

    open suspend fun cancelSale(id: String, reason: String, authorizedByUserId: String? = null): Result<SaleResponse> =
        submitCancellation(id, reason, authorizedByUserId, null)

    suspend fun cancelSaleWithPin(id: String, reason: String, pin: String): Result<SaleResponse> =
        submitCancellation(id, reason, null, pin)

    private suspend fun submitCancellation(id: String, reason: String, authorizedByUserId: String?, pin: String?): Result<SaleResponse> {
        return try {
            val res = http.post("$baseUrl/api/v1/sales/$id/cancel") {
                contentType(ContentType.Application.Json)
                setBody(CancelSaleRequest(reason = reason, authorizedByUserId = authorizedByUserId))
                if (pin != null) header("X-Nexus-Action-Approval", pin)
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

    open suspend fun updateSettingsPreferences(patch: SettingsPreferencesPatch): Result<SettingsDto> {
        return try {
            val res = http.patch("$baseUrl/api/v1/system/settings/preferences") {
                contentType(ContentType.Application.Json)
                setBody(patch)
            }
            if (res.status.isSuccess()) Result.success(res.body())
            else Result.failure(Exception(parseError(res).message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun listSuppliers(): Result<List<SupplierDto>> =
        purchaseCall { http.get("$baseUrl/api/v1/purchases/suppliers") }

    suspend fun saveSupplier(id: String?, pin: String, data: SaveSupplierRequest): Result<SupplierDto> =
        purchaseCall {
            val url = "$baseUrl/api/v1/purchases/suppliers"
            val payload = PurchaseAuthorization(pin, data)
            if (id == null) {
                http.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            } else {
                http.put("$url/$id") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            }
        }

    suspend fun listPurchases(limit: Int, offset: Int): Result<List<PurchaseSummary>> =
        purchaseCall {
            http.get("$baseUrl/api/v1/purchases/receipts") {
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    suspend fun getPurchase(id: String): Result<PurchaseDetails> =
        purchaseCall { http.get("$baseUrl/api/v1/purchases/receipts/$id") }

    suspend fun listSupplierPayables(includeSettled: Boolean, limit: Int, offset: Int): Result<List<SupplierPayable>> =
        purchaseCall {
            http.get("$baseUrl/api/v1/purchases/payables") {
                parameter("includeSettled", includeSettled)
                parameter("limit", limit)
                parameter("offset", offset)
            }
        }

    suspend fun getSupplierPayable(id: String): Result<SupplierPayableDetail?> {
        val result = purchaseCall<SupplierPayableDetail> { http.get("$baseUrl/api/v1/purchases/payables/${id.encodeURLPathPart()}") }
        val error = result.exceptionOrNull() as? PurchaseRequestException
        return if (error?.status == 404 && error.error.code == "NO_SUPPLIER_PAYABLE") Result.success(null) else result
    }

    suspend fun registerSupplierPayable(id: String, pin: String, data: RegisterSupplierPayableRequest): Result<SupplierPayableDetail> =
        purchaseCall {
            http.post("$baseUrl/api/v1/purchases/payables/${id.encodeURLPathPart()}") {
                contentType(ContentType.Application.Json)
                setBody(PurchaseAuthorization(pin, data))
            }
        }

    suspend fun recordSupplierPayment(id: String, pin: String, data: RecordSupplierPaymentRequest): Result<SupplierPayableDetail> =
        purchaseCall {
            http.post("$baseUrl/api/v1/purchases/payables/${id.encodeURLPathPart()}/payments") {
                contentType(ContentType.Application.Json)
                setBody(PurchaseAuthorization(pin, data))
            }
        }

    open suspend fun receivePurchase(pin: String, data: ReceivePurchaseRequest): Result<PurchaseDetails> =
        purchaseCall {
            http.post("$baseUrl/api/v1/purchases/receipts") {
                contentType(ContentType.Application.Json)
                setBody(PurchaseAuthorization(pin, data))
            }
        }

    suspend fun returnPurchase(id: String, pin: String, data: ReturnPurchaseRequest): Result<PurchaseReturn> =
        purchaseCall {
            http.post("$baseUrl/api/v1/purchases/receipts/$id/returns") {
                contentType(ContentType.Application.Json)
                setBody(PurchaseAuthorization(pin, data))
            }
        }

    private suspend inline fun <reified T> purchaseCall(send: () -> HttpResponse): Result<T> =
        try {
            val response = send()
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(PurchaseRequestException(response.status.value, parseError(response)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
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

    open suspend fun getUsers(includeInactive: Boolean = true): Result<List<UserDto>> {
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

internal class SaleSubmissionRejected(message: String, val status: Int) : Exception(message)

internal class SaleSubmissionUncertain(cause: Throwable) : Exception(cause.message, cause)

internal fun Throwable.saleOutcomeIsUncertain(): Boolean = when (this) {
    is SaleSubmissionRejected -> status >= 500 || status == 408 || status == 429
    is SaleSubmissionUncertain -> true
    is io.ktor.utils.io.errors.IOException -> true
    else -> false
}

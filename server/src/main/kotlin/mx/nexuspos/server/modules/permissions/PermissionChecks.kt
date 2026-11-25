package mx.nexuspos.server.modules.permissions

import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.UpdateProductRequest
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.UpdateCustomerRequest
import mx.nexuspos.contracts.reports.SalesAnalysisReport
import mx.nexuspos.contracts.reports.SalesAnalysisTotals
import mx.nexuspos.contracts.sales.CreateSaleRequest
import mx.nexuspos.server.platform.ForbiddenException

internal fun Access.checkActor(id: String) {
    if (user.id != id) throw ForbiddenException("El operador debe coincidir con la sesión de esta terminal.")
}

internal fun Access.checkSale(request: CreateSaleRequest) {
    checkActor(request.cashierUserId)
    if (request.lines.any { it.productId == null && it.presentationId == null }) require("sales.quick")
    if (request.customerId != null) require("sales.customer")
    if (request.discountCents > 0 || request.lines.any { it.discountCents > 0 || it.discountPercent > 0 }) require("sales.discount")
    if (request.lines.any { it.manualPriceCents != null }) require("sales.price.manual")
    if (request.lines.any { it.productId != null && it.presentationId.isNullOrBlank() && it.manualPriceCents == null }) {
        throw ForbiddenException("Indica la presentación para verificar el precio de catálogo.")
    }
    for (list in request.lines.mapNotNull { it.priceListLevel }.distinct()) require("sales.price.$list")
    val methods = request.payments.map { it.method.lowercase() }.distinct()
    if (methods.size > 1) require("sales.pay.mixed")
    for (method in methods) require("sales.pay.$method")
}

internal fun Access.checkCustomerEdit(previous: CustomerDto, request: UpdateCustomerRequest) {
    if (previous.isActive != request.isActive) {
        require(if (request.isActive) "customers.reactivate" else "customers.deactivate")
    }
    if (previous.creditEnabled != request.creditEnabled || previous.hasCreditLimit != request.hasCreditLimit ||
        previous.creditLimitCents != (if (request.hasCreditLimit) request.creditLimitCents else 0L)) {
        require("customers.credit.configure")
    }
}

internal fun Access.checkProductEdit(previous: ProductDto, request: UpdateProductRequest) {
    if (previous.baseUnit != request.baseUnit) require("products.unit.edit")
    if (previous.costCents != request.costCents) require("products.cost.edit")
    if (previous.taxRate != request.taxRate) require("products.tax.edit")
    if (previous.isActive != request.isActive) require(if (request.isActive) "products.reactivate" else "products.deactivate")
    if (previous.usesInventory != request.usesInventory || previous.minStockMillis != request.minStockMillis ||
        previous.maxStockMillis != request.maxStockMillis || previous.isPerishable != request.isPerishable ||
        previous.shelfLifeAlertDays != request.shelfLifeAlertDays) require("products.stock.edit")
    if (previous.presentations.size != request.presentations.size || request.presentations.any { item ->
        val previousItem = previous.presentations.firstOrNull {
            if (item.id == null) it.barcode == item.barcode.trim() else it.id == item.id
        }
        previousItem?.priceCents != item.priceCents
    }) require("products.price.edit")
}

internal fun Access?.projectProduct(product: ProductDto): ProductDto = if (this == null) product else product.copy(
    costCents = product.costCents.takeIf { "products.cost.view" in permissions } ?: 0,
    currentStockMillis = product.currentStockMillis.takeIf { "inventory.stock" in permissions } ?: 0
)

internal fun Access?.projectSalesAnalysis(report: SalesAnalysisReport): SalesAnalysisReport {
    if (this == null || "reports.margin" in permissions) return report
    fun SalesAnalysisTotals.hideCosts() = copy(costCents = 0, profitCents = 0, estimatedCostEvents = 0)
    return report.copy(totals = report.totals.hideCosts(), rows = report.rows.map { it.copy(totals = it.totals.hideCosts()) }, costsVisible = false)
}

internal fun Access?.ownSalesFilter(): String? =
    this?.user?.id?.takeIf { "transactions.all" !in permissions }

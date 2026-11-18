package mx.nexuspos.contracts.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogImportRowDto(
    val rowNumber: Int = 0,
    val sku: String,
    val barcode: String = "",
    val name: String,
    val department: String = "General",
    val baseUnit: String = "PZA", // PZA, KG, LT
    val costCents: Long = 0L,
    val priceCents: Long = 0L,
    val taxRate: Double = 0.0,
    val currentStockMillis: Long = 0L,
    val minStockMillis: Long = 0L,
    val isActive: Boolean = true
)

@Serializable
data class CatalogImportRequest(
    val rows: List<CatalogImportRowDto>
)

@Serializable
data class CatalogImportRowResult(
    val rowNumber: Int,
    val sku: String,
    val barcode: String,
    val name: String,
    val status: String, // CREATED, UPDATED, SKIPPED, ERROR
    val message: String? = null
)

@Serializable
data class CatalogImportResponse(
    val totalRows: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val errorCount: Int,
    val rows: List<CatalogImportRowResult>
)

package mx.nexuspos.client.features.offline

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.ProductDto

class OfflineCatalogCache(
    private val storage: LocalPersistenceProvider = getDefaultPersistenceProvider()
) {
    private val fileName = "offline_catalog.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var inMemoryCatalog: List<ProductDto> = emptyList()

    init {
        loadFromStorage()
    }

    fun loadFromStorage(): List<ProductDto> {
        val raw = runCatching { storage.readText(fileName) }.getOrNull()
        if (raw.isNullOrBlank()) {
            inMemoryCatalog = emptyList()
            return emptyList()
        }
        return try {
            val list = json.decodeFromString<List<ProductDto>>(raw).map { it.copy(costCents = 0) }
            inMemoryCatalog = list
            list
        } catch (e: Exception) {
            inMemoryCatalog = emptyList()
            emptyList()
        }
    }

    fun saveCatalog(products: List<ProductDto>) {
        inMemoryCatalog = products.map { it.copy(costCents = 0) }
        try {
            val encoded = json.encodeToString(inMemoryCatalog)
            storage.writeText(fileName, encoded)
        } catch (e: Exception) {
            // Log/ignore
        }
    }

    fun findProductByBarcode(barcode: String): ProductDto? {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return null
        return inMemoryCatalog.firstOrNull { prod ->
            prod.sku.equals(trimmed, ignoreCase = true) ||
                prod.presentations.any { it.barcode.equals(trimmed, ignoreCase = true) }
        }
    }

    fun searchProducts(query: String): List<ProductDto> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return inMemoryCatalog
        return inMemoryCatalog.filter { prod ->
            prod.name.lowercase().contains(trimmed) ||
                prod.sku.lowercase().contains(trimmed) ||
                prod.presentations.any { it.barcode.contains(trimmed) || it.name.lowercase().contains(trimmed) }
        }
    }

    fun count(): Int = inMemoryCatalog.size

    fun clear() {
        inMemoryCatalog = emptyList()
        storage.delete(fileName)
    }
}

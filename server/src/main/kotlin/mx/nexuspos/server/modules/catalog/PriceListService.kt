package mx.nexuspos.server.modules.catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.catalog.CatalogPriceList
import mx.nexuspos.contracts.catalog.UpdateCatalogPriceList
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException

internal class PriceListService(private val db: DatabaseFactory) {
    fun list(): List<CatalogPriceList> = db.inTransaction { connection ->
        connection.prepareStatement("SELECT level_id, name, enabled FROM price_lists ORDER BY level_id").use {
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(CatalogPriceList(rows.getInt(1), rows.getString(2), rows.getBoolean(3))) } }
        }
    }

    fun update(level: Int, request: UpdateCatalogPriceList): CatalogPriceList {
        val name = request.name.trim()
        if (level !in 2..5 || name.length !in 1..60) throw ValidationException("Captura un nombre válido para esta lista de precios.")
        return db.inTransaction { connection ->
            connection.prepareStatement("UPDATE price_lists SET name = ?, enabled = ? WHERE level_id = ?").use {
                it.setString(1, name)
                it.setBoolean(2, request.enabled)
                it.setInt(3, level)
                it.executeUpdate()
            }
            CatalogPriceList(level, name, request.enabled)
        }
    }

    fun setPrice(productId: String, presentationId: String, level: Int, cents: Long?) {
        if (level !in 2..5 || (cents != null && cents < 0)) throw ValidationException("Captura un precio válido.")
        db.inTransaction { connection ->
            val prices = connection.prepareStatement(
                "SELECT price_list_prices FROM product_presentations WHERE id = ? AND product_id = ? AND is_active FOR UPDATE"
            ).use {
                it.setString(1, presentationId)
                it.setString(2, productId)
                it.executeQuery().use { row ->
                    if (!row.next()) throw NotFoundException("Presentación no encontrada.")
                    Json.decodeFromString<Map<Int, Long>>(row.getString(1))
                }
            }
            val updated = if (cents == null) prices - level else prices + (level to cents)
            connection.prepareStatement("UPDATE product_presentations SET price_list_prices = ? WHERE id = ?").use {
                it.setString(1, Json.encodeToString(updated))
                it.setString(2, presentationId)
                it.executeUpdate()
            }
        }
    }
}

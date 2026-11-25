package mx.nexuspos.server.modules.sales

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.sales.ParkSaleRequest
import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.contracts.sales.ParkedSaleItemDto
import mx.nexuspos.contracts.sales.ParkedSaleResponse
import mx.nexuspos.contracts.sales.RetrieveParkedSaleRequest
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID

class ParkedSalesService(private val db: DatabaseFactory) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun parkSale(request: ParkSaleRequest): ParkedSaleResponse {
        if (request.items.isEmpty()) {
            throw ValidationException("No se puede aparcar un ticket sin productos.")
        }
        val label = request.customLabel.trim().ifBlank { "Ticket sin nombre" }
        val id = "parked-${UUID.randomUUID().toString().take(12)}"
        val now = System.currentTimeMillis()
        val itemsJson = json.encodeToString(request.items)

        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                """
                INSERT INTO parked_sales (
                    id, custom_label, cashier_user_id, cashier_name,
                    customer_id, customer_name, register_id, total_cents,
                    items_count, items_json, parked_at, status,
                    created_at, updated_at, note
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PARKED', ?, ?, ?)
                """.trimIndent()
            )
            stmt.setString(1, id)
            stmt.setString(2, label)
            stmt.setString(3, request.cashierUserId)
            stmt.setString(4, request.cashierName)
            stmt.setString(5, request.customerId)
            stmt.setString(6, request.customerName)
            stmt.setString(7, request.registerId)
            stmt.setLong(8, request.totalCents)
            stmt.setInt(9, request.items.size)
            stmt.setString(10, itemsJson)
            stmt.setLong(11, now)
            stmt.setLong(12, now)
            stmt.setLong(13, now)
            stmt.setString(14, request.note)
            stmt.executeUpdate()
            stmt.close()

            ParkedSaleResponse(
                id = id,
                customLabel = label,
                cashierUserId = request.cashierUserId,
                cashierName = request.cashierName,
                registerId = request.registerId,
                customerId = request.customerId,
                customerName = request.customerName,
                totalCents = request.totalCents,
                itemsCount = request.items.size,
                items = request.items,
                parkedAt = now,
                status = "PARKED",
                note = request.note
            )
        }
    }

    fun getParkedSales(registerId: String? = null): List<ParkedSaleResponse> {
        return db.inTransaction { conn ->
            val sql = if (registerId != null && registerId.isNotBlank()) {
                "SELECT * FROM parked_sales WHERE status = 'PARKED' AND register_id = ? ORDER BY parked_at DESC"
            } else {
                "SELECT * FROM parked_sales WHERE status = 'PARKED' ORDER BY parked_at DESC"
            }
            val stmt = conn.prepareStatement(sql)
            if (registerId != null && registerId.isNotBlank()) {
                stmt.setString(1, registerId)
            }
            val rs = stmt.executeQuery()
            val list = mutableListOf<ParkedSaleResponse>()
            while (rs.next()) {
                val itemsRaw = rs.getString("items_json")
                val parsedItems = try {
                    json.decodeFromString<List<ParkedSaleItemDto>>(itemsRaw)
                } catch (e: Exception) {
                    emptyList()
                }
                list.add(
                    ParkedSaleResponse(
                        id = rs.getString("id"),
                        customLabel = rs.getString("custom_label"),
                        cashierUserId = rs.getString("cashier_user_id"),
                        cashierName = rs.getString("cashier_name"),
                        registerId = rs.getString("register_id"),
                        customerId = rs.getString("customer_id"),
                        customerName = rs.getString("customer_name"),
                        totalCents = rs.getLong("total_cents"),
                        itemsCount = rs.getInt("items_count"),
                        items = parsedItems,
                        parkedAt = rs.getLong("parked_at"),
                        status = rs.getString("status"),
                        retrievedAt = rs.getObject("retrieved_at")?.let { rs.getLong("retrieved_at") },
                        retrievedByUserId = rs.getString("retrieved_by_user_id"),
                        note = rs.getString("note")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun retrieveParkedSale(id: String, request: RetrieveParkedSaleRequest): ParkedSaleResponse {
        val now = System.currentTimeMillis()
        return db.inTransaction { conn ->
            val selectStmt = conn.prepareStatement("SELECT * FROM parked_sales WHERE id = ? FOR UPDATE")
            selectStmt.setString(1, id)
            val rs = selectStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                selectStmt.close()
                throw NotFoundException("Ticket en espera '$id' no encontrado.")
            }
            val currentStatus = rs.getString("status")
            if (currentStatus != "PARKED") {
                rs.close()
                selectStmt.close()
                throw ValidationException("El ticket en espera '$id' ya fue $currentStatus previamente.")
            }

            val customLabel = rs.getString("custom_label")
            val note = rs.getString("note")
            val cashierUserId = rs.getString("cashier_user_id")
            val cashierName = rs.getString("cashier_name")
            val customerId = rs.getString("customer_id")
            val customerName = rs.getString("customer_name")
            val totalCents = rs.getLong("total_cents")
            val itemsCount = rs.getInt("items_count")
            val parkedAt = rs.getLong("parked_at")
            val itemsRaw = rs.getString("items_json")
            val parsedItems = try {
                json.decodeFromString<List<ParkedSaleItemDto>>(itemsRaw)
            } catch (e: Exception) {
                emptyList()
            }
            rs.close()
            selectStmt.close()

            val pricedItems = conn.prepareStatement(
                "SELECT price_cents, price_tiers_json, product_id FROM product_presentations WHERE id = ? AND is_active = TRUE"
            ).use { statement ->
                parsedItems.map { item ->
                    val presentationId = item.presentationId ?: return@map item
                    statement.setString(1, presentationId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) item else item.copy(
                            publicPriceCents = result.getLong("price_cents"),
                            priceTiers = json.decodeFromString(result.getString("price_tiers_json")),
                            kitComponents = conn.prepareStatement("""
                                SELECT component_product_id, quantity_millis FROM product_kit_components
                                WHERE kit_product_id = ? ORDER BY component_product_id
                            """.trimIndent()).use { query ->
                                query.setString(1, result.getString("product_id"))
                                query.executeQuery().use { rows ->
                                    buildList {
                                        while (rows.next()) add(KitComponent(rows.getString(1), rows.getLong(2)))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            val updateStmt = conn.prepareStatement(
                """
                UPDATE parked_sales
                SET status = 'RETRIEVED',
                    retrieved_at = ?,
                    retrieved_by_user_id = ?,
                    register_id = ?,
                    updated_at = ?
                WHERE id = ?
                """.trimIndent()
            )
            updateStmt.setLong(1, now)
            updateStmt.setString(2, request.retrievedByUserId)
            updateStmt.setString(3, request.registerId)
            updateStmt.setLong(4, now)
            updateStmt.setString(5, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            ParkedSaleResponse(
                id = id,
                customLabel = customLabel,
                cashierUserId = cashierUserId,
                cashierName = cashierName,
                registerId = request.registerId,
                customerId = customerId,
                customerName = customerName,
                totalCents = totalCents,
                itemsCount = itemsCount,
                items = pricedItems,
                parkedAt = parkedAt,
                status = "RETRIEVED",
                retrievedAt = now,
                retrievedByUserId = request.retrievedByUserId,
                note = note
            )
        }
    }

    fun discardParkedSale(id: String, userId: String): ParkedSaleResponse {
        val now = System.currentTimeMillis()
        return db.inTransaction { conn ->
            val selectStmt = conn.prepareStatement("SELECT * FROM parked_sales WHERE id = ? FOR UPDATE")
            selectStmt.setString(1, id)
            val rs = selectStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                selectStmt.close()
                throw NotFoundException("Ticket en espera '$id' no encontrado.")
            }
            val currentStatus = rs.getString("status")
            if (currentStatus != "PARKED") {
                rs.close()
                selectStmt.close()
                throw ValidationException("El ticket en espera '$id' ya fue $currentStatus previamente.")
            }

            val customLabel = rs.getString("custom_label")
            val note = rs.getString("note")
            val cashierUserId = rs.getString("cashier_user_id")
            val cashierName = rs.getString("cashier_name")
            val registerId = rs.getString("register_id")
            val customerId = rs.getString("customer_id")
            val customerName = rs.getString("customer_name")
            val totalCents = rs.getLong("total_cents")
            val itemsCount = rs.getInt("items_count")
            val parkedAt = rs.getLong("parked_at")
            val itemsRaw = rs.getString("items_json")
            val parsedItems = try {
                json.decodeFromString<List<ParkedSaleItemDto>>(itemsRaw)
            } catch (e: Exception) {
                emptyList()
            }
            rs.close()
            selectStmt.close()

            val updateStmt = conn.prepareStatement(
                """
                UPDATE parked_sales
                SET status = 'DISCARDED',
                    updated_at = ?
                WHERE id = ?
                """.trimIndent()
            )
            updateStmt.setLong(1, now)
            updateStmt.setString(2, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            ParkedSaleResponse(
                id = id,
                customLabel = customLabel,
                cashierUserId = cashierUserId,
                cashierName = cashierName,
                registerId = registerId,
                customerId = customerId,
                customerName = customerName,
                totalCents = totalCents,
                itemsCount = itemsCount,
                items = parsedItems,
                parkedAt = parkedAt,
                status = "DISCARDED",
                note = note
            )
        }
    }
}

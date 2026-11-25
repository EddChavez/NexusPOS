package mx.nexuspos.server.modules.purchases

import mx.nexuspos.contracts.purchases.ReceivePurchaseLine
import mx.nexuspos.server.platform.InsufficientStockException
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.util.UUID

internal data class PurchaseProduct(
    val id: String, val name: String, val sku: String, val unit: String,
    val perishable: Boolean, val receivable: Boolean
) {
    fun validateQuantity(quantity: Long) {
        if (quantity <= 0 || (unit == "PZA" && quantity % 1000L != 0L)) {
            throw ValidationException("Captura una cantidad válida en $unit para $name.")
        }
    }
}

internal class PurchaseInventory(private val conn: Connection, private val now: Long) {
    fun lockProducts(ids: Collection<String>): Map<String, PurchaseProduct> = conn.prepareStatement(
        """
        SELECT p.name, p.sku, p.base_unit, p.is_perishable,
               p.is_active AND p.uses_inventory AND NOT EXISTS (
                   SELECT 1 FROM product_kit_components k WHERE k.kit_product_id = p.id
               ) AS receivable
        FROM products p WHERE p.id = ? FOR UPDATE
        """.trimIndent()
    ).use { query ->
        ids.distinct().sorted().associateWith { id ->
            query.setString(1, id)
            query.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("El producto $id no existe.")
                PurchaseProduct(id, row.getString(1), row.getString(2), row.getString(3), row.getBoolean(4), row.getBoolean(5))
            }
        }
    }

    fun receiveLot(line: ReceivePurchaseLine): String? {
        val number = line.lotNumber ?: return null
        val date = checkNotNull(line.expirationDate)
        val candidate = "lot-" + UUID.randomUUID()
        conn.prepareStatement(
            """
            INSERT INTO product_lots
                (id, product_id, lot_number, expiration_date, quantity_millis, initial_quantity_millis, cost_cents, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?) ON CONFLICT DO NOTHING
            """.trimIndent()
        ).use {
            it.setString(1, candidate)
            it.setString(2, line.productId)
            it.setString(3, number)
            it.setString(4, date)
            it.setLong(5, line.unitCostCents)
            it.setLong(6, now)
            it.setLong(7, now)
            it.executeUpdate()
        }
        conn.prepareStatement(
            """
            UPDATE product_lots SET quantity_millis = quantity_millis + ?, initial_quantity_millis = initial_quantity_millis + ?,
                cost_cents = ?, is_active = TRUE, updated_at = ?
            WHERE product_id = ? AND lot_number = ? AND expiration_date = ?
            """.trimIndent()
        ).use {
            it.setLong(1, line.quantityMillis)
            it.setLong(2, line.quantityMillis)
            it.setLong(3, line.unitCostCents)
            it.setLong(4, now)
            it.setString(5, line.productId)
            it.setString(6, number)
            it.setString(7, date)
            check(it.executeUpdate() == 1)
        }
        return conn.prepareStatement(
            "SELECT id FROM product_lots WHERE product_id = ? AND lot_number = ? AND expiration_date = ?"
        ).use {
            it.setString(1, line.productId)
            it.setString(2, number)
            it.setString(3, date)
            it.executeQuery().use { row -> check(row.next()); row.getString(1) }
        }
    }

    fun lockLots(productId: String): Map<String, Long> = conn.prepareStatement(
        "SELECT id, quantity_millis FROM product_lots WHERE product_id = ? AND is_active = TRUE ORDER BY id FOR UPDATE"
    ).use {
        it.setString(1, productId)
        it.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) } }
    }

    fun removeFromLot(lotId: String, quantity: Long) {
        conn.prepareStatement(
            """
            UPDATE product_lots SET quantity_millis = quantity_millis - ?, is_active = quantity_millis > ?, updated_at = ?
            WHERE id = ? AND is_active = TRUE AND quantity_millis >= ?
            """.trimIndent()
        ).use {
            it.setLong(1, quantity)
            it.setLong(2, quantity)
            it.setLong(3, now)
            it.setString(4, lotId)
            it.setLong(5, quantity)
            if (it.executeUpdate() != 1) throw InsufficientStockException("El lote no tiene suficiente mercancía disponible.")
        }
    }

    fun changeStock(product: PurchaseProduct, delta: Long, minimumRemaining: Long? = null) {
        conn.prepareStatement(
            "INSERT INTO stock_balances (product_id, quantity_millis, updated_at) VALUES (?, 0, ?) ON CONFLICT DO NOTHING"
        ).use {
            it.setString(1, product.id)
            it.setLong(2, now)
            it.executeUpdate()
        }
        val before = conn.prepareStatement("SELECT quantity_millis FROM stock_balances WHERE product_id = ? FOR UPDATE").use {
            it.setString(1, product.id)
            it.executeQuery().use { row -> check(row.next()); row.getLong(1) }
        }
        val after = checkedPurchaseSum(before, delta)
        if (minimumRemaining != null && after < minimumRemaining) {
            throw InsufficientStockException("La existencia disponible de ${product.name} no alcanza para devolver esa cantidad sin afectar otros lotes.")
        }
        conn.prepareStatement("UPDATE stock_balances SET quantity_millis = ?, updated_at = ? WHERE product_id = ?").use {
            it.setLong(1, after)
            it.setLong(2, now)
            it.setString(3, product.id)
            it.executeUpdate()
        }
    }

    fun updateCatalogCost(productId: String, unitCost: Long) {
        conn.prepareStatement("UPDATE products SET cost_cents = ?, updated_at = ? WHERE id = ?").use {
            it.setLong(1, unitCost)
            it.setLong(2, now)
            it.setString(3, productId)
            it.executeUpdate()
        }
    }

    fun recordMovement(productId: String, delta: Long, reason: String, reference: String, operatorId: String) {
        conn.prepareStatement(
            """
            INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use {
            it.setString(1, "mov-" + UUID.randomUUID())
            it.setString(2, productId)
            it.setString(3, if (delta > 0) "ENTRY" else "PURCHASE_RETURN")
            it.setLong(4, delta)
            it.setString(5, reason)
            it.setString(6, reference)
            it.setString(7, operatorId)
            it.setLong(8, now)
            it.executeUpdate()
        }
    }
}

internal fun checkedPurchaseSum(left: Long, right: Long): Long =
    try { Math.addExact(left, right) } catch (_: ArithmeticException) {
        throw ValidationException("La cantidad o el importe supera el valor permitido.")
    }

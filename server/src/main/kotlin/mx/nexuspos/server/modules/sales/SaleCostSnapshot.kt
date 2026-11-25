package mx.nexuspos.server.modules.sales

import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.server.platform.ValidationException
import java.math.BigInteger
import java.sql.Connection

internal data class SaleCatalogSnapshot(
    val costCents: Long,
    val name: String,
    val sku: String,
    val unit: String,
    val departmentId: String?,
    val departmentName: String?
)

internal fun loadSaleCatalogSnapshots(conn: Connection, ids: Set<String>): Map<String, SaleCatalogSnapshot> = buildMap {
    // Keep checkout independent of the size of the catalog, including when selling kits.
    ids.chunked(250).forEach { batch ->
        val parameters = batch.joinToString(",") { "?" }
        conn.prepareStatement(
            """
            SELECT p.id, p.cost_cents, p.name, p.sku, p.base_unit, p.department_id, d.name AS department_name
            FROM products p LEFT JOIN departments d ON d.id = p.department_id
            WHERE p.id IN ($parameters)
            """.trimIndent()
        ).use { query ->
            batch.forEachIndexed { index, id -> query.setString(index + 1, id) }
            query.executeQuery().use { rows ->
                while (rows.next()) put(rows.getString("id"), SaleCatalogSnapshot(
                    rows.getLong("cost_cents"), rows.getString("name"), rows.getString("sku"), rows.getString("base_unit"),
                    rows.getString("department_id"), rows.getString("department_name")
                ))
            }
        }
    }
}

internal fun snapshotCost(
    productId: String?,
    baseQuantityMillis: Long,
    kitStock: List<KitComponent>,
    catalog: Map<String, SaleCatalogSnapshot>
): Long? {
    val milliCents = if (kitStock.isNotEmpty()) {
        kitStock.fold(BigInteger.ZERO) { total, component ->
            val cost = catalog[component.productId]?.costCents ?: return null
            total + BigInteger.valueOf(component.quantityMillis) * BigInteger.valueOf(cost)
        }
    } else {
        val cost = productId?.let(catalog::get)?.costCents ?: return null
        BigInteger.valueOf(baseQuantityMillis) * BigInteger.valueOf(cost)
    }
    return roundSaleCost(milliCents, 1000L)
}

internal fun roundSaleCost(value: BigInteger, divisor: Long): Long = try {
    ((value + BigInteger.valueOf(divisor / 2)) / BigInteger.valueOf(divisor)).longValueExact()
} catch (_: ArithmeticException) {
    throw ValidationException("El costo de la partida supera el importe permitido.")
}

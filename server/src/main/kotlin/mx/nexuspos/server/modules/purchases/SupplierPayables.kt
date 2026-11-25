package mx.nexuspos.server.modules.purchases

import mx.nexuspos.contracts.purchases.SupplierPayable
import mx.nexuspos.contracts.purchases.SupplierPayableDetail
import mx.nexuspos.contracts.purchases.SupplierPayment
import mx.nexuspos.contracts.purchases.SupplierPaymentMethod
import java.sql.Connection
import java.sql.ResultSet

private val payableQuery = """
    SELECT p.id, p.supplier_name, p.document_number, p.total_cents, a.due_date,
        COALESCE((SELECT SUM(r.total_cents) FROM purchase_returns r WHERE r.receipt_id = p.id), 0) AS returned_cents,
        COALESCE((SELECT SUM(m.amount_cents) FROM supplier_payments m WHERE m.receipt_id = p.id), 0) AS paid_cents
    FROM supplier_payables a JOIN purchase_receipts p ON p.id = a.receipt_id
""".trimIndent()

internal fun listSupplierPayables(connection: Connection, settled: Boolean, limit: Int, offset: Int): List<SupplierPayable> {
    val condition = if (settled) "" else "WHERE total_cents - returned_cents > paid_cents"
    return connection.prepareStatement(
        "SELECT * FROM ($payableQuery) accounts $condition ORDER BY due_date, id LIMIT ? OFFSET ?"
    ).use {
        it.setInt(1, limit.coerceIn(1, 100))
        it.setInt(2, offset.coerceAtLeast(0))
        it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.payable()) } }
    }
}

internal fun readSupplierPayable(connection: Connection, receiptId: String): SupplierPayable? =
    connection.prepareStatement("$payableQuery WHERE p.id = ?").use {
        it.setString(1, receiptId)
        it.executeQuery().use { row -> if (row.next()) row.payable() else null }
    }

internal fun readSupplierPayableDetail(connection: Connection, receiptId: String): SupplierPayableDetail? {
    val account = readSupplierPayable(connection, receiptId) ?: return null
    val payments = connection.prepareStatement(
        """
        SELECT m.*, u.name AS operator_name FROM supplier_payments m JOIN users u ON u.id = m.paid_by
        WHERE m.receipt_id = ? ORDER BY m.created_at DESC, m.id DESC
        """.trimIndent()
    ).use {
        it.setString(1, receiptId)
        it.executeQuery().use { rows ->
            buildList {
                while (rows.next()) add(
                    SupplierPayment(
                        rows.getString("id"), rows.getLong("amount_cents"),
                        SupplierPaymentMethod.valueOf(rows.getString("method")), rows.getString("reference"),
                        rows.getString("operator_name"), rows.getLong("created_at"), rows.getString("cash_movement_id")
                    )
                )
            }
        }
    }
    return SupplierPayableDetail(account, payments)
}

private fun ResultSet.payable() = SupplierPayable(
    getString("id"), getString("supplier_name"), getString("document_number"), getString("due_date"),
    getLong("total_cents"), getLong("returned_cents"), getLong("paid_cents")
)

package mx.nexuspos.server.modules.purchases

import mx.nexuspos.contracts.purchases.*
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.DuplicatePurchaseDocumentException
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal fun ResultSet.supplier() = SupplierDto(
    getString("id"), getString("name"), getString("tax_id"), getString("phone"), getString("email"), getBoolean("is_active")
)

internal fun ResultSet.summary() = PurchaseSummary(
    getString("id"), getString("supplier_id"), getString("supplier_name"),
    PurchaseDocumentType.valueOf(getString("document_type")), getString("document_number"),
    getLong("total_cents"), getLong("returned_cents"), getLong("created_at")
)

internal fun lockReceipt(conn: Connection, id: String) {
    conn.prepareStatement("SELECT id FROM purchase_receipts WHERE id = ? FOR UPDATE").use {
        it.setString(1, id)
        it.executeQuery().use { row -> if (!row.next()) throw NotFoundException("No se encontró la compra.") }
    }
}

internal fun insertReceipt(
    conn: Connection, id: String, request: ReceivePurchaseRequest, supplier: SupplierDto,
    total: Long, operatorId: String, now: Long
) {
    conn.prepareStatement(
        """
        INSERT INTO purchase_receipts
            (id, supplier_id, supplier_name, document_type, document_number, total_cents, notes, received_by, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
        """.trimIndent()
    ).use {
        it.setString(1, id)
        it.setString(2, supplier.id)
        it.setString(3, supplier.name)
        it.setString(4, request.documentType.name)
        it.setString(5, request.documentNumber)
        it.setLong(6, total)
        it.setString(7, request.notes)
        it.setString(8, operatorId)
        it.setLong(9, now)
        if (it.executeUpdate() != 1) throw DuplicatePurchaseDocumentException("Ese documento ya se registró para el proveedor. Consulta el historial de compras.")
    }
}

internal fun insertReceiptLine(
    conn: Connection, receiptId: String, index: Int, line: ReceivePurchaseLine,
    product: PurchaseProduct, total: Long, lotId: String?
) {
    conn.prepareStatement(
        """
        INSERT INTO purchase_receipt_lines
            (id, receipt_id, position, product_id, product_name, sku, base_unit,
             quantity_millis, unit_cost_cents, total_cents, lot_id, lot_number, expiration_date)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use {
        it.setString(1, "pl-" + UUID.randomUUID())
        it.setString(2, receiptId)
        it.setInt(3, index)
        it.setString(4, product.id)
        it.setString(5, product.name)
        it.setString(6, product.sku)
        it.setString(7, product.unit)
        it.setLong(8, line.quantityMillis)
        it.setLong(9, line.unitCostCents)
        it.setLong(10, total)
        it.setString(11, lotId)
        it.setString(12, line.lotNumber)
        it.setString(13, line.expirationDate)
        it.executeUpdate()
    }
}

internal data class StoredPurchaseLine(val line: PurchaseLine, val lotId: String?)

internal fun readLines(conn: Connection, id: String): List<StoredPurchaseLine> = conn.prepareStatement(
    """
    SELECT l.*, COALESCE((SELECT SUM(r.quantity_millis) FROM purchase_return_lines r WHERE r.receipt_line_id = l.id), 0) AS returned_millis
    FROM purchase_receipt_lines l WHERE l.receipt_id = ? ORDER BY l.position
    """.trimIndent()
).use {
    it.setString(1, id)
    it.executeQuery().use { rows ->
        buildList {
            while (rows.next()) {
                add(
                    StoredPurchaseLine(
                        PurchaseLine(
                            rows.getString("id"), rows.getString("product_id"), rows.getString("product_name"),
                            rows.getString("sku"), rows.getString("base_unit"), rows.getLong("quantity_millis"),
                            rows.getLong("unit_cost_cents"), rows.getLong("total_cents"),
                            rows.getString("lot_number"), rows.getString("expiration_date"), rows.getLong("returned_millis")
                        ),
                        rows.getString("lot_id")
                    )
                )
            }
        }
    }
}

internal fun readReceipt(conn: Connection, id: String): PurchaseDetails {
    val returns = readReturns(conn, id)
    return conn.prepareStatement(
        """
        SELECT p.*, u.name AS operator_name, ? AS returned_cents
        FROM purchase_receipts p JOIN users u ON u.id = p.received_by WHERE p.id = ?
        """.trimIndent()
    ).use {
        it.setLong(1, returns.sumOf { returned -> returned.totalCents })
        it.setString(2, id)
        it.executeQuery().use { row ->
            if (!row.next()) throw NotFoundException("No se encontró la compra.")
            PurchaseDetails(row.summary(), row.getString("operator_name"), row.getString("notes"), readLines(conn, id).map { line -> line.line }, returns)
        }
    }
}

internal fun operatorName(conn: Connection, operatorId: String): String =
    conn.prepareStatement("SELECT name FROM users WHERE id = ?").use {
        it.setString(1, operatorId)
        it.executeQuery().use { row -> check(row.next()); row.getString(1) }
    }

internal fun insertReturn(conn: Connection, result: PurchaseReturn, operatorId: String) {
    conn.prepareStatement(
        "INSERT INTO purchase_returns (id, receipt_id, reason, total_cents, returned_by, created_at) VALUES (?, ?, ?, ?, ?, ?)"
    ).use {
        it.setString(1, result.id)
        it.setString(2, result.purchaseId)
        it.setString(3, result.reason)
        it.setLong(4, result.totalCents)
        it.setString(5, operatorId)
        it.setLong(6, result.createdAt)
        it.executeUpdate()
    }
    conn.prepareStatement(
        "INSERT INTO purchase_return_lines (return_id, receipt_line_id, quantity_millis, total_cents) VALUES (?, ?, ?, ?)"
    ).use {
        for (line in result.lines) {
            it.setString(1, result.id)
            it.setString(2, line.purchaseLineId)
            it.setLong(3, line.quantityMillis)
            it.setLong(4, line.totalCents)
            it.addBatch()
        }
        it.executeBatch()
    }
}

private fun readReturns(conn: Connection, id: String): List<PurchaseReturn> = conn.prepareStatement(
    """
    SELECT r.id, r.reason, r.total_cents, r.created_at, u.name, l.receipt_line_id, l.quantity_millis, l.total_cents AS line_cents
    FROM purchase_returns r JOIN users u ON r.returned_by = u.id
    JOIN purchase_return_lines l ON l.return_id = r.id
    WHERE r.receipt_id = ? ORDER BY r.created_at DESC, r.id DESC, l.receipt_line_id
    """.trimIndent()
).use {
    it.setString(1, id)
    it.executeQuery().use { rows ->
        val returns = linkedMapOf<String, PurchaseReturn>()
        val lines = mutableMapOf<String, MutableList<PurchaseReturnLine>>()
        while (rows.next()) {
            val returnId = rows.getString("id")
            returns.getOrPut(returnId) {
                PurchaseReturn(returnId, id, rows.getString("reason"), rows.getString("name"), rows.getLong("total_cents"), rows.getLong("created_at"), emptyList())
            }
            lines.getOrPut(returnId) { mutableListOf() }.add(
                PurchaseReturnLine(rows.getString("receipt_line_id"), rows.getLong("quantity_millis"), rows.getLong("line_cents"))
            )
        }
        returns.values.map { result -> result.copy(lines = lines.getValue(result.id)) }
    }
}

package mx.nexuspos.server.modules.cash

import mx.nexuspos.contracts.cash.AuditedCashMovement
import mx.nexuspos.contracts.cash.CashMovementCursor
import mx.nexuspos.contracts.cash.CashMovementHistoryPage
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.IdempotencyMismatchException
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.SessionClosedException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class CashMovementAuditService(private val db: DatabaseFactory) {
    fun list(sessionId: String, before: CashMovementCursor?): CashMovementHistoryPage = db.inTransaction { conn ->
        val open = sessionIsOpen(conn, sessionId, lock = false)
        val seek = if (before == null) "" else "AND (m.created_at < ? OR (m.created_at = ? AND m.id < ?))"
        val rows = conn.prepareStatement(
            """
            $movementQuery WHERE m.session_id = ? $seek
            ORDER BY m.created_at DESC, m.id DESC LIMIT ?
            """.trimIndent()
        ).use { query ->
            var column = 1
            query.setString(column++, sessionId)
            if (before != null) {
                query.setLong(column++, before.createdAt)
                query.setLong(column++, before.createdAt)
                query.setString(column++, before.id)
            }
            query.setInt(column, pageSize + 1)
            query.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.movement()) } }
        }
        val page = rows.take(pageSize)
        val next = if (rows.size > pageSize) page.last().let { CashMovementCursor(it.createdAt, it.id) } else null
        CashMovementHistoryPage(open, page, next)
    }

    fun reverse(sessionId: String, movementId: String, reason: String, supervisorId: String): AuditedCashMovement {
        val explanation = reason.trim()
        if (explanation.isEmpty() || explanation.length > 256) {
            throw ValidationException("Captura un motivo de entre 1 y 256 caracteres.")
        }
        return db.inTransaction { conn ->
            // El corte Z y los movimientos de caja toman el mismo bloqueo antes de escribir.
            val open = sessionIsOpen(conn, sessionId, lock = true)
            val original = readMovement(conn, sessionId, movementId)
            if (original.supplierPaymentId != null) {
                throw ValidationException("Esta salida está vinculada a un abono de proveedor.")
            }
            val existingId = original.reversedByMovementId
            if (existingId != null) {
                val existing = readMovement(conn, sessionId, existingId)
                if (existing.concept == explanation && existing.authorizedByUserId == supervisorId) return@inTransaction existing
                throw IdempotencyMismatchException("El movimiento ya tiene una reversión registrada.")
            }
            if (!open) throw SessionClosedException("El turno ya está cerrado. El arqueo histórico se conserva.")
            if (!original.isReversible) throw ValidationException("Selecciona una entrada o salida original con importe positivo.")

            val id = "mov-" + UUID.randomUUID()
            val oppositeType = if (original.type == "CASH_IN") "CASH_OUT" else "CASH_IN"
            conn.prepareStatement(
                """
                INSERT INTO cash_movements
                    (id, session_id, type, amount_cents, concept, user_id, created_at, reverses_movement_id, authorized_by_user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use {
                it.setString(1, id)
                it.setString(2, sessionId)
                it.setString(3, oppositeType)
                it.setLong(4, original.amountCents)
                it.setString(5, explanation)
                it.setString(6, supervisorId)
                it.setLong(7, System.currentTimeMillis())
                it.setString(8, original.id)
                it.setString(9, supervisorId)
                it.executeUpdate()
            }
            readMovement(conn, sessionId, id)
        }
    }

    private fun sessionIsOpen(conn: Connection, sessionId: String, lock: Boolean): Boolean =
        conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ?" + if (lock) " FOR UPDATE" else "").use {
            it.setString(1, sessionId)
            it.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("No se encontró el turno de caja.")
                row.getString(1) == "OPEN"
            }
        }

    private fun readMovement(conn: Connection, sessionId: String, id: String): AuditedCashMovement =
        conn.prepareStatement("$movementQuery WHERE m.session_id = ? AND m.id = ?").use {
            it.setString(1, sessionId)
            it.setString(2, id)
            it.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("No se encontró el movimiento en este turno.")
                row.movement()
            }
        }

    private fun ResultSet.movement() = AuditedCashMovement(
        id = getString("id"),
        sessionId = getString("session_id"),
        type = getString("type"),
        amountCents = getLong("amount_cents"),
        concept = getString("concept"),
        cashierId = getString("user_id"),
        cashierName = getString("cashier_name"),
        createdAt = getLong("created_at"),
        reversesId = getString("reverses_movement_id"),
        reversedByMovementId = getString("reversal_id"),
        authorizedByUserId = getString("authorized_by_user_id"),
        authorizedByName = getString("supervisor_name"),
        supplierPaymentId = getString("supplier_payment_id")
    )

    private companion object {
        const val pageSize = 30
        val movementQuery = """
            SELECT m.*, COALESCE(u.name, m.user_id) AS cashier_name,
                   supervisor.name AS supervisor_name, reversal.id AS reversal_id, payment.id AS supplier_payment_id
            FROM cash_movements m
            LEFT JOIN users u ON u.id = m.user_id
            LEFT JOIN users supervisor ON supervisor.id = m.authorized_by_user_id
            LEFT JOIN cash_movements reversal ON reversal.reverses_movement_id = m.id
            LEFT JOIN supplier_payments payment ON payment.cash_movement_id = m.id
        """.trimIndent()
    }
}

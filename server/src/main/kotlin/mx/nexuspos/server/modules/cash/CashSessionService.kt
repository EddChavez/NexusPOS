package mx.nexuspos.server.modules.cash

import mx.nexuspos.contracts.cash.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.SessionClosedException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID

class CashSessionService(private val db: DatabaseFactory) {

    fun openSession(request: OpenSessionRequest): CashSessionSummary {
        return db.inTransaction { conn ->
            // Check if there is already an open session on this register (RN-CAJ-01)
            val checkStmt = conn.prepareStatement(
                "SELECT id FROM cash_sessions WHERE register_id = ? AND status = 'OPEN'"
            )
            checkStmt.setString(1, request.registerId)
            val checkRs = checkStmt.executeQuery()
            if (checkRs.next()) {
                val existingId = checkRs.getString("id")
                checkRs.close()
                checkStmt.close()
                throw ValidationException("La caja '${request.registerId}' ya tiene una sesión abierta ($existingId)")
            }
            checkRs.close()
            checkStmt.close()

            val sessionId = "sess-" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()

            val insertStmt = conn.prepareStatement(
                """
                INSERT INTO cash_sessions (id, register_id, user_id, initial_fund_cents, opened_at, expected_cash_cents, status, notes)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', '')
                """.trimIndent()
            )
            insertStmt.setString(1, sessionId)
            insertStmt.setString(2, request.registerId)
            insertStmt.setString(3, request.userId)
            insertStmt.setLong(4, request.initialFundCents)
            insertStmt.setLong(5, now)
            insertStmt.setLong(6, request.initialFundCents)
            insertStmt.executeUpdate()
            insertStmt.close()

            // Fetch register and user names
            val nameStmt = conn.prepareStatement(
                "SELECT r.name as reg_name, u.name as user_name FROM cash_registers r, users u WHERE r.id = ? AND u.id = ?"
            )
            nameStmt.setString(1, request.registerId)
            nameStmt.setString(2, request.userId)
            val nameRs = nameStmt.executeQuery()
            val regName = if (nameRs.next()) nameRs.getString("reg_name") else "Caja"
            val userName = nameRs.getString("user_name")
            nameRs.close()
            nameStmt.close()

            CashSessionSummary(
                sessionId = sessionId,
                registerId = request.registerId,
                registerName = regName,
                cashierId = request.userId,
                cashierName = userName,
                openedAt = now,
                initialFundCents = request.initialFundCents,
                salesCashCents = 0L,
                creditPaymentsCashCents = 0L,
                cashInCents = 0L,
                cashOutCents = 0L,
                refundsCashCents = 0L,
                expectedCashCents = request.initialFundCents,
                status = "OPEN"
            )
        }
    }

    fun recordMovement(sessionId: String, request: CashMovementRequest) {
        db.inTransaction { conn ->
            val sessStmt = conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ?")
            sessStmt.setString(1, sessionId)
            val rs = sessStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                sessStmt.close()
                throw NotFoundException("Sesión de caja no encontrada")
            }
            if (rs.getString("status") != "OPEN") {
                rs.close()
                sessStmt.close()
                throw SessionClosedException("No se pueden registrar movimientos en una sesión cerrada")
            }
            rs.close()
            sessStmt.close()

            val movId = "mov-" + UUID.randomUUID().toString().take(8)
            val insertStmt = conn.prepareStatement(
                "INSERT INTO cash_movements (id, session_id, type, amount_cents, concept, user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            insertStmt.setString(1, movId)
            insertStmt.setString(2, sessionId)
            insertStmt.setString(3, request.type)
            insertStmt.setLong(4, request.amountCents)
            insertStmt.setString(5, request.concept)
            insertStmt.setString(6, request.userId)
            insertStmt.setLong(7, System.currentTimeMillis())
            insertStmt.executeUpdate()
            insertStmt.close()
        }
    }

    fun getSessionSummary(sessionId: String): CashSessionSummary {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                """
                SELECT s.id, s.register_id, r.name as reg_name, s.user_id, u.name as user_name,
                       s.initial_fund_cents, s.opened_at, s.closed_at, s.status, s.notes,
                       s.counted_cash_cents, s.difference_cents
                FROM cash_sessions s
                JOIN cash_registers r ON s.register_id = r.id
                JOIN users u ON s.user_id = u.id
                WHERE s.id = ?
                """.trimIndent()
            )
            stmt.setString(1, sessionId)
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                stmt.close()
                throw NotFoundException("Sesión de caja '$sessionId' no encontrada")
            }

            val regId = rs.getString("register_id")
            val regName = rs.getString("reg_name")
            val userId = rs.getString("user_id")
            val userName = rs.getString("user_name")
            val initialFund = rs.getLong("initial_fund_cents")
            val openedAt = rs.getLong("opened_at")
            val closedAt = rs.getLong("closed_at").let { if (rs.wasNull()) null else it }
            val status = rs.getString("status")
            val countedCash = rs.getLong("counted_cash_cents").let { if (rs.wasNull()) null else it }
            val storedDifference = rs.getLong("difference_cents").let { if (rs.wasNull()) null else it }
            rs.close()
            stmt.close()

            // 1. Sum sales in cash (payments of method CASH minus change)
            val salesStmt = conn.prepareStatement(
                """
                SELECT COALESCE(SUM(sp.amount_cents), 0) as cash_total
                FROM sale_payments sp
                JOIN sales s ON sp.sale_id = s.id
                WHERE s.cash_session_id = ? AND sp.method = 'CASH' AND s.status = 'COMPLETED'
                """.trimIndent()
            )
            salesStmt.setString(1, sessionId)
            val salesRs = salesStmt.executeQuery()
            salesRs.next()
            val salesCashCents = salesRs.getLong("cash_total")
            salesRs.close()
            salesStmt.close()

            // 2. Sum credit payments in cash
            val creditStmt = conn.prepareStatement(
                """
                SELECT COALESCE(SUM(amount_cents), 0) as credit_cash
                FROM credit_movements
                WHERE cash_session_id = ? AND type = 'PAYMENT'
                """.trimIndent()
            )
            creditStmt.setString(1, sessionId)
            val creditRs = creditStmt.executeQuery()
            creditRs.next()
            val creditPaymentsCashCents = creditRs.getLong("credit_cash")
            creditRs.close()
            creditStmt.close()

            // 3. Sum cash movements (IN and OUT)
            val movStmt = conn.prepareStatement(
                """
                SELECT type, COALESCE(SUM(amount_cents), 0) as total
                FROM cash_movements
                WHERE session_id = ?
                GROUP BY type
                """.trimIndent()
            )
            movStmt.setString(1, sessionId)
            val movRs = movStmt.executeQuery()
            var cashInCents = 0L
            var cashOutCents = 0L
            while (movRs.next()) {
                val type = movRs.getString("type")
                val total = movRs.getLong("total")
                if (type == "CASH_IN") cashInCents = total
                if (type == "CASH_OUT") cashOutCents = total
            }
            movRs.close()
            movStmt.close()

            // Formula RN-CAJ-04: initial + salesCash + creditPaymentsCash + cashIn - cashOut - refunds
            val expectedCash = initialFund + salesCashCents + creditPaymentsCashCents + cashInCents - cashOutCents
            val diff = countedCash?.let { it - expectedCash } ?: storedDifference

            CashSessionSummary(
                sessionId = sessionId,
                registerId = regId,
                registerName = regName,
                cashierId = userId,
                cashierName = userName,
                openedAt = openedAt,
                closedAt = closedAt,
                initialFundCents = initialFund,
                salesCashCents = salesCashCents,
                creditPaymentsCashCents = creditPaymentsCashCents,
                cashInCents = cashInCents,
                cashOutCents = cashOutCents,
                refundsCashCents = 0L,
                expectedCashCents = expectedCash,
                countedCashCents = countedCash,
                differenceCents = diff,
                status = status
            )
        }
    }

    fun closeSession(sessionId: String, request: CloseSessionRequest): CashSessionSummary {
        val summary = getSessionSummary(sessionId)
        if (summary.status == "CLOSED") {
            throw SessionClosedException("La sesión '$sessionId' ya está cerrada")
        }

        val expected = summary.expectedCashCents
        val difference = request.countedCashCents - expected
        val now = System.currentTimeMillis()

        db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                """
                UPDATE cash_sessions
                SET closed_at = ?, expected_cash_cents = ?, counted_cash_cents = ?, difference_cents = ?, status = 'CLOSED', notes = ?
                WHERE id = ?
                """.trimIndent()
            )
            stmt.setLong(1, now)
            stmt.setLong(2, expected)
            stmt.setLong(3, request.countedCashCents)
            stmt.setLong(4, difference)
            stmt.setString(5, request.notes)
            stmt.setString(6, sessionId)
            stmt.executeUpdate()
            stmt.close()
        }

        return summary.copy(
            closedAt = now,
            countedCashCents = request.countedCashCents,
            differenceCents = difference,
            status = "CLOSED"
        )
    }

    fun getCurrentOpenSession(registerId: String): CashSessionSummary? {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement("SELECT id FROM cash_sessions WHERE register_id = ? AND status = 'OPEN'")
            stmt.setString(1, registerId)
            val rs = stmt.executeQuery()
            val sessionId = if (rs.next()) rs.getString("id") else null
            rs.close()
            stmt.close()
            sessionId?.let { getSessionSummary(it) }
        }
    }
}

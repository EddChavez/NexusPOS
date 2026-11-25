package mx.nexuspos.server.modules.cash

import mx.nexuspos.contracts.cash.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ConflictException
import mx.nexuspos.server.platform.AppException
import mx.nexuspos.server.platform.ForbiddenException
import mx.nexuspos.server.modules.permissions.writeAudit
import mx.nexuspos.server.platform.SessionClosedException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.util.UUID

class CashSessionService(private val db: DatabaseFactory) {

    fun listRegisters(): List<CashRegisterDto> = db.inTransaction { connection ->
        connection.prepareStatement("SELECT id, name, is_active FROM cash_registers ORDER BY name, id").use {
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(CashRegisterDto(rows.getString(1), rows.getString(2), rows.getBoolean(3))) } }
        }
    }

    fun saveRegister(id: String?, request: SaveCashRegisterRequest): CashRegisterDto {
        val name = request.name.trim()
        if (name.length !in 1..100) throw ValidationException("El nombre de la caja debe tener de 1 a 100 caracteres.")
        val registerId = id ?: "caja-${UUID.randomUUID().toString().take(12)}"
        return db.inTransaction { connection ->
            if (id == null) {
                connection.prepareStatement("INSERT INTO cash_registers (id, name, is_active) VALUES (?, ?, ?)").use {
                    it.setString(1, registerId)
                    it.setString(2, name)
                    it.setBoolean(3, request.active)
                    it.executeUpdate()
                }
            } else {
                lockRegister(connection, id)
                if (!request.active) {
                    connection.prepareStatement("SELECT 1 FROM cash_sessions WHERE register_id = ? AND status = 'OPEN'").use {
                        it.setString(1, id)
                        it.executeQuery().use { row -> if (row.next()) throw ConflictException("Cierra el turno antes de desactivar la caja.") }
                    }
                }
                connection.prepareStatement("UPDATE cash_registers SET name = ?, is_active = ? WHERE id = ?").use {
                    it.setString(1, name)
                    it.setBoolean(2, request.active)
                    it.setString(3, id)
                    it.executeUpdate()
                }
            }
            CashRegisterDto(registerId, name, request.active)
        }
    }

    fun handoff(sessionId: String, incomingOperator: String): CashSessionSummary = db.inTransaction { connection ->
        val current = lockOperator(connection, sessionId)
        if (current == incomingOperator) return@inTransaction getSessionSummary(connection, sessionId)
        val enabled = connection.prepareStatement("SELECT setting_value FROM settings WHERE setting_key = 'allow_shift_handoff'").use {
            it.executeQuery().use { row -> row.next() && row.getString(1).toBoolean() }
        }
        if (!enabled) throw ForbiddenException("El relevo de operador está deshabilitado.")
        connection.prepareStatement("UPDATE cash_sessions SET operator_user_id = ? WHERE id = ?").use {
            it.setString(1, incomingOperator)
            it.setString(2, sessionId)
            it.executeUpdate()
        }
        writeAudit(connection, "cash.handoff", incomingOperator, sessionId)
        getSessionSummary(connection, sessionId)
    }

    private fun lockRegister(connection: Connection, id: String): Boolean =
        connection.prepareStatement("SELECT is_active FROM cash_registers WHERE id = ? FOR UPDATE").use {
            it.setString(1, id)
            it.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("Caja no encontrada.")
                row.getBoolean(1)
            }
        }

    fun openSession(request: OpenSessionRequest): CashSessionSummary {
        return db.inTransaction { conn ->
            if (!lockRegister(conn, request.registerId)) throw ForbiddenException("La caja está inactiva.")
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

    fun recordMovement(sessionId: String, request: CashMovementRequest, verifyOperator: Boolean = false, drawerApproval: String? = null) {
        if (request.type !in setOf("CASH_IN", "CASH_OUT") || request.amountCents <= 0 || request.concept.trim().length !in 1..256) {
            throw ValidationException("Verifica el tipo, importe y motivo del movimiento de efectivo.")
        }
        db.inTransaction { conn ->
            if (verifyOperator) requireOperator(conn, sessionId, request.userId)
            val sessStmt = conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ? FOR UPDATE")
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

            if (request.type == "CASH_IN") enforceDrawerLimit(conn, sessionId, request.amountCents, drawerApproval, request.userId)
            val movId = "mov-" + UUID.randomUUID().toString().take(8)
            val insertStmt = conn.prepareStatement(
                "INSERT INTO cash_movements (id, session_id, type, amount_cents, concept, user_id, created_at, authorized_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )
            insertStmt.setString(1, movId)
            insertStmt.setString(2, sessionId)
            insertStmt.setString(3, request.type)
            insertStmt.setLong(4, request.amountCents)
            insertStmt.setString(5, request.concept)
            insertStmt.setString(6, request.userId)
            insertStmt.setLong(7, System.currentTimeMillis())
            insertStmt.setString(8, drawerApproval)
            insertStmt.executeUpdate()
            insertStmt.close()
        }
    }

    internal fun enforceDrawerLimit(connection: Connection, sessionId: String, additionalCash: Long, approvedBy: String?, actorId: String) {
        if (additionalCash <= 0) return
        val limit = connection.prepareStatement("SELECT setting_value FROM settings WHERE setting_key = 'cash_drawer_limit_cents'").use {
            it.executeQuery().use { row -> if (row.next()) row.getString(1).toLongOrNull() else null }
        } ?: return
        val expected = getSessionSummary(connection, sessionId).expectedCashCents
        if (expected <= limit && additionalCash <= limit - expected) return
        if (approvedBy == null) throw CashLimitException()
        connection.prepareStatement("INSERT INTO security_audit (id, action, actor_user_id, target_id, created_at, approved_by_user_id) VALUES (?, ?, ?, ?, ?, ?)").use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, "cash.limit.approved")
            it.setString(3, actorId)
            it.setString(4, sessionId)
            it.setLong(5, System.currentTimeMillis())
            it.setString(6, approvedBy)
            it.executeUpdate()
        }
    }

    fun getSessionSummary(sessionId: String): CashSessionSummary =
        db.inTransaction { conn -> getSessionSummary(conn, sessionId) }

    private fun getSessionSummary(conn: Connection, sessionId: String): CashSessionSummary =
        with(conn) {
            val stmt = prepareStatement(
                """
                SELECT s.id, s.register_id, r.name as reg_name, s.user_id, u.name as user_name,
                       s.initial_fund_cents, s.opened_at, s.closed_at, s.status, s.notes,
                       s.counted_cash_cents, s.difference_cents, COALESCE(s.operator_user_id, s.user_id) AS operator_id,
                       COALESCE(o.name, u.name) AS operator_name
                FROM cash_sessions s
                JOIN cash_registers r ON s.register_id = r.id
                JOIN users u ON s.user_id = u.id
                LEFT JOIN users o ON o.id = s.operator_user_id
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
            val operatorId = rs.getString("operator_id")
            val operatorName = rs.getString("operator_name")
            val initialFund = rs.getLong("initial_fund_cents")
            val openedAt = rs.getLong("opened_at")
            val closedAt = rs.getLong("closed_at").let { if (rs.wasNull()) null else it }
            val status = rs.getString("status")
            val countedCash = rs.getLong("counted_cash_cents").let { if (rs.wasNull()) null else it }
            val storedDifference = rs.getLong("difference_cents").let { if (rs.wasNull()) null else it }
            rs.close()
            stmt.close()

            // 1. Sum sales in cash (payments of method CASH minus change)
            val salesStmt = prepareStatement(
                """
                SELECT COALESCE(SUM(sp.amount_cents), 0) as cash_total
                FROM sale_payments sp
                JOIN sales s ON sp.sale_id = s.id
                WHERE s.cash_session_id = ? AND sp.method = 'CASH' AND s.status != 'CANCELLED'
                """.trimIndent()
            )
            salesStmt.setString(1, sessionId)
            val salesRs = salesStmt.executeQuery()
            salesRs.next()
            val salesCashCents = salesRs.getLong("cash_total")
            salesRs.close()
            salesStmt.close()

            // 2. Sum credit payments in cash
            val creditStmt = prepareStatement(
                """
                SELECT COALESCE(SUM(amount_cents), 0) as credit_cash
                FROM credit_movements
                WHERE cash_session_id = ? AND type = 'PAYMENT' AND COALESCE(payment_method, 'CASH') = 'CASH'
                """.trimIndent()
            )
            creditStmt.setString(1, sessionId)
            val creditRs = creditStmt.executeQuery()
            creditRs.next()
            val creditPaymentsCashCents = creditRs.getLong("credit_cash")
            creditRs.close()
            creditStmt.close()

            // 3. Sum cash movements (IN and OUT)
            val movStmt = prepareStatement(
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

            // 4. Sum refunds in cash for this cash session
            val refStmt = prepareStatement(
                """
                SELECT COALESCE(SUM(total_cents), 0) as total_refunds
                FROM sale_refunds
                WHERE cash_session_id = ? AND refund_method = 'CASH'
                """.trimIndent()
            )
            refStmt.setString(1, sessionId)
            val refRs = refStmt.executeQuery()
            refRs.next()
            val refundsCashCents = refRs.getLong("total_refunds")
            refRs.close()
            refStmt.close()

            // Formula RN-CAJ-04: initial + salesCash + creditPaymentsCash + cashIn - cashOut - refunds
            val expectedCash = initialFund + salesCashCents + creditPaymentsCashCents + cashInCents - cashOutCents - refundsCashCents
            val diff = countedCash?.let { it - expectedCash } ?: storedDifference

            // 5. Query denominations
            val denomList = mutableListOf<CashDenominationCountDto>()
            val denomQuery = prepareStatement(
                """
                SELECT denomination_cents, is_coin, pieces, total_cents
                FROM cash_session_denominations
                WHERE session_id = ?
                ORDER BY is_coin ASC, denomination_cents DESC
                """.trimIndent()
            )
            denomQuery.setString(1, sessionId)
            val denomRs = denomQuery.executeQuery()
            while (denomRs.next()) {
                denomList.add(
                    CashDenominationCountDto(
                        denominationCents = denomRs.getLong("denomination_cents"),
                        isCoin = denomRs.getBoolean("is_coin"),
                        pieces = denomRs.getInt("pieces"),
                        totalCents = denomRs.getLong("total_cents")
                    )
                )
            }
            denomRs.close()
            denomQuery.close()

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
                refundsCashCents = refundsCashCents,
                expectedCashCents = expectedCash,
                countedCashCents = countedCash,
                differenceCents = diff,
                status = status,
                denominations = denomList,
                operatorUserId = operatorId,
                operatorName = operatorName
            )
        }

    fun closeSession(sessionId: String, request: CloseSessionRequest, operatorId: String? = null): CashSessionSummary =
        db.inTransaction { conn ->
            conn.prepareStatement("SELECT id FROM cash_sessions WHERE id = ? FOR UPDATE").use {
                it.setString(1, sessionId)
                it.executeQuery().use { row ->
                    if (!row.next()) throw NotFoundException("Sesión de caja no encontrada")
                }
            }
            if (operatorId != null) requireOperator(conn, sessionId, operatorId)
            val summary = getSessionSummary(conn, sessionId)
            if (summary.status == "CLOSED") {
                throw SessionClosedException("La sesión '$sessionId' ya está cerrada")
            }

            val expected = summary.expectedCashCents
            val difference = request.countedCashCents - expected
            val now = System.currentTimeMillis()

            val stmt = conn.prepareStatement(
                """
                UPDATE cash_sessions
                SET closed_at = ?, expected_cash_cents = ?, counted_cash_cents = ?, difference_cents = ?, status = 'CLOSED', notes = ?
                WHERE id = ? AND status = 'OPEN'
                """.trimIndent()
            )
            stmt.setLong(1, now)
            stmt.setLong(2, expected)
            stmt.setLong(3, request.countedCashCents)
            stmt.setLong(4, difference)
            stmt.setString(5, request.notes)
            stmt.setString(6, sessionId)
            stmt.use {
                if (it.executeUpdate() != 1) throw SessionClosedException("La sesión '$sessionId' ya está cerrada")
            }

            if (request.denominations.isNotEmpty()) {
                val denomInsert = conn.prepareStatement(
                    """
                    INSERT INTO cash_session_denominations (id, session_id, denomination_cents, is_coin, pieces, total_cents, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                )
                for (d in request.denominations) {
                    val dId = "csd-" + UUID.randomUUID().toString().take(8)
                    denomInsert.setString(1, dId)
                    denomInsert.setString(2, sessionId)
                    denomInsert.setLong(3, d.denominationCents)
                    denomInsert.setBoolean(4, d.isCoin)
                    denomInsert.setInt(5, d.pieces)
                    denomInsert.setLong(6, d.totalCents)
                    denomInsert.setLong(7, now)
                    denomInsert.addBatch()
                }
                denomInsert.executeBatch()
                denomInsert.close()
            }

            summary.copy(
                closedAt = now,
                countedCashCents = request.countedCashCents,
                differenceCents = difference,
                status = "CLOSED",
                denominations = request.denominations
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
            sessionId?.let { getSessionSummary(conn, it) }
        }
    }
}


internal fun requireOperator(connection: Connection, sessionId: String, userId: String) {
    if (lockOperator(connection, sessionId) != userId) throw ForbiddenException("Este turno pertenece a otro operador. Solicita un relevo.")
}

private fun lockOperator(connection: Connection, sessionId: String): String =
    connection.prepareStatement("SELECT status, COALESCE(operator_user_id, user_id) FROM cash_sessions WHERE id = ? FOR UPDATE").use {
        it.setString(1, sessionId)
        it.executeQuery().use { row ->
            if (!row.next()) throw NotFoundException("Turno no encontrado.")
            if (row.getString(1) != "OPEN") throw SessionClosedException("El turno ya está cerrado.")
            row.getString(2)
        }
    }

internal class CashLimitException : AppException("CASH_LIMIT", "Captura el PIN de una persona autorizada para superar el límite de efectivo.")

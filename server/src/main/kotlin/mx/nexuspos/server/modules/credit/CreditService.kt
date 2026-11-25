package mx.nexuspos.server.modules.credit

import mx.nexuspos.server.modules.cash.requireOperator

import mx.nexuspos.contracts.credit.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.sql.Connection
import java.util.UUID

class CreditService(private val db: DatabaseFactory, seedDefaults: Boolean = true) {

    init {
        if (seedDefaults) seedInitialCustomers()
    }

    private fun seedInitialCustomers() {
        db.inTransaction { conn ->
            val countStmt = conn.prepareStatement("SELECT COUNT(*) FROM customers")
            val rs = countStmt.executeQuery()
            rs.next()
            val count = rs.getInt(1)
            rs.close()
            countStmt.close()

            val now = System.currentTimeMillis()
            if (count == 0) {
                val insertStmt = conn.prepareStatement(
                    """
                    INSERT INTO customers (id, folio, name, phone, email, rfc, address, notes, credit_enabled, has_credit_limit, credit_limit_cents, current_balance_cents, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                )
                // Don Pancho (Límite $1,500.00, Saldo actual $350.00)
                insertStmt.setString(1, "cust-1")
                insertStmt.setString(2, "CLI-0001")
                insertStmt.setString(3, "Don Pancho (Vecino #12)")
                insertStmt.setString(4, "555-111-2233")
                insertStmt.setString(5, "pancho@vecindario.mx")
                insertStmt.setString(6, "XAXX010101000")
                insertStmt.setString(7, "Calle Morelos #12, Col. Centro")
                insertStmt.setString(8, "Cliente fundador, paga quincenalmente")
                insertStmt.setBoolean(9, true)
                insertStmt.setBoolean(10, true)
                insertStmt.setLong(11, 150000L)
                insertStmt.setLong(12, 35000L)
                insertStmt.setBoolean(13, true)
                insertStmt.setLong(14, now)
                insertStmt.setLong(15, now)
                insertStmt.executeUpdate()

                // Doña Mary (Límite $800.00, Saldo actual $0.00)
                insertStmt.setString(1, "cust-2")
                insertStmt.setString(2, "CLI-0002")
                insertStmt.setString(3, "Doña Mary")
                insertStmt.setString(4, "555-444-5566")
                insertStmt.setString(5, "")
                insertStmt.setString(6, "XAXX010101000")
                insertStmt.setString(7, "Av. Juárez #45")
                insertStmt.setString(8, "Paga los sábados por la tarde")
                insertStmt.setBoolean(9, true)
                insertStmt.setBoolean(10, true)
                insertStmt.setLong(11, 80000L)
                insertStmt.setLong(12, 0L)
                insertStmt.setBoolean(13, true)
                insertStmt.setLong(14, now)
                insertStmt.setLong(15, now)
                insertStmt.executeUpdate()
                insertStmt.close()

                val updateCounter = conn.prepareStatement("UPDATE counters SET current_value = 102 WHERE name = 'customer_folio'")
                updateCounter.executeUpdate()
                updateCounter.close()
            } else {
                // Ensure existing customers have folios
                val fixFolio1 = conn.prepareStatement("UPDATE customers SET folio = 'CLI-0001' WHERE id = 'cust-1' AND (folio IS NULL OR folio = '')")
                fixFolio1.executeUpdate()
                fixFolio1.close()

                val fixFolio2 = conn.prepareStatement("UPDATE customers SET folio = 'CLI-0002' WHERE id = 'cust-2' AND (folio IS NULL OR folio = '')")
                fixFolio2.executeUpdate()
                fixFolio2.close()
            }
        }
    }

    private fun generateCustomerFolio(conn: Connection): String {
        val stmt = conn.prepareStatement("SELECT current_value FROM counters WHERE name = 'customer_folio' FOR UPDATE")
        val rs = stmt.executeQuery()
        val current = if (rs.next()) rs.getLong(1) else 100L
        rs.close()
        stmt.close()

        val next = current + 1L
        val updateStmt = conn.prepareStatement("UPDATE counters SET current_value = ? WHERE name = 'customer_folio'")
        updateStmt.setLong(1, next)
        updateStmt.executeUpdate()
        updateStmt.close()

        return "CLI-%04d".format(next)
    }

    private fun generateReceiptFolio(conn: Connection): String {
        val countStmt = conn.prepareStatement("INSERT INTO counters (name, current_value) VALUES ('credit_receipt_folio', 100) ON CONFLICT DO NOTHING")
        countStmt.executeUpdate()
        countStmt.close()

        val stmt = conn.prepareStatement("SELECT current_value FROM counters WHERE name = 'credit_receipt_folio' FOR UPDATE")
        val rs = stmt.executeQuery()
        val current = if (rs.next()) rs.getLong(1) else 100L
        rs.close()
        stmt.close()

        val next = current + 1L
        val updateStmt = conn.prepareStatement("UPDATE counters SET current_value = ? WHERE name = 'credit_receipt_folio'")
        updateStmt.setLong(1, next)
        updateStmt.executeUpdate()
        updateStmt.close()

        return "REC-%04d".format(next)
    }

    fun searchCustomers(query: String?, includeInactive: Boolean = false): List<CustomerDto> {
        return db.inTransaction { conn ->
            val baseSql = """
                SELECT id, folio, name, phone, email, rfc, address, notes, credit_enabled, has_credit_limit,
                       credit_limit_cents, current_balance_cents, loyalty_balance_cents, total_loyalty_earned_cents, total_loyalty_redeemed_cents,
                       is_active, created_at, updated_at
                FROM customers
            """.trimIndent()

            val whereClauses = mutableListOf<String>()
            if (!includeInactive) {
                whereClauses.add("is_active = TRUE")
            }
            if (!query.isNullOrBlank()) {
                whereClauses.add("(LOWER(name) LIKE ? OR phone LIKE ? OR LOWER(folio) LIKE ? OR LOWER(rfc) LIKE ?)")
            }

            val sql = if (whereClauses.isNotEmpty()) {
                "$baseSql WHERE ${whereClauses.joinToString(" AND ")} ORDER BY name ASC"
            } else {
                "$baseSql ORDER BY name ASC"
            }

            val stmt = conn.prepareStatement(sql)
            if (!query.isNullOrBlank()) {
                val q = "%${query.trim().lowercase()}%"
                stmt.setString(1, q)
                stmt.setString(2, q)
                stmt.setString(3, q)
                stmt.setString(4, q)
            }

            val rs = stmt.executeQuery()
            val list = mutableListOf<CustomerDto>()
            while (rs.next()) {
                list.add(mapCustomerDto(rs))
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun getCustomer(id: String): CustomerDto {
        return db.inTransaction { conn ->
            getCustomerFromConn(conn, id)
        }
    }

    private fun getCustomerFromConn(conn: Connection, id: String): CustomerDto {
        val stmt = conn.prepareStatement(
            """
            SELECT id, folio, name, phone, email, rfc, address, notes, credit_enabled, has_credit_limit,
                   credit_limit_cents, current_balance_cents, loyalty_balance_cents, total_loyalty_earned_cents, total_loyalty_redeemed_cents,
                   is_active, created_at, updated_at
            FROM customers WHERE id = ?
            """.trimIndent()
        )
        stmt.setString(1, id)
        val rs = stmt.executeQuery()
        if (!rs.next()) {
            rs.close()
            stmt.close()
            throw NotFoundException("Cliente '$id' no encontrado")
        }
        val dto = mapCustomerDto(rs)
        rs.close()
        stmt.close()
        return dto
    }

    private fun mapCustomerDto(rs: java.sql.ResultSet): CustomerDto {
        val limit = rs.getLong("credit_limit_cents")
        val balance = rs.getLong("current_balance_cents")
        val hasLimit = rs.getBoolean("has_credit_limit")
        val available = if (!hasLimit) 99999900L else (limit - balance).coerceAtLeast(0L)

        return CustomerDto(
            id = rs.getString("id"),
            folio = rs.getString("folio") ?: "",
            name = rs.getString("name"),
            phone = rs.getString("phone") ?: "",
            email = rs.getString("email") ?: "",
            rfc = rs.getString("rfc") ?: "",
            address = rs.getString("address") ?: "",
            notes = rs.getString("notes") ?: "",
            creditEnabled = rs.getBoolean("credit_enabled"),
            hasCreditLimit = hasLimit,
            creditLimitCents = limit,
            currentBalanceCents = balance,
            availableCreditCents = available,
            loyaltyBalanceCents = rs.getLong("loyalty_balance_cents"),
            totalLoyaltyEarnedCents = rs.getLong("total_loyalty_earned_cents"),
            totalLoyaltyRedeemedCents = rs.getLong("total_loyalty_redeemed_cents"),
            isActive = rs.getBoolean("is_active"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    fun createCustomer(request: CreateCustomerRequest): CustomerDto {
        if (request.name.isBlank()) {
            throw ValidationException("El nombre del cliente es obligatorio")
        }
        val id = "cust-" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()

        return db.inTransaction { conn ->
            val folio = generateCustomerFolio(conn)
            val stmt = conn.prepareStatement(
                """
                INSERT INTO customers (
                    id, folio, name, phone, email, rfc, address, notes,
                    credit_enabled, has_credit_limit, credit_limit_cents, current_balance_cents,
                    is_active, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, TRUE, ?, ?)
                """.trimIndent()
            )
            stmt.setString(1, id)
            stmt.setString(2, folio)
            stmt.setString(3, request.name.trim())
            stmt.setString(4, request.phone.trim())
            stmt.setString(5, request.email.trim())
            stmt.setString(6, request.rfc.trim().uppercase())
            stmt.setString(7, request.address.trim())
            stmt.setString(8, request.notes.trim())
            stmt.setBoolean(9, request.creditEnabled)
            stmt.setBoolean(10, request.hasCreditLimit)
            stmt.setLong(11, if (request.hasCreditLimit) request.creditLimitCents else 0L)
            stmt.setLong(12, now)
            stmt.setLong(13, now)
            stmt.executeUpdate()
            stmt.close()

            getCustomerFromConn(conn, id)
        }
    }

    fun updateCustomer(id: String, request: UpdateCustomerRequest, verify: (CustomerDto) -> Unit = {}): CustomerDto {
        if (request.name.isBlank()) {
            throw ValidationException("El nombre del cliente es obligatorio")
        }
        val now = System.currentTimeMillis()

        return db.inTransaction { conn ->
            conn.prepareStatement("SELECT id FROM customers WHERE id = ? FOR UPDATE").use {
                it.setString(1, id)
                it.executeQuery().use { row -> if (!row.next()) throw NotFoundException("Cliente '$id' no encontrado") }
            }
            verify(getCustomerFromConn(conn, id))
            val stmt = conn.prepareStatement(
                """
                UPDATE customers SET
                    name = ?,
                    phone = ?,
                    email = ?,
                    rfc = ?,
                    address = ?,
                    notes = ?,
                    credit_enabled = ?,
                    has_credit_limit = ?,
                    credit_limit_cents = ?,
                    is_active = ?,
                    updated_at = ?
                WHERE id = ?
                """.trimIndent()
            )
            stmt.setString(1, request.name.trim())
            stmt.setString(2, request.phone.trim())
            stmt.setString(3, request.email.trim())
            stmt.setString(4, request.rfc.trim().uppercase())
            stmt.setString(5, request.address.trim())
            stmt.setString(6, request.notes.trim())
            stmt.setBoolean(7, request.creditEnabled)
            stmt.setBoolean(8, request.hasCreditLimit)
            stmt.setLong(9, if (request.hasCreditLimit) request.creditLimitCents else 0L)
            stmt.setBoolean(10, request.isActive)
            stmt.setLong(11, now)
            stmt.setString(12, id)
            val rows = stmt.executeUpdate()
            stmt.close()

            if (rows == 0) {
                throw NotFoundException("Cliente '$id' no encontrado")
            }
            getCustomerFromConn(conn, id)
        }
    }

    fun deactivateCustomer(id: String): CustomerDto = setCustomerActive(id, false)

    fun reactivateCustomer(id: String): CustomerDto = setCustomerActive(id, true)

    private fun setCustomerActive(id: String, active: Boolean): CustomerDto {
        return db.inTransaction { conn ->
            conn.prepareStatement("UPDATE customers SET is_active = ?, updated_at = ? WHERE id = ?").use {
                it.setBoolean(1, active)
                it.setLong(2, System.currentTimeMillis())
                it.setString(3, id)
                if (it.executeUpdate() == 0) throw NotFoundException("Cliente '$id' no encontrado")
            }
            getCustomerFromConn(conn, id)
        }
    }

    fun registerPayment(request: RegisterCreditPaymentRequest, verifyOperator: Boolean = false): CreditMovementDto {
        if (request.amountCents <= 0L) {
            throw ValidationException("El monto del abono debe ser mayor a cero")
        }

        return db.inTransaction { conn ->
            if (verifyOperator) requireOperator(conn, request.cashSessionId, request.cashierUserId)
            val configuredMethods = conn.prepareStatement("SELECT setting_value FROM settings WHERE setting_key = 'credit_payment_methods'").use {
                it.executeQuery().use { row -> if (row.next()) row.getString(1).split(',').toSet() else setOf("CASH", "TRANSFER", "CARD") }
            }
            if (request.paymentMethod.uppercase() !in configuredMethods) throw ValidationException("Ese método está deshabilitado para abonos.")
            // Fetch customer with lock
            val custStmt = conn.prepareStatement(
                "SELECT name, credit_enabled, current_balance_cents FROM customers WHERE id = ? FOR UPDATE"
            )
            custStmt.setString(1, request.customerId)
            val rs = custStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                custStmt.close()
                throw NotFoundException("Cliente '${request.customerId}' no encontrado")
            }
            val currentBalance = rs.getLong("current_balance_cents")
            val custName = rs.getString("name")
            rs.close()
            custStmt.close()

            // RN-CRE-03 & Decisión aprobada: Bloqueo estricto de abonos que excedan el saldo actual deudor
            if (request.amountCents > currentBalance) {
                throw ValidationException(
                    "El abono de $${request.amountCents / 100.0} excede el saldo pendiente de $${currentBalance / 100.0}"
                )
            }

            val newBalance = currentBalance - request.amountCents
            val now = System.currentTimeMillis()
            val updateStmt = conn.prepareStatement(
                "UPDATE customers SET current_balance_cents = ?, updated_at = ? WHERE id = ?"
            )
            updateStmt.setLong(1, newBalance)
            updateStmt.setLong(2, now)
            updateStmt.setString(3, request.customerId)
            updateStmt.executeUpdate()
            updateStmt.close()

            val movId = "c-mov-" + UUID.randomUUID().toString().take(8)
            val receiptFolio = request.receiptFolio ?: generateReceiptFolio(conn)
            val desc = if (request.notes.isNotBlank()) "Abono: ${request.notes} (Recibo $receiptFolio)" else "Abono a cuenta (Recibo $receiptFolio)"

            val movStmt = conn.prepareStatement(
                """
                INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, cash_session_id, receipt_folio, description, user_id, created_at, payment_method)
                VALUES (?, ?, 'PAYMENT', ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            movStmt.setString(1, movId)
            movStmt.setString(2, request.customerId)
            movStmt.setLong(3, request.amountCents)
            movStmt.setLong(4, newBalance)
            movStmt.setString(5, request.cashSessionId)
            movStmt.setString(6, receiptFolio)
            movStmt.setString(7, desc)
            movStmt.setString(8, request.cashierUserId)
            movStmt.setLong(9, now)
            movStmt.setString(10, request.paymentMethod.uppercase())
            movStmt.executeUpdate()
            movStmt.close()

            CreditMovementDto(
                id = movId,
                customerId = request.customerId,
                type = "PAYMENT",
                amountCents = request.amountCents,
                balanceAfterCents = newBalance,
                cashSessionId = request.cashSessionId,
                receiptFolio = receiptFolio,
                description = desc,
                userId = request.cashierUserId,
                createdAt = now
            )
        }
    }

    fun getCustomerStatement(customerId: String): CustomerStatementDto {
        return db.inTransaction { conn ->
            val customer = getCustomerFromConn(conn, customerId)

            val stmt = conn.prepareStatement(
                """
                SELECT cm.id, cm.customer_id, cm.type, cm.amount_cents, cm.balance_after_cents,
                       cm.sale_id, cm.cash_session_id, cm.receipt_folio, cm.description, cm.user_id,
                       cm.created_at, COALESCE(u.name, 'Cajero') as user_name
                FROM credit_movements cm
                LEFT JOIN users u ON cm.user_id = u.id
                WHERE cm.customer_id = ?
                ORDER BY cm.created_at ASC
                """.trimIndent()
            )
            stmt.setString(1, customerId)
            val rs = stmt.executeQuery()
            val movements = mutableListOf<CreditMovementDto>()
            var totalCharges = 0L
            var totalPayments = 0L

            while (rs.next()) {
                val type = rs.getString("type")
                val amount = rs.getLong("amount_cents")
                if (type == "CHARGE") totalCharges += amount
                if (type == "PAYMENT" || type == "REVERSAL") totalPayments += amount

                movements.add(
                    CreditMovementDto(
                        id = rs.getString("id"),
                        customerId = rs.getString("customer_id"),
                        type = type,
                        amountCents = amount,
                        balanceAfterCents = rs.getLong("balance_after_cents"),
                        saleId = rs.getString("sale_id"),
                        cashSessionId = rs.getString("cash_session_id"),
                        receiptFolio = rs.getString("receipt_folio"),
                        description = rs.getString("description"),
                        userId = rs.getString("user_id"),
                        userName = rs.getString("user_name"),
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()

            CustomerStatementDto(
                customer = customer,
                movements = movements,
                totalChargesCents = totalCharges,
                totalPaymentsCents = totalPayments
            )
        }
    }

    fun getMovements(customerId: String): List<CreditMovementDto> {
        return getCustomerStatement(customerId).movements
    }
}

package mx.nexuspos.server.modules.credit

import mx.nexuspos.contracts.credit.CreateCustomerRequest
import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.RegisterCreditPaymentRequest
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID

class CreditService(private val db: DatabaseFactory) {

    init {
        seedInitialCustomers()
    }

    private fun seedInitialCustomers() {
        db.inTransaction { conn ->
            val countStmt = conn.prepareStatement("SELECT COUNT(*) FROM customers")
            val rs = countStmt.executeQuery()
            rs.next()
            val count = rs.getInt(1)
            rs.close()
            countStmt.close()

            if (count == 0) {
                val insertStmt = conn.prepareStatement(
                    "INSERT INTO customers (id, name, phone, credit_enabled, credit_limit_cents, current_balance_cents, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                // Don Pancho (Límite $1,500.00, Saldo actual $350.00)
                insertStmt.setString(1, "cust-1")
                insertStmt.setString(2, "Don Pancho (Vecino #12)")
                insertStmt.setString(3, "555-111-2233")
                insertStmt.setBoolean(4, true)
                insertStmt.setLong(5, 150000L)
                insertStmt.setLong(6, 35000L)
                insertStmt.setBoolean(7, true)
                insertStmt.executeUpdate()

                // Doña Mary (Límite $800.00, Saldo actual $0.00)
                insertStmt.setString(1, "cust-2")
                insertStmt.setString(2, "Doña Mary")
                insertStmt.setString(3, "555-444-5566")
                insertStmt.setBoolean(4, true)
                insertStmt.setLong(5, 80000L)
                insertStmt.setLong(6, 0L)
                insertStmt.setBoolean(7, true)
                insertStmt.executeUpdate()
                insertStmt.close()
            }
        }
    }

    fun searchCustomers(query: String?): List<CustomerDto> {
        return db.inTransaction { conn ->
            val sql = if (query.isNullOrBlank()) {
                "SELECT id, name, phone, credit_enabled, credit_limit_cents, current_balance_cents, is_active FROM customers WHERE is_active = TRUE ORDER BY name"
            } else {
                "SELECT id, name, phone, credit_enabled, credit_limit_cents, current_balance_cents, is_active FROM customers WHERE is_active = TRUE AND (LOWER(name) LIKE ? OR phone LIKE ?) ORDER BY name"
            }
            val stmt = conn.prepareStatement(sql)
            if (!query.isNullOrBlank()) {
                val q = "%${query.trim().lowercase()}%"
                stmt.setString(1, q)
                stmt.setString(2, q)
            }
            val rs = stmt.executeQuery()
            val list = mutableListOf<CustomerDto>()
            while (rs.next()) {
                val limit = rs.getLong("credit_limit_cents")
                val balance = rs.getLong("current_balance_cents")
                list.add(
                    CustomerDto(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        phone = rs.getString("phone"),
                        creditEnabled = rs.getBoolean("credit_enabled"),
                        creditLimitCents = limit,
                        currentBalanceCents = balance,
                        availableCreditCents = (limit - balance).coerceAtLeast(0L),
                        isActive = rs.getBoolean("is_active")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun getCustomer(id: String): CustomerDto {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "SELECT id, name, phone, credit_enabled, credit_limit_cents, current_balance_cents, is_active FROM customers WHERE id = ?"
            )
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                stmt.close()
                throw NotFoundException("Cliente '$id' no encontrado")
            }
            val limit = rs.getLong("credit_limit_cents")
            val balance = rs.getLong("current_balance_cents")
            val customer = CustomerDto(
                id = rs.getString("id"),
                name = rs.getString("name"),
                phone = rs.getString("phone"),
                creditEnabled = rs.getBoolean("credit_enabled"),
                creditLimitCents = limit,
                currentBalanceCents = balance,
                availableCreditCents = (limit - balance).coerceAtLeast(0L),
                isActive = rs.getBoolean("is_active")
            )
            rs.close()
            stmt.close()
            customer
        }
    }

    fun createCustomer(request: CreateCustomerRequest): CustomerDto {
        val id = "cust-" + UUID.randomUUID().toString().take(8)
        db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "INSERT INTO customers (id, name, phone, credit_enabled, credit_limit_cents, current_balance_cents, is_active) VALUES (?, ?, ?, ?, ?, 0, TRUE)"
            )
            stmt.setString(1, id)
            stmt.setString(2, request.name)
            stmt.setString(3, request.phone)
            stmt.setBoolean(4, request.creditEnabled)
            stmt.setLong(5, request.creditLimitCents)
            stmt.executeUpdate()
            stmt.close()
        }
        return getCustomer(id)
    }

    fun registerPayment(request: RegisterCreditPaymentRequest): CreditMovementDto {
        if (request.amountCents <= 0L) {
            throw ValidationException("El monto del abono debe ser mayor a cero")
        }

        return db.inTransaction { conn ->
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
            rs.close()
            custStmt.close()

            // RN-CRE-03: Un abono no puede exceder el saldo
            if (request.amountCents > currentBalance) {
                throw ValidationException(
                    "El abono de $${request.amountCents / 100.0} excede el saldo pendiente de $${currentBalance / 100.0}"
                )
            }

            val newBalance = currentBalance - request.amountCents
            val updateStmt = conn.prepareStatement(
                "UPDATE customers SET current_balance_cents = ? WHERE id = ?"
            )
            updateStmt.setLong(1, newBalance)
            updateStmt.setString(2, request.customerId)
            updateStmt.executeUpdate()
            updateStmt.close()

            val movId = "c-mov-" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val desc = if (request.notes.isNotBlank()) "Abono: ${request.notes}" else "Abono a cuenta"

            val movStmt = conn.prepareStatement(
                """
                INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, cash_session_id, description, user_id, created_at)
                VALUES (?, ?, 'PAYMENT', ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            movStmt.setString(1, movId)
            movStmt.setString(2, request.customerId)
            movStmt.setLong(3, request.amountCents)
            movStmt.setLong(4, newBalance)
            movStmt.setString(5, request.cashSessionId)
            movStmt.setString(6, desc)
            movStmt.setString(7, request.cashierUserId)
            movStmt.setLong(8, now)
            movStmt.executeUpdate()
            movStmt.close()

            CreditMovementDto(
                id = movId,
                customerId = request.customerId,
                type = "PAYMENT",
                amountCents = request.amountCents,
                balanceAfterCents = newBalance,
                cashSessionId = request.cashSessionId,
                description = desc,
                createdAt = now
            )
        }
    }

    fun getMovements(customerId: String): List<CreditMovementDto> {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                """
                SELECT id, customer_id, type, amount_cents, balance_after_cents, sale_id, cash_session_id, description, created_at
                FROM credit_movements
                WHERE customer_id = ?
                ORDER BY created_at DESC
                """.trimIndent()
            )
            stmt.setString(1, customerId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<CreditMovementDto>()
            while (rs.next()) {
                list.add(
                    CreditMovementDto(
                        id = rs.getString("id"),
                        customerId = rs.getString("customer_id"),
                        type = rs.getString("type"),
                        amountCents = rs.getLong("amount_cents"),
                        balanceAfterCents = rs.getLong("balance_after_cents"),
                        saleId = rs.getString("sale_id"),
                        cashSessionId = rs.getString("cash_session_id"),
                        description = rs.getString("description"),
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }
}

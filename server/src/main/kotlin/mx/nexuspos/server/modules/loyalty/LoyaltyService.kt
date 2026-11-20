package mx.nexuspos.server.modules.loyalty

import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.loyalty.AdjustLoyaltyBalanceRequest
import mx.nexuspos.contracts.loyalty.LoyaltyMovementDto
import mx.nexuspos.contracts.loyalty.LoyaltySummaryDto
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.ValidationException
import java.util.UUID

class LoyaltyService(private val db: DatabaseFactory) {

    fun getLoyaltyMovements(customerId: String): List<LoyaltyMovementDto> {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                """
                SELECT id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at
                FROM customer_loyalty_movements
                WHERE customer_id = ?
                ORDER BY created_at DESC
                """.trimIndent()
            )
            stmt.setString(1, customerId)
            val rs = stmt.executeQuery()
            val list = mutableListOf<LoyaltyMovementDto>()
            while (rs.next()) {
                list.add(
                    LoyaltyMovementDto(
                        id = rs.getString("id"),
                        customerId = rs.getString("customer_id"),
                        saleId = rs.getString("sale_id"),
                        type = rs.getString("type"),
                        amountCents = rs.getLong("amount_cents"),
                        balanceAfterCents = rs.getLong("balance_after_cents"),
                        description = rs.getString("description") ?: "",
                        userId = rs.getString("user_id") ?: "",
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun getLoyaltySummary(customerId: String): LoyaltySummaryDto {
        return db.inTransaction { conn ->
            val custStmt = conn.prepareStatement(
                """
                SELECT id, folio, name, loyalty_balance_cents, total_loyalty_earned_cents, total_loyalty_redeemed_cents
                FROM customers
                WHERE id = ?
                """.trimIndent()
            )
            custStmt.setString(1, customerId)
            val rs = custStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                custStmt.close()
                throw NotFoundException("Cliente '$customerId' no encontrado")
            }
            val id = rs.getString("id")
            val folio = rs.getString("folio") ?: ""
            val name = rs.getString("name")
            val balance = rs.getLong("loyalty_balance_cents")
            val earned = rs.getLong("total_loyalty_earned_cents")
            val redeemed = rs.getLong("total_loyalty_redeemed_cents")
            rs.close()
            custStmt.close()

            val movements = getLoyaltyMovements(customerId)

            LoyaltySummaryDto(
                customerId = id,
                customerFolio = folio,
                customerName = name,
                currentBalanceCents = balance,
                totalEarnedCents = earned,
                totalRedeemedCents = redeemed,
                movements = movements
            )
        }
    }

    fun adjustLoyaltyBalance(req: AdjustLoyaltyBalanceRequest): CustomerDto {
        if (req.amountCents == 0L) {
            throw ValidationException("El monto del ajuste no puede ser cero")
        }
        val now = System.currentTimeMillis()

        return db.inTransaction { conn ->
            val custStmt = conn.prepareStatement(
                """
                SELECT id, folio, name, phone, email, rfc, address, notes, credit_enabled, has_credit_limit,
                       credit_limit_cents, current_balance_cents, loyalty_balance_cents, total_loyalty_earned_cents,
                       total_loyalty_redeemed_cents, is_active, created_at, updated_at
                FROM customers
                WHERE id = ?
                FOR UPDATE
                """.trimIndent()
            )
            custStmt.setString(1, req.customerId)
            val rs = custStmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                custStmt.close()
                throw NotFoundException("Cliente '${req.customerId}' no encontrado")
            }

            val currentLoyalty = rs.getLong("loyalty_balance_cents")
            val newLoyalty = currentLoyalty + req.amountCents
            if (newLoyalty < 0L) {
                rs.close()
                custStmt.close()
                throw ValidationException("El saldo resultante en monedero no puede ser negativo ($newLoyalty cents)")
            }

            val earned = rs.getLong("total_loyalty_earned_cents")
            val newEarned = if (req.amountCents > 0L) earned + req.amountCents else earned

            val updateStmt = conn.prepareStatement(
                "UPDATE customers SET loyalty_balance_cents = ?, total_loyalty_earned_cents = ?, updated_at = ? WHERE id = ?"
            )
            updateStmt.setLong(1, newLoyalty)
            updateStmt.setLong(2, newEarned)
            updateStmt.setLong(3, now)
            updateStmt.setString(4, req.customerId)
            updateStmt.executeUpdate()
            updateStmt.close()

            // Registrar movimiento de lealtad
            val movStmt = conn.prepareStatement(
                """
                INSERT INTO customer_loyalty_movements (id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at)
                VALUES (?, ?, NULL, 'ADJUSTMENT', ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            val movId = "lm-" + UUID.randomUUID().toString().take(12)
            movStmt.setString(1, movId)
            movStmt.setString(2, req.customerId)
            movStmt.setLong(3, req.amountCents)
            movStmt.setLong(4, newLoyalty)
            val finalDesc = req.reason.trim().ifBlank { "Ajuste manual de saldo de monedero" }
            movStmt.setString(5, finalDesc)
            movStmt.setString(6, req.userId)
            movStmt.setLong(7, now)
            movStmt.executeUpdate()
            movStmt.close()

            val limit = rs.getLong("credit_limit_cents")
            val balance = rs.getLong("current_balance_cents")
            val hasLimit = rs.getBoolean("has_credit_limit")
            val available = if (!hasLimit) 99999900L else (limit - balance).coerceAtLeast(0L)

            val updatedCustomer = CustomerDto(
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
                loyaltyBalanceCents = newLoyalty,
                totalLoyaltyEarnedCents = newEarned,
                totalLoyaltyRedeemedCents = rs.getLong("total_loyalty_redeemed_cents"),
                isActive = rs.getBoolean("is_active"),
                createdAt = rs.getLong("created_at"),
                updatedAt = now
            )
            rs.close()
            custStmt.close()
            updatedCustomer
        }
    }
}

package mx.nexuspos.server.modules.identity

import mx.nexuspos.contracts.identity.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.Security
import mx.nexuspos.server.platform.UnauthorizedException
import java.util.UUID

class IdentityService(private val db: DatabaseFactory) {

    init {
        seedInitialUsers()
    }

    private fun seedInitialUsers() {
        db.inTransaction { conn ->
            val checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM users")
            val rs = checkStmt.executeQuery()
            rs.next()
            val count = rs.getInt(1)
            rs.close()
            checkStmt.close()

            if (count == 0) {
                val insertStmt = conn.prepareStatement(
                    "INSERT INTO users (id, name, pin_hash, role, is_active) VALUES (?, ?, ?, ?, ?)"
                )
                // Admin user (PIN: 1234)
                insertStmt.setString(1, "usr-admin")
                insertStmt.setString(2, "Administrador")
                insertStmt.setString(3, Security.hashPin("1234"))
                insertStmt.setString(4, "ADMIN")
                insertStmt.setBoolean(5, true)
                insertStmt.executeUpdate()

                // Cashier user (PIN: 0000)
                insertStmt.setString(1, "usr-cajero")
                insertStmt.setString(2, "Cajero Principal")
                insertStmt.setString(3, Security.hashPin("0000"))
                insertStmt.setString(4, "CASHIER")
                insertStmt.setBoolean(5, true)
                insertStmt.executeUpdate()
                insertStmt.close()
            }
        }
    }

    fun login(request: LoginRequest): LoginResponse {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "SELECT id, name, pin_hash, role FROM users WHERE is_active = TRUE"
            )
            val rs = stmt.executeQuery()
            var matchedUser: Triple<String, String, String>? = null // id, name, role
            while (rs.next()) {
                val pinHash = rs.getString("pin_hash")
                if (Security.verifyPin(request.pin, pinHash)) {
                    matchedUser = Triple(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("role")
                    )
                    break
                }
            }
            rs.close()
            stmt.close()

            if (matchedUser == null) {
                throw UnauthorizedException("PIN incorrecto o usuario inactivo")
            }

            val (id, name, role) = matchedUser
            val permissions = getPermissionsForRole(role)
            val token = Security.generateSecureToken()

            LoginResponse(
                token = token,
                userId = id,
                name = name,
                role = role,
                permissions = permissions
            )
        }
    }

    fun authorizeSupervisor(request: AuthorizeRequest): AuthorizeResponse {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "SELECT id, name, pin_hash, role FROM users WHERE role IN ('ADMIN', 'SUPERVISOR') AND is_active = TRUE"
            )
            val rs = stmt.executeQuery()
            var supervisor: Pair<String, String>? = null
            while (rs.next()) {
                val pinHash = rs.getString("pin_hash")
                if (Security.verifyPin(request.pin, pinHash)) {
                    supervisor = Pair(rs.getString("id"), rs.getString("name"))
                    break
                }
            }
            rs.close()
            stmt.close()

            if (supervisor == null) {
                AuthorizeResponse(authorized = false)
            } else {
                AuthorizeResponse(
                    authorized = true,
                    supervisorId = supervisor.first,
                    supervisorName = supervisor.second
                )
            }
        }
    }

    fun createPairingCode(): PairingCodeResponse {
        val code = (100000..999999).random().toString()
        val expiresAt = System.currentTimeMillis() + (15 * 60 * 1000L) // 15 minutos

        db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "INSERT INTO pairing_codes (code, expires_at, is_used) VALUES (?, ?, FALSE)"
            )
            stmt.setString(1, code)
            stmt.setLong(2, expiresAt)
            stmt.executeUpdate()
            stmt.close()
        }

        return PairingCodeResponse(code = code, expiresAt = expiresAt)
    }

    fun claimPairingCode(request: PairingClaimRequest): PairingClaimResponse {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement(
                "SELECT expires_at, is_used FROM pairing_codes WHERE code = ?"
            )
            stmt.setString(1, request.code)
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                stmt.close()
                throw NotFoundException("Código de emparejamiento no encontrado")
            }

            val expiresAt = rs.getLong("expires_at")
            val isUsed = rs.getBoolean("is_used")
            rs.close()
            stmt.close()

            val now = System.currentTimeMillis()
            if (isUsed || now > expiresAt) {
                throw UnauthorizedException("El código de emparejamiento ha expirado o ya fue utilizado")
            }

            // Mark code as used
            val updateStmt = conn.prepareStatement("UPDATE pairing_codes SET is_used = TRUE WHERE code = ?")
            updateStmt.setString(1, request.code)
            updateStmt.executeUpdate()
            updateStmt.close()

            val deviceId = "dev-" + UUID.randomUUID().toString().take(8)
            val token = Security.generateSecureToken()
            val tokenHash = Security.hashToken(token)

            val devStmt = conn.prepareStatement(
                "INSERT INTO devices (id, name, type, token_hash, is_revoked, created_at, last_seen_at) VALUES (?, ?, ?, ?, FALSE, ?, ?)"
            )
            devStmt.setString(1, deviceId)
            devStmt.setString(2, request.deviceName)
            devStmt.setString(3, request.deviceType)
            devStmt.setString(4, tokenHash)
            devStmt.setLong(5, now)
            devStmt.setLong(6, now)
            devStmt.executeUpdate()
            devStmt.close()

            PairingClaimResponse(deviceToken = token, deviceId = deviceId)
        }
    }

    private fun getPermissionsForRole(role: String): List<String> {
        return when (role) {
            "ADMIN" -> listOf("SELL", "CREDIT", "DISCOUNT", "CANCEL_SALE", "REFUND", "CASH_IN_OUT", "VIEW_REPORTS", "MANAGE_CATALOG", "MANAGE_USERS", "SETTINGS")
            "SUPERVISOR" -> listOf("SELL", "CREDIT", "DISCOUNT", "CANCEL_SALE", "REFUND", "CASH_IN_OUT", "VIEW_REPORTS", "MANAGE_CATALOG")
            else -> listOf("SELL", "CREDIT", "OPEN_CLOSE_SESSION")
        }
    }
}

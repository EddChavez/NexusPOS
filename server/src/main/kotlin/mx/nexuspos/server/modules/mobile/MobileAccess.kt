package mx.nexuspos.server.modules.mobile

import mx.nexuspos.contracts.mobile.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.*
import java.security.SecureRandom
import java.sql.Connection
import java.util.UUID

internal class MobileAccess(private val db: DatabaseFactory, private val now: () -> Long = System::currentTimeMillis) {
    private val random = SecureRandom()
    private val attempts = linkedMapOf<String, Pair<Long, Int>>()

    fun issue(pin: String): MobilePairingCode = db.inTransaction { conn ->
        requireAdmin(conn, pin)
        val expires = now() + 10 * 60_000
        conn.prepareStatement("DELETE FROM mobile_pairing_codes WHERE expires_at <= ?").use {
            it.setLong(1, now())
            it.executeUpdate()
        }
        repeat(4) {
            val code = random.nextInt(100_000_000).toString().padStart(8, '0')
            val inserted = conn.prepareStatement(
                "INSERT INTO mobile_pairing_codes VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use {
                it.setString(1, Security.hashToken(code))
                it.setLong(2, expires)
                it.executeUpdate()
            }
            if (inserted == 1) return@inTransaction MobilePairingCode(code, expires)
        }
        error("No se pudo emitir un código de vinculación.")
    }

    fun claim(request: MobilePairRequest): MobileDeviceCredential {
        val name = request.name.trim()
        if (!request.code.matches(Regex("[0-9]{8}")) || name.length !in 1..80) {
            throw ValidationException("Captura el código de 8 dígitos y un nombre de hasta 80 caracteres.")
        }
        limit("pairing", 30)
        return db.inTransaction { conn ->
            conn.prepareStatement("DELETE FROM mobile_pairing_codes WHERE code_hash = ? AND expires_at > ?").use {
                it.setString(1, Security.hashToken(request.code))
                it.setLong(2, now())
                if (it.executeUpdate() != 1) throw UnauthorizedException("El código venció, es incorrecto o ya se usó.")
            }
            val id = "mobile-" + UUID.randomUUID()
            val token = Security.generateSecureToken()
            conn.prepareStatement(
                "INSERT INTO devices (id, name, type, token_hash, is_revoked, created_at, last_seen_at) VALUES (?, ?, ?, ?, FALSE, ?, ?)"
            ).use {
                it.setString(1, id)
                it.setString(2, name)
                it.setString(3, type)
                it.setString(4, Security.hashToken(token))
                it.setLong(5, now())
                it.setLong(6, now())
                it.executeUpdate()
            }
            MobileDeviceCredential(id, token)
        }
    }

    fun login(deviceToken: String, pin: String): MobileSession = db.inTransaction { conn ->
        val device = conn.prepareStatement("SELECT id, is_revoked FROM devices WHERE token_hash = ? AND type = ? FOR UPDATE").use {
            it.setString(1, Security.hashToken(deviceToken))
            it.setString(2, type)
            it.executeQuery().use { row ->
                if (!row.next() || row.getBoolean("is_revoked")) throw ForbiddenException("Vuelve a vincular este dispositivo.")
                row.getString("id")
            }
        }
        limit("login:$device", 6)
        val (userId, name) = findUser(conn, pin, adminOnly = false)
        val token = Security.generateSecureToken()
        conn.prepareStatement("DELETE FROM mobile_sessions WHERE device_id = ?").use {
            it.setString(1, device)
            it.executeUpdate()
        }
        conn.prepareStatement("INSERT INTO mobile_sessions VALUES (?, ?, ?, ?)").use {
            it.setString(1, Security.hashToken(token))
            it.setString(2, device)
            it.setString(3, userId)
            it.setLong(4, now() + idleMillis)
            it.executeUpdate()
        }
        MobileSession(token, name)
    }

    fun authorize(token: String) = db.inTransaction { conn ->
        val current = now()
        conn.prepareStatement(
            """
            UPDATE mobile_sessions s SET expires_at = ?
            WHERE s.token_hash = ? AND s.expires_at > ?
            AND EXISTS (SELECT 1 FROM devices d WHERE d.id = s.device_id AND NOT d.is_revoked AND d.type = 'MOBILE_SUPERVISION_V1')
            AND EXISTS (SELECT 1 FROM users u WHERE u.id = s.user_id AND u.is_active AND u.role IN ('ADMIN', 'SUPERVISOR'))
            """.trimIndent()
        ).use {
            it.setLong(1, current + idleMillis)
            it.setString(2, Security.hashToken(token))
            it.setLong(3, current)
            if (it.executeUpdate() != 1) throw UnauthorizedException("La sesión terminó. Ingresa tu PIN.")
        }
    }

    fun logout(token: String) = db.inTransaction { conn ->
        conn.prepareStatement("DELETE FROM mobile_sessions WHERE token_hash = ?").use {
            it.setString(1, Security.hashToken(token))
            it.executeUpdate()
        }
    }

    fun list(pin: String): List<MobileDevice> = db.inTransaction { conn ->
        requireAdmin(conn, pin)
        conn.prepareStatement("SELECT id, name, is_revoked FROM devices WHERE type = ? ORDER BY created_at DESC, id").use {
            it.setString(1, type)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(MobileDevice(rs.getString(1), rs.getString(2), rs.getBoolean(3))) } }
        }
    }

    fun revoke(pin: String, id: String) = db.inTransaction { conn ->
        requireAdmin(conn, pin)
        conn.prepareStatement("UPDATE devices SET is_revoked = TRUE WHERE id = ? AND type = ?").use {
            it.setString(1, id)
            it.setString(2, type)
            if (it.executeUpdate() != 1) throw NotFoundException("No se encontró el dispositivo.")
        }
        conn.prepareStatement("DELETE FROM mobile_sessions WHERE device_id = ?").use {
            it.setString(1, id)
            it.executeUpdate()
        }
    }

    private fun requireAdmin(conn: Connection, pin: String) {
        limit("admin", 30)
        findUser(conn, pin, adminOnly = true)
    }

    private fun findUser(conn: Connection, pin: String, adminOnly: Boolean): Pair<String, String> {
        if (!pin.matches(Regex("[0-9]{4}([0-9]{2})?"))) throw ValidationException("Captura un PIN de 4 o 6 dígitos.")
        val roles = if (adminOnly) "'ADMIN'" else "'ADMIN', 'SUPERVISOR'"
        conn.prepareStatement("SELECT id, name, pin_hash FROM users WHERE is_active AND role IN ($roles)").use {
            it.executeQuery().use { rs ->
                while (rs.next()) {
                    if (Security.verifyPin(pin, rs.getString(3))) return rs.getString(1) to rs.getString(2)
                }
            }
        }
        throw UnauthorizedException(if (adminOnly) "Se requiere un PIN de administrador." else "PIN de encargado incorrecto o usuario inactivo.")
    }

    @Synchronized
    private fun limit(key: String, maximum: Int) {
        val time = now()
        attempts.entries.removeIf { time - it.value.first >= 60_000 }
        val window = attempts[key] ?: (time to 0)
        if (window.second >= maximum) throw MobileRateLimitException()
        attempts[key] = window.first to (window.second + 1)
    }

    private companion object {
        const val type = "MOBILE_SUPERVISION_V1"
        const val idleMillis = 15 * 60_000L
    }
}

internal class MobileRateLimitException : AppException("RATE_LIMITED", "Demasiados intentos. Espera un minuto.")

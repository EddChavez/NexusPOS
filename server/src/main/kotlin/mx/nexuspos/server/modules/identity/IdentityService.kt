package mx.nexuspos.server.modules.identity

import mx.nexuspos.contracts.identity.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.Security
import mx.nexuspos.server.platform.UnauthorizedException
import mx.nexuspos.server.platform.ValidationException
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
        if (request.deviceType !in setOf("CASHIER_DESKTOP", "SUPERVISOR_MOBILE")) {
            throw ValidationException("Tipo de dispositivo inválido.")
        }
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
            "ADMIN" -> listOf(
                "SELL", "CREDIT", "DISCOUNT", "CANCEL_SALE", "REFUND", "CASH_IN_OUT", "VIEW_REPORTS", "MANAGE_CATALOG", "MANAGE_USERS", "SETTINGS",
                "customers.view", "customers.create", "customers.update", "customers.deactivate",
                "customers.credit.view", "customers.credit.configure", "customers.credit.charge", "customers.credit.payment", "customers.credit.reverse",
                "customers.statement.view", "customers.statement.print", "customers.reports.view", "customers.export"
            )
            "SUPERVISOR" -> listOf(
                "SELL", "CREDIT", "DISCOUNT", "CANCEL_SALE", "REFUND", "CASH_IN_OUT", "VIEW_REPORTS", "MANAGE_CATALOG",
                "customers.view", "customers.create", "customers.update",
                "customers.credit.view", "customers.credit.configure", "customers.credit.charge", "customers.credit.payment", "customers.credit.reverse",
                "customers.statement.view", "customers.statement.print", "customers.reports.view"
            )
            else -> listOf(
                "SELL", "CREDIT", "OPEN_CLOSE_SESSION",
                "customers.view", "customers.credit.view", "customers.credit.charge", "customers.credit.payment",
                "customers.statement.view", "customers.statement.print"
            )
        }
    }

    fun getAllUsers(includeInactive: Boolean = true): List<UserDto> {
        return db.inTransaction { conn ->
            val sql = if (includeInactive) {
                "SELECT id, name, role, is_active FROM users ORDER BY is_active DESC, role ASC, name ASC"
            } else {
                "SELECT id, name, role, is_active FROM users WHERE is_active = TRUE ORDER BY role ASC, name ASC"
            }
            val stmt = conn.prepareStatement(sql)
            val rs = stmt.executeQuery()
            val list = mutableListOf<UserDto>()
            while (rs.next()) {
                list.add(
                    UserDto(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        role = rs.getString("role"),
                        isActive = rs.getBoolean("is_active")
                    )
                )
            }
            rs.close()
            stmt.close()
            list
        }
    }

    fun getUserById(id: String): UserDto {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement("SELECT id, name, role, is_active FROM users WHERE id = ?")
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                rs.close()
                stmt.close()
                throw NotFoundException("Usuario no encontrado")
            }
            val user = UserDto(
                id = rs.getString("id"),
                name = rs.getString("name"),
                role = rs.getString("role"),
                isActive = rs.getBoolean("is_active")
            )
            rs.close()
            stmt.close()
            user
        }
    }

    fun createUser(req: CreateUserRequest): UserDto {
        if (req.name.isBlank()) {
            throw ValidationException("El nombre del usuario no puede estar vacío")
        }
        val roleUpper = req.role.uppercase().trim()
        if (roleUpper !in listOf("CASHIER", "SUPERVISOR", "ADMIN")) {
            throw ValidationException("Rol de usuario inválido: ${req.role}. Debe ser CASHIER, SUPERVISOR o ADMIN")
        }
        val pin = req.pin.trim()
        if (!pin.matches(Regex("^\\d{4}$"))) {
            throw ValidationException("El PIN debe consistir exactamente de 4 dígitos numéricos")
        }

        return db.inTransaction { conn ->
            // Validar unicidad del PIN entre usuarios activos
            val checkStmt = conn.prepareStatement("SELECT id, pin_hash FROM users WHERE is_active = TRUE")
            val checkRs = checkStmt.executeQuery()
            while (checkRs.next()) {
                val existingHash = checkRs.getString("pin_hash")
                if (Security.verifyPin(pin, existingHash)) {
                    checkRs.close()
                    checkStmt.close()
                    throw ValidationException("El PIN ingresado ya está asignado a otro usuario activo")
                }
            }
            checkRs.close()
            checkStmt.close()

            val userId = "usr-" + UUID.randomUUID().toString().take(8)
            val pinHash = Security.hashPin(pin)
            val insertStmt = conn.prepareStatement(
                "INSERT INTO users (id, name, pin_hash, role, is_active) VALUES (?, ?, ?, ?, TRUE)"
            )
            insertStmt.setString(1, userId)
            insertStmt.setString(2, req.name.trim())
            insertStmt.setString(3, pinHash)
            insertStmt.setString(4, roleUpper)
            insertStmt.executeUpdate()
            insertStmt.close()

            UserDto(
                id = userId,
                name = req.name.trim(),
                role = roleUpper,
                isActive = true
            )
        }
    }

    fun updateUser(id: String, req: UpdateUserRequest): UserDto {
        if (req.name.isBlank()) {
            throw ValidationException("El nombre del usuario no puede estar vacío")
        }
        val roleUpper = req.role.uppercase().trim()
        if (roleUpper !in listOf("CASHIER", "SUPERVISOR", "ADMIN")) {
            throw ValidationException("Rol de usuario inválido: ${req.role}")
        }

        return db.inTransaction { conn ->
            val userStmt = conn.prepareStatement("SELECT id, role, is_active FROM users WHERE id = ?")
            userStmt.setString(1, id)
            val userRs = userStmt.executeQuery()
            if (!userRs.next()) {
                userRs.close()
                userStmt.close()
                throw NotFoundException("Usuario no encontrado")
            }
            val currentRole = userRs.getString("role")
            val isActive = userRs.getBoolean("is_active")
            userRs.close()
            userStmt.close()

            // Si se intenta cambiar el rol de ADMIN a otro, verificar que no sea el único ADMIN activo
            if (currentRole == "ADMIN" && roleUpper != "ADMIN" && isActive) {
                val adminCountStmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = TRUE AND id != ?")
                adminCountStmt.setString(1, id)
                val adminRs = adminCountStmt.executeQuery()
                adminRs.next()
                val remainingAdmins = adminRs.getInt(1)
                adminRs.close()
                adminCountStmt.close()
                if (remainingAdmins == 0) {
                    throw ValidationException("No es posible cambiar el rol del único Administrador activo del sistema")
                }
            }

            val newPin = req.pin?.trim()
            if (!newPin.isNullOrBlank()) {
                if (!newPin.matches(Regex("^\\d{4}$"))) {
                    throw ValidationException("El PIN debe consistir exactamente de 4 dígitos numéricos")
                }
                val checkStmt = conn.prepareStatement("SELECT id, pin_hash FROM users WHERE is_active = TRUE AND id != ?")
                checkStmt.setString(1, id)
                val checkRs = checkStmt.executeQuery()
                while (checkRs.next()) {
                    val existingHash = checkRs.getString("pin_hash")
                    if (Security.verifyPin(newPin, existingHash)) {
                        checkRs.close()
                        checkStmt.close()
                        throw ValidationException("El nuevo PIN ya está asignado a otro usuario activo")
                    }
                }
                checkRs.close()
                checkStmt.close()

                val newHash = Security.hashPin(newPin)
                val updateStmt = conn.prepareStatement("UPDATE users SET name = ?, role = ?, pin_hash = ? WHERE id = ?")
                updateStmt.setString(1, req.name.trim())
                updateStmt.setString(2, roleUpper)
                updateStmt.setString(3, newHash)
                updateStmt.setString(4, id)
                updateStmt.executeUpdate()
                updateStmt.close()
            } else {
                val updateStmt = conn.prepareStatement("UPDATE users SET name = ?, role = ? WHERE id = ?")
                updateStmt.setString(1, req.name.trim())
                updateStmt.setString(2, roleUpper)
                updateStmt.setString(3, id)
                updateStmt.executeUpdate()
                updateStmt.close()
            }

            UserDto(
                id = id,
                name = req.name.trim(),
                role = roleUpper,
                isActive = isActive
            )
        }
    }

    fun deactivateUser(id: String): UserDto {
        return db.inTransaction { conn ->
            val userStmt = conn.prepareStatement("SELECT id, name, role, is_active FROM users WHERE id = ?")
            userStmt.setString(1, id)
            val userRs = userStmt.executeQuery()
            if (!userRs.next()) {
                userRs.close()
                userStmt.close()
                throw NotFoundException("Usuario no encontrado")
            }
            val name = userRs.getString("name")
            val role = userRs.getString("role")
            val isActive = userRs.getBoolean("is_active")
            userRs.close()
            userStmt.close()

            if (!isActive) {
                return@inTransaction UserDto(id = id, name = name, role = role, isActive = false)
            }

            if (role == "ADMIN") {
                val adminCountStmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND is_active = TRUE AND id != ?")
                adminCountStmt.setString(1, id)
                val adminRs = adminCountStmt.executeQuery()
                adminRs.next()
                val remainingAdmins = adminRs.getInt(1)
                adminRs.close()
                adminCountStmt.close()
                if (remainingAdmins == 0) {
                    throw ValidationException("No es posible desactivar al único Administrador activo del sistema")
                }
            }

            val updateStmt = conn.prepareStatement("UPDATE users SET is_active = FALSE WHERE id = ?")
            updateStmt.setString(1, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            UserDto(id = id, name = name, role = role, isActive = false)
        }
    }

    fun reactivateUser(id: String): UserDto {
        return db.inTransaction { conn ->
            val userStmt = conn.prepareStatement("SELECT id, name, role, pin_hash, is_active FROM users WHERE id = ?")
            userStmt.setString(1, id)
            val userRs = userStmt.executeQuery()
            if (!userRs.next()) {
                userRs.close()
                userStmt.close()
                throw NotFoundException("Usuario no encontrado")
            }
            val name = userRs.getString("name")
            val role = userRs.getString("role")
            val isActive = userRs.getBoolean("is_active")
            userRs.close()
            userStmt.close()

            if (isActive) {
                return@inTransaction UserDto(id = id, name = name, role = role, isActive = true)
            }

            val updateStmt = conn.prepareStatement("UPDATE users SET is_active = TRUE WHERE id = ?")
            updateStmt.setString(1, id)
            updateStmt.executeUpdate()
            updateStmt.close()

            UserDto(id = id, name = name, role = role, isActive = true)
        }
    }
}

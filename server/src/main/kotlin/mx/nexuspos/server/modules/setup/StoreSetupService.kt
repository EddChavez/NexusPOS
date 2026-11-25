package mx.nexuspos.server.modules.setup

import mx.nexuspos.contracts.system.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.*
import java.security.SecureRandom
import java.sql.Connection
import java.time.ZoneId
import java.util.UUID

internal enum class SetupPurpose { COMPLETE, ADOPT, RECOVER }

internal class StoreSetupService(
    private val db: DatabaseFactory,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val random = SecureRandom()

    fun status(): SetupStatus = db.inTransaction { SetupStatus(state(it)) }

    fun issue(purpose: SetupPurpose): String = db.inTransaction { connection ->
        lock(connection)
        requireState(connection, purpose)
        val code = random.nextInt(100_000_000).toString().padStart(8, '0')
        connection.prepareStatement("DELETE FROM store_setup_codes WHERE purpose = ?").use {
            it.setString(1, purpose.name)
            it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO store_setup_codes (purpose, code_hash, expires_at) VALUES (?, ?, ?)").use {
            it.setString(1, purpose.name)
            it.setString(2, Security.hashToken(code))
            it.setLong(3, now() + 600_000L)
            it.executeUpdate()
        }
        code
    }

    fun complete(request: StoreSetupRequest): StoreSetupResult {
        val businessName = required(request.businessName, 128, "nombre del negocio")
        val branchName = required(request.branchName, 128, "nombre de la sucursal")
        val registerName = required(request.registerName, 100, "nombre de la caja")
        val rootName = required(request.rootName, 128, "nombre del administrador")
        val profile = validateProfile(StoreProfile(
            businessName = businessName, branchName = branchName, businessType = request.businessType,
            country = request.country, address = request.address, phone = request.phone, timeZone = request.timeZone
        ))
        validateRootPin(request.rootPin)
        if (request.maxCashierDiscountPercent !in 0.0..100.0) throw ValidationException("El descuento debe estar entre 0 y 100.")
        return useCode(SetupPurpose.COMPLETE, request.code) { connection ->
            connection.prepareStatement("INSERT INTO users (id, name, pin_hash, role, is_active, is_root) VALUES (?, ?, ?, 'ADMIN', TRUE, TRUE)").use {
                it.setString(1, "usr-admin")
                it.setString(2, rootName)
                it.setString(3, Security.hashPin(request.rootPin))
                it.executeUpdate()
            }
            connection.prepareStatement("UPDATE cash_registers SET name = ? WHERE id = 'caja-1'").use {
                it.setString(1, registerName)
                check(it.executeUpdate() == 1)
            }
            saveProfile(connection, profile)
            writeSettings(connection, mapOf(
                "allow_negative_stock" to request.allowNegativeStock.toString(),
                "blind_cash_count_enabled" to request.blindCashCount.toString(),
                "max_cashier_discount_percent" to request.maxCashierDiscountPercent.toString(),
                "loyalty_enabled" to request.loyaltyEnabled.toString()
            ))
            finish(connection, "usr-admin")
            audit(connection, "setup.complete", "usr-admin")
            StoreSetupResult("caja-1", registerName)
        }
    }

    fun adopt(request: AdoptRootRequest) {
        validateRootPin(request.rootPin)
        if (!request.administratorPin.matches(Regex("[0-9]{4}"))) throw ValidationException("Captura el PIN del administrador vigente.")
        useCode(SetupPurpose.ADOPT, request.code) { connection ->
            val admin = connection.prepareStatement("SELECT id, pin_hash FROM users WHERE role = 'ADMIN' AND is_active FOR UPDATE").use {
                it.executeQuery().use { rows ->
                    var id: String? = null
                    while (rows.next() && id == null) {
                        if (Security.verifyPin(request.administratorPin, rows.getString("pin_hash"))) id = rows.getString("id")
                    }
                    id
                }
            } ?: throw UnauthorizedException("PIN de administrador incorrecto.")
            rotateFactoryCashier(connection, request.replacementCashierPin)
            rotateRoot(connection, admin, request.rootPin)
            finish(connection, admin)
            audit(connection, "setup.adopt", admin)
        }
    }

    fun recover(request: RecoverRootRequest) {
        validateRootPin(request.rootPin)
        useCode(SetupPurpose.RECOVER, request.code) { connection ->
            val id = checkNotNull(rootId(connection))
            rotateRoot(connection, id, request.rootPin)
            audit(connection, "setup.recover", id)
        }
    }

    fun profile(): StoreProfile = db.inTransaction { connection ->
        val values = connection.prepareStatement("SELECT setting_key, setting_value FROM settings").use {
            it.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) } }
        }
        connection.prepareStatement(
            "SELECT s.business_id, s.branch_id, u.name FROM store_setup s LEFT JOIN users u ON u.id = s.root_user_id WHERE s.id = 1"
        ).use {
            it.executeQuery().use { row ->
                check(row.next())
                StoreProfile(
                    row.getString(1), row.getString(2), values["store_name"].orEmpty(), values["business_type"].orEmpty(),
                    values["country"] ?: "MX", values["branch_name"].orEmpty(), values["store_address"].orEmpty(),
                    values["store_phone"].orEmpty(), values["store_time_zone"] ?: "America/Mexico_City", row.getString(3)
                )
            }
        }
    }

    fun updateProfile(profile: StoreProfile): StoreProfile {
        val validated = validateProfile(profile)
        db.inTransaction { saveProfile(it, validated) }
        return profile()
    }

    private fun validateProfile(profile: StoreProfile): StoreProfile {
        val country = profile.country.trim().uppercase()
        if (!country.matches(Regex("[A-Z]{2}"))) throw ValidationException("Usa un código de país de dos letras.")
        val zone = profile.timeZone.trim()
        if (zone !in ZoneId.getAvailableZoneIds()) throw ValidationException("Selecciona una zona horaria válida.")
        return profile.copy(
            businessName = required(profile.businessName, 128, "nombre del negocio"),
            businessType = optional(profile.businessType, 80),
            country = country,
            branchName = required(profile.branchName, 128, "nombre de la sucursal"),
            address = required(profile.address, 300, "domicilio"),
            phone = optional(profile.phone, 40),
            timeZone = zone
        )
    }

    private fun saveProfile(connection: Connection, profile: StoreProfile) = writeSettings(connection, mapOf(
        "store_name" to profile.businessName,
        "business_type" to profile.businessType,
        "country" to profile.country,
        "branch_name" to profile.branchName,
        "store_address" to profile.address,
        "store_phone" to profile.phone,
        "store_time_zone" to profile.timeZone
    ))

    private fun <T : Any> useCode(purpose: SetupPurpose, submitted: String, run: (Connection) -> T): T {
        if (!submitted.matches(Regex("[0-9]{8}"))) throw ValidationException("Captura el código de configuración de 8 dígitos.")
        val result = db.inTransaction { connection ->
            lock(connection)
            requireState(connection, purpose)
            val valid = connection.prepareStatement(
                "UPDATE store_setup_codes SET attempts = attempts + 1 WHERE purpose = ? AND expires_at > ? AND attempts < 6"
            ).use {
                it.setString(1, purpose.name)
                it.setLong(2, now())
                it.executeUpdate() == 1
            }
            if (!valid) return@inTransaction null
            val matching = connection.prepareStatement("SELECT code_hash FROM store_setup_codes WHERE purpose = ?").use {
                it.setString(1, purpose.name)
                it.executeQuery().use { row -> row.next() && row.getString(1) == Security.hashToken(submitted) }
            }
            if (!matching) return@inTransaction null
            // Keep failed authorization attempts while rolling back any partial setup.
            val savepoint = connection.setSavepoint()
            val value = try {
                Result.success(run(connection))
            } catch (error: AppException) {
                connection.rollback(savepoint)
                return@inTransaction Result.failure<T>(error)
            }
            connection.prepareStatement("DELETE FROM store_setup_codes WHERE purpose = ?").use {
                it.setString(1, purpose.name)
                it.executeUpdate()
            }
            value
        }
        return result?.getOrThrow() ?: throw UnauthorizedException("Código inválido, vencido o agotado. Genera otro en el servidor.")
    }

    private fun lock(connection: Connection) {
        connection.prepareStatement("SELECT id FROM store_setup WHERE id = 1 FOR UPDATE").use {
            it.executeQuery().use { row -> check(row.next()) }
        }
    }

    private fun state(connection: Connection): SetupState {
        if (rootId(connection) != null) return SetupState.READY
        val existing = connection.prepareStatement("SELECT 1 FROM users LIMIT 1").use {
            it.executeQuery().use { row -> row.next() }
        }
        return if (existing) SetupState.LEGACY else SetupState.NEW
    }

    private fun requireState(connection: Connection, purpose: SetupPurpose) {
        val expected = when (purpose) {
            SetupPurpose.COMPLETE -> SetupState.NEW
            SetupPurpose.ADOPT -> SetupState.LEGACY
            SetupPurpose.RECOVER -> SetupState.READY
        }
        if (state(connection) != expected) throw ConflictException("La instalación ya cambió de estado. Actualiza la pantalla.")
    }

    private fun rootId(connection: Connection): String? =
        connection.prepareStatement("SELECT root_user_id FROM store_setup WHERE id = 1").use {
            it.executeQuery().use { row -> if (row.next()) row.getString(1) else null }
        }

    private fun finish(connection: Connection, userId: String) {
        connection.prepareStatement("UPDATE store_setup SET root_user_id = ?, business_id = ?, branch_id = ?, completed_at = ? WHERE id = 1").use {
            it.setString(1, userId)
            it.setString(2, UUID.randomUUID().toString())
            it.setString(3, UUID.randomUUID().toString())
            it.setLong(4, now())
            it.executeUpdate()
        }
    }

    private fun rotateRoot(connection: Connection, id: String, pin: String) {
        connection.prepareStatement("UPDATE users SET pin_hash = ?, is_root = TRUE WHERE id = ?").use {
            it.setString(1, Security.hashPin(pin))
            it.setString(2, id)
            check(it.executeUpdate() == 1)
        }
        revokeUserSessions(connection, id)
    }

    private fun rotateFactoryCashier(connection: Connection, pin: String?) {
        val factory = connection.prepareStatement("SELECT pin_hash FROM users WHERE id = 'usr-cajero' AND is_active FOR UPDATE").use {
            it.executeQuery().use { row -> row.next() && Security.verifyPin("0000", row.getString(1)) }
        }
        if (!factory) return
        if (pin == null || !pin.matches(Regex("[0-9]{4}")) || pin in setOf("0000", "1234")) {
            throw ValidationException("Reemplaza el PIN de fábrica del cajero con uno de 4 dígitos.")
        }
        connection.prepareStatement("SELECT pin_hash FROM users WHERE is_active AND id <> 'usr-cajero'").use {
            it.executeQuery().use { rows ->
                while (rows.next()) if (Security.verifyPin(pin, rows.getString(1))) throw ValidationException("Ese PIN ya está asignado.")
            }
        }
        connection.prepareStatement("UPDATE users SET pin_hash = ? WHERE id = 'usr-cajero'").use {
            it.setString(1, Security.hashPin(pin))
            it.executeUpdate()
        }
        revokeUserSessions(connection, "usr-cajero")
    }

    private fun audit(connection: Connection, action: String, id: String) {
        connection.prepareStatement("INSERT INTO security_audit (id, action, actor_user_id, target_id, created_at) VALUES (?, ?, ?, ?, ?)").use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, action)
            it.setString(3, id)
            it.setString(4, id)
            it.setLong(5, now())
            it.executeUpdate()
        }
    }
}

internal fun validateRootPin(pin: String) {
    val trivial = pin.toSet().size <= 2 || pin in "0123456789012345" || pin in "9876543210987654"
    if (!pin.matches(Regex("[0-9]{6}")) || trivial) {
        throw ValidationException("Elige un PIN de 6 dígitos sin secuencias ni repeticiones triviales.")
    }
}

internal fun revokeUserSessions(connection: Connection, userId: String) {
    for (table in listOf("desktop_sessions", "mobile_sessions")) {
        connection.prepareStatement("DELETE FROM $table WHERE user_id = ?").use {
            it.setString(1, userId)
            it.executeUpdate()
        }
    }
}

internal fun writeSettings(connection: Connection, values: Map<String, String>) {
    connection.prepareStatement("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT DO NOTHING").use { insert ->
        connection.prepareStatement("UPDATE settings SET setting_value = ? WHERE setting_key = ?").use { update ->
            for ((key, value) in values) {
                insert.setString(1, key)
                insert.setString(2, value)
                insert.executeUpdate()
                update.setString(1, value)
                update.setString(2, key)
                update.executeUpdate()
            }
        }
    }
}

private fun required(value: String, max: Int, label: String): String =
    value.trim().takeIf { it.length in 1..max } ?: throw ValidationException("Captura el $label (hasta $max caracteres).")

private fun optional(value: String, max: Int): String =
    value.trim().takeIf { it.length <= max } ?: throw ValidationException("El valor admite hasta $max caracteres.")

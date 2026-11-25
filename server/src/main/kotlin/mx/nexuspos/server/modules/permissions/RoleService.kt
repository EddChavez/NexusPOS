package mx.nexuspos.server.modules.permissions

import mx.nexuspos.contracts.identity.*
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.*
import java.sql.Connection
import java.util.UUID

internal class RoleService(private val db: DatabaseFactory) {
    fun permissions(actor: UserDto): Set<String> = db.inTransaction { permissionsForRole(it, actor.role) }

    fun list(): List<RoleDto> = db.inTransaction { connection ->
        val counts = connection.prepareStatement("SELECT role, COUNT(*) FROM users GROUP BY role").use {
            it.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) } }
        }
        val builtIn = PermissionCatalog.builtIns.map { (id, name) ->
            RoleDto(id, name, permissionsForRole(connection, id), counts[id] ?: 0, true)
        }
        val custom = connection.prepareStatement("SELECT id, name FROM custom_roles ORDER BY normalized_name, id").use {
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) } }
        }
        builtIn + custom.map { (id, name) -> RoleDto(id, name, permissionsForRole(connection, id), counts[id] ?: 0) }
    }

    fun save(actor: UserDto, id: String?, request: SaveRoleRequest): RoleDto = db.inTransaction { connection ->
        val name = request.name.trim()
        if (name.length !in 1..80 || PermissionCatalog.builtIns.values.any { it.equals(name, ignoreCase = true) }) {
            throw ValidationException("Elige un nombre distinto para el rol.")
        }
        val actorPermissions = permissionsForRole(connection, actor.role)
        if ("roles.manage" !in actorPermissions || !actorPermissions.containsAll(request.permissions)) {
            throw ForbiddenException("No puedes otorgar permisos que tu cuenta no tiene.")
        }
        for (definition in PermissionCatalog.definitions) {
            val parent = definition.parent
            if (definition.key in request.permissions && parent != null && parent !in request.permissions) {
                throw ValidationException("Activa primero el permiso requerido para ${definition.label}.")
            }
        }
        lockRoles(connection)
        val roleId = id ?: "ROLE-${UUID.randomUUID().toString().take(16).uppercase()}"
        if (id != null) {
            requireCustomRole(connection, id)
            if (!actorPermissions.containsAll(permissionsForRole(connection, id))) throw ForbiddenException("Ese rol tiene permisos superiores.")
        }
        val duplicate = connection.prepareStatement("SELECT 1 FROM custom_roles WHERE normalized_name = ? AND id <> ?").use {
            it.setString(1, name.lowercase())
            it.setString(2, roleId)
            it.executeQuery().use { row -> row.next() }
        }
        if (duplicate) throw ConflictException("Ya existe un rol con ese nombre.")
        if (id == null) {
            connection.prepareStatement("INSERT INTO custom_roles (id, name, normalized_name) VALUES (?, ?, ?)").use {
                it.setString(1, roleId)
                it.setString(2, name)
                it.setString(3, name.lowercase())
                it.executeUpdate()
            }
        } else {
            connection.prepareStatement("UPDATE custom_roles SET name = ?, normalized_name = ? WHERE id = ?").use {
                it.setString(1, name)
                it.setString(2, name.lowercase())
                it.setString(3, roleId)
                it.executeUpdate()
            }
        }
        connection.prepareStatement("DELETE FROM custom_role_permissions WHERE role_id = ?").use {
            it.setString(1, roleId)
            it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO custom_role_permissions (role_id, permission_key) VALUES (?, ?)").use {
            for (permission in request.permissions) {
                it.setString(1, roleId)
                it.setString(2, permission)
                it.addBatch()
            }
            it.executeBatch()
        }
        writeAudit(connection, if (id == null) "roles.create" else "roles.update", actor.id, roleId)
        RoleDto(roleId, name, request.permissions, assignedCount(connection, roleId))
    }

    fun delete(actor: UserDto, id: String) = db.inTransaction { connection ->
        lockRoles(connection)
        requireCustomRole(connection, id)
        val available = permissionsForRole(connection, actor.role)
        if ("roles.manage" !in available || !available.containsAll(permissionsForRole(connection, id))) {
            throw ForbiddenException("No tienes acceso para eliminar este rol.")
        }
        if (assignedCount(connection, id) != 0) throw ConflictException("Reasigna primero a los usuarios de este rol.")
        connection.prepareStatement("DELETE FROM custom_roles WHERE id = ?").use {
            it.setString(1, id)
            it.executeUpdate()
        }
        writeAudit(connection, "roles.delete", actor.id, id)
    }

    private fun requireCustomRole(connection: Connection, id: String) {
        if (id in PermissionCatalog.builtIns) throw ForbiddenException("Los perfiles iniciales se pueden duplicar como roles personalizados.")
        if (!roleExists(connection, id)) throw NotFoundException("Rol no encontrado.")
    }
}

internal fun lockRoles(connection: Connection) {
    connection.prepareStatement("SELECT id FROM store_setup WHERE id = 1 FOR UPDATE").use {
        it.executeQuery().use { row -> check(row.next()) }
    }
}

internal fun roleExists(connection: Connection, role: String): Boolean = role in PermissionCatalog.builtIns ||
    connection.prepareStatement("SELECT 1 FROM custom_roles WHERE id = ?").use {
        it.setString(1, role)
        it.executeQuery().use { row -> row.next() }
    }

internal fun permissionsForRole(connection: Connection, role: String): Set<String> = PermissionCatalog.builtIn(role) ?:
    connection.prepareStatement("SELECT permission_key FROM custom_role_permissions WHERE role_id = ?").use {
        it.setString(1, role)
        it.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
    }

private fun assignedCount(connection: Connection, id: String): Int =
    connection.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?").use {
        it.setString(1, id)
        it.executeQuery().use { row -> row.next(); row.getInt(1) }
    }

internal fun writeAudit(connection: Connection, action: String, actorId: String, target: String, approvedBy: String? = null) {
    connection.prepareStatement("INSERT INTO security_audit (id, action, actor_user_id, target_id, created_at, approved_by_user_id) VALUES (?, ?, ?, ?, ?, ?)").use {
        it.setString(1, UUID.randomUUID().toString())
        it.setString(2, action)
        it.setString(3, actorId)
        it.setString(4, target)
        it.setLong(5, System.currentTimeMillis())
        it.setString(6, approvedBy)
        it.executeUpdate()
    }
}

package mx.nexuspos.contracts.identity

import kotlinx.serialization.Serializable

@Serializable
data class PermissionDto(val key: String, val group: String, val label: String, val parent: String? = null)

@Serializable
data class RoleDto(
    val id: String,
    val name: String,
    val permissions: Set<String>,
    val assignedUsers: Int,
    val builtIn: Boolean = false
)

@Serializable
data class SaveRoleRequest(val name: String, val permissions: Set<String>)

package mx.nexuspos.server.modules.permissions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import mx.nexuspos.contracts.identity.SaveRoleRequest
import mx.nexuspos.contracts.identity.UserDto
import mx.nexuspos.contracts.system.SetupState
import mx.nexuspos.server.modules.identity.IdentityService
import mx.nexuspos.server.modules.setup.StoreSetupService
import mx.nexuspos.server.modules.setup.desktopToken
import mx.nexuspos.server.platform.*

internal data class Access(val user: UserDto, val permissions: Set<String>) {
    fun require(vararg keys: String) {
        if (!permissions.containsAll(keys.asList())) throw ForbiddenException("Tu rol no permite realizar esta acción.")
    }
}
private val accessKey = AttributeKey<Access>("desktopAccess")
internal val ApplicationCall.access: Access? get() = attributes.getOrNull(accessKey)

internal fun ApplicationCall.requireDesktopAccess(identity: IdentityService, roles: RoleService): Access {
    access?.let { return it }
    val user = identity.authenticate(desktopToken())
    return Access(user, roles.permissions(user)).also { attributes.put(accessKey, it) }
}

internal fun Route.roleRoutes(identity: IdentityService, roles: RoleService, setup: StoreSetupService) {
    route("/roles") {
        fun ApplicationCall.actor(write: Boolean = false): Access {
            val actor = requireDesktopAccess(identity, roles)
            if (write) {
                if (setup.status().state != SetupState.READY) throw ConflictException("Configura primero al administrador raíz.")
                actor.require("roles.manage")
            } else if (actor.permissions.none { it in setOf("roles.manage", "users.assign_role", "users.view") }) {
                throw ForbiddenException("Tu rol no permite consultar roles.")
            }
            return actor
        }
        get { call.actor(); call.respond(roles.list()) }
        get("/permissions") { call.actor(); call.respond(PermissionCatalog.definitions) }
        post {
            val actor = call.actor(write = true)
            call.respond(HttpStatusCode.Created, roles.save(actor.user, null, call.receive<SaveRoleRequest>()))
        }
        put("/{id}") {
            val actor = call.actor(write = true)
            val id = call.parameters["id"] ?: throw ValidationException("Indica el rol.")
            call.respond(roles.save(actor.user, id, call.receive<SaveRoleRequest>()))
        }
        delete("/{id}") {
            val actor = call.actor(write = true)
            roles.delete(actor.user, call.parameters["id"] ?: throw ValidationException("Indica el rol."))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal fun permissionFor(path: String, method: HttpMethod): String? {
    val parts = path.removePrefix("/api/v1/").split('/')
    val read = method == HttpMethod.Get
    val leaf = parts.last()
    return when (parts.first()) {
        "users" -> when {
            read -> "users.view"
            method == HttpMethod.Delete -> "users.deactivate"
            leaf == "reactivate" -> "users.reactivate"
            method == HttpMethod.Put -> "users.edit"
            else -> "users.create"
        }
        "system" -> when {
            read -> null
            leaf == "store-profile" -> "settings.profile"
            leaf == "commerce" -> "settings.commerce"
            else -> "settings.operating"
        }
        "pairing" -> if (leaf == "codes") "devices.manage" else null
        "departments" -> if (read) "products.view" else if (method == HttpMethod.Delete) "departments.delete" else "departments.edit"
        "products" -> when {
            read -> "products.view"
            method == HttpMethod.Delete -> "products.deactivate"
            leaf == "reactivate" -> "products.reactivate"
            leaf == "kit" -> "products.kit.edit"
            leaf == "prices" -> "products.tiers.edit"
            parts.getOrNull(4) == "list-prices" -> "products.price.$leaf"
            parts.size == 1 -> "products.create"
            else -> "products.edit"
        }
        "price-lists" -> if (read) "products.view" else "settings.commerce"
        "catalog" -> if (read) "products.export" else "products.import"
        "inventory" -> when {
            leaf == "movements" && read -> "inventory.movements"
            leaf == "movements" -> "inventory.stock"
            leaf == "write-off" -> "inventory.write_off"
            parts.getOrNull(1) == "lots" -> "inventory.lots"
            else -> "inventory.report"
        }
        "registers" -> if (read) "cash.view" else "registers.manage"
        "cash-sessions" -> when {
            parts.size == 1 -> "cash.open"
            read -> "cash.view"
            leaf == "handoff" -> "cash.handoff"
            leaf == "close" -> "cash.close"
            leaf == "reverse" -> "cash.reverse"
            parts.getOrNull(2) == "movements" && leaf == "history" -> "cash.audit"
            leaf == "movements" -> "cash.view"
            leaf == "search" -> "cash.history"
            parts.getOrNull(1) == "history" && parts.size > 3 -> "transactions.closed"
            else -> "cash.detail"
        }
        "customers" -> when {
            leaf == "reactivate" -> "customers.reactivate"
            method == HttpMethod.Delete -> "customers.deactivate"
            leaf == "payments" -> "customers.credit.payment"
            leaf == "statement" || leaf == "movements" -> "customers.statement.view"
            leaf == "adjust" -> "customers.loyalty.adjust"
            parts.getOrNull(2) == "loyalty" -> "customers.loyalty.view"
            read -> "customers.view"
            method == HttpMethod.Put -> "customers.update"
            else -> "customers.create"
        }
        "sales" -> when {
            read -> "transactions.own"
            leaf == "cancel" || leaf == "refund" -> "transactions.own"
            else -> "sales.checkout"
        }
        "purchases" -> when {
            parts.getOrNull(1) == "payables" -> null // These routes enforce their own permissions in all setup states.
            read -> "purchases.view"
            parts.getOrNull(1) == "suppliers" -> "suppliers.edit"
            leaf == "returns" -> "purchases.return"
            else -> "purchases.receive"
        }
        "reports" -> "reports.sales"
        "parked-sales" -> when {
            read -> "sales.held.view"
            leaf == "retrieve" -> "sales.held.retrieve"
            leaf == "discard" -> "sales.held.discard"
            else -> "sales.held.park"
        }
        else -> null
    }
}

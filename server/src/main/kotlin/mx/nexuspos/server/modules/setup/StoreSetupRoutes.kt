package mx.nexuspos.server.modules.setup

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mx.nexuspos.contracts.system.*
import mx.nexuspos.server.modules.identity.IdentityService
import mx.nexuspos.server.modules.permissions.RoleService
import mx.nexuspos.server.modules.permissions.requireDesktopAccess
import mx.nexuspos.server.platform.ForbiddenException

internal fun ApplicationCall.desktopToken(): String? =
    request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)

internal fun Route.storeSetupRoutes(setup: StoreSetupService, identity: IdentityService, roles: RoleService) {
    route("/setup") {
        get("/status") { call.respond(setup.status()) }
        post("/complete") {
            call.requireSetupTransport()
            call.respond(HttpStatusCode.Created, setup.complete(call.receive<StoreSetupRequest>()))
        }
        post("/adopt-root") {
            call.requireSetupTransport()
            setup.adopt(call.receive<AdoptRootRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/recover-root") {
            call.requireSetupTransport()
            setup.recover(call.receive<RecoverRootRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/system/store-profile") {
        identity.authenticate(call.desktopToken())
        call.respond(setup.profile())
    }
    put("/system/store-profile") {
        call.requireDesktopAccess(identity, roles).require("settings.profile")
        call.respond(setup.updateProfile(call.receive<StoreProfile>()))
    }
    delete("/auth/session") {
        identity.logout(call.desktopToken())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun ApplicationCall.requireSetupTransport() {
    val connection = request.local
    if (connection.scheme != "https" && connection.remoteAddress !in setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")) {
        throw ForbiddenException("Configura la tienda desde el servidor local o con una dirección HTTPS de confianza.")
    }
}

package mx.nexuspos.server.modules.mobile

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mx.nexuspos.contracts.mobile.*
import mx.nexuspos.server.platform.UnauthorizedException
import mx.nexuspos.server.platform.ValidationException

internal fun Route.mobileRoutes(access: MobileAccess, queries: MobileQueries) {
    route("/mobile") {
        post("/pairing/claim") {
            call.respond(HttpStatusCode.Created, access.claim(call.receive<MobilePairRequest>()))
        }
        post("/session") {
            call.respond(access.login(call.mobileBearer(), call.receive<MobileSessionRequest>().pin))
        }
        delete("/session") {
            access.logout(call.mobileBearer())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/overview") {
            access.authorize(call.mobileBearer())
            call.respond(queries.overview())
        }
        get("/products") {
            access.authorize(call.mobileBearer())
            call.respond(queries.products(call.request.queryParameters["query"], call.request.queryParameters["barcode"]))
        }
        get("/shifts") {
            access.authorize(call.mobileBearer())
            call.respond(queries.shifts(call.request.queryParameters["date"], call.request.queryParameters["registerId"]))
        }
        get("/shifts/{id}/sales") {
            access.authorize(call.mobileBearer())
            call.respond(queries.shiftSales(
                call.parameters["id"] ?: throw ValidationException("Falta el turno."),
                call.positiveFolio("folio"), call.positiveFolio("before")
            ))
        }
    }
    route("/mobile-admin") {
        post("/pairing") { call.respond(HttpStatusCode.Created, access.issue(call.receive<MobileAdminRequest>().pin)) }
        post("/devices") { call.respond(access.list(call.receive<MobileAdminRequest>().pin)) }
        post("/devices/{id}/revoke") {
            access.revoke(call.receive<MobileAdminRequest>().pin, call.parameters["id"] ?: throw ValidationException("Falta el dispositivo."))
            call.respond(true)
        }
    }
}

private fun ApplicationCall.mobileBearer(): String {
    val token = request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }
        ?.substringAfter(' ')?.takeIf { it.length == 73 }
    return token ?: throw UnauthorizedException("Se requiere una credencial móvil.")
}

private fun ApplicationCall.positiveFolio(name: String): Long? = request.queryParameters[name]?.let {
    it.toLongOrNull()?.takeIf { folio -> folio > 0 } ?: throw ValidationException("Folio inválido.")
}

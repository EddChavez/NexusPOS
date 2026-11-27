package mx.nexuspos.client.core

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mx.nexuspos.client.features.auth.AuthViewModel
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.identity.LoginResponse
import mx.nexuspos.contracts.reports.SalesAnalysisRequest
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSessionRequestTest {
    @Test
    fun sendsDesktopSessionOnlyToConfiguredOriginAndPreservesExplicitAuthorization() = runBlocking {
        val configured = authorizationEcho()
        val other = authorizationEcho()
        val baseUrl = "http://127.0.0.1:${configured.address.port}"
        val api = ApiClient(baseUrl)
        try {
            api.sessionToken = "test-session"
            assertEquals("Bearer test-session", api.http.get("$baseUrl/api/v1/roles").bodyAsText())
            assertEquals(
                "Bearer explicit-token",
                api.http.get("$baseUrl/api/v1/roles") { bearerAuth("explicit-token") }.bodyAsText()
            )
            assertEquals(
                "",
                api.http.get("http://127.0.0.1:${other.address.port}/api/v1/roles").bodyAsText()
            )
        } finally {
            api.http.close()
            other.stop(0)
            configured.stop(0)
        }
    }

    @Test
    fun rejectsInvalidDesktopSessionWithoutLockingForAnIncorrectReportPin() = runBlocking {
        val server = rejectionServer()
        val api = ApiClient("http://127.0.0.1:${server.address.port}")
        val auth = AuthViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        var locked = 0
        auth.onTerminalLocked = { locked++ }
        try {
            auth.unlockWithUser(operator("current-session"))

            val report = api.getSalesAnalysis(SalesAnalysisRequest("1234", "2026-11-26", "2026-11-26", "UTC"))
            assertEquals("PIN de encargado incorrecto.", report.exceptionOrNull()?.message)
            assertEquals("current-session", api.sessionToken)
            assertTrue(auth.uiState.value.isAuthenticated)
            assertEquals(0, locked)

            val roles = api.listRoles()
            assertEquals("La sesión terminó. Ingresa tu PIN.", roles.exceptionOrNull()?.message)
            assertEquals("La sesión terminó. Ingresa tu PIN.", auth.uiState.value.errorMessage)
            assertFalse(auth.uiState.value.isAuthenticated)
            assertNull(api.sessionToken)
            assertEquals(1, locked)
        } finally {
            api.http.close()
            server.stop(0)
        }
    }

    @Test
    fun lateSessionRejectionLeavesTheNewSessionActive() = runBlocking {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = rejectionServer {
            arrived.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val api = ApiClient("http://127.0.0.1:${server.address.port}")
        val auth = AuthViewModel(api, CoroutineScope(Dispatchers.Unconfined))
        try {
            auth.unlockWithUser(operator("previous-session"))
            val pending = async { api.listRoles() }
            assertTrue(withContext(Dispatchers.IO) { arrived.await(10, TimeUnit.SECONDS) })

            auth.unlockWithUser(operator("new-session"))
            release.countDown()

            assertTrue(pending.await().isFailure)
            assertTrue(auth.uiState.value.isAuthenticated)
            assertEquals("new-session", api.sessionToken)
            assertNull(auth.uiState.value.errorMessage)
        } finally {
            release.countDown()
            api.http.close()
            server.stop(0)
        }
    }

    private fun operator(token: String) = LoginResponse(token, "operator", "Operador", "ADMIN", emptyList())

    private fun rejectionServer(beforeRolesResponse: () -> Unit = {}): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val error = when (exchange.requestURI.path) {
                    "/api/v1/reports/sales-analysis" -> ApiError("UNAUTHENTICATED", "PIN de encargado incorrecto.")
                    "/api/v1/roles" -> {
                        beforeRolesResponse()
                        ApiError(ApiError.DESKTOP_SESSION_INVALID, "La sesión terminó. Ingresa tu PIN.")
                    }
                    else -> null
                }
                if (error == null) {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                } else {
                    val body = """{"code":"${error.code}","message":"${error.message}"}""".encodeToByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(401, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            start()
        }

    private fun authorizationEcho(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestHeaders.getFirst("Authorization").orEmpty().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
}

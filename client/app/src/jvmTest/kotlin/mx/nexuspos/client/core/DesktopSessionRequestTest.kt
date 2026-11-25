package mx.nexuspos.client.core

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun authorizationEcho(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestHeaders.getFirst("Authorization").orEmpty().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }
}

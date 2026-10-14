package org.ozafry.nexuspos.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            nexusModule()
        }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\": \"UP\""))
    }

    @Test
    fun testCatalogEndpoint() = testApplication {
        application {
            nexusModule()
        }
        val response = client.get("/api/catalog")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Arroz Morelos"))
    }
}

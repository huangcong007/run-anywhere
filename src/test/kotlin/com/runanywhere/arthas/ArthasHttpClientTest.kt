package com.runanywhere.arthas

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArthasHttpClientTest {

    @Test
    fun `detects session not found in response body`() {
        val client = ArthasHttpClient(port = 9999)
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"message":"session not found: abc-123","state":"FAILED"}""",
            isSuccess = false,
            errorMessage = "session not found: abc-123"
        )
        assertTrue(client.isSessionNotFound(response))
    }

    @Test
    fun `does not treat normal response as session not found`() {
        val client = ArthasHttpClient(port = 9999)
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"state":"SCHEDULED","body":{"jobId":1}}""",
            isSuccess = true
        )
        assertFalse(client.isSessionNotFound(response))
    }
}

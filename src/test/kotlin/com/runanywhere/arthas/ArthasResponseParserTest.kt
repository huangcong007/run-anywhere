package com.runanywhere.arthas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArthasResponseParserTest {

    @Test
    fun `parses value fields from json body`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"type":"ognl","value":"hello\nworld"}]}}""",
            isSuccess = true
        )
        assertEquals("hello\nworld", ArthasResponseParser.parse(response))
    }

    @Test
    fun `returns no result for failed response`() {
        val response = ArthasResponse(-1, "", false, "error")
        assertTrue(ArthasResponseParser.parse(response).contains("error"))
    }

    @Test
    fun `returns raw body when no value field`() {
        val body = """{"status":"ok"}"""
        val response = ArthasResponse(200, body, true)
        assertEquals(body, ArthasResponseParser.parse(response))
    }

    @Test
    fun `detects interrupted execution`() {
        val body = """{"body":{"command":"test","timeExpired":true,"timeout":30000},"message":"The job is exceeded time limit, force interrupt","state":"INTERRUPTED"}"""
        val response = ArthasResponse(200, body, false)
        assertTrue(ArthasResponseParser.isInterrupted(response))
        assertFalse(ArthasResponseParser.isExecutionSuccessful(response))
        assertTrue(ArthasResponseParser.parse(response).contains("执行超时"))
    }

    @Test
    fun `detects session not found failure`() {
        val body = """{"message":"session not found: 32995768-d6dc-4a31-bb55-9ea84b34320b","state":"FAILED"}"""
        val response = ArthasResponse(200, body, false, "session not found: 32995768-d6dc-4a31-bb55-9ea84b34320b")
        assertTrue(ArthasResponseParser.isFailed(response))
        assertFalse(ArthasResponseParser.isExecutionSuccessful(response))
        assertTrue(ArthasResponseParser.parse(response).contains("session not found"))
    }

    @Test
    fun `parses integer vmtool result`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"type":"vmtool","value":2}]}}""",
            isSuccess = true
        )
        assertEquals(2, ArthasResponseParser.parseIntValue(response))
        assertEquals(null, ArthasResponseParser.parseIntValue(ArthasResponse(-1, "", false, "x")))
    }

    @Test
    fun `detects async submitted execution`() {
        val body = """{"sessionId":"abc","state":"SCHEDULED","body":{"jobStatus":"READY","jobId":3,"command":"test"}}"""
        val response = ArthasResponse(200, body, true)
        assertTrue(ArthasResponseParser.isAsyncSubmitted(response))
        assertTrue(ArthasResponseParser.isExecutionSuccessful(response, asyncMode = true))
        assertTrue(ArthasResponseParser.parse(response, asyncMode = true).contains("已异步提交执行"))
    }
}

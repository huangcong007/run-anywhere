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
    fun `object value is rendered as clean json`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"type":"vmtool","value":{
                "@type":"com.example.User",
                "@fieldTypes":"id=java.lang.Long,name=java.lang.String",
                "id":1,"name":"ada"}}]}}""",
            isSuccess = true
        )
        // @type / @fieldTypes 是 Arthas 的展示元数据，不应出现在结果里
        assertEquals(
            """
            {
              "id": 1,
              "name": "ada"
            }
            """.trimIndent(),
            ArthasResponseParser.parse(response)
        )
    }

    @Test
    fun `nested object and array are pruned recursively`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"value":{"list":[
                {"@type":"com.example.Item","@fieldTypes":"id=int","id":2}]}}]}}""",
            isSuccess = true
        )
        assertEquals(
            """
            {
              "list": [
                {
                  "id": 2
                }
              ]
            }
            """.trimIndent(),
            ArthasResponseParser.parse(response)
        )
    }

    @Test
    fun `json string value is pretty printed`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"value":"{\"code\":0,\"data\":[1,2]}"}]}}""",
            isSuccess = true
        )
        assertEquals(
            """
            {
              "code": 0,
              "data": [
                1,
                2
              ]
            }
            """.trimIndent(),
            ArthasResponseParser.parse(response)
        )
    }

    @Test
    fun `plain string value stays untouched`() {
        val response = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"value":"hello"}]}}""",
            isSuccess = true
        )
        assertEquals("hello", ArthasResponseParser.parse(response))
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
        assertFalse(ArthasResponseParser.hasExecutionOutput(response))
    }

    @Test
    fun `reads string items from a vmtool list`() {
        val jsonArray = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"value":["User@1","User@2"]}]}}""",
            isSuccess = true
        )
        assertEquals(listOf("User@1", "User@2"), ArthasResponseParser.parseStringItems(jsonArray))

        val text = ArthasResponse(
            statusCode = 200,
            body = """{"body":{"results":[{"value":"[User@1, User@2]"}]}}""",
            isSuccess = true
        )
        assertEquals(listOf("User@1", "User@2"), ArthasResponseParser.parseStringItems(text))
    }

    @Test
    fun `unknown pull action keeps the submitted response path`() {
        val response = ArthasResponse(400, """{"message":"unknown action: pull_results"}""", false, "unknown action: pull_results")
        assertTrue(ArthasResponseParser.isPullUnsupported(response))
        assertFalse(ArthasResponseParser.isPullUnsupported(ArthasResponse(200, """{"state":"SUCCEEDED","body":{"results":[]}}""", true)))
    }
}

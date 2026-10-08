package com.runanywhere.arthas

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

data class ArthasResponse(
    val statusCode: Int,
    val body: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Arthas HTTP API 客户端。
 *
 * 支持同步 [exec] 与异步 [asyncExec]（fire-and-forget，适合断点调试场景）。
 */
class ArthasHttpClient(
    private val host: String = "127.0.0.1",
    private val port: Int = ArthasManager.DEFAULT_HTTP_PORT,
    private val connectTimeoutMs: Long = 5000,
    private val requestTimeoutMs: Long = 30000
) {
    companion object {
        private val LOG = logger<ArthasHttpClient>()
        private val GSON = Gson()
        private const val ASYNC_SUBMIT_TIMEOUT_MS = 15_000L
        private const val MAX_SESSION_RETRY = 1
    }

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build()
    }

    private val baseUrl: String get() = "http://$host:$port/api"

    @Volatile
    private var sessionId: String? = null

    fun isAvailable(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofMillis(connectTimeoutMs))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }

    fun exec(command: String, execTimeout: Long = requestTimeoutMs): ArthasResponse {
        return post(
            payload = mapOf(
                "action" to "exec",
                "command" to command,
                "execTimeout" to execTimeout
            ),
            timeoutMs = requestTimeoutMs + execTimeout
        )
    }

    /**
     * 异步提交命令，立即返回调度结果（不等待方法执行完成）。
     * 每次提交前刷新 HTTP Session，若遇到 session 失效则自动重建并重试。
     */
    fun asyncExec(command: String): ArthasResponse {
        repeat(MAX_SESSION_RETRY + 1) { attempt ->
            val sid = initFreshSession()
                ?: return ArthasResponse(-1, "", false, "Failed to init Arthas HTTP session")

            val response = post(
                payload = mapOf(
                    "action" to "async_exec",
                    "command" to command,
                    "sessionId" to sid
                ),
                timeoutMs = ASYNC_SUBMIT_TIMEOUT_MS
            )

            if (!isSessionNotFound(response) || attempt == MAX_SESSION_RETRY) {
                return response
            }
            LOG.warn("Arthas session expired (attempt ${attempt + 1}), re-initializing session")
            clearSession()
        }
        return ArthasResponse(-1, "", false, "Failed to submit async command after session retry")
    }

    fun stop(): ArthasResponse {
        val response = exec("stop")
        clearSession()
        return response
    }

    fun clearSession() {
        sessionId = null
    }

    /**
     * 每次异步调用前创建新 Session，避免复用已在 Arthas 服务端过期的 sessionId。
     */
    private fun initFreshSession(): String? {
        clearSession()
        val response = post(mapOf("action" to "init_session"), timeoutMs = connectTimeoutMs)
        if (!response.isSuccess) {
            LOG.warn("Failed to init Arthas session: ${response.body}")
            return null
        }
        val sid = parseSessionId(response.body)
        if (sid == null) {
            LOG.warn("Arthas init_session response missing sessionId: ${response.body}")
            return null
        }
        sessionId = sid
        LOG.info("Arthas HTTP session initialized: $sid")
        return sid
    }

    internal fun isSessionNotFound(response: ArthasResponse): Boolean {
        if (response.body.isEmpty()) return false
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return false
            val message = root.get("message")?.asString ?: return false
            message.contains("session not found", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSessionId(body: String): String? {
        return try {
            GSON.fromJson(body, JsonObject::class.java)?.get("sessionId")?.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun post(payload: Map<String, Any>, timeoutMs: Long): ArthasResponse {
        val action = payload["action"]
        return try {
            val requestBody = GSON.toJson(payload)
            LOG.info("Arthas $action: POST $baseUrl")
            LOG.debug("Arthas $action body: $requestBody")

            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            val body = response.body()
            val httpOk = response.statusCode() == 200
            val arthasOk = httpOk && isArthasStateSuccessful(body)
            val errorMessage = if (arthasOk) null else extractErrorMessage(body, response.statusCode())

            LOG.info("Arthas $action response: status=${response.statusCode()}, success=$arthasOk, bodyLength=${body.length}")
            LOG.debug("Arthas $action response body: ${body.take(2000)}")

            ArthasResponse(
                statusCode = response.statusCode(),
                body = body,
                isSuccess = arthasOk,
                errorMessage = errorMessage
            )
        } catch (e: Exception) {
            LOG.warn("Failed Arthas $action request", e)
            ArthasResponse(
                statusCode = -1,
                body = "",
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Arthas HTTP API 在业务失败时仍可能返回 HTTP 200，需解析 body.state 判断。
     */
    private fun isArthasStateSuccessful(body: String): Boolean {
        if (body.isEmpty()) return true
        return try {
            val root = GSON.fromJson(body, JsonObject::class.java) ?: return true
            when (root.get("state")?.asString) {
                "FAILED", "INTERRUPTED" -> false
                else -> true
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun extractErrorMessage(body: String, httpStatus: Int): String {
        return try {
            val root = GSON.fromJson(body, JsonObject::class.java)
            root?.get("message")?.asString ?: "HTTP $httpStatus"
        } catch (_: Exception) {
            "HTTP $httpStatus"
        }
    }
}

package com.runanywhere.arthas

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.runanywhere.i18n.I18n

/**
 * 解析 Arthas HTTP API 返回的 JSON 响应体。
 */
object ArthasResponseParser {

    private val GSON = Gson()

    /** 结果统一以缩进 JSON 展示；关闭 HTML 转义，避免 `<`、`=`、`&` 被写成 \u003c 之类。 */
    private val PRETTY_GSON: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /** Arthas 在展开对象时插入的类型元数据，展示时去掉。 */
    private val ARTHAS_META_KEYS = setOf("@type", "@fieldTypes")

    fun isAsyncSubmitted(response: ArthasResponse): Boolean {
        if (!response.isSuccess || response.body.isEmpty()) return false
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return false
            root.get("state")?.asString == "SCHEDULED"
        } catch (_: Exception) {
            false
        }
    }

    fun isInterrupted(response: ArthasResponse): Boolean {
        if (response.body.isEmpty()) return false
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return false
            if (root.get("state")?.asString == "INTERRUPTED") return true
            root.getAsJsonObject("body")?.get("timeExpired")?.asBoolean == true
        } catch (_: Exception) {
            false
        }
    }

    fun isFailed(response: ArthasResponse): Boolean {
        if (response.body.isEmpty()) return !response.isSuccess
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return !response.isSuccess
            root.get("state")?.asString == "FAILED"
        } catch (_: Exception) {
            !response.isSuccess
        }
    }

    fun isExecutionSuccessful(response: ArthasResponse, asyncMode: Boolean = false): Boolean {
        if (!response.isSuccess) return false
        if (isInterrupted(response) || isFailed(response)) return false
        if (asyncMode && isAsyncSubmitted(response)) return true
        return true
    }

    fun hasExecutionOutput(response: ArthasResponse): Boolean {
        if (response.body.isEmpty()) return false
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return false
            val results = root.getAsJsonObject("body")?.getAsJsonArray("results") ?: return false
            results.size() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * pull_results 不被当前 Arthas 识别，或 consumer 已经不存在。此时保留提交结果。
     */
    fun isPullUnsupported(response: ArthasResponse): Boolean {
        if (response.isSuccess) return false
        val msg = (response.errorMessage ?: response.body).lowercase()
        return msg.contains("unknown action") ||
            msg.contains("not support") ||
            msg.contains("no such action") ||
            msg.contains("pull_results") ||
            msg.contains("consumer")
    }

    fun parseStringItems(response: ArthasResponse): List<String> {
        if (!response.isSuccess || response.body.isEmpty()) return emptyList()
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return emptyList()
            val results = root.getAsJsonObject("body")?.getAsJsonArray("results") ?: return emptyList()
            if (results.size() == 0) return emptyList()
            val value = results[0].asJsonObject.get("value") ?: return emptyList()
            when {
                value.isJsonArray -> value.asJsonArray.map { element ->
                    if (element.isJsonPrimitive) element.asString else element.toString()
                }
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> splitBracketList(value.asString)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun splitBracketList(text: String): List<String> {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun parseIntValue(response: ArthasResponse): Int? {
        if (!response.isSuccess || response.body.isEmpty()) return null
        // 直接读结构化 value，不经过展示用的 parse()：展示格式将来变化不应影响实例计数。
        val direct = runCatching {
            val root = GSON.fromJson(response.body, JsonObject::class.java)
            val results = root?.getAsJsonObject("body")?.getAsJsonArray("results")
            results?.firstOrNull()?.asJsonObject?.get("value")?.asInt
        }.getOrNull()
        return direct ?: parse(response).trim().toIntOrNull()
    }

    fun parse(response: ArthasResponse, asyncMode: Boolean = false): String {
        if (response.body.isNotEmpty()) {
            if (asyncMode && isAsyncSubmitted(response)) {
                return formatAsyncSubmittedMessage(response)
            }
            if (isInterrupted(response)) {
                return formatInterruptedMessage(response)
            }
            if (isFailed(response)) {
                return formatFailedMessage(response)
            }
        }
        if (!response.isSuccess || response.body.isEmpty()) {
            if (response.errorMessage != null) {
                return formatFailedMessage(response)
            }
            return I18n.PARSED_NO_RESULT()
        }
        if (asyncMode && isAsyncSubmitted(response)) {
            return formatAsyncSubmittedMessage(response)
        }
        if (isInterrupted(response)) {
            return formatInterruptedMessage(response)
        }
        if (isFailed(response)) {
            return formatFailedMessage(response)
        }
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return response.body
            val body = root.getAsJsonObject("body") ?: return response.body
            val results = body.getAsJsonArray("results") ?: return response.body
            val values = results.mapNotNull { element ->
                element.asJsonObject.get("value")?.let { formatAsJson(it) }
            }
            if (values.isNotEmpty()) values.joinToString("\n") else response.body
        } catch (_: Exception) {
            response.body
        }
    }

    /**
     * 把 Arthas 的 value 统一渲染成 JSON 文本。
     *
     * - 对象 / 数组：去掉 `@type`、`@fieldTypes` 后缩进输出，得到干净的 JSON；
     * - 字符串：本身是 JSON 的（常见的 "接口返回 String" 场景）按 JSON 缩进，否则原样返回；
     * - 数字 / 布尔：原样输出，它们本身就是合法 JSON 标量。
     *
     * 不把普通字符串加上引号：那会让"方法返回 hello"显示成 `"hello"`，
     * 徒增噪音，而它并不携带结构信息。
     */
    private fun formatAsJson(value: JsonElement): String = when {
        value.isJsonObject || value.isJsonArray -> PRETTY_GSON.toJson(stripArthasMeta(value))
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
            val text = value.asString
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                runCatching { PRETTY_GSON.toJson(stripArthasMeta(JsonParser.parseString(trimmed))) }
                    .getOrDefault(text)
            } else {
                text
            }
        }
        else -> value.toString()
    }

    /** 递归删除 Arthas 注入的类型元数据，保留真实字段。 */
    private fun stripArthasMeta(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().apply {
            for ((key, child) in element.asJsonObject.entrySet()) {
                if (key in ARTHAS_META_KEYS) continue
                add(key, stripArthasMeta(child))
            }
        }
        element.isJsonArray -> JsonArray().apply {
            element.asJsonArray.forEach { add(stripArthasMeta(it)) }
        }
        else -> element
    }

    private fun formatAsyncSubmittedMessage(response: ArthasResponse): String {
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return response.body
            val body = root.getAsJsonObject("body")
            val jobId = body?.get("jobId")?.asInt
            val jobStatus = body?.get("jobStatus")?.asString
            val command = body?.get("command")?.asString
            val sessionId = root.get("sessionId")?.asString
            buildString {
                appendLine(I18n.PARSED_ASYNC_SUBMITTED())
                if (jobId != null) appendLine(I18n.PARSED_JOB_ID(jobId))
                if (jobStatus != null) appendLine(I18n.PARSED_JOB_STATUS(jobStatus))
                if (sessionId != null) appendLine(I18n.PARSED_SESSION(sessionId))
                if (command != null) {
                    appendLine()
                    appendLine(I18n.PARSED_COMMAND(command))
                }
                appendLine()
                appendLine(I18n.PARSED_NOTE_HEADER())
                appendLine(I18n.PARSED_ASYNC_NOTE1())
                appendLine(I18n.PARSED_ASYNC_NOTE2())
            }
        } catch (_: Exception) {
            response.body
        }
    }

    private fun formatFailedMessage(response: ArthasResponse): String {
        return try {
            val root = if (response.body.isNotEmpty()) {
                GSON.fromJson(response.body, JsonObject::class.java)
            } else null
            val message = root?.get("message")?.asString
                ?: response.errorMessage
                ?: I18n.PARSED_FAILED()
            buildString {
                appendLine(I18n.PARSED_FAILED())
                appendLine(I18n.PARSED_REASON(message))
                if (message.contains("session not found", ignoreCase = true)) {
                    appendLine()
                    appendLine(I18n.PARSED_SESSION_EXPIRED_NOTE1())
                    appendLine(I18n.PARSED_SESSION_EXPIRED_NOTE2())
                }
            }
        } catch (_: Exception) {
            response.errorMessage ?: response.body.ifEmpty { I18n.PARSED_FAILED() }
        }
    }

    private fun formatInterruptedMessage(response: ArthasResponse): String {
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return response.body
            val message = root.get("message")?.asString ?: I18n.PARSED_TIMEOUT_DEFAULT_REASON()
            val body = root.getAsJsonObject("body")
            val timeoutMs = body?.get("timeout")?.asLong
            val command = body?.get("command")?.asString
            buildString {
                appendLine(I18n.PARSED_TIMEOUT())
                if (timeoutMs != null) {
                    appendLine(I18n.PARSED_TIMEOUT_LIMIT(timeoutMs, timeoutMs / 1000))
                }
                appendLine(I18n.PARSED_REASON(message))
                if (command != null) {
                    appendLine()
                    appendLine(I18n.PARSED_COMMAND(command))
                }
                appendLine()
                appendLine(I18n.PARSED_SUGGESTION_HEADER())
                appendLine(I18n.PARSED_TIMEOUT_SUGGESTION1())
                appendLine(I18n.PARSED_TIMEOUT_SUGGESTION2())
                appendLine(I18n.PARSED_TIMEOUT_SUGGESTION3())
            }
        } catch (_: Exception) {
            response.body
        }
    }
}

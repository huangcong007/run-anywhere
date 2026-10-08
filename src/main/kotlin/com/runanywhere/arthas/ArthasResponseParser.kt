package com.runanywhere.arthas

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 解析 Arthas HTTP API 返回的 JSON 响应体。
 */
object ArthasResponseParser {

    private val GSON = Gson()

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
        if (!response.isSuccess) return null
        return parse(response).trim().toIntOrNull()
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
            return "(no result)"
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
            val values = buildList {
                for (element in results) {
                    val obj = element.asJsonObject
                    val valueElement = obj.get("value") ?: continue
                    if (valueElement.isJsonPrimitive && valueElement.asJsonPrimitive.isString) {
                        add(valueElement.asString)
                    } else {
                        add(valueElement.toString())
                    }
                }
            }
            if (values.isNotEmpty()) values.joinToString("\n") else response.body
        } catch (_: Exception) {
            response.body
        }
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
                appendLine("已异步提交执行")
                if (jobId != null) appendLine("Job ID：$jobId")
                if (jobStatus != null) appendLine("任务状态：$jobStatus")
                if (sessionId != null) appendLine("Session：$sessionId")
                if (command != null) {
                    appendLine()
                    appendLine("命令：$command")
                }
                appendLine()
                appendLine("说明：")
                appendLine("命令已在目标 JVM 中调度。")
                appendLine("若在等待上限内没有拿到返回值，多半是目标线程停在断点。继续执行后请重新调用。")
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
                ?: "执行失败"
            buildString {
                appendLine("执行失败")
                appendLine("原因：$message")
                if (message.contains("session not found", ignoreCase = true)) {
                    appendLine()
                    appendLine("说明：Arthas HTTP Session 已过期或被服务端清理。")
                    appendLine("插件会自动重建 Session 并重试；若仍失败，请重启目标应用后重试。")
                }
            }
        } catch (_: Exception) {
            response.errorMessage ?: response.body.ifEmpty { "执行失败" }
        }
    }

    private fun formatInterruptedMessage(response: ArthasResponse): String {
        return try {
            val root = GSON.fromJson(response.body, JsonObject::class.java) ?: return response.body
            val message = root.get("message")?.asString ?: "执行超时，已被 Arthas 强制中断"
            val body = root.getAsJsonObject("body")
            val timeoutMs = body?.get("timeout")?.asLong
            val command = body?.get("command")?.asString
            buildString {
                appendLine("执行超时")
                if (timeoutMs != null) {
                    appendLine("超时限制：${timeoutMs}ms（${timeoutMs / 1000} 秒）")
                }
                appendLine("原因：$message")
                if (command != null) {
                    appendLine()
                    appendLine("命令：$command")
                }
                appendLine()
                appendLine("建议：")
                appendLine("1. 勾选「异步执行」：命令在目标 JVM 后台运行，断点不会触发插件 HTTP 超时")
                appendLine("2. 在 Settings → Other Settings → Run Anywhere 中增大「同步执行超时」")
                appendLine("3. 确认目标方法不会在目标 JVM 中死锁或无限阻塞")
            }
        } catch (_: Exception) {
            response.body
        }
    }
}

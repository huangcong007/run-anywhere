package com.runanywhere.arthas

/**
 * 单个 JVM 的 Arthas 会话。
 * 每个 PID 独占一组端口和 HTTP 客户端，支持多进程并行 attach。
 */
data class ArthasSession(
    val pid: Long,
    val httpPort: Int,
    val telnetPort: Int,
    val bootProcess: Process,
    val httpClient: ArthasHttpClient
)

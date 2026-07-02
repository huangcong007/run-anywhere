package com.runanywhere.process

/**
 * 从 Run Configuration 提取的进程匹配信息。
 */
data class RunTargetInfo(
    val moduleName: String?,
    val mainClassName: String?,
    val configurationName: String
)

package com.runanywhere.settings

import com.intellij.ide.util.PropertiesComponent

/**
 * 插件持久化设置。
 */
object RunAnywhereSettings {

    private const val SECURITY_CONFIRMED_KEY = "run-anywhere.security.confirmed"
    private const val EXEC_TIMEOUT_SECONDS_KEY = "run-anywhere.exec-timeout-seconds"
    private const val ASYNC_EXECUTION_KEY = "run-anywhere.async-execution"
    private const val WARMUP_ATTACH_KEY = "run-anywhere.warmup-attach"
    private const val JSON_LIBRARY_KEY = "run-anywhere.json-library"
    private const val RESULT_EXPAND_LEVEL_KEY = "run-anywhere.result-expand-level"

    const val DEFAULT_EXEC_TIMEOUT_SECONDS = 300
    const val MIN_EXEC_TIMEOUT_SECONDS = 30
    const val MAX_EXEC_TIMEOUT_SECONDS = 3600
    const val DEFAULT_RESULT_EXPAND_LEVEL = 3
    const val MIN_RESULT_EXPAND_LEVEL = 0
    const val MAX_RESULT_EXPAND_LEVEL = 10

    fun isSecurityConfirmed(): Boolean =
        PropertiesComponent.getInstance().getBoolean(SECURITY_CONFIRMED_KEY, false)

    fun setSecurityConfirmed(confirmed: Boolean) {
        PropertiesComponent.getInstance().setValue(SECURITY_CONFIRMED_KEY, confirmed, false)
    }

    fun getExecTimeoutSeconds(): Int {
        val value = PropertiesComponent.getInstance().getInt(EXEC_TIMEOUT_SECONDS_KEY, DEFAULT_EXEC_TIMEOUT_SECONDS)
        return value.coerceIn(MIN_EXEC_TIMEOUT_SECONDS, MAX_EXEC_TIMEOUT_SECONDS)
    }

    fun setExecTimeoutSeconds(seconds: Int) {
        PropertiesComponent.getInstance().setValue(
            EXEC_TIMEOUT_SECONDS_KEY,
            seconds.coerceIn(MIN_EXEC_TIMEOUT_SECONDS, MAX_EXEC_TIMEOUT_SECONDS),
            DEFAULT_EXEC_TIMEOUT_SECONDS
        )
    }

    fun getExecTimeoutMs(): Long = getExecTimeoutSeconds() * 1000L

    fun isAsyncExecution(): Boolean =
        PropertiesComponent.getInstance().getBoolean(ASYNC_EXECUTION_KEY, true)

    fun setAsyncExecution(async: Boolean) {
        PropertiesComponent.getInstance().setValue(ASYNC_EXECUTION_KEY, async, true)
    }

    /** 进程启动后是否提前 Attach。默认关闭，避免未确认就向目标 JVM 注入 Arthas。 */
    fun isWarmupAttach(): Boolean =
        PropertiesComponent.getInstance().getBoolean(WARMUP_ATTACH_KEY, false)

    fun setWarmupAttach(enabled: Boolean) {
        PropertiesComponent.getInstance().setValue(WARMUP_ATTACH_KEY, enabled, false)
    }

    fun getJsonLibrary(): JsonLibrary {
        val name = PropertiesComponent.getInstance().getValue(JSON_LIBRARY_KEY)
        return JsonLibrary.entries.find { it.name == name } ?: JsonLibrary.AUTO
    }

    fun setJsonLibrary(library: JsonLibrary) {
        PropertiesComponent.getInstance().setValue(JSON_LIBRARY_KEY, library.name, JsonLibrary.AUTO.name)
    }

    fun getResultExpandLevel(): Int {
        val value = PropertiesComponent.getInstance().getInt(RESULT_EXPAND_LEVEL_KEY, DEFAULT_RESULT_EXPAND_LEVEL)
        return value.coerceIn(MIN_RESULT_EXPAND_LEVEL, MAX_RESULT_EXPAND_LEVEL)
    }

    fun setResultExpandLevel(level: Int) {
        PropertiesComponent.getInstance().setValue(
            RESULT_EXPAND_LEVEL_KEY,
            level.coerceIn(MIN_RESULT_EXPAND_LEVEL, MAX_RESULT_EXPAND_LEVEL),
            DEFAULT_RESULT_EXPAND_LEVEL
        )
    }
}

package com.runanywhere.settings

import com.intellij.ide.util.PropertiesComponent

/**
 * 插件持久化设置。
 */
object RunAnywhereSettings {

    private const val SECURITY_CONFIRMED_KEY = "run-anywhere.security.confirmed"
    private const val EXEC_TIMEOUT_SECONDS_KEY = "run-anywhere.exec-timeout-seconds"
    private const val ASYNC_EXECUTION_KEY = "run-anywhere.async-execution"
    private const val JSON_LIBRARY_KEY = "run-anywhere.json-library"

    const val DEFAULT_EXEC_TIMEOUT_SECONDS = 300
    const val MIN_EXEC_TIMEOUT_SECONDS = 30
    const val MAX_EXEC_TIMEOUT_SECONDS = 3600

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

    fun getJsonLibrary(): JsonLibrary {
        val name = PropertiesComponent.getInstance().getValue(JSON_LIBRARY_KEY)
        return JsonLibrary.entries.find { it.name == name } ?: JsonLibrary.AUTO
    }

    fun setJsonLibrary(library: JsonLibrary) {
        PropertiesComponent.getInstance().setValue(JSON_LIBRARY_KEY, library.name, JsonLibrary.AUTO.name)
    }
}

package com.runanywhere.process

import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * 从运行中的 [RunContentDescriptor] 提取模块、主类等信息，用于进程自动匹配。
 *
 * 进程启动时把 [ExecutionEnvironment] 写到我们自己的 [Key] 上。
 * IDE 重启后该 Key 会丢失，此时按 [RunContentDescriptor.runConfigurationName]
 * 反查 [RunManager] 中的同名配置。
 */
object RunTargetInfoExtractor {

    private val TARGET_INFO_KEY: Key<RunTargetInfo> = Key.create("run-anywhere.runTargetInfo")

    fun remember(handler: ProcessHandler, env: ExecutionEnvironment) {
        handler.putUserData(TARGET_INFO_KEY, fromEnvironment(env))
    }

    fun extract(project: Project, descriptor: RunContentDescriptor): RunTargetInfo {
        val handler = descriptor.processHandler
        val cached = handler?.getUserData(TARGET_INFO_KEY)
        if (cached != null) {
            return cached.withConfigurationName(descriptor.displayName)
        }

        val fromDescriptor = fromDescriptor(project, descriptor)
        if (fromDescriptor != null) {
            handler?.putUserData(TARGET_INFO_KEY, fromDescriptor)
            return fromDescriptor
        }

        return RunTargetInfo(
            moduleName = null,
            mainClassName = null,
            configurationName = descriptor.displayName.orEmpty()
        )
    }

    /**
     * 通过 Run Content 记录的执行配置名反查 [RunManager]（2026.1+ 提供该属性）。
     */
    private fun fromDescriptor(project: Project, descriptor: RunContentDescriptor): RunTargetInfo? {
        val configurationName = descriptor.runConfigurationName?.takeIf { it.isNotBlank() } ?: return null
        val settings = RunManager.getInstance(project)
            .allSettings
            .firstOrNull { it.name == configurationName }
            ?: return null
        return fromConfiguration(settings.configuration, configurationName)
    }

    internal fun fromEnvironment(env: ExecutionEnvironment): RunTargetInfo =
        fromConfiguration(env.runProfile as? RunConfiguration, env.runProfile.name)

    private fun fromConfiguration(config: RunConfiguration?, configurationName: String): RunTargetInfo {
        val moduleName = (config as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.name
        return RunTargetInfo(
            moduleName = moduleName,
            mainClassName = extractMainClassName(config),
            configurationName = configurationName
        )
    }

    private fun RunTargetInfo.withConfigurationName(displayName: String?): RunTargetInfo {
        if (configurationName.isNotEmpty() || displayName.isNullOrEmpty()) return this
        return copy(configurationName = displayName)
    }

    /**
     * 提取启动主类：优先 ApplicationConfiguration，再反射 getMainClassName（兼容 Spring Boot 等配置类型）。
     */
    internal fun extractMainClassName(config: RunConfiguration?): String? {
        if (config == null) return null
        if (config is ApplicationConfiguration) {
            return config.mainClassName?.takeIf { it.isNotBlank() }
        }
        return invokeMainClassName(config)
    }

    private fun invokeMainClassName(config: RunConfiguration): String? {
        return try {
            val method = config.javaClass.methods.firstOrNull {
                it.name == "getMainClassName" && it.parameterCount == 0
            }
            (method?.invoke(config) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}

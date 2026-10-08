package com.runanywhere.process

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
 * 平台内部的同名 Key 是另一个实例，事后按名字新建读不到。
 */
object RunTargetInfoExtractor {

    private val TARGET_INFO_KEY: Key<RunTargetInfo> = Key.create("run-anywhere.runTargetInfo")

    @Suppress("UNCHECKED_CAST")
    private val platformEnvironmentKey: Key<ExecutionEnvironment>? by lazy {
        try {
            val clazz = Class.forName("com.intellij.execution.impl.ExecutionManagerImpl")
            val field = clazz.declaredFields.firstOrNull { candidate ->
                java.lang.reflect.Modifier.isStatic(candidate.modifiers) &&
                    candidate.name.contains("ENVIRONMENT")
            } ?: return@lazy null
            field.isAccessible = true
            field.get(null) as? Key<ExecutionEnvironment>
        } catch (_: Throwable) {
            null
        }
    }

    fun remember(handler: ProcessHandler, env: ExecutionEnvironment) {
        handler.putUserData(TARGET_INFO_KEY, fromEnvironment(env))
    }

    fun extract(@Suppress("UNUSED_PARAMETER") project: Project, descriptor: RunContentDescriptor): RunTargetInfo {
        val handler = descriptor.processHandler
        val cached = handler?.getUserData(TARGET_INFO_KEY)
        if (cached != null) {
            return cached.withConfigurationName(descriptor.displayName)
        }
        if (handler != null) {
            val env = readPlatformEnvironment(handler)
            if (env != null) {
                val info = fromEnvironment(env).withConfigurationName(descriptor.displayName)
                handler.putUserData(TARGET_INFO_KEY, info)
                return info
            }
        }
        return RunTargetInfo(
            moduleName = null,
            mainClassName = null,
            configurationName = descriptor.displayName.orEmpty()
        )
    }

    internal fun fromEnvironment(env: ExecutionEnvironment): RunTargetInfo {
        val config = env.runProfile as? RunConfiguration
        val moduleName = (config as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.name
        val mainClassName = extractMainClassName(config)
        return RunTargetInfo(
            moduleName = moduleName,
            mainClassName = mainClassName,
            configurationName = config?.name.orEmpty()
        )
    }

    private fun RunTargetInfo.withConfigurationName(displayName: String?): RunTargetInfo {
        if (configurationName.isNotEmpty() || displayName.isNullOrEmpty()) return this
        return copy(configurationName = displayName)
    }

    private fun readPlatformEnvironment(handler: ProcessHandler): ExecutionEnvironment? {
        val key = platformEnvironmentKey ?: return null
        return try {
            handler.getUserData(key)
        } catch (_: Exception) {
            null
        }
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

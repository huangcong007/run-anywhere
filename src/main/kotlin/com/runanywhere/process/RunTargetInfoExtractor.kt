package com.runanywhere.process

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * 从运行中的 [RunContentDescriptor] 提取模块、主类等信息，用于进程自动匹配。
 */
object RunTargetInfoExtractor {

    private val EXECUTION_ENVIRONMENT_KEY: Key<ExecutionEnvironment> = Key.create("EXECUTION_ENVIRONMENT")

    fun extract(project: Project, descriptor: RunContentDescriptor): RunTargetInfo {
        val handler = descriptor.processHandler
        val env = handler?.getUserData(EXECUTION_ENVIRONMENT_KEY)
        val config = env?.runProfile as? RunConfiguration
        val moduleName = (config as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.name
        val mainClassName = extractMainClassName(config)
        return RunTargetInfo(
            moduleName = moduleName,
            mainClassName = mainClassName,
            configurationName = config?.name ?: descriptor.displayName.orEmpty()
        )
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

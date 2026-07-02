package com.runanywhere.process

import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * 运行中的 Java 进程信息。
 * @param pid 进程 PID
 * @param displayName 显示名（Run Configuration 名称）
 * @param descriptor 关联的 RunContentDescriptor
 */
data class RunningProcess(
    val pid: Long,
    val displayName: String,
    val descriptor: RunContentDescriptor
)

/**
 * 检测 IDEA 启动的 Java 进程并获取 PID。
 *
 * 通过 ExecutionManager 获取所有运行中的 Run Content，
 * 过滤出 OSProcessHandler 且进程仍存活的情况，提取 PID。
 */
object RunningProcessDetector {
    private val LOG = logger<RunningProcessDetector>()

    /**
     * 获取当前项目中所有运行中的 Java 进程。
     * @param project 当前项目
     * @return 运行中的进程列表，可能为空
     */
    fun getRunningProcesses(project: Project): List<RunningProcess> {
        val result = mutableListOf<RunningProcess>()
        try {
            val contentManager = ExecutionManager.getInstance(project).getContentManager()
            val descriptors = contentManager.getAllDescriptors()
            LOG.info("Found ${descriptors.size} run content descriptors")
            for (descriptor in descriptors) {
                try {
                    val processHandler = descriptor.processHandler
                    if (processHandler == null) {
                        LOG.info("Descriptor '${descriptor.displayName}' has no processHandler, skip")
                        continue
                    }
                    // 只处理 OSProcessHandler（本地进程）
                    if (processHandler !is OSProcessHandler) {
                        LOG.info("Descriptor '${descriptor.displayName}' processHandler is ${processHandler.javaClass.simpleName}, not OSProcessHandler, skip")
                        continue
                    }
                    // 检查进程是否仍在运行
                    if (processHandler.isProcessTerminated) {
                        LOG.info("Descriptor '${descriptor.displayName}' process already terminated, skip")
                        continue
                    }
                    val pid = extractPid(processHandler)
                    if (pid == null) {
                        LOG.warn("Cannot extract PID from descriptor '${descriptor.displayName}'")
                        continue
                    }
                    val name = descriptor.displayName ?: "PID $pid"
                    LOG.info("Found running process: PID=$pid, name=$name")
                    result.add(RunningProcess(pid, name, descriptor))
                } catch (e: Exception) {
                    LOG.warn("Error processing descriptor", e)
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to collect running processes", e)
        }
        return result
    }

    /**
     * 从 OSProcessHandler 提取 PID。
     *
     * 直接调用 Process.pid()（Java 9+ public 方法），不使用反射。
     * 若失败则回退到反射（需 setAccessible(true) 绕过模块化限制）。
     *
     * @return PID，若无法提取返回 null
     */
    private fun extractPid(handler: OSProcessHandler): Long? {
        // 方案1：直接调用 Process.pid()（Java 9+ public 方法，无需反射）
        try {
            val process = handler.process
            // Process.pid() 是 Java 9+ 的 public 方法，直接调用
            val pid = process.pid()
            LOG.info("Extracted PID $pid via Process.pid()")
            return pid
        } catch (e: Exception) {
            LOG.warn("Failed to get PID via direct Process.pid() call: ${e.message}")
        }

        // 方案2：反射访问 Process.pid() 方法（setAccessible 绕过模块限制）
        try {
            val process = handler.process
            val pidMethod = process.javaClass.getMethod("pid")
            pidMethod.isAccessible = true
            val pid = (pidMethod.invoke(process) as Number).toLong()
            LOG.info("Extracted PID $pid via reflective Process.pid()")
            return pid
        } catch (e: Exception) {
            LOG.warn("Failed to get PID via reflective Process.pid(): ${e.message}")
        }

        // 方案3：反射访问 ProcessImpl.pid 字段（Windows）
        try {
            val process = handler.process
            // 沿继承链查找 pid 字段（可能在父类）
            var clazz: Class<*>? = process.javaClass
            while (clazz != null) {
                try {
                    val pidField = clazz.getDeclaredField("pid")
                    pidField.isAccessible = true
                    val pid = (pidField.get(process) as Number).toLong()
                    LOG.info("Extracted PID $pid via reflection on ${clazz.name}.pid field")
                    return pid
                } catch (nf: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get PID via reflection on pid field: ${e.message}")
        }

        LOG.warn("All PID extraction methods failed for ${handler.javaClass.simpleName}")
        return null
    }

    /**
     * 判断是否有运行中的进程。
     */
    fun hasRunningProcess(project: Project): Boolean = getRunningProcesses(project).isNotEmpty()
}

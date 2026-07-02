package com.runanywhere.service

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.runanywhere.arthas.ArthasManager
import com.runanywhere.concurrent.RunAnywhereExecutors
import com.runanywhere.process.RunningProcessDetector

/**
 * 项目级 Service：管理 Arthas 会话。
 *
 * 职责：
 * - 持有 ArthasManager 实例
 * - 监听 Java 进程启动事件，自动预热 attach（减少首次右键调用的等待时间）
 * - 项目关闭时自动 detach 所有会话（实现 Disposable）
 */
@Service(Service.Level.PROJECT)
class ArthasSessionService(private val project: Project) : Disposable {

    companion object {
        private val LOG = logger<ArthasSessionService>()

        @JvmStatic
        fun getInstance(project: Project): ArthasSessionService =
            project.getService(ArthasSessionService::class.java)
    }

    val arthasManager: ArthasManager = ArthasManager(project)

    private val messageBusConnection: MessageBusConnection = project.messageBus.connect(this)

    init {
        // 订阅执行事件：Java 进程启动后自动预热 attach
        messageBusConnection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(executionId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                onProcessStarted(handler)
            }
        })
        LOG.info("ArthasSessionService initialized, warmup listener registered")
    }

    /**
     * Java 进程启动后的处理：延迟 2 秒提取 PID 并触发预热 attach。
     *
     * 延迟是为了让进程完成初始化（避免 attach 过早失败）。
     * 预热在后台线程执行，不阻塞用户操作。
     */
    private fun onProcessStarted(handler: ProcessHandler) {
        if (handler !is OSProcessHandler) return
        if (handler.isProcessTerminated) return

        // 延迟 2 秒后提取 PID 并预热（进程刚启动时可能不稳定）
        RunAnywhereExecutors.executeWarmup {
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return@executeWarmup
            }
            if (handler.isProcessTerminated) {
                LOG.info("Process terminated before warmup, skip")
                return@executeWarmup
            }
            // 从当前所有运行进程中找到匹配的（通过 processHandler 引用匹配）
            val processes = RunningProcessDetector.getRunningProcesses(project)
            val target = processes.firstOrNull { it.descriptor.processHandler === handler }
            if (target == null) {
                LOG.info("Process not found in running processes, skip warmup")
                return@executeWarmup
            }
            LOG.info("Auto warmup: triggering Arthas attach for PID ${target.pid} (${target.displayName})")
            arthasManager.warmup(target.pid)
        }
    }

    /**
     * 项目关闭时自动清理 Arthas 会话。
     */
    override fun dispose() {
        LOG.info("Disposing ArthasSessionService, detaching all sessions")
        arthasManager.detachAll()
    }
}


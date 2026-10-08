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
import com.runanywhere.process.RunTargetInfoExtractor
import com.runanywhere.process.RunningProcessDetector
import com.runanywhere.settings.RunAnywhereSettings

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
                onProcessStarted(env, handler)
            }

            override fun processTerminated(
                executorId: String,
                env: ExecutionEnvironment,
                handler: ProcessHandler,
                exitCode: Int
            ) {
                onProcessTerminated(handler)
            }
        })
        LOG.info("ArthasSessionService initialized, warmup listener registered")
    }

    /**
     * 记下运行配置，供进程匹配使用。预热默认关闭，打开后延迟 2 秒再 Attach。
     */
    private fun onProcessStarted(env: ExecutionEnvironment, handler: ProcessHandler) {
        RunTargetInfoExtractor.remember(handler, env)
        if (!RunAnywhereSettings.isWarmupAttach()) return
        if (handler !is OSProcessHandler || handler.isProcessTerminated) return
        val commandLine = try {
            handler.commandLine
        } catch (_: Exception) {
            return
        }
        if (!RunningProcessDetector.isTargetJvmCommand(commandLine)) return

        RunAnywhereExecutors.executeWarmup {
            try {
                Thread.sleep(2000)
            } catch (_: InterruptedException) {
                return@executeWarmup
            }
            if (project.isDisposed || handler.isProcessTerminated) {
                LOG.info("Process terminated before warmup, skip")
                return@executeWarmup
            }
            val pid = try {
                handler.process.pid()
            } catch (_: Exception) {
                return@executeWarmup
            }
            LOG.info("Auto warmup: triggering Arthas attach for PID $pid")
            arthasManager.warmup(pid)
        }
    }

    private fun onProcessTerminated(handler: ProcessHandler) {
        if (handler !is OSProcessHandler) return
        val pid = try {
            handler.process.pid()
        } catch (_: Exception) {
            return
        }
        RunAnywhereExecutors.executeMethod {
            LOG.info("Process $pid terminated, detaching Arthas")
            arthasManager.detach(pid)
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


package com.runanywhere.concurrent

import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Run Anywhere 插件专用线程池。
 */
object RunAnywhereExecutors {

    private val LOG = logger<RunAnywhereExecutors>()
    private val shutdown = AtomicBoolean(false)

    private val threadFactory = object : ThreadFactory {
        private val counter = AtomicInteger(0)
        private val delegate = Executors.defaultThreadFactory()

        override fun newThread(r: Runnable): Thread {
            val thread = delegate.newThread(r)
            thread.name = "runAnywhere-${counter.incrementAndGet()}"
            thread.isDaemon = true
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }

    private val methodExecutor: ExecutorService =
        Executors.newFixedThreadPool(4, threadFactory)

    private val warmupExecutor: ExecutorService =
        Executors.newSingleThreadExecutor(threadFactory)

    fun executeMethod(task: Runnable) {
        if (shutdown.get()) {
            LOG.warn("RunAnywhereExecutors is shut down, ignoring method task")
            return
        }
        methodExecutor.execute {
            try {
                task.run()
            } catch (t: Throwable) {
                LOG.error("Unexpected error in runAnywhere method executor", t)
            }
        }
    }

    fun executeWarmup(task: Runnable) {
        if (shutdown.get()) {
            LOG.warn("RunAnywhereExecutors is shut down, ignoring warmup task")
            return
        }
        warmupExecutor.execute {
            try {
                task.run()
            } catch (t: Throwable) {
                LOG.warn("Error in runAnywhere warmup executor", t)
            }
        }
    }

    /**
     * 关闭所有线程池（IDE 退出时调用）。
     */
    fun shutdown() {
        if (!shutdown.compareAndSet(false, true)) return
        LOG.info("Shutting down RunAnywhereExecutors")
        shutdownExecutor(methodExecutor, "method")
        shutdownExecutor(warmupExecutor, "warmup")
    }

    private fun shutdownExecutor(executor: ExecutorService, name: String) {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("RunAnywhere $name executor did not terminate in time, forcing shutdown")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

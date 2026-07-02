package com.runanywhere.arthas

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.runanywhere.settings.RunAnywhereSettings
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Arthas 生命周期管理器。
 *
 * 每个 PID 维护独立的 [ArthasSession]（独立 HTTP 端口 + HTTP 客户端），
 * 支持同时对多个 JVM 进行 attach 和方法调用。
 */
class ArthasManager(@Suppress("UNUSED_PARAMETER") private val project: Project) {

    companion object {
        private val LOG = logger<ArthasManager>()

        const val ARTHAS_VERSION = "4.0.5"
        const val DEFAULT_HTTP_PORT = 8563
        const val DEFAULT_TELNET_PORT = 3658
        private const val MAX_PORT_OFFSET = 100
        private const val ARTHAS_RESOURCE_PATH = "/arthas/arthas-boot.jar"

        private fun arthasStandardHome(): Path =
            Paths.get(System.getProperty("user.home"), ".arthas", "lib", ARTHAS_VERSION, "arthas")

        private fun arthasStandardJar(): Path = arthasStandardHome().resolve("arthas-boot.jar")

        private fun arthasHomeDir(): Path =
            Paths.get(System.getProperty("user.home"), ".run-anywhere", "arthas")

        private fun arthasJarPath(): Path = arthasHomeDir().resolve("arthas-boot.jar")
    }

    private val sessions = ConcurrentHashMap<Long, ArthasSession>()
    private val attachingPids = ConcurrentHashMap.newKeySet<Long>()

    @Synchronized
    private fun ensureArthasJar(): Path {
        val standardJar = arthasStandardJar()
        if (Files.exists(standardJar) && Files.size(standardJar) > 0) {
            LOG.info("Using Arthas standard install at $standardJar")
            return standardJar
        }

        val jarPath = arthasJarPath()
        if (Files.exists(jarPath) && Files.size(jarPath) > 0) {
            return jarPath
        }

        Files.createDirectories(arthasHomeDir())
        val resourceUrl = javaClass.getResource(ARTHAS_RESOURCE_PATH)
            ?: throw IllegalStateException(
                "Bundled arthas-boot.jar not found at $ARTHAS_RESOURCE_PATH. " +
                    "Run './gradlew downloadArthasBoot' before building."
            )

        try {
            resourceUrl.openStream().use { input ->
                Files.copy(input, jarPath, StandardCopyOption.REPLACE_EXISTING)
            }
            LOG.info("Extracted arthas-boot.jar to $jarPath")
        } catch (e: Exception) {
            if (!Files.exists(jarPath)) {
                downloadArthasJar(jarPath)
            }
        }
        return jarPath
    }

    private fun downloadArthasJar(target: Path) {
        val url = URL(
            "https://repo1.maven.org/maven2/com/taobao/arthas/arthas-boot/" +
                "$ARTHAS_VERSION/arthas-boot-$ARTHAS_VERSION.jar"
        )
        LOG.info("Downloading arthas-boot.jar from $url")
        url.openStream().use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Attach Arthas 到目标 JVM。
     */
    @Synchronized
    fun attach(pid: Long): Boolean {
        val existing = sessions[pid]
        if (existing != null) {
            if (existing.httpClient.isAvailable()) {
                return true
            }
            detach(pid)
        }

        return try {
            val jarPath = ensureArthasJar()
            val httpPort = allocateHttpPort()
            val telnetPort = allocateTelnetPort()
            LOG.info("Attaching Arthas to PID $pid (http=$httpPort, telnet=$telnetPort) using $jarPath")

            val processBuilder = ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-Xms16m",
                "-Xmx64m",
                "-XX:+UseSerialGC",
                "-XX:TieredStopAtLevel=1",
                "-XX:CICompilerCount=2",
                "-jar",
                jarPath.toString(),
                pid.toString(),
                "--telnet-port", telnetPort.toString(),
                "--http-port", httpPort.toString(),
                "--tunnel-server", "disabled",
                "--session-timeout", "0",
                "--attach-only"
            ).apply {
                redirectErrorStream(true)
                directory(jarPath.parent.toFile())
            }

            val process = processBuilder.start()
            val httpClient = ArthasHttpClient(port = httpPort)

            startOutputReader(process, pid)

            val ready = waitForHttpReady(httpClient, httpPort, timeoutMs = 60000)
            if (ready) {
                sessions[pid] = ArthasSession(pid, httpPort, telnetPort, process, httpClient)
                LOG.info("Arthas attached to PID $pid on HTTP port $httpPort")
            } else {
                LOG.warn("Arthas attach to PID $pid timed out. process alive=${process.isAlive}")
                destroyProcess(process)
            }
            ready
        } catch (e: Exception) {
            LOG.error("Failed to attach Arthas to PID $pid", e)
            false
        }
    }

    /**
     * 在指定 PID 的 Arthas 会话上执行命令。
     */
    fun exec(
        pid: Long,
        command: String,
        execTimeout: Long = RunAnywhereSettings.getExecTimeoutMs(),
        async: Boolean = false
    ): ArthasResponse {
        val session = sessions[pid]
            ?: return ArthasResponse(-1, "", false, "No Arthas session for PID $pid")
        return if (async) session.httpClient.asyncExec(command)
        else session.httpClient.exec(command, execTimeout)
    }

    @Synchronized
    fun detach(pid: Long) {
        val session = sessions.remove(pid) ?: return
        try {
            session.httpClient.stop()
        } catch (e: Exception) {
            LOG.warn("Failed to stop Arthas via HTTP API for PID $pid", e)
        }
        destroyProcess(session.bootProcess)
        LOG.info("Arthas detached from PID $pid (port ${session.httpPort})")
    }

    fun detachAll() {
        sessions.keys.toList().forEach { detach(it) }
    }

    fun isAttached(pid: Long): Boolean {
        val session = sessions[pid] ?: return false
        return session.httpClient.isAvailable()
    }

    fun warmup(pid: Long) {
        if (isAttached(pid)) {
            LOG.info("Arthas warmup skipped, PID $pid already attached")
            return
        }
        if (!attachingPids.add(pid)) {
            LOG.info("Arthas warmup skipped, PID $pid is being attached")
            return
        }
        LOG.info("Arthas warmup triggered for PID $pid")
        com.runanywhere.concurrent.RunAnywhereExecutors.executeWarmup {
            try {
                val ok = attach(pid)
                LOG.info("Arthas warmup result for PID $pid: $ok")
            } finally {
                attachingPids.remove(pid)
            }
        }
    }

    private fun startOutputReader(process: Process, pid: Long) {
        Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> LOG.debug("[Arthas:$pid] $line") }
                }
            } catch (e: Exception) {
                LOG.warn("Error reading Arthas output for PID $pid", e)
            }
        }.apply {
            isDaemon = true
            name = "arthas-output-reader-$pid"
        }.start()
    }

    private fun waitForHttpReady(client: ArthasHttpClient, port: Int, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isPortOpen(port) && client.isAvailable()) {
                return true
            }
            Thread.sleep(200)
        }
        return false
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
                socket.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun allocateHttpPort(): Int = allocatePort(DEFAULT_HTTP_PORT, sessions.values.map { it.httpPort })

    private fun allocateTelnetPort(): Int = allocatePort(DEFAULT_TELNET_PORT, sessions.values.map { it.telnetPort })

    private fun allocatePort(startPort: Int, usedPorts: Collection<Int>): Int {
        for (offset in 0 until MAX_PORT_OFFSET) {
            val port = startPort + offset
            if (port !in usedPorts && isPortFree(port)) {
                return port
            }
        }
        throw IllegalStateException("No free port found starting from $startPort")
    }

    private fun isPortFree(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    private fun destroyProcess(process: Process) {
        try {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to destroy Arthas process", e)
        }
    }
}

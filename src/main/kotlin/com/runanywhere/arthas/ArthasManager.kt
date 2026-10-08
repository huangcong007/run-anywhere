package com.runanywhere.arthas

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.runanywhere.settings.RunAnywhereSettings
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
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
 * 端口分配和会话表用管理器锁保护；Jar 下载和就绪等待只占用该 PID 的锁。
 */
class ArthasManager(@Suppress("UNUSED_PARAMETER") private val project: Project) {

    companion object {
        private val LOG = logger<ArthasManager>()

        const val ARTHAS_VERSION = "4.0.5"
        const val DEFAULT_HTTP_PORT = 8563
        const val DEFAULT_TELNET_PORT = 3658
        private const val MAX_PORT_OFFSET = 100
        private const val SESSION_TIMEOUT_SECONDS = 1800
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val ARTHAS_RESOURCE_PATH = "/arthas/arthas-boot.jar"

        private fun arthasStandardHome(): Path =
            Paths.get(System.getProperty("user.home"), ".arthas", "lib", ARTHAS_VERSION, "arthas")

        private fun arthasStandardJar(): Path = arthasStandardHome().resolve("arthas-boot.jar")

        private fun arthasHomeDir(): Path =
            Paths.get(System.getProperty("user.home"), ".run-anywhere", "arthas")

        private fun arthasJarPath(): Path = arthasHomeDir().resolve("arthas-boot.jar")
    }

    private val sessions = ConcurrentHashMap<Long, ArthasSession>()
    private val reservedPorts = ConcurrentHashMap.newKeySet<Int>()
    private val pidLocks = ConcurrentHashMap<Long, Any>()
    private val attachingPids = ConcurrentHashMap.newKeySet<Long>()

    private fun pidLock(pid: Long): Any = pidLocks.computeIfAbsent(pid) { Any() }

    @Synchronized
    private fun ensureArthasJar(): Path {
        val standardJar = arthasStandardJar()
        if (isUsableJar(standardJar)) {
            LOG.info("Using Arthas standard install at $standardJar")
            return standardJar
        }

        val jarPath = arthasJarPath()
        if (isUsableJar(jarPath)) return jarPath

        Files.createDirectories(arthasHomeDir())
        val resourceUrl = javaClass.getResource(ARTHAS_RESOURCE_PATH)
        if (resourceUrl != null) {
            try {
                copyToFile(resourceUrl, jarPath)
                if (isUsableJar(jarPath)) {
                    LOG.info("Extracted arthas-boot.jar to $jarPath")
                    return jarPath
                }
            } catch (e: Exception) {
                LOG.warn("Failed to extract bundled arthas-boot.jar", e)
            }
            Files.deleteIfExists(jarPath)
        } else {
            LOG.warn("Bundled arthas-boot.jar not found at $ARTHAS_RESOURCE_PATH")
        }

        val url = URI(
            "https://repo1.maven.org/maven2/com/taobao/arthas/arthas-boot/" +
                "$ARTHAS_VERSION/arthas-boot-$ARTHAS_VERSION.jar"
        ).toURL()
        LOG.info("Downloading arthas-boot.jar from $url")
        copyToFile(url, jarPath)
        if (!isUsableJar(jarPath)) {
            Files.deleteIfExists(jarPath)
            throw IllegalStateException("Downloaded arthas-boot.jar is invalid")
        }
        return jarPath
    }

    private fun copyToFile(url: URL, target: Path) {
        Files.createDirectories(target.parent)
        val part = target.resolveSibling("${target.fileName}.part")
        val connection = url.openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            connection.getInputStream().use { input ->
                Files.newOutputStream(part).use { output -> input.copyTo(output) }
            }
            if (!isUsableJar(part)) {
                throw IllegalStateException("File is not a jar: $url")
            }
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(part)
        }
    }

    private fun isUsableJar(path: Path): Boolean {
        if (!Files.isRegularFile(path) || Files.size(path) < 4) return false
        return Files.newInputStream(path).use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
    }

    /**
     * Attach Arthas 到目标 JVM。同一 PID 串行，不同 PID 可以并行等待就绪。
     */
    fun attach(pid: Long): Boolean {
        synchronized(pidLock(pid)) {
            val existing = sessions[pid]
            if (existing != null) {
                if (existing.httpClient.isAvailable()) return true
                detachLocked(pid)
            }
            return try {
                attachNew(pid)
            } catch (e: Exception) {
                LOG.error("Failed to attach Arthas to PID $pid", e)
                false
            }
        }
    }

    private fun attachNew(pid: Long): Boolean {
        val jarPath = ensureArthasJar()
        var httpPort: Int? = null
        var telnetPort: Int? = null
        var process: Process? = null
        var published = false
        try {
            synchronized(this) {
                httpPort = allocatePort(DEFAULT_HTTP_PORT)
                telnetPort = allocatePort(DEFAULT_TELNET_PORT)
            }
            val http = httpPort ?: return false
            val telnet = telnetPort ?: return false
            LOG.info("Attaching Arthas to PID $pid (http=$http, telnet=$telnet) using $jarPath")

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
                "--target-ip", "127.0.0.1",
                "--telnet-port", telnet.toString(),
                "--http-port", http.toString(),
                "--tunnel-server", "disabled",
                "--session-timeout", SESSION_TIMEOUT_SECONDS.toString(),
                "--attach-only"
            ).apply {
                redirectErrorStream(true)
                directory(jarPath.parent.toFile())
            }

            process = processBuilder.start()
            val httpClient = ArthasHttpClient(port = http)
            startOutputReader(process, pid)
            val ready = waitForHttpReady(httpClient, http, timeoutMs = 60000)
            if (!ready) {
                LOG.warn("Arthas attach to PID $pid timed out. process alive=${process.isAlive}")
                return false
            }
            synchronized(this) {
                sessions[pid] = ArthasSession(pid, http, telnet, process, httpClient)
                published = true
            }
            LOG.info("Arthas attached to PID $pid on HTTP port $http")
            return true
        } finally {
            if (!published) {
                process?.let { destroyProcess(it) }
            }
            val http = httpPort
            val telnet = telnetPort
            if (http != null || telnet != null) {
                synchronized(this) {
                    if (http != null) reservedPorts.remove(http)
                    if (telnet != null) reservedPorts.remove(telnet)
                }
            }
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

    fun detach(pid: Long) {
        synchronized(pidLock(pid)) {
            detachLocked(pid)
        }
    }

    private fun detachLocked(pid: Long) {
        val session = synchronized(this) { sessions.remove(pid) } ?: return
        try {
            session.httpClient.stop()
        } catch (e: Exception) {
            LOG.warn("Failed to stop Arthas via HTTP API for PID $pid", e)
        }
        destroyProcess(session.bootProcess)
        LOG.info("Arthas detached from PID $pid (port ${session.httpPort})")
    }

    fun detachAll() {
        val pids = synchronized(this) { sessions.keys.toList() }
        pids.forEach { detach(it) }
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

    private fun allocatePort(startPort: Int): Int {
        val occupied = HashSet(reservedPorts)
        sessions.values.forEach {
            occupied.add(it.httpPort)
            occupied.add(it.telnetPort)
        }
        for (offset in 0 until MAX_PORT_OFFSET) {
            val port = startPort + offset
            if (port in occupied || !isPortFree(port)) continue
            reservedPorts.add(port)
            return port
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

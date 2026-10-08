package com.runanywhere.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JvmCommandFilterTest {

    @Test
    fun `java executable with a spaced path is a target`() {
        val command = "\"C:\\Program Files\\Java\\jdk-21\\bin\\java.exe\" -jar app.jar"
        assertEquals("java.exe", RunningProcessDetector.executableName(command))
        assertTrue(RunningProcessDetector.isTargetJvmCommand(command))
    }

    @Test
    fun `gradle launcher and node are not targets`() {
        assertFalse(
            RunningProcessDetector.isTargetJvmCommand(
                "java.exe org.gradle.launcher.GradleMain :bootRun"
            )
        )
        assertFalse(RunningProcessDetector.isTargetJvmCommand("node.exe server.js"))
        assertFalse(RunningProcessDetector.isTargetJvmCommand("npm.cmd start"))
    }

    @Test
    fun `application classpath may mention gradle caches`() {
        val command = "java -cp C:\\Users\\me\\.gradle\\caches\\modules-2\\files-2.1\\app.jar com.example.App"
        assertTrue(RunningProcessDetector.isTargetJvmCommand(command))
    }
}

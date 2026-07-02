package com.runanywhere.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessMatchScorerTest {

    @Test
    fun `module match has highest score`() {
        val score = ProcessMatchScorer.score(
            sourceModuleName = "app-service",
            className = "com.example.service.UserService",
            runModuleName = "app-service",
            mainClassName = "com.example.Application",
            configurationName = "Application",
            displayName = "Application"
        )
        assertTrue(score >= 100)
    }

    @Test
    fun `main class package match boosts score`() {
        val score = ProcessMatchScorer.score(
            sourceModuleName = null,
            className = "com.example.service.UserService",
            runModuleName = "other-module",
            mainClassName = "com.example.Application",
            configurationName = "Boot App",
            displayName = "Boot App"
        )
        assertTrue(score >= 60)
    }

    @Test
    fun `unrelated process gets zero score`() {
        val score = ProcessMatchScorer.score(
            sourceModuleName = "module-a",
            className = "com.foo.BarService",
            runModuleName = "module-b",
            mainClassName = "com.other.OtherApp",
            configurationName = "Other",
            displayName = "Other"
        )
        assertEquals(0, score)
    }

    @Test
    fun `maiyawx admin package matches maiyawx-admin module`() {
        val segments = ProcessMatchScorer.packageSegments(
            "com.maiyawx.admin.service.material.MaterialService"
        )
        assertEquals(listOf("admin", "service", "material"), segments)

        val score = ProcessMatchScorer.score(
            sourceModuleName = "maiyawx-admin",
            className = "com.maiyawx.admin.service.material.MaterialService",
            runModuleName = "maiyawx-admin",
            mainClassName = null,
            configurationName = "AdminApplication",
            displayName = "AdminApplication"
        )
        // module exact match 100 + package segment admin in module name 45
        assertTrue(score >= 100)
    }

    @Test
    fun `admin segment matches when main class unavailable`() {
        val score = ProcessMatchScorer.score(
            sourceModuleName = null,
            className = "com.maiyawx.admin.service.material.MaterialService",
            runModuleName = "maiyawx-admin",
            mainClassName = null,
            configurationName = "AdminApplication",
            displayName = "AdminApplication"
        )
        assertTrue(score >= 45)
    }
}

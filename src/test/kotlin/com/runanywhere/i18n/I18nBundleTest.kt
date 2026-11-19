package com.runanywhere.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.ResourceBundle

/**
 * 保证 i18n 接入不"漏 key"：
 * 1. [I18n] 里引用的每个 key 都能在 Bundle 中取到；
 * 2. Bundle 里没有空值；
 * 3. 含 JSON 片段 / 单个 `{` 的文案不会被格式化逻辑破坏。
 */
class I18nBundleTest {

    private val bundle: ResourceBundle = ResourceBundle.getBundle("messages.RunAnywhereBundle")

    @Test
    fun `every key referenced by I18n exists in bundle`() {
        val source = readProjectFile("src/main/kotlin/com/runanywhere/i18n/I18n.kt")
        val keys = Regex("message\\(\"([^\"]+)\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(keys.size > 50, "expected to find the message keys in I18n.kt, got ${keys.size}")

        val missing = keys.filterNot { bundle.containsKey(it) }
        assertTrue(missing.isEmpty(), "keys missing from RunAnywhereBundle.properties: $missing")
    }

    @Test
    fun `bundle has no blank values`() {
        val blanks = bundle.keys.toList().filter { bundle.getString(it).isBlank() }
        assertTrue(blanks.isEmpty(), "blank message values: $blanks")
    }

    @Test
    fun `placeholder substitution keeps json braces intact`() {
        val message = I18n.JSON_ERROR_OBJECT("id")
        assertTrue(message.contains("id"))
        assertTrue(message.contains("{"), "literal brace should survive: $message")
        assertFalse(message.contains("{0}"), "placeholder should be replaced: $message")
    }

    @Test
    fun `json hints keep their json samples`() {
        assertTrue(I18n.JSON_HINT_OBJECT().contains("{\"id\":1"), I18n.JSON_HINT_OBJECT())
        assertTrue(I18n.JSON_HINT_ARRAY().contains("["), I18n.JSON_HINT_ARRAY())
        assertTrue(I18n.JSON_HINT_MAP().contains("{\"key1\""), I18n.JSON_HINT_MAP())
    }

    @Test
    fun `security body keeps html markup`() {
        val body = I18n.SECURITY_BODY()
        assertTrue(body.startsWith("<html>"), body)
        assertTrue(body.contains("<strong>"), body)
    }

    @Test
    fun `parameterized message replaces every placeholder`() {
        val message = I18n.PARSED_TIMEOUT_LIMIT(1500, 1)
        assertTrue(message.contains("1500"), message)
        assertTrue(message.contains("1 秒"), message)
        assertEquals(false, message.contains("{0}") || message.contains("{1}"), message)
    }

    @Test
    fun `english bundle has the same key set as the base bundle`() {
        val baseKeys = bundle.keys.toList().toSet()
        val english = ResourceBundle.getBundle("messages.RunAnywhereBundle", java.util.Locale.ENGLISH)
        val englishKeys = english.keys.toList().toSet()
        assertEquals(baseKeys, englishKeys)
    }

    @Test
    fun `english locale resolves english text`() {
        val english = ResourceBundle.getBundle("messages.RunAnywhereBundle", java.util.Locale.ENGLISH)
        assertEquals("Succeeded", english.getString("tab.status.success"))
        assertEquals("Welcome", english.getString("toolWindow.welcomeTab"))
        // \u0020 转义要还原成空格，否则会输出 "Error:NullPointerException"
        assertEquals("Error: ", english.getString("tab.error.prefix"))
    }

    private fun readProjectFile(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate.readText(Charsets.UTF_8)
            dir = dir.parentFile
        }
        error("cannot locate $relativePath from ${System.getProperty("user.dir")}")
    }
}

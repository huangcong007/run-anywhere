package com.runanywhere.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 结果 Tab 标题格式守卫。
 *
 * 标题格式：`<运行配置名> · PID <pid> <方法名>()[ #序号]`
 * 状态行依赖 [extractPid] 从这里取 PID，改标题格式时必须同步。
 */
class TabTitleTest {

    @Test
    fun `extracts pid from title with run configuration name`() {
        assertEquals("1234", extractPid("demoApp · PID 1234 test2()"))
    }

    @Test
    fun `extracts pid from duplicated tab title`() {
        assertEquals("1234", extractPid("demoApp · PID 1234 test2() #2"))
    }

    @Test
    fun `extracts pid when configuration name contains pid text`() {
        assertEquals("1234", extractPid("PID demo · PID 1234 test2()"))
    }

    @Test
    fun `returns placeholder when title has no pid`() {
        assertEquals("?", extractPid("Welcome"))
    }
}

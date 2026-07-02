package com.runanywhere.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.runanywhere.arthas.ArthasResponse
import com.runanywhere.arthas.ArthasResponseParser
import java.awt.BorderLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JPanel

class RunResultToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        private const val WELCOME_TAB_NAME = "Welcome"

        /**
         * 创建"执行中" Tab 并同步返回 Content。
         * 若不在 EDT，使用 invokeAndWait 确保 Content 可用。
         */
        fun showRunning(project: Project, tabTitle: String, command: String): Content? {
            if (ApplicationManager.getApplication().isDispatchThread) {
                return doCreateRunningTab(project, tabTitle, command)
            }
            var created: Content? = null
            ApplicationManager.getApplication().invokeAndWait {
                created = doCreateRunningTab(project, tabTitle, command)
            }
            return created
        }

        private fun doCreateRunningTab(project: Project, tabTitle: String, command: String): Content? {
            val toolWindow = project.getService(com.intellij.openapi.wm.ToolWindowManager::class.java)
                ?.getToolWindow("Run Anywhere") ?: return null
            toolWindow.show()
            val contentManager = toolWindow.contentManager
            val panel = ResultPanel.createRunning(tabTitle, command)
            val content = ContentFactory.getInstance().createContent(panel, tabTitle, false)
            content.isCloseable = true
            contentManager.addContent(content)
            contentManager.setSelectedContent(content)
            return content
        }

        fun updateResult(project: Project, content: Content?, result: InvocationResult) {
            if (content == null) {
                showResult(project, result)
                return
            }
            ApplicationManager.getApplication().invokeLater {
                val panel = content.component as? ResultPanel ?: return@invokeLater
                panel.updateWithResult(result)
                content.displayName = result.tabTitle
            }
        }

        fun showResult(project: Project, result: InvocationResult) {
            ApplicationManager.getApplication().invokeLater {
                val toolWindow = project.getService(com.intellij.openapi.wm.ToolWindowManager::class.java)
                    ?.getToolWindow("Run Anywhere") ?: return@invokeLater
                toolWindow.show()
                val contentManager = toolWindow.contentManager
                val panel = ResultPanel.createCompleted(result)
                val content = ContentFactory.getInstance().createContent(panel, result.tabTitle, false)
                content.isCloseable = true
                contentManager.addContent(content)
                contentManager.setSelectedContent(content)
            }
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(JBTextArea().apply {
                text = "Run Anywhere results will appear here.\n" +
                    "Right-click on a Java method and select \"运行方法 (Run Anywhere)\"."
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            }, BorderLayout.CENTER)
        }
        val content = ContentFactory.getInstance().createContent(panel, WELCOME_TAB_NAME, false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

        val closeAllAction = CloseAllTabsAction(toolWindow)
        toolWindow.setTitleActions(listOf(closeAllAction))
    }
}

data class InvocationResult(
    val tabTitle: String,
    val command: String,
    val response: ArthasResponse,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val asyncMode: Boolean = false
) {
    val isSuccess: Boolean get() = ArthasResponseParser.isExecutionSuccessful(response, asyncMode)
}

private class ResultPanel private constructor() : JPanel(BorderLayout()) {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        border = JBUI.Borders.empty(10)
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    companion object {
        fun createRunning(tabTitle: String, command: String): ResultPanel =
            ResultPanel().apply { renderRunning(tabTitle, command) }

        fun createCompleted(result: InvocationResult): ResultPanel =
            ResultPanel().apply { renderResult(result) }
    }

    fun updateWithResult(result: InvocationResult) {
        renderResult(result)
    }

    private fun renderRunning(tabTitle: String, command: String) {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        textArea.text = buildString {
            appendLine("═".repeat(60))
            appendLine("开始时间： $now")
            appendLine("状态：     执行中...")
            appendLine("PID：      ${extractPid(tabTitle)}")
            appendLine("═".repeat(60))
            appendLine()
            appendLine("【执行的命令】")
            appendLine(command)
            appendLine()
            appendLine("【正在等待 Arthas 响应，可同时运行其他方法】")
        }
        textArea.foreground = JBColor(0x0000CC, 0xCCCCFF)
    }

    private fun renderResult(result: InvocationResult) {
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        textArea.text = buildString {
            appendLine("═".repeat(60))
            appendLine("时间：     ${result.timestamp.format(timeFormatter)}")
            appendLine("状态：     ${formatStatus(result)}")
            appendLine("PID：      ${extractPid(result.tabTitle)}")
            appendLine("═".repeat(60))
            appendLine()
            appendLine("【执行的命令】")
            appendLine(result.command)
            appendLine()
            appendLine("【原始响应】")
            if (result.response.errorMessage != null) {
                appendLine("错误：${result.response.errorMessage}")
            }
            appendLine(result.response.body)
            appendLine()
            appendLine("【解析结果】")
            appendLine(ArthasResponseParser.parse(result.response, result.asyncMode))
        }
        textArea.foreground = if (result.isSuccess) JBColor.BLACK else JBColor.RED
    }

    private fun formatStatus(result: InvocationResult): String {
        return when {
            result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> "已提交（异步）"
            result.isSuccess -> "成功"
            else -> "失败"
        }
    }

    private fun extractPid(tabTitle: String): String =
        tabTitle.substringAfter("PID ").substringBefore(" ")
}

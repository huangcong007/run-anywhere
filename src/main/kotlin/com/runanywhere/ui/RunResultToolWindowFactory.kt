package com.runanywhere.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.runanywhere.arthas.ArthasResponse
import com.runanywhere.arthas.ArthasResponseParser
import com.runanywhere.i18n.I18n
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

/**
 * Run Anywhere 结果工具窗口工厂。
 */
class RunResultToolWindowFactory : ToolWindowFactory {

    companion object {
        private val LOG = logger<RunResultToolWindowFactory>()

        /** 从标题中提取 PID；若无法提取则返回 "?"。 */
        fun extractPid(tabTitle: String): String =
            tabTitle.substringAfter("PID ").substringBefore(" ")

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

        private fun doCreateRunningTab(
            project: Project,
            tabTitle: String,
            command: String,
        ): Content? {
            val toolWindow = project.getService(com.intellij.openapi.wm.ToolWindowManager::class.java)
                ?.getToolWindow("Run Anywhere") ?: return null
            toolWindow.show()
            val contentManager = toolWindow.contentManager
            val panel = ResultPanel.createRunning(tabTitle, command)
            val content = ContentFactory.getInstance().createContent(panel, tabTitle, false)
            content.isCloseable = true
            contentManager.addContent(content)
            contentManager.setSelectedContent(content)
            announceResult(panel, I18n.ANNOUNCE_RUNNING(tabTitle))
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
                announceResult(panel, describeResult(result))
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
                announceResult(panel, describeResult(result))
            }
        }

        /** 生成结果摘要，供屏幕阅读器播报。 */
        private fun describeResult(result: InvocationResult): String {
            val pid = extractPid(result.tabTitle)
            val status = when {
                result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> I18n.TAB_STATUS_ASYNC()
                result.isSuccess -> I18n.TAB_STATUS_SUCCESS()
                else -> I18n.TAB_STATUS_FAIL()
            }
            return I18n.ANNOUNCE_RESULT(pid, status)
        }

        /**
         * 通过 [com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil] 播报状态变化（P2 B10）。
         */
        private fun announceResult(panel: JPanel, message: String) {
            com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil.announce(panel, message, true)
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // P1 A5：空态改用中文并引导操作
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(JBTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                background = UIUtil.getTextFieldBackground()
                foreground = UIUtil.getContextHelpForeground()
                font = Font(font.name, Font.ITALIC, font.size)
                text = I18n.TAB_WELCOME_TEXT()
            }, BorderLayout.CENTER)
        }
        val content = ContentFactory.getInstance().createContent(panel, I18n.WELCOME_TAB_NAME(), false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

        val closeAllAction = CloseAllTabsAction(toolWindow)
        toolWindow.setTitleActions(listOf(closeAllAction))
    }
}

/** 从标题中提取 PID；若无法提取则返回 "?"。 */
internal fun extractPid(tabTitle: String): String =
    tabTitle.substringAfter("PID ").substringBefore(" ")

data class InvocationResult(
    val tabTitle: String,
    val command: String,
    val response: ArthasResponse,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val asyncMode: Boolean = false,
) {
    val isSuccess: Boolean get() = ArthasResponseParser.isExecutionSuccessful(response, asyncMode)
}

private class ResultPanel private constructor() : JPanel(BorderLayout()) {

    // 状态行（始终显示）
    private val stateArea = JTextArea(3, 50).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.emptyBottom(8)
    }

    // 分隔线
    private val separator = Box.createHorizontalStrut(Int.MAX_VALUE)

    // 详情区（原始命令 + 原始 JSON），默认隐藏
    private val detailsArea = JTextArea(8, 50).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val detailsScroll = JScrollPane(detailsArea).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        isVisible = false
        preferredSize = Dimension(500, 80)
    }

    // 复制诊断信息按钮
    private val copyButton = JButton(I18n.TAB_COPY_BUTTON()).apply {
        toolTipText = I18n.TAB_COPY_TIP()
        setBounds(0, 0, 140, 24)
        isVisible = false
        addActionListener { copyDiagnosticInfo() }
    }

    // 开关按钮
    private var isDetailsVisible = false
    private val toggleDetailsButton: JButton = JButton(I18n.TAB_TOGGLE_DETAILS()).apply {
        toolTipText = I18n.TAB_TOGGLE_DETAILS_TIP()
        setBounds(0, 0, 100, 24)
        isVisible = false
        addActionListener {
            isDetailsVisible = !isDetailsVisible
            detailsScroll.isVisible = isDetailsVisible
            this.text = if (isDetailsVisible) I18n.TAB_COLLAPSE_DETAILS() else I18n.TAB_TOGGLE_DETAILS()
        }
    }

    init {
        border = JBUI.Borders.empty(10)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(stateArea)
        add(separator)
        add(detailsScroll)
        add(Box.createVerticalStrut(4))
        add(copyButton)
        add(toggleDetailsButton)
    }

    companion object {
        fun createRunning(tabTitle: String, command: String): ResultPanel =
            ResultPanel().also { it.renderRunning(tabTitle, command) }

        fun createCompleted(result: InvocationResult): ResultPanel =
            ResultPanel().also { it.renderResult(result) }
    }

    fun updateWithResult(result: InvocationResult) {
        renderResult(result)
    }

    private fun renderRunning(tabTitle: String, command: String) {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        stateArea.setText(buildString {
            appendLine(I18n.TAB_RUNNING_START_AT(now))
            appendLine(I18n.TAB_RUNNING_STATUS())
            appendLine(I18n.TAB_RUNNING_PID(extractPid(tabTitle)))
        })
        stateArea.foreground = UIUtil.getLabelForeground()
        stateArea.font = UIUtil.getLabelFont().deriveFont(Font.PLAIN.toFloat())
        detailsArea.isVisible = false
        copyButton.isVisible = false
        toggleDetailsButton.isVisible = false
        detailsScroll.isVisible = false
    }

    private fun renderResult(result: InvocationResult) {
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val statusText = formatStatus(result)
        val icon = when {
            result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> "\uD83D\uDCDD"
            result.isSuccess -> "\u2705"
            else -> "\u274C"
        }

        stateArea.setText(buildString {
            appendLine(I18n.TAB_RESULT_TIME(result.timestamp.format(timeFormatter)))
            appendLine(I18n.TAB_RESULT_STATUS(icon, statusText))
            appendLine(I18n.TAB_RESULT_PID(extractPid(result.tabTitle)))
        })
        stateArea.foreground = if (result.isSuccess) UIUtil.getLabelForeground() else UIUtil.getErrorForeground()
        stateArea.font = UIUtil.getLabelFont().deriveFont(Font.PLAIN.toFloat())

        // P1 B4：原始 JSON 折叠进详情，默认不可见
        detailsArea.setText(buildString {
            appendLine(I18n.TAB_COMMAND_HEADER())
            appendLine(result.command)
            appendLine()
            appendLine(I18n.TAB_RAW_HEADER())
            if (result.response.errorMessage != null) {
                appendLine(I18n.TAB_ERROR_PREFIX() + result.response.errorMessage)
            }
            appendLine(result.response.body)
            appendLine()
            appendLine(I18n.TAB_PARSED_HEADER())
            appendLine(ArthasResponseParser.parse(result.response, result.asyncMode))
        })
        detailsArea.isVisible = false
        detailsScroll.isVisible = false
        copyButton.isVisible = true
        toggleDetailsButton.isVisible = true
    }

    private fun copyDiagnosticInfo() {
        val text = buildString {
            appendLine(I18n.TAB_COMMAND_HEADER())
            appendLine(stateArea.text.trim())
            appendLine()
            appendLine(I18n.TAB_RAW_HEADER())
            appendLine(detailsArea.text.trim())
        }
        com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(
            java.awt.datatransfer.StringSelection(text.trimEnd())
        )
    }

    private fun formatStatus(result: InvocationResult): String {
        return when {
            result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> I18n.TAB_STATUS_ASYNC()
            result.isSuccess -> I18n.TAB_STATUS_SUCCESS()
            else -> I18n.TAB_STATUS_FAIL()
        }
    }
}

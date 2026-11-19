package com.runanywhere.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.runanywhere.arthas.ArthasResponse
import com.runanywhere.arthas.ArthasResponseParser
import com.runanywhere.i18n.I18n
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
         * P1 A6：同名 Tab 加序号（`method() #2`），避免同一方法反复调用后
         * 多个 Tab 标题完全一样、无法分辨。序号按现存 Tab 计算，关掉后自动复用。
         */
        fun uniqueTabTitle(project: Project, base: String): String {
            val toolWindow = project.getService(com.intellij.openapi.wm.ToolWindowManager::class.java)
                ?.getToolWindow("Run Anywhere") ?: return base
            val existing = toolWindow.contentManager.contents.mapTo(HashSet()) { it.displayName }
            if (base !in existing) return base
            var index = 2
            while ("$base #$index" in existing) index++
            return "$base #$index"
        }

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

    /**
     * 状态摘要（时间 / 状态 / PID）。用 [UIUtil.getLabelFont] 原样字体，
     * 不要再 deriveFont(0f)（派生 0 号字会让整块文字不可见）。
     */
    private val statusArea = JTextArea(3, 0).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.emptyBottom(8)
    }

    /** 正文：解析结果常驻，原始命令 / 原始响应按需展开。 */
    private val contentArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(6)
    }
    private val contentScroll = JScrollPane(contentArea).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.customLine(JBColor.border(), 1)
    }

    private var lastResult: InvocationResult? = null
    private var lastCommand: String = ""
    private var isDetailsVisible = false

    private val copyButton = JButton(I18n.TAB_COPY_BUTTON()).apply {
        toolTipText = I18n.TAB_COPY_TIP()
        isVisible = false
        addActionListener { copyDiagnosticInfo() }
    }

    private val toggleDetailsButton = JButton(I18n.TAB_TOGGLE_DETAILS()).apply {
        toolTipText = I18n.TAB_TOGGLE_DETAILS_TIP()
        isVisible = false
        addActionListener {
            isDetailsVisible = !isDetailsVisible
            text = if (isDetailsVisible) I18n.TAB_COLLAPSE_DETAILS() else I18n.TAB_TOGGLE_DETAILS()
            renderContent()
        }
    }

    /** 操作按钮固定贴底右对齐，不再随 BoxLayout 居中漂浮。 */
    private val buttonBar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(8)
        add(copyButton)
        add(toggleDetailsButton)
        isVisible = false
    }

    init {
        border = JBUI.Borders.empty(10)
        add(statusArea, BorderLayout.NORTH)
        add(contentScroll, BorderLayout.CENTER)
        add(buttonBar, BorderLayout.SOUTH)
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun createRunning(tabTitle: String, command: String): ResultPanel =
            ResultPanel().also { it.renderRunning(tabTitle, command) }

        fun createCompleted(result: InvocationResult): ResultPanel =
            ResultPanel().also { it.renderResult(result) }
    }

    fun updateWithResult(result: InvocationResult) {
        renderResult(result)
    }

    private fun renderRunning(tabTitle: String, command: String) {
        lastCommand = command
        lastResult = null
        val now = LocalDateTime.now().format(TIME_FORMAT)
        statusArea.text = buildString {
            appendLine(I18n.TAB_RUNNING_START_AT(now))
            appendLine(I18n.TAB_RUNNING_STATUS())
            append(I18n.TAB_RUNNING_PID(extractPid(tabTitle)))
        }
        statusArea.foreground = UIUtil.getLabelForeground()

        isDetailsVisible = false
        renderContent()
        copyButton.isVisible = false
        toggleDetailsButton.isVisible = false
        buttonBar.isVisible = false
    }

    private fun renderResult(result: InvocationResult) {
        lastResult = result
        lastCommand = result.command
        val statusText = formatStatus(result)
        val icon = when {
            result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> "\uD83D\uDCDD"
            result.isSuccess -> "\u2705"
            else -> "\u274C"
        }

        statusArea.text = buildString {
            appendLine(I18n.TAB_RESULT_TIME(result.timestamp.format(TIME_FORMAT)))
            appendLine(I18n.TAB_RESULT_STATUS(icon, statusText))
            append(I18n.TAB_RESULT_PID(extractPid(result.tabTitle)))
        }
        statusArea.foreground = if (result.isSuccess) UIUtil.getLabelForeground() else UIUtil.getErrorForeground()

        isDetailsVisible = false
        toggleDetailsButton.text = I18n.TAB_TOGGLE_DETAILS()
        renderContent()
        copyButton.isVisible = true
        toggleDetailsButton.isVisible = true
        buttonBar.isVisible = true
    }

    /**
     * 正文渲染：解析结果常驻顶部；「原始命令 / 原始响应」由按钮控制显隐。
     * 失败时同时给出错误信息，避免用户只看到空白正文。
     */
    private fun renderContent() {
        val result = lastResult
        contentArea.text = buildString {
            if (result == null) {
                // 执行中：先展示即将执行的命令
                appendLine(I18n.TAB_COMMAND_HEADER())
                append(lastCommand)
            } else {
                appendLine(I18n.TAB_PARSED_HEADER())
                appendLine(ArthasResponseParser.parse(result.response, result.asyncMode).trimEnd())
                if (isDetailsVisible) {
                    appendLine()
                    appendLine(I18n.TAB_COMMAND_HEADER())
                    append(lastCommand)
                    appendLine()
                    appendLine()
                    appendLine(I18n.TAB_RAW_HEADER())
                    result.response.errorMessage?.let { appendLine(I18n.TAB_ERROR_PREFIX() + it) }
                    append(result.response.body)
                }
            }
        }.trimEnd()
        contentArea.foreground = UIUtil.getLabelForeground()
        contentArea.caretPosition = 0
    }

    private fun copyDiagnosticInfo() {
        val result = lastResult
        val text = buildString {
            appendLine(statusArea.text.trim())
            appendLine()
            appendLine(I18n.TAB_COMMAND_HEADER())
            appendLine(lastCommand)
            if (result != null) {
                appendLine()
                appendLine(I18n.TAB_RAW_HEADER())
                result.response.errorMessage?.let { appendLine(I18n.TAB_ERROR_PREFIX() + it) }
                appendLine(result.response.body)
                appendLine()
                appendLine(I18n.TAB_PARSED_HEADER())
                appendLine(ArthasResponseParser.parse(result.response, result.asyncMode))
            }
        }
        CopyPasteManager.getInstance().setContents(StringSelection(text.trimEnd()))
    }

    private fun formatStatus(result: InvocationResult): String {
        return when {
            result.asyncMode && ArthasResponseParser.isAsyncSubmitted(result.response) -> I18n.TAB_STATUS_ASYNC()
            result.isSuccess -> I18n.TAB_STATUS_SUCCESS()
            else -> I18n.TAB_STATUS_FAIL()
        }
    }
}

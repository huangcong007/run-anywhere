package com.runanywhere.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content

/**
 * 关闭工具窗口中所有可关闭的结果 Tab（保留 Welcome Tab）。
 */
class CloseAllTabsAction(
    private val toolWindow: ToolWindow
) : DumbAwareAction(
    "全部关闭",
    "关闭所有结果 Tab（保留 Welcome）",
    com.intellij.icons.AllIcons.Actions.GC
) {
    override fun actionPerformed(e: AnActionEvent) {
        val contentManager = toolWindow.contentManager
        // 收集所有可关闭的 content（保留 Welcome）
        val toRemove = contentManager.contents.filter { it.isCloseable }
        toRemove.forEach { content: Content ->
            contentManager.removeContent(content, true)
        }
    }

    override fun update(e: AnActionEvent) {
        // 只要有可关闭的 tab 才启用按钮
        val contentManager = toolWindow.contentManager
        e.presentation.isEnabled = contentManager.contents.any { it.isCloseable }
    }

    override fun getActionUpdateThread(): com.intellij.openapi.actionSystem.ActionUpdateThread {
        return com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }
}

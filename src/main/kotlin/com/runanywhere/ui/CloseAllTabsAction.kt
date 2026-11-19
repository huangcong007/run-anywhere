package com.runanywhere.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content

/**
 * 关闭工具窗口中所有可关闭的结果 Tab（保留 Welcome Tab）。
 *
 * 使用 [AllIcons.General.Close]（×）图标，语义更贴合"关闭全部"。
 */
class CloseAllTabsAction(
    private val toolWindow: ToolWindow
) : DumbAwareAction(
    "全部关闭",
    "关闭所有结果 Tab（保留 Welcome）",
    AllIcons.General.Close
) {
    override fun actionPerformed(e: AnActionEvent) {
        val contentManager = toolWindow.contentManager
        val toRemove = contentManager.contents.filter { it.isCloseable }
        toRemove.forEach { content: Content ->
            contentManager.removeContent(content, true)
        }
    }

    override fun update(e: AnActionEvent) {
        val contentManager = toolWindow.contentManager
        e.presentation.isEnabled = contentManager.contents.any { it.isCloseable }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

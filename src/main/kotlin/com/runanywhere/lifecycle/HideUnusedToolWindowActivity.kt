package com.runanywhere.lifecycle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.runanywhere.settings.RunAnywhereSettings
import com.runanywhere.ui.RunResultToolWindowFactory

/**
 * P1 A4：Run Anywhere 不是全项目通用功能，从未使用过时不占底部 stripe 按钮。
 *
 * 第一次调用方法时由 [RunResultToolWindowFactory] 重新 `setAvailable(true)` 并显示。
 * 用户用过一次后（[RunAnywhereSettings.isToolWindowRevealed]）不再隐藏，避免每次都去找入口。
 *
 * 副作用：未使用过时 View → Tool Windows 里也看不到该窗口，入口只有编辑器右键菜单。
 */
class HideUnusedToolWindowActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (RunAnywhereSettings.isToolWindowRevealed()) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ToolWindowManager.getInstance(project)
                .getToolWindow(RunResultToolWindowFactory.TOOL_WINDOW_ID)
                ?.setAvailable(false, null)
        }
    }
}

package com.runanywhere.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * 统一的非模态反馈入口。
 *
 * 平台规范（Informing Users / Notifications）明确要求避免用模态消息框提示
 * 错误与状态变化，因此插件内所有"告知用户"都走气球通知；编辑器内可就地
 * 提示的失败优先使用 [com.intellij.codeInsight.hint.HintManager]。
 *
 * 通知组 "Run Anywhere" 注册于 plugin.xml。
 */
object RunAnywhereNotifications {

    private const val GROUP_ID = "Run Anywhere"

    /** 执行失败、attach 失败等需要用户关注但不阻断操作的问题。 */
    fun error(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.ERROR)
    }

    /** 缺少前置条件（如没有运行中的进程），需要引导用户先做一步操作。 */
    fun warning(project: Project, title: String, content: String) {
        notify(project, title, content, NotificationType.WARNING)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}

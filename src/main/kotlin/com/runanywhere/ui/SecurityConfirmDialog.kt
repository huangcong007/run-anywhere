package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.runanywhere.i18n.I18n
import com.runanywhere.settings.RunAnywhereSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 首次调用前的安全确认对话框。
 */
class SecurityConfirmDialog(project: Project) : DialogWrapper(project) {

    private val dontShowAgainCheckBox = JBCheckBox(I18n.SECURITY_DONT_ASK(), false)

    init {
        title = I18n.SECURITY_DIALOG_TITLE()
        setOKButtonText(I18n.SECURITY_OK_BUTTON())
        setCancelButtonText(I18n.SECURITY_CANCEL_BUTTON())
        init()
        // P1 B8：危险操作不作为默认按钮。Enter 走“取消”，避免手快误触注入目标 JVM。
        rootPane?.defaultButton = getButton(getCancelAction())
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            add(
                JLabel(I18n.SECURITY_BODY()),
                BorderLayout.CENTER
            )
            add(dontShowAgainCheckBox, BorderLayout.SOUTH)
        }
    }

    override fun doOKAction() {
        if (dontShowAgainCheckBox.isSelected) {
            RunAnywhereSettings.setSecurityConfirmed(true)
        }
        super.doOKAction()
    }

    companion object {
        /**
         * 若用户尚未确认，弹出对话框。
         * @return true 表示用户确认继续
         */
        fun confirmIfNeeded(project: Project): Boolean {
            if (RunAnywhereSettings.isSecurityConfirmed()) return true
            return SecurityConfirmDialog(project).showAndGet()
        }
    }
}

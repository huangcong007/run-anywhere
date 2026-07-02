package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.runanywhere.settings.RunAnywhereSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 首次调用前的安全确认对话框。
 */
class SecurityConfirmDialog(project: Project) : DialogWrapper(project) {

    private val dontShowAgainCheckBox = JBCheckBox("不再提示", false)

    init {
        title = "Run Anywhere - 安全确认"
        setOKButtonText("我了解，继续调用")
        setCancelButtonText("取消")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 12)).apply {
            add(
                JLabel(
                    "<html>" +
                        "<p>此操作将通过 Arthas 在<strong>运行中的 JVM</strong>内执行任意 Java 方法。</p>" +
                        "<p>请确认：</p>" +
                        "<ul>" +
                        "<li>仅用于<strong>开发/调试环境</strong></li>" +
                        "<li>不要在生产环境使用</li>" +
                        "<li>调用可能产生副作用（写数据库、发消息等）</li>" +
                        "</ul>" +
                        "</html>"
                ),
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

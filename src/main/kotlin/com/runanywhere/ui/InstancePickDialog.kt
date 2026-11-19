package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.runanywhere.i18n.I18n
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 同一个类有多个实例时，让用户选择 vmtool 的 instances 下标。
 */
class InstancePickDialog(
    project: Project,
    className: String,
    labels: List<String>
) : DialogWrapper(project) {

    private val centerLabel: JLabel
    private val combo = ComboBox(DefaultComboBoxModel(labels.toTypedArray()))

    init {
        val safeName = className.replace("&", "&amp;").replace("<", "&lt;")
        centerLabel = JLabel(
            "<html>" + I18n.INSTANCE_PICK_LABEL(safeName, labels.size) + "</html>"
        )
        title = I18n.INSTANCE_PICK_TITLE()
        setOKButtonText(I18n.INSTANCE_PICK_OK())
        setCancelButtonText(I18n.INSTANCE_PICK_CANCEL())
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 8)).apply {
            add(centerLabel, BorderLayout.NORTH)
            add(combo, BorderLayout.CENTER)
        }
    }

    fun showAndGetIndex(): Int? {
        if (!showAndGet()) return null
        return combo.selectedIndex.takeIf { it >= 0 }
    }
}

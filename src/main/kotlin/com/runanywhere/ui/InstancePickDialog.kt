package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
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
    count: Int
) : DialogWrapper(project) {

    private val centerLabel: JLabel
    private val combo = ComboBox(
        DefaultComboBoxModel((0 until count).map { index -> "instances[$index]" }.toTypedArray())
    )

    init {
        val safeName = className.replace("&", "&amp;").replace("<", "&lt;")
        centerLabel = JLabel("<html>类 $safeName 有 $count 个实例，请选择要调用的那个。</html>")
        title = "选择实例"
        setOKButtonText("调用")
        setCancelButtonText("取消")
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

package com.runanywhere.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings → Other Settings → Run Anywhere 配置面板。
 */
class RunAnywhereSettingsComponent {

    private val asyncCheckBox = JBCheckBox("异步执行（不等待返回结果）")
    private val timeoutField = JBTextField(10)
    private val timeoutHintLabel = JBLabel()
    private val jsonLibraryCombo = ComboBox(
        DefaultComboBoxModel(JsonLibrary.entries.map { it.displayName }.toTypedArray())
    )

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(asyncCheckBox)
        .addLabeledComponent("同步执行超时（秒）", timeoutField)
        .addComponent(timeoutHintLabel)
        .addLabeledComponent("对象参数 JSON 库", jsonLibraryCombo)
        .addComponent(
            JBLabel("无 Jackson 时可选手动指定 Gson / Fastjson；自动模式会按 classpath 依次尝试。")
        )
        .addComponentFillVertically(JPanel(), 0)
        .panel.apply {
            border = JBUI.Borders.empty(10)
        }

    init {
        asyncCheckBox.toolTipText =
            "使用 Arthas async_exec 提交命令后立即返回，不等待方法执行完成。\n" +
                "适合断点调试：目标线程在断点暂停时不会导致插件超时。"
        timeoutHintLabel.text =
            "同步执行时的 Arthas 超时（${RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS}-" +
                "${RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS} 秒）。异步模式下忽略此项。"
        asyncCheckBox.addActionListener { updateTimeoutFieldState() }
    }

    fun getPanel(): JComponent = panel

    fun isModified(): Boolean {
        return asyncCheckBox.isSelected != RunAnywhereSettings.isAsyncExecution() ||
            timeoutField.text.trim() != RunAnywhereSettings.getExecTimeoutSeconds().toString() ||
            selectedJsonLibrary() != RunAnywhereSettings.getJsonLibrary()
    }

    fun apply() {
        val timeoutSeconds = timeoutField.text.trim().toIntOrNull()
            ?.coerceIn(RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS, RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS)
            ?: RunAnywhereSettings.DEFAULT_EXEC_TIMEOUT_SECONDS
        RunAnywhereSettings.setExecTimeoutSeconds(timeoutSeconds)
        RunAnywhereSettings.setAsyncExecution(asyncCheckBox.isSelected)
        RunAnywhereSettings.setJsonLibrary(selectedJsonLibrary())
    }

    fun reset() {
        asyncCheckBox.isSelected = RunAnywhereSettings.isAsyncExecution()
        timeoutField.text = RunAnywhereSettings.getExecTimeoutSeconds().toString()
        selectJsonLibrary(RunAnywhereSettings.getJsonLibrary())
        updateTimeoutFieldState()
    }

    private fun selectedJsonLibrary(): JsonLibrary {
        val displayName = jsonLibraryCombo.selectedItem as? String
        return JsonLibrary.entries.find { it.displayName == displayName } ?: JsonLibrary.AUTO
    }

    private fun selectJsonLibrary(library: JsonLibrary) {
        jsonLibraryCombo.selectedItem = library.displayName
    }

    private fun updateTimeoutFieldState() {
        val async = asyncCheckBox.isSelected
        timeoutField.isEnabled = !async
        timeoutHintLabel.isEnabled = !async
    }
}

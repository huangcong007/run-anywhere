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

    private val asyncCheckBox = JBCheckBox("异步执行（后台等待返回值，断点不超时）")
    private val warmupCheckBox = JBCheckBox("进程启动后预热 Attach")
    private val timeoutField = JBTextField(10)
    private val timeoutHintLabel = JBLabel()
    private val expandField = JBTextField(10)
    private val expandHintLabel = JBLabel()
    private val jsonLibraryCombo = ComboBox(
        DefaultComboBoxModel(JsonLibrary.entries.map { it.displayName }.toTypedArray())
    )

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(asyncCheckBox)
        .addComponent(warmupCheckBox)
        .addLabeledComponent("执行超时（秒）", timeoutField)
        .addComponent(timeoutHintLabel)
        .addLabeledComponent("结果展开层级", expandField)
        .addComponent(expandHintLabel)
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
            "使用 Arthas async_exec 提交命令，再拉取返回值。\n" +
                "目标线程停在断点时不会触发插件 HTTP 超时。等待上限与执行超时相同。"
        warmupCheckBox.toolTipText =
            "应用启动约 2 秒后自动 Attach。关闭时只在点击「运行方法」后注入 Arthas。\n" +
                "HTTP 只监听 127.0.0.1，会话超时 30 分钟。"
        timeoutHintLabel.text =
            "同步执行的 Arthas 超时，也是异步模式等待返回值的上限（" +
                "${RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS}-" +
                "${RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS} 秒）。"
        expandHintLabel.text =
            "Arthas -x 展开层级（${RunAnywhereSettings.MIN_RESULT_EXPAND_LEVEL}-" +
                "${RunAnywhereSettings.MAX_RESULT_EXPAND_LEVEL}）。返回 void 时固定为 0。"
    }

    fun getPanel(): JComponent = panel

    fun isModified(): Boolean {
        return asyncCheckBox.isSelected != RunAnywhereSettings.isAsyncExecution() ||
            warmupCheckBox.isSelected != RunAnywhereSettings.isWarmupAttach() ||
            timeoutField.text.trim() != RunAnywhereSettings.getExecTimeoutSeconds().toString() ||
            expandField.text.trim() != RunAnywhereSettings.getResultExpandLevel().toString() ||
            selectedJsonLibrary() != RunAnywhereSettings.getJsonLibrary()
    }

    fun apply() {
        val timeoutSeconds = timeoutField.text.trim().toIntOrNull()
            ?.coerceIn(RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS, RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS)
            ?: RunAnywhereSettings.DEFAULT_EXEC_TIMEOUT_SECONDS
        val expandLevel = expandField.text.trim().toIntOrNull()
            ?.coerceIn(RunAnywhereSettings.MIN_RESULT_EXPAND_LEVEL, RunAnywhereSettings.MAX_RESULT_EXPAND_LEVEL)
            ?: RunAnywhereSettings.DEFAULT_RESULT_EXPAND_LEVEL
        RunAnywhereSettings.setExecTimeoutSeconds(timeoutSeconds)
        RunAnywhereSettings.setResultExpandLevel(expandLevel)
        RunAnywhereSettings.setAsyncExecution(asyncCheckBox.isSelected)
        RunAnywhereSettings.setWarmupAttach(warmupCheckBox.isSelected)
        RunAnywhereSettings.setJsonLibrary(selectedJsonLibrary())
    }

    fun reset() {
        asyncCheckBox.isSelected = RunAnywhereSettings.isAsyncExecution()
        warmupCheckBox.isSelected = RunAnywhereSettings.isWarmupAttach()
        timeoutField.text = RunAnywhereSettings.getExecTimeoutSeconds().toString()
        expandField.text = RunAnywhereSettings.getResultExpandLevel().toString()
        selectJsonLibrary(RunAnywhereSettings.getJsonLibrary())
    }

    private fun selectedJsonLibrary(): JsonLibrary {
        val displayName = jsonLibraryCombo.selectedItem as? String
        return JsonLibrary.entries.find { it.displayName == displayName } ?: JsonLibrary.AUTO
    }

    private fun selectJsonLibrary(library: JsonLibrary) {
        jsonLibraryCombo.selectedItem = library.displayName
    }

}

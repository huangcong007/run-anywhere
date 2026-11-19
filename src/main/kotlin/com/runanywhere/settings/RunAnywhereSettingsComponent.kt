package com.runanywhere.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings → Other Settings → Run Anywhere 配置面板。
 *
 * 按平台规范（Validation Errors / Plugin UX）：数值输入改用 [JSpinner] 并设上下界，
 * 避免用户输入非法值；Apply 时直接取 spinner 当前值，不再静默夹紧。
 */
class RunAnywhereSettingsComponent {

    private val asyncCheckBox = JBCheckBox("异步执行（后台等待返回值，断点不超时）")
    private val warmupCheckBox = JBCheckBox("进程启动后预热 Attach")

    // 执行超时：受控 spinner（30–3600 秒），替代自由文本（P1 C1）
    private val timeoutSpinner: JSpinner = spinnerFor(
        RunAnywhereSettings.getExecTimeoutSeconds(),
        RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS,
        RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS,
    )
    private val timeoutHintLabel = JBLabel()

    // 结果展开层级：受控 spinner（0–10）（P1 C1）
    private val expandSpinner: JSpinner = spinnerFor(
        RunAnywhereSettings.getResultExpandLevel(),
        RunAnywhereSettings.MIN_RESULT_EXPAND_LEVEL,
        RunAnywhereSettings.MAX_RESULT_EXPAND_LEVEL,
    )
    private val expandHintLabel = JBLabel()

    private val jsonLibraryCombo = ComboBox(
        DefaultComboBoxModel(JsonLibrary.entries.map { it.displayName }.toTypedArray())
    )
    // 恢复首次安全提示按钮：用户一旦勾选"不再提示"后无法在 UI 撤销，此处提供入口（P1 C8）
    private val restoreSecurityButton = JButton("恢复首次安全提示").apply {
        toolTipText = "重新开启首次调用前的风险确认对话框"
        setBounds(0, 0, 200, 30)
        addActionListener {
            ApplicationManager.getApplication().invokeLater {
                RunAnywhereSettings.setSecurityConfirmed(false)
            }
        }
    }

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(asyncCheckBox)
        .addComponent(warmupCheckBox)
        .addLabeledComponent("执行超时（秒）", timeoutSpinner)
        .addComponent(timeoutHintLabel)
        .addLabeledComponent("结果展开层级", expandSpinner)
        .addComponent(expandHintLabel)
        .addLabeledComponent("对象参数 JSON 库", jsonLibraryCombo)
        .addComponent(
            JBLabel("无 Jackson 时可选手动指定 Gson / Fastjson；自动模式会按 classpath 依次尝试。")
        )
        .addComponent(restoreSecurityButton)
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

    /**
     * 检测修改状态（供 Configurable.isModified）。
     * 对比字段直接从 spinner/checkbox/combo 读取，不再依赖原始文本，避免误报。
     */
    fun isModified(): Boolean {
        return asyncCheckBox.isSelected != RunAnywhereSettings.isAsyncExecution() ||
            warmupCheckBox.isSelected != RunAnywhereSettings.isWarmupAttach() ||
            timeoutSpinner.value != RunAnywhereSettings.getExecTimeoutSeconds() ||
            expandSpinner.value != RunAnywhereSettings.getResultExpandLevel() ||
            selectedJsonLibrary() != RunAnywhereSettings.getJsonLibrary()
    }

    /**
     * 保存当前 UI 值到持久化存储。
     * 由于 spinner 已受边界限制，这里无需再做 clamp/coerceIn（P1 C1 修复）。
     */
    fun apply() {
        RunAnywhereSettings.setExecTimeoutSeconds(timeoutSpinner.value as Int)
        RunAnywhereSettings.setResultExpandLevel(expandSpinner.value as Int)
        RunAnywhereSettings.setAsyncExecution(asyncCheckBox.isSelected)
        RunAnywhereSettings.setWarmupAttach(warmupCheckBox.isSelected)
        RunAnywhereSettings.setJsonLibrary(selectedJsonLibrary())
    }

    fun reset() {
        asyncCheckBox.isSelected = RunAnywhereSettings.isAsyncExecution()
        warmupCheckBox.isSelected = RunAnywhereSettings.isWarmupAttach()
        timeoutSpinner.value = RunAnywhereSettings.getExecTimeoutSeconds()
        expandSpinner.value = RunAnywhereSettings.getResultExpandLevel()
        selectJsonLibrary(RunAnywhereSettings.getJsonLibrary())
    }

    private fun selectedJsonLibrary(): JsonLibrary {
        val displayName = jsonLibraryCombo.selectedItem.toString()
        return JsonLibrary.entries.find { it.displayName == displayName } ?: JsonLibrary.AUTO
    }

    private fun selectJsonLibrary(library: JsonLibrary) {
        jsonLibraryCombo.setSelectedItem(library.displayName)
    }

    private companion object {
        private fun spinnerFor(initial: Int, min: Int, max: Int): JSpinner {
            return JSpinner(SpinnerNumberModel(initial, min, max, 1)).also {
                (it.editor as? JSpinner.NumberEditor)?.textField?.isEditable = true
            }
        }
    }
}

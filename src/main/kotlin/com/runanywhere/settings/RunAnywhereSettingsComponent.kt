package com.runanywhere.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.runanywhere.i18n.I18n
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

    private val asyncCheckBox = JBCheckBox(I18n.SETTINGS_ASYNC())
    private val warmupCheckBox = JBCheckBox(I18n.SETTINGS_WARMUP())

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
        DefaultComboBoxModel(JsonLibrary.entries.map { it.displayName() }.toTypedArray())
    )
    // 恢复首次安全提示按钮：用户一旦勾选"不再提示"后无法在 UI 撤销，此处提供入口（P1 C8）
    private val restoreSecurityButton = JButton(I18n.SETTINGS_RESTORE_SECURITY()).apply {
        toolTipText = I18n.SETTINGS_RESTORE_SECURITY_TIP()
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
        .addLabeledComponent(I18n.SETTINGS_TIMEOUT_LABEL(), timeoutSpinner)
        .addComponent(timeoutHintLabel)
        .addLabeledComponent(I18n.SETTINGS_EXPAND_LABEL(), expandSpinner)
        .addComponent(expandHintLabel)
        .addLabeledComponent(I18n.SETTINGS_JSON_LIB_LABEL(), jsonLibraryCombo)
        .addComponent(
            JBLabel(I18n.SETTINGS_JSON_LIB_HINT())
        )
        .addComponent(restoreSecurityButton)
        .addComponentFillVertically(JPanel(), 0)
        .panel.apply {
            border = JBUI.Borders.empty(10)
        }

    init {
        asyncCheckBox.toolTipText = I18n.SETTINGS_ASYNC_TIP()
        warmupCheckBox.toolTipText = I18n.SETTINGS_WARMUP_TIP()
        timeoutHintLabel.text = I18n.SETTINGS_TIMEOUT_HINT(
            RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS,
            RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS
        )
        expandHintLabel.text = I18n.SETTINGS_EXPAND_HINT(
            RunAnywhereSettings.MIN_RESULT_EXPAND_LEVEL,
            RunAnywhereSettings.MAX_RESULT_EXPAND_LEVEL
        )
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
        return JsonLibrary.entries.find { it.displayName() == displayName } ?: JsonLibrary.AUTO
    }

    private fun selectJsonLibrary(library: JsonLibrary) {
        jsonLibraryCombo.setSelectedItem(library.displayName())
    }

    private companion object {
        private fun spinnerFor(initial: Int, min: Int, max: Int): JSpinner {
            return JSpinner(SpinnerNumberModel(initial, min, max, 1)).also {
                (it.editor as? JSpinner.NumberEditor)?.textField?.isEditable = true
            }
        }
    }
}

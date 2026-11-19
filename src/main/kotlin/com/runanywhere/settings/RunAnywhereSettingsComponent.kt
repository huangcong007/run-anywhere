package com.runanywhere.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.runanywhere.RunAnywhereLinks
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
 * 平台规范（Validation Errors / Plugin UX）要求：数值输入受控、越界时提示而非静默改值、
 * 设置项改动必须服从 Apply / Cancel 语义。
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

    private val jsonLibraryCombo = ComboBox(
        DefaultComboBoxModel(JsonLibrary.entries.map { it.displayName() }.toTypedArray())
    )

    /**
     * 待提交的"恢复首次安全提示"请求。
     *
     * F4：不再点击即写盘——那会让"点按钮 + 取消关闭设置页"仍然生效，违反 Cancel 语义。
     * 现在只在 [apply] 时落地，点击后的反馈由 Apply 按钮点亮 + [restoreHintLabel] 承担。
     */
    private var securityResetRequested = false

    private val restoreHintLabel = JBLabel(I18n.SETTINGS_RESTORE_PENDING()).apply {
        foreground = UIUtil.getContextHelpForeground()
        isVisible = false
    }

    private val restoreSecurityButton = JButton(I18n.SETTINGS_RESTORE_SECURITY()).apply {
        toolTipText = I18n.SETTINGS_RESTORE_SECURITY_TIP()
        addActionListener {
            securityResetRequested = true
            restoreHintLabel.isVisible = true
            panel.revalidate()
            panel.repaint()
        }
    }

    private val docsLink = ActionLink(I18n.SETTINGS_DOCS_LINK()) {
        BrowserUtil.browse(RunAnywhereLinks.DOCS_URL)
    }

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(asyncCheckBox)
        .addComponent(warmupCheckBox)
        .addLabeledComponent(I18n.SETTINGS_TIMEOUT_LABEL(), timeoutSpinner)
        .addComponent(timeoutHintLabel)
        .addLabeledComponent(I18n.SETTINGS_JSON_LIB_LABEL(), jsonLibraryCombo)
        .addComponent(JBLabel(I18n.SETTINGS_JSON_LIB_HINT()))
        .addComponent(restoreSecurityButton)
        .addComponent(restoreHintLabel)
        .addComponent(docsLink)
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
    }

    fun getPanel(): JComponent = panel

    /**
     * 检测修改状态（供 Configurable.isModified）。
     *
     * 读的是编辑框里的实际文本而不是 spinner 模型值：`SpinnerNumberModel` 会把越界值
     * clamp 回边界，只比模型值会漏掉"用户输入了非法值"这一态。
     */
    fun isModified(): Boolean {
        if (securityResetRequested) return true
        if (asyncCheckBox.isSelected != RunAnywhereSettings.isAsyncExecution()) return true
        if (warmupCheckBox.isSelected != RunAnywhereSettings.isWarmupAttach()) return true
        val timeout = readSpinnerText(timeoutSpinner) ?: return true
        if (timeout != RunAnywhereSettings.getExecTimeoutSeconds()) return true
        return selectedJsonLibrary() != RunAnywhereSettings.getJsonLibrary()
    }

    /**
     * P2 C2 / F5：校验当前输入，失败时由 Configurable 抛 `ConfigurationException` 就地提示。
     *
     * spinner 有上下界，但 NumberEditor 允许手工输入。此前直接读 `spinner.value` 判断，
     * 由于模型已 clamp，分支永远不可达；现在解析输入文本，越界与非数字都能报出来。
     *
     * @return 首个错误文案；全部合法时返回 null。
     */
    fun validate(): String? {
        val timeout = readSpinnerText(timeoutSpinner)
            ?: return I18n.SETTINGS_ERROR_NUMBER(I18n.SETTINGS_TIMEOUT_LABEL())
        if (timeout !in RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS..RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS) {
            return I18n.SETTINGS_ERROR_TIMEOUT(
                RunAnywhereSettings.MIN_EXEC_TIMEOUT_SECONDS,
                RunAnywhereSettings.MAX_EXEC_TIMEOUT_SECONDS,
                timeout,
            )
        }
        return null
    }

    /**
     * 保存当前 UI 值到持久化存储。
     * 数值取自校验通过后的输入文本，不做静默夹紧（P1 C1）。
     */
    fun apply() {
        readSpinnerText(timeoutSpinner)?.let { RunAnywhereSettings.setExecTimeoutSeconds(it) }
        RunAnywhereSettings.setAsyncExecution(asyncCheckBox.isSelected)
        RunAnywhereSettings.setWarmupAttach(warmupCheckBox.isSelected)
        RunAnywhereSettings.setJsonLibrary(selectedJsonLibrary())
        if (securityResetRequested) {
            RunAnywhereSettings.setSecurityConfirmed(false)
            securityResetRequested = false
            restoreHintLabel.isVisible = false
        }
    }

    fun reset() {
        securityResetRequested = false
        restoreHintLabel.isVisible = false
        asyncCheckBox.isSelected = RunAnywhereSettings.isAsyncExecution()
        warmupCheckBox.isSelected = RunAnywhereSettings.isWarmupAttach()
        timeoutSpinner.value = RunAnywhereSettings.getExecTimeoutSeconds()
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

        /** 读取 spinner 编辑框文本；为空时退回模型值，非数字返回 null。 */
        private fun readSpinnerText(spinner: JSpinner): Int? {
            val raw = (spinner.editor as? JSpinner.NumberEditor)
                ?.textField?.text
                ?.replace(",", "")
                ?.replace(" ", "")
                ?.trim()
                .orEmpty()
            if (raw.isEmpty()) return spinner.value as? Int
            return raw.toIntOrNull()
        }
    }
}

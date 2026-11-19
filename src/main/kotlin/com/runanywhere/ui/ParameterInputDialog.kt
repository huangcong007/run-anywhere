package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import com.runanywhere.i18n.I18n
import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * 方法参数输入对话框。
 */
class ParameterInputDialog(
    project: Project,
    private val parameters: List<MethodMetadata.ParamInfo>
) : DialogWrapper(project) {

    private val inputFields = mutableListOf<JComponent>()
    private val nullCheckBoxes = mutableListOf<JCheckBox>()
    private var ognlArgs: List<String> = emptyList()

    init {
        title = I18n.PARAM_DIALOG_TITLE()
        setOKButtonText(I18n.PARAM_OK_BUTTON())
        init()
    }

    override fun createCenterPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
        }

        if (parameters.isEmpty()) {
            gbc.gridx = 0; gbc.gridy = 0
            panel.add(JLabel(I18n.PARAM_EMPTY()), gbc)
            return panel
        }

        gbc.gridy = 0
        gbc.gridx = 0; panel.add(JLabel(I18n.PARAM_COLUMN_NAME()), gbc)
        gbc.gridx = 1; panel.add(JLabel(I18n.PARAM_COLUMN_TYPE()), gbc)
        gbc.gridx = 2; panel.add(JLabel(I18n.PARAM_COLUMN_VALUE()), gbc)
        gbc.gridx = 3; panel.add(JLabel(I18n.PARAM_COLUMN_NULL()), gbc)

        parameters.forEachIndexed { index, param ->
            gbc.gridy = index + 1

            gbc.gridx = 0
            panel.add(JLabel(param.name), gbc)

            gbc.gridx = 1
            val typeLabel = JLabel(formatTypeLabel(param)).apply {
                if (!OgnlLiteralConverter.isSupportedInputType(param)) {
                    toolTipText = I18n.PARAM_UNSUPPORTED_TYPE()
                    foreground = UIUtil.getLabelDisabledForeground()
                } else if (JsonInputValidator.requiresJsonInput(param.kind)) {
                    toolTipText = I18n.PARAM_JSON_HINT()
                }
            }
            panel.add(typeLabel, gbc)

            gbc.gridx = 2
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            val input = createInputComponent(param)
            // P1 C5 / F7：输入控件自身需要 accessible name，否则读屏只报"文本字段"
            applyAccessibleName(input, param)
            inputFields.add(input)
            panel.add(input, gbc)
            gbc.weightx = 0.0
            gbc.fill = GridBagConstraints.NONE

            gbc.gridx = 3
            val nullCb = JCheckBox().apply {
                // P1 C5：为屏幕阅读器提供 accessibleName
                accessibleContext.accessibleName = I18n.PARAM_NULL_ACCESSIBLE_NAME(param.name)
                addActionListener { setInputEnabled(input, !isSelected) }
            }
            nullCheckBoxes.add(nullCb)
            panel.add(nullCb, gbc)
        }

        return panel
    }

    private fun createInputComponent(param: MethodMetadata.ParamInfo): JComponent {
        val supported = OgnlLiteralConverter.isSupportedInputType(param)
        // F6：不支持的类型不再"只挂 tooltip"。禁用态的输入框内常显说明，
        // 让不悬停、不用鼠标的用户也能知道该参数会以 null 传入。
        val unsupportedHint = I18n.PARAM_UNSUPPORTED_INLINE()
        return if (JsonInputValidator.requiresJsonInput(param.kind)) {
            JScrollPane(
                JBTextArea(3, 24).apply {
                    lineWrap = true
                    wrapStyleWord = true
                    emptyText.text = if (supported) JsonInputValidator.expectedJsonHint(param.kind) else unsupportedHint
                    isEnabled = supported
                }
            ).apply {
                preferredSize = Dimension(280, 72)
            }
        } else {
            val placeholder = if (!supported) {
                unsupportedHint
            } else when {
                param.isEnum -> I18n.PARAM_PLACEHOLDER_ENUM()
                param.type == "long" || param.type == "java.lang.Long" -> I18n.PARAM_PLACEHOLDER_LONG()
                param.type.startsWith("java.time.") -> I18n.PARAM_PLACEHOLDER_TIME()
                else -> I18n.PARAM_PLACEHOLDER_DEFAULT()
            }
            JBTextField(20).apply {
                emptyText.text = placeholder
                isEnabled = supported
            }
        }
    }

    /**
     * 给输入控件设置 accessible name。
     * 不支持的参数额外把"将传 null"写进名称，读屏用户同样能获得该信息。
     */
    private fun applyAccessibleName(input: JComponent, param: MethodMetadata.ParamInfo) {
        val base = I18n.PARAM_INPUT_ACCESSIBLE_NAME(param.name, formatTypeLabel(param))
        val name = if (OgnlLiteralConverter.isSupportedInputType(param)) {
            base
        } else {
            "$base ${I18n.PARAM_UNSUPPORTED_INLINE()}"
        }
        val target: JComponent = when (input) {
            is JScrollPane -> input.viewport.view as? JComponent ?: input
            else -> input
        }
        target.accessibleContext.accessibleName = name
    }

    private fun formatTypeLabel(param: MethodMetadata.ParamInfo): String {
        param.typeDesc?.takeIf { it.args.isNotEmpty() || it.arrayDimensions > 0 }?.let { return it.display() }
        return when (param.kind) {
            ParamTypeKind.COLLECTION -> "${param.type}<${param.elementType ?: "?"}>"
            ParamTypeKind.MAP -> "${param.type}<${param.mapKeyType ?: "?"}, ${param.mapValueType ?: "?"}>"
            else -> param.type
        }
    }

    private fun setInputEnabled(component: JComponent, enabled: Boolean) {
        when (component) {
            is JBTextField -> component.isEnabled = enabled
            is JScrollPane -> component.viewport.view?.isEnabled = enabled
        }
    }

    private fun readInputText(component: JComponent): String = when (component) {
        is JBTextField -> component.text
        is JScrollPane -> (component.viewport.view as? JBTextArea)?.text.orEmpty()
        else -> ""
    }

    /**
     * 逐项校验输入；返回 [ValidationInfo] 后由平台就地提示并阻止关闭，
     * 不使用模态消息框（平台规范：Validation Errors / Informing Users）。
     */
    private fun validateInputs(): ValidationInfo? {
        for (i in parameters.indices) {
            val param = parameters[i]
            if (nullCheckBoxes[i].isSelected) continue
            val raw = readInputText(inputFields[i])
            if (JsonInputValidator.requiresJsonInput(param.kind) && raw.isNotBlank() &&
                !JsonInputValidator.isValidJson(raw, param.kind)
            ) {
                return ValidationInfo(
                    JsonInputValidator.validationErrorMessage(param.name, param.kind),
                    inputFields[i]
                )
            }
            val literalError = OgnlLiteralConverter.validate(raw, param)
            if (literalError != null) {
                return ValidationInfo(literalError, inputFields[i])
            }
        }
        return null
    }

    override fun doValidate(): ValidationInfo? = validateInputs()

    override fun doOKAction() {
        val error = validateInputs()
        if (error != null) {
            setErrorText(error.message, error.component)
            return
        }

        ognlArgs = parameters.indices.map { i ->
            val param = parameters[i]
            if (nullCheckBoxes[i].isSelected) {
                "null"
            } else {
                OgnlLiteralConverter.toOgnlLiteral(readInputText(inputFields[i]), param)
            }
        }
        super.doOKAction()
    }

    fun getOgnlArgs(): List<String> = ognlArgs
}

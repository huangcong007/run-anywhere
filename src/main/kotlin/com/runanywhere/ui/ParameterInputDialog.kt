package com.runanywhere.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
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
        title = "输入方法参数"
        setOKButtonText("调用")
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
            panel.add(JLabel("该方法无参数。"), gbc)
            return panel
        }

        gbc.gridy = 0
        gbc.gridx = 0; panel.add(JLabel("参数名"), gbc)
        gbc.gridx = 1; panel.add(JLabel("类型"), gbc)
        gbc.gridx = 2; panel.add(JLabel("值"), gbc)
        gbc.gridx = 3; panel.add(JLabel("传 null"), gbc)

        parameters.forEachIndexed { index, param ->
            gbc.gridy = index + 1

            gbc.gridx = 0
            panel.add(JLabel(param.name), gbc)

            gbc.gridx = 1
            val typeLabel = JLabel(formatTypeLabel(param)).apply {
                if (!OgnlLiteralConverter.isSupportedInputType(param)) {
                    toolTipText = "不支持的类型，将传 null"
                    foreground = JBColor.GRAY
                } else if (JsonInputValidator.requiresJsonInput(param.kind)) {
                    toolTipText = "通过 JSON 输入；反序列化库可在 Settings → Other Settings → Run Anywhere 配置"
                }
            }
            panel.add(typeLabel, gbc)

            gbc.gridx = 2
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            val input = createInputComponent(param)
            inputFields.add(input)
            panel.add(input, gbc)
            gbc.weightx = 0.0
            gbc.fill = GridBagConstraints.NONE

            gbc.gridx = 3
            val nullCb = JCheckBox().apply {
                addActionListener { setInputEnabled(input, !isSelected) }
            }
            nullCheckBoxes.add(nullCb)
            panel.add(nullCb, gbc)
        }

        return panel
    }

    private fun createInputComponent(param: MethodMetadata.ParamInfo): JComponent {
        val supported = OgnlLiteralConverter.isSupportedInputType(param)
        return if (JsonInputValidator.requiresJsonInput(param.kind)) {
            JScrollPane(
                JBTextArea(3, 24).apply {
                    lineWrap = true
                    wrapStyleWord = true
                    emptyText.text = JsonInputValidator.expectedJsonHint(param.kind)
                    isEnabled = supported
                }
            ).apply {
                preferredSize = Dimension(280, 72)
            }
        } else {
            val placeholder = when {
                param.isEnum -> "枚举常量名，如 ACTIVE"
                param.type == "long" || param.type == "java.lang.Long" -> "数字，如 123 或 123L"
                param.type.startsWith("java.time.") -> "ISO-8601，如 2026-10-08T15:04:05"
                else -> "输入值，留空表示 null"
            }
            JBTextField(20).apply {
                emptyText.text = placeholder
                isEnabled = supported
            }
        }
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

    override fun doOKAction() {
        for (i in parameters.indices) {
            val param = parameters[i]
            if (nullCheckBoxes[i].isSelected) continue
            val raw = readInputText(inputFields[i])
            if (JsonInputValidator.requiresJsonInput(param.kind) && raw.isNotBlank()) {
                if (!JsonInputValidator.isValidJson(raw, param.kind)) {
                    Messages.showErrorDialog(
                        contentPanel,
                        JsonInputValidator.validationErrorMessage(param.name, param.kind),
                        "JSON 格式错误"
                    )
                    return
                }
            }
            val literalError = OgnlLiteralConverter.validate(raw, param)
            if (literalError != null) {
                Messages.showErrorDialog(contentPanel, literalError, "参数格式错误")
                return
            }
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

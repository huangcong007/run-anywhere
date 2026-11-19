package com.runanywhere.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * 注册于 Settings → Other Settings → Run Anywhere。
 */
class RunAnywhereSettingsConfigurable : Configurable {

    private var component: RunAnywhereSettingsComponent? = null

    override fun getDisplayName(): String = "Run Anywhere"

    override fun createComponent(): JComponent {
        component = RunAnywhereSettingsComponent().also { it.reset() }
        return component!!.getPanel()
    }

    override fun isModified(): Boolean = component?.isModified() ?: false

    override fun apply() {
        component?.apply()
    }

    override fun reset() {
        component?.reset()
    }

    override fun disposeUIResources() {
        component = null
    }

    /** P2 C3：设置页提供 Help topic，方便用户查看文档。 */
    override fun getHelpTopic(): String? = "run-anywhere-settings"
}

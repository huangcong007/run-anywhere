package com.runanywhere.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.runanywhere.i18n.I18n
import javax.swing.JComponent

/**
 * 注册于 Settings → Other Settings → Run Anywhere。
 */
class RunAnywhereSettingsConfigurable : Configurable {

    private var component: RunAnywhereSettingsComponent? = null

    override fun getDisplayName(): String = I18n.SETTINGS_NAME()

    override fun createComponent(): JComponent {
        component = RunAnywhereSettingsComponent().also { it.reset() }
        return component!!.getPanel()
    }

    override fun isModified(): Boolean = component?.isModified() ?: false

    override fun apply() {
        val current = component ?: return
        // P2 C2：校验失败抛 ConfigurationException，由设置页就地红字提示，不静默改值
        current.validate()?.let { throw ConfigurationException(it) }
        current.apply()
    }

    override fun reset() {
        component?.reset()
    }

    override fun disposeUIResources() {
        component = null
    }

    /**
     * F2：不返回 help topic。
     *
     * 平台会把 topic 拼成 `https://www.jetbrains.com/help/idea/<topic>.html`，
     * 而本插件没有对应的官方帮助页，返回 topic 只会让 Help 按钮跳到 404。
     * 文档入口改在面板内的「查看使用说明」链接（指向项目 Wiki），返回 null 即隐藏平台 Help 按钮。
     */
    override fun getHelpTopic(): String? = null
}

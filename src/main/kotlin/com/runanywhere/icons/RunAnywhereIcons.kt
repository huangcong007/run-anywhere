package com.runanywhere.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Run Anywhere 插件图标。
 *
 * 平台规范要求工具窗口图标提供 16×16 和 20×20 两种尺寸（P2 A3）：
 * - [ToolWindow16] 用于底部 stripe 按钮（16px 场景）
 * - [ToolWindow20] 用于工具窗口标题栏（20px 场景）
 *
 * 彩色版本仅用于 Marketplace 列表图，工具窗口内使用灰阶。
 */
object RunAnywhereIcons {

    @JvmField
    val ToolWindow16: Icon = IconLoader.getIcon("/icons/runAnywhere_16.svg", RunAnywhereIcons::class.java)

    @JvmField
    val ToolWindow20: Icon = IconLoader.getIcon("/icons/runAnywhere_20.svg", RunAnywhereIcons::class.java)

    /** 向后兼容：原 ToolWindow 引用（16px 版本） */
    @JvmField
    val ToolWindow: Icon = ToolWindow16
}

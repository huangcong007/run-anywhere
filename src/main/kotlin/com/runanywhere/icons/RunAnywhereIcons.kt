package com.runanywhere.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Run Anywhere 插件图标。
 * 设计：蓝色圆圈 + 闪电，表示"任意位置精准快速执行"。
 * 区别于 IDE 自带的 Run（三角播放）图标。
 */
object RunAnywhereIcons {
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/runAnywhere.svg", RunAnywhereIcons::class.java)
}

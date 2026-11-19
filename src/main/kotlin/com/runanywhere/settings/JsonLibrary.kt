package com.runanywhere.settings

import com.runanywhere.i18n.I18n

/**
 * 对象参数 JSON 反序列化所使用的库。
 *
 * [displayName] 用函数而非枚举常量属性，保证文案在 UI 打开时才从 Bundle 取，
 * 便于后续切换语言。
 */
enum class JsonLibrary {
    /** 按 Jackson → Gson → Fastjson2 → Fastjson 顺序在目标 JVM 中检测 */
    AUTO,
    JACKSON,
    GSON,
    FASTJSON2,
    FASTJSON;

    fun displayName(): String = when (this) {
        AUTO -> I18n.SETTINGS_JSON_LIB_AUTO()
        JACKSON -> "Jackson"
        GSON -> "Gson"
        FASTJSON2 -> "Fastjson2"
        FASTJSON -> "Fastjson 1.x"
    }
}

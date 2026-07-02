package com.runanywhere.settings

/**
 * 对象参数 JSON 反序列化所使用的库。
 */
enum class JsonLibrary(val displayName: String) {
    /** 按 Jackson → Gson → Fastjson2 → Fastjson 顺序在目标 JVM 中检测 */
    AUTO("自动检测（Jackson → Gson → Fastjson2 → Fastjson）"),
    JACKSON("Jackson"),
    GSON("Gson"),
    FASTJSON2("Fastjson2"),
    FASTJSON("Fastjson 1.x")
}

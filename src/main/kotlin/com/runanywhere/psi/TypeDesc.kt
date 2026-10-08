package com.runanywhere.psi

/**
 * 保留泛型实参和数组维度的类型描述。
 * 扁平的 [raw] 不够表达 `List<List<User>>` 这类嵌套结构。
 */
data class TypeDesc(
    val raw: String,
    val args: List<TypeDesc> = emptyList(),
    val arrayDimensions: Int = 0
) {
    fun isNested(): Boolean {
        if (arrayDimensions > 0 && args.isNotEmpty()) return true
        return args.any { it.args.isNotEmpty() || it.arrayDimensions > 0 || it.isNested() }
    }

    fun display(): String {
        val generic = if (args.isEmpty()) raw else raw + "<" + args.joinToString(", ") { it.display() } + ">"
        return generic + "[]".repeat(arrayDimensions)
    }
}

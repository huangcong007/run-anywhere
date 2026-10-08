package com.runanywhere.ui

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import com.runanywhere.settings.RunAnywhereSettings

/**
 * 将用户输入的原始字符串转换为 OGNL 字面量。
 */
object OgnlLiteralConverter {

    private val NUMERIC_TYPES = setOf(
        "byte", "short", "int", "float", "double",
        "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
        "java.lang.Float", "java.lang.Double"
    )

    fun toOgnlLiteral(raw: String, param: MethodMetadata.ParamInfo): String =
        toOgnlLiteral(raw, param.type, param.isPrimitive, param.isEnum, param)

    fun toOgnlLiteral(
        raw: String,
        type: String,
        isPrimitive: Boolean,
        isEnum: Boolean = false,
        param: MethodMetadata.ParamInfo? = null
    ): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "null"

        val kind = param?.kind ?: when {
            isEnum -> ParamTypeKind.ENUM
            else -> ParamTypeKind.SIMPLE
        }

        if (JsonInputValidator.requiresJsonInput(kind)) {
            return JsonDeserializeOgnlBuilder.build(
                trimmed,
                param ?: return "null",
                RunAnywhereSettings.getJsonLibrary()
            )
        }

        return when {
            isEnum -> "@$type@$trimmed"
            type == "long" || type == "java.lang.Long" ->
                if (trimmed.endsWith("L", ignoreCase = true)) trimmed else "${trimmed}L"
            isPrimitive && type != "boolean" && type != "char" -> trimmed
            type == "java.lang.String" || type == "String" -> ognlStringExpr(trimmed)
            type == "boolean" || type == "java.lang.Boolean" -> trimmed
            type in NUMERIC_TYPES -> trimmed
            type == "char" || type == "java.lang.Character" -> charLiteral(trimmed)
            type == "java.math.BigDecimal" ->
                "new java.math.BigDecimal(\"${escapeString(trimmed)}\")"
            type == "java.math.BigInteger" ->
                "new java.math.BigInteger(\"${escapeString(trimmed)}\")"
            else -> "null"
        }
    }

    fun isSupportedInputType(param: MethodMetadata.ParamInfo): Boolean =
        isSupportedInputType(param.type, param.isPrimitive, param.isEnum, param.kind)

    fun isSupportedInputType(
        type: String,
        isPrimitive: Boolean,
        isEnum: Boolean = false,
        kind: ParamTypeKind = ParamTypeKind.SIMPLE
    ): Boolean {
        if (JsonInputValidator.requiresJsonInput(kind)) return true
        if (isPrimitive || isEnum) return true
        return when (type) {
            "java.lang.String", "String",
            "java.lang.Boolean", "Boolean",
            "java.lang.Byte", "Byte",
            "java.lang.Short", "Short",
            "java.lang.Integer", "Integer",
            "java.lang.Long", "Long",
            "java.lang.Float", "Float",
            "java.lang.Double", "Double",
            "java.lang.Character", "Character",
            "java.math.BigDecimal",
            "java.math.BigInteger" -> true
            else -> false
        }
    }

    /**
     * 生成不含裸单引号的 OGNL 字符串表达式。
     * Arthas 命令外层使用单引号，字符串里的 `'` 会把命令截断，因此拆成拼接。
     */
    internal fun ognlStringExpr(value: String): String {
        if ('\'' !in value) {
            return "\"" + escapeForOgnlDoubleQuotedString(value) + "\""
        }
        val apostrophe = "new java.lang.String(@java.lang.Character@toChars(39))"
        val pieces = ArrayList<String>()
        value.split('\'').forEachIndexed { index, part ->
            if (index > 0) pieces.add(apostrophe)
            if (part.isNotEmpty()) {
                pieces.add("\"" + escapeForOgnlDoubleQuotedString(part) + "\"")
            }
        }
        return pieces.joinToString(" + ").ifEmpty { apostrophe }
    }

    internal fun escapeForOgnlDoubleQuotedString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * @return 不合法时的提示；空输入和 JSON 参数返回 null（JSON 另行校验，空输入表示 null）
     */
    fun validate(raw: String, param: MethodMetadata.ParamInfo): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || JsonInputValidator.requiresJsonInput(param.kind)) return null
        if (!isSupportedInputType(param)) return null
        if (param.isEnum) {
            return if (trimmed.matches(ENUM_NAME)) null
            else "参数 ${param.name} 需要枚举常量名，例如 ACTIVE。"
        }
        val type = param.type
        return when {
            type == "boolean" || type == "java.lang.Boolean" ->
                if (trimmed == "true" || trimmed == "false") null
                else "参数 ${param.name} 需要 true 或 false。"
            type == "char" || type == "java.lang.Character" ->
                if (trimmed.length == 1) null
                else "参数 ${param.name} 需要一个字符。"
            type == "long" || type == "java.lang.Long" ->
                if (trimmed.matches(LONG_LITERAL)) null
                else "参数 ${param.name} 需要整数，例如 123 或 123L。"
            type == "float" || type == "double" ||
                type == "java.lang.Float" || type == "java.lang.Double" ->
                if (trimmed.matches(DECIMAL_LITERAL)) null
                else "参数 ${param.name} 需要数字，例如 1.5。"
            type == "java.math.BigDecimal" ->
                if (trimmed.matches(BIG_DECIMAL_LITERAL)) null
                else "参数 ${param.name} 需要数字，例如 99.99。"
            type == "java.math.BigInteger" ->
                if (trimmed.matches(INTEGER_LITERAL)) null
                else "参数 ${param.name} 需要整数，例如 99。"
            param.isPrimitive || type in NUMERIC_TYPES ->
                if (trimmed.matches(INTEGER_LITERAL)) null
                else "参数 ${param.name} 需要整数，例如 123。"
            else -> null
        }
    }

    private fun charLiteral(trimmed: String): String {
        if (trimmed.length != 1) return "null"
        val ch = trimmed[0]
        return if (ch == '\'') "@java.lang.Character@toChars(39)[0]" else "'$ch'"
    }

    private fun escapeString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private val ENUM_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val INTEGER_LITERAL = Regex("^-?\\d+$")
    private val LONG_LITERAL = Regex("^-?\\d+[lL]?$")
    private val DECIMAL_LITERAL = Regex("^-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[fFdD]?$")
    private val BIG_DECIMAL_LITERAL = Regex("^-?\\d+(\\.\\d+)?$")
}

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
            type == "java.lang.String" || type == "String" ->
                "\"" + escapeString(trimmed) + "\""
            type == "boolean" || type == "java.lang.Boolean" -> trimmed
            type in NUMERIC_TYPES -> trimmed
            type == "char" || type == "java.lang.Character" ->
                if (trimmed.length == 1) "'$trimmed'" else "'${escapeChar(trimmed)}'"
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

    internal fun escapeForOgnlDoubleQuotedString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun escapeString(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun escapeChar(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'")
}

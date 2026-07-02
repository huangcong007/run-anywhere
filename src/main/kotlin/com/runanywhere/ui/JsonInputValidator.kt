package com.runanywhere.ui

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.runanywhere.psi.ParamTypeKind

/**
 * 校验用户输入的 JSON 文本是否可用于对象/集合参数。
 */
object JsonInputValidator {

    fun requiresJsonInput(kind: ParamTypeKind): Boolean =
        kind in JSON_INPUT_KINDS

    fun isValidJson(raw: String, kind: ParamTypeKind): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return true
        return try {
            val element = JsonParser.parseString(trimmed)
            when (kind) {
                ParamTypeKind.OBJECT -> element.isJsonObject
                ParamTypeKind.ARRAY, ParamTypeKind.COLLECTION -> element.isJsonArray
                ParamTypeKind.MAP -> element.isJsonObject
                else -> true
            }
        } catch (_: JsonSyntaxException) {
            false
        }
    }

    fun expectedJsonHint(kind: ParamTypeKind): String = when (kind) {
        ParamTypeKind.OBJECT -> "JSON 对象，如 {\"id\":1,\"name\":\"test\"}"
        ParamTypeKind.ARRAY, ParamTypeKind.COLLECTION -> "JSON 数组，如 [{\"id\":1}] 或 [1,2,3]"
        ParamTypeKind.MAP -> "JSON 对象，如 {\"key1\":{\"id\":1}}"
        else -> ""
    }

    fun validationErrorMessage(paramName: String, kind: ParamTypeKind): String = when (kind) {
        ParamTypeKind.OBJECT -> "参数 $paramName 需要有效的 JSON 对象（以 { 开头）。"
        ParamTypeKind.ARRAY, ParamTypeKind.COLLECTION -> "参数 $paramName 需要有效的 JSON 数组（以 [ 开头）。"
        ParamTypeKind.MAP -> "参数 $paramName 需要有效的 JSON 对象（以 { 开头）。"
        else -> "参数 $paramName 的 JSON 格式无效。"
    }

    private val JSON_INPUT_KINDS = setOf(
        ParamTypeKind.OBJECT,
        ParamTypeKind.ARRAY,
        ParamTypeKind.COLLECTION,
        ParamTypeKind.MAP
    )
}

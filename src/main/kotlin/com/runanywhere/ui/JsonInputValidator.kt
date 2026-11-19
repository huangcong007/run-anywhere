package com.runanywhere.ui

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.runanywhere.i18n.I18n
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
        ParamTypeKind.OBJECT -> I18n.JSON_HINT_OBJECT()
        ParamTypeKind.ARRAY, ParamTypeKind.COLLECTION -> I18n.JSON_HINT_ARRAY()
        ParamTypeKind.MAP -> I18n.JSON_HINT_MAP()
        else -> ""
    }

    fun validationErrorMessage(paramName: String, kind: ParamTypeKind): String = when (kind) {
        ParamTypeKind.OBJECT -> I18n.JSON_ERROR_OBJECT(paramName)
        ParamTypeKind.ARRAY, ParamTypeKind.COLLECTION -> I18n.JSON_ERROR_ARRAY(paramName)
        ParamTypeKind.MAP -> I18n.JSON_ERROR_MAP(paramName)
        else -> I18n.JSON_ERROR_GENERIC(paramName)
    }

    private val JSON_INPUT_KINDS = setOf(
        ParamTypeKind.OBJECT,
        ParamTypeKind.ARRAY,
        ParamTypeKind.COLLECTION,
        ParamTypeKind.MAP
    )
}

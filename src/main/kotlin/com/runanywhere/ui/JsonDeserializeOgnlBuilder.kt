package com.runanywhere.ui

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import com.runanywhere.settings.JsonLibrary

/**
 * 为对象/集合参数生成 OGNL 反序列化表达式，支持多种 JSON 库。
 */
object JsonDeserializeOgnlBuilder {

    private const val JACKSON_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper"
    private const val GSON = "com.google.gson.Gson"
    private const val FASTJSON2 = "com.alibaba.fastjson2.JSON"
    private const val FASTJSON = "com.alibaba.fastjson.JSON"

    fun build(json: String, param: MethodMetadata.ParamInfo, library: JsonLibrary): String {
        val escapedJson = OgnlLiteralConverter.escapeForOgnlDoubleQuotedString(json)
        return when (library) {
            JsonLibrary.AUTO -> buildAutoFallback(escapedJson, param)
            JsonLibrary.JACKSON -> buildJackson(escapedJson, param)
            JsonLibrary.GSON -> buildGson(escapedJson, param)
            JsonLibrary.FASTJSON2 -> buildFastjson2(escapedJson, param)
            JsonLibrary.FASTJSON -> buildFastjson1(escapedJson, param)
        }
    }

    private fun buildAutoFallback(escapedJson: String, param: MethodMetadata.ParamInfo): String {
        val loader = "#_raLoader=@java.lang.Thread@currentThread().getContextClassLoader()"
        val hasClass = { className: String ->
            "@java.lang.Class@forName(\"$className\",true,#_raLoader)!=null"
        }
        val branches = listOf(
            hasClass(JACKSON_MAPPER) to buildJackson(escapedJson, param, mapperVar = "_raMapper"),
            hasClass(GSON) to buildGson(escapedJson, param, gsonVar = "_raGson"),
            hasClass(FASTJSON2) to buildFastjson2(escapedJson, param),
            hasClass(FASTJSON) to buildFastjson1(escapedJson, param)
        )
        val chain = branches.fold("null") { acc, (condition, expression) ->
            "($condition?$expression:$acc)"
        }
        return "($loader,$chain)"
    }

    private fun buildJackson(
        escapedJson: String,
        param: MethodMetadata.ParamInfo,
        mapperVar: String = "_raMapper"
    ): String {
        val readValue = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "#$mapperVar.readValue(\"$escapedJson\",@${param.type}@class)"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "#$mapperVar.readValue(\"$escapedJson\"," +
                    "#$mapperVar.getTypeFactory().constructArrayType(@$componentType@class))"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "#$mapperVar.readValue(\"$escapedJson\"," +
                    "#$mapperVar.getTypeFactory().constructCollectionType(@${param.type}@class,@$elementType@class))"
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                "#$mapperVar.readValue(\"$escapedJson\"," +
                    "#$mapperVar.getTypeFactory().constructMapType(@${param.type}@class,@$keyType@class,@$valueType@class))"
            }
            else -> return "null"
        }
        return "(#$mapperVar=new $JACKSON_MAPPER(),$readValue)"
    }

    private fun buildGson(
        escapedJson: String,
        param: MethodMetadata.ParamInfo,
        gsonVar: String = "_raGson"
    ): String {
        val typeExpr = when (param.kind) {
            ParamTypeKind.OBJECT -> "@${param.type}@class"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "@$componentType[]@class"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "@com.google.gson.reflect.TypeToken@getParameterized(@${param.type}@class,@$elementType@class).getType()"
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                "@com.google.gson.reflect.TypeToken@getParameterized(@${param.type}@class,@$keyType@class,@$valueType@class).getType()"
            }
            else -> return "null"
        }
        return "(#$gsonVar=new $GSON(),#$gsonVar.fromJson(\"$escapedJson\",$typeExpr))"
    }

    private fun buildFastjson2(escapedJson: String, param: MethodMetadata.ParamInfo): String {
        val typeExpr = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "@$FASTJSON2@parseObject(\"$escapedJson\",@${param.type}@class)"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "@$FASTJSON2@parseObject(\"$escapedJson\",@$componentType[]@class)"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "@$FASTJSON2@parseObject(\"$escapedJson\"," +
                    "@com.alibaba.fastjson2.TypeReference@collectionType(@${param.type}@class,@$elementType@class))"
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                "@$FASTJSON2@parseObject(\"$escapedJson\"," +
                    "@com.alibaba.fastjson2.TypeReference@mapType(@${param.type}@class,@$keyType@class,@$valueType@class))"
            }
            else -> return "null"
        }
        return "($typeExpr)"
    }

    private fun buildFastjson1(escapedJson: String, param: MethodMetadata.ParamInfo): String {
        val expr = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "@$FASTJSON@parseObject(\"$escapedJson\",@${param.type}@class)"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "@$FASTJSON@parseArray(\"$escapedJson\",@$componentType@class).toArray(@" +
                    "$componentType[]@class)"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "@$FASTJSON@parseArray(\"$escapedJson\",@$elementType@class)"
            }
            ParamTypeKind.MAP ->
                "@$FASTJSON@parseObject(\"$escapedJson\",@java.util.Map@class)"
            else -> return "null"
        }
        return "($expr)"
    }
}

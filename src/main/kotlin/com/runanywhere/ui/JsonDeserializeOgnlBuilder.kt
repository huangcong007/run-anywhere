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

    // fastjson2 的 JSON 是抽象类，OGNL 的 @Class@method 语法无法在抽象类上调用静态方法
    // （报 "No method accessor for interface ..."），改用具体子类：
    // - parseObject → JSONObject；数组同样走 parseObject + 数组 Class
    //   （parseArray 运行时返回 ArrayList，其上没有 toArray(Class) 重载可用）
    private const val FASTJSON2_OBJECT = "com.alibaba.fastjson2.JSONObject"
    private const val FASTJSON2_TYPE_IMPL = "com.alibaba.fastjson2.util.ParameterizedTypeImpl"
    private const val FASTJSON2_NO_FEATURES =
        "@java.lang.reflect.Array@newInstance(@com.alibaba.fastjson2.JSONReader\$Feature@class,0)"

    // fastjson 1.x 的 JSON 是具体类，带 Type 的重载可以按声明的 Map/Set 反序列化。
    // 仅 Class 的重载仍走 JSONObject，和已验证可用的调用方式保持一致。
    private const val FASTJSON = "com.alibaba.fastjson.JSONObject"
    private const val FASTJSON1_JSON = "com.alibaba.fastjson.JSON"
    private const val FASTJSON1_TYPE_IMPL = "com.alibaba.fastjson.util.ParameterizedTypeImpl"
    private const val FASTJSON1_NO_FEATURES =
        "@java.lang.reflect.Array@newInstance(@com.alibaba.fastjson.parser.Feature@class,0)"

    // OGNL 的 `@X[]@class` 不是合法语法（解析期 ParseException: Encountered "["），
    // 数组 Class 只能用反射构造出来再取类型；基本类型与多维数组同样适用。
    // PSI 给出的类型名可能自带 [] 后缀（多维数组的元素类型、List<User[]>、Map 的 value），
    // 因此所有"类型名 → Class 表达式"都要经过这里。
    private fun classExpr(typeName: String, extraDimensions: Int = 0): String {
        var base = typeName
        var dimensions = extraDimensions
        while (base.endsWith("[]")) {
            base = base.removeSuffix("[]")
            dimensions++
        }
        var expr = "@$base@class"
        repeat(dimensions) {
            expr = "@java.lang.reflect.Array@newInstance($expr,0).getClass()"
        }
        return expr
    }

    private fun arrayClassExpr(componentType: String): String = classExpr(componentType, extraDimensions = 1)

    private fun parameterizedType(implClass: String, rawClassExpr: String, vararg argExprs: String): String {
        val args = argExprs.joinToString(",") { "($it)" }
        return "new $implClass(new java.lang.reflect.Type[]{$args},null,($rawClassExpr))"
    }

    fun build(json: String, param: MethodMetadata.ParamInfo, library: JsonLibrary): String {
        val jsonExpr = OgnlLiteralConverter.ognlStringExpr(json)
        return when (library) {
            JsonLibrary.AUTO -> buildAutoFallback(jsonExpr, param)
            JsonLibrary.JACKSON -> buildJackson(jsonExpr, param)
            JsonLibrary.GSON -> buildGson(jsonExpr, param)
            JsonLibrary.FASTJSON2 -> buildFastjson2(jsonExpr, param)
            JsonLibrary.FASTJSON -> buildFastjson1(jsonExpr, param)
        }
    }

    private fun buildAutoFallback(jsonExpr: String, param: MethodMetadata.ParamInfo): String {
        val loader = "#_raLoader=@java.lang.Thread@currentThread().getContextClassLoader()"
        val hasClass = { className: String ->
            "@java.lang.Class@forName(\"$className\",true,#_raLoader)!=null"
        }
        val branches = listOf(
            hasClass(JACKSON_MAPPER) to buildJackson(jsonExpr, param, mapperVar = "_raMapper"),
            hasClass(GSON) to buildGson(jsonExpr, param, gsonVar = "_raGson"),
            hasClass(FASTJSON2_OBJECT) to buildFastjson2(jsonExpr, param),
            hasClass(FASTJSON) to buildFastjson1(jsonExpr, param)
        )
        val chain = branches.fold("null") { acc, (condition, expression) ->
            "($condition?$expression:$acc)"
        }
        return "($loader,$chain)"
    }

    private fun buildJackson(
        jsonExpr: String,
        param: MethodMetadata.ParamInfo,
        mapperVar: String = "_raMapper"
    ): String {
        val readValue = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "#$mapperVar.readValue($jsonExpr,${classExpr(param.type)})"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "#$mapperVar.readValue($jsonExpr," +
                    "#$mapperVar.getTypeFactory().constructArrayType(${classExpr(componentType)}))"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "#$mapperVar.readValue($jsonExpr," +
                    "#$mapperVar.getTypeFactory().constructCollectionType(${classExpr(param.type)},${classExpr(elementType)}))"
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                "#$mapperVar.readValue($jsonExpr," +
                    "#$mapperVar.getTypeFactory().constructMapType(${classExpr(param.type)},${classExpr(keyType)},${classExpr(valueType)}))"
            }
            else -> return "null"
        }
        return "(#$mapperVar=new $JACKSON_MAPPER(),$readValue)"
    }

    private fun buildGson(
        jsonExpr: String,
        param: MethodMetadata.ParamInfo,
        gsonVar: String = "_raGson"
    ): String {
        val typeExpr = when (param.kind) {
            ParamTypeKind.OBJECT -> classExpr(param.type)
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                arrayClassExpr(componentType)
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                "@com.google.gson.reflect.TypeToken@getParameterized(${classExpr(param.type)},${classExpr(elementType)}).getType()"
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                "@com.google.gson.reflect.TypeToken@getParameterized(${classExpr(param.type)},${classExpr(keyType)},${classExpr(valueType)}).getType()"
            }
            else -> return "null"
        }
        return "(#$gsonVar=new $GSON(),#$gsonVar.fromJson($jsonExpr,$typeExpr))"
    }

    private fun buildFastjson2(jsonExpr: String, param: MethodMetadata.ParamInfo): String {
        val typeExpr = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "@$FASTJSON2_OBJECT@parseObject($jsonExpr,${classExpr(param.type)})"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "@$FASTJSON2_OBJECT@parseObject($jsonExpr,${arrayClassExpr(componentType)})"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                fastjson2TypedParse(
                    jsonExpr,
                    parameterizedType(FASTJSON2_TYPE_IMPL, classExpr(param.type), classExpr(elementType))
                )
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                fastjson2TypedParse(
                    jsonExpr,
                    parameterizedType(
                        FASTJSON2_TYPE_IMPL,
                        classExpr(param.type),
                        classExpr(keyType),
                        classExpr(valueType)
                    )
                )
            }
            else -> return "null"
        }
        return "($typeExpr)"
    }

    private fun buildFastjson1(jsonExpr: String, param: MethodMetadata.ParamInfo): String {
        val expr = when (param.kind) {
            ParamTypeKind.OBJECT ->
                "@$FASTJSON@parseObject($jsonExpr,${classExpr(param.type)})"
            ParamTypeKind.ARRAY -> {
                val componentType = param.elementType ?: param.type.removeSuffix("[]")
                "@$FASTJSON@parseObject($jsonExpr,${arrayClassExpr(componentType)})"
            }
            ParamTypeKind.COLLECTION -> {
                val elementType = param.elementType ?: "java.lang.Object"
                fastjson1TypedParse(
                    jsonExpr,
                    parameterizedType(FASTJSON1_TYPE_IMPL, classExpr(param.type), classExpr(elementType))
                )
            }
            ParamTypeKind.MAP -> {
                val keyType = param.mapKeyType ?: "java.lang.String"
                val valueType = param.mapValueType ?: "java.lang.Object"
                fastjson1TypedParse(
                    jsonExpr,
                    parameterizedType(
                        FASTJSON1_TYPE_IMPL,
                        classExpr(param.type),
                        classExpr(keyType),
                        classExpr(valueType)
                    )
                )
            }
            else -> return "null"
        }
        return "($expr)"
    }

    private fun fastjson2TypedParse(jsonExpr: String, typeExpr: String): String =
        "@$FASTJSON2_OBJECT@parseObject($jsonExpr,$typeExpr,$FASTJSON2_NO_FEATURES)"

    private fun fastjson1TypedParse(jsonExpr: String, typeExpr: String): String =
        "@$FASTJSON1_JSON@parseObject($jsonExpr,$typeExpr,$FASTJSON1_NO_FEATURES)"
}

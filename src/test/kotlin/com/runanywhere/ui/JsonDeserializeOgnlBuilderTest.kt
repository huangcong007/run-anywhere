package com.runanywhere.ui

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import com.runanywhere.settings.JsonLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonDeserializeOgnlBuilderTest {

    private val userParam = MethodMetadata.ParamInfo(
        name = "user",
        type = "com.example.User",
        kind = ParamTypeKind.OBJECT
    )

    private val listParam = MethodMetadata.ParamInfo(
        name = "users",
        type = "java.util.List",
        kind = ParamTypeKind.COLLECTION,
        elementType = "com.example.User"
    )

    private val arrayParam = MethodMetadata.ParamInfo(
        name = "users",
        type = "com.example.User[]",
        kind = ParamTypeKind.ARRAY,
        elementType = "com.example.User"
    )

    private val matrixParam = MethodMetadata.ParamInfo(
        name = "rows",
        type = "com.example.User[][]",
        kind = ParamTypeKind.ARRAY,
        elementType = "com.example.User[]"
    )

    @Test
    fun `jackson object deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1}", userParam, JsonLibrary.JACKSON)
        assertTrue(result.contains("new com.fasterxml.jackson.databind.ObjectMapper()"))
        assertTrue(result.contains("@com.example.User@class"))
    }

    @Test
    fun `gson object deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1}", userParam, JsonLibrary.GSON)
        assertTrue(result.contains("new com.google.gson.Gson()"))
        assertTrue(result.contains("fromJson"))
    }

    @Test
    fun `fastjson2 collection deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", listParam, JsonLibrary.FASTJSON2)
        assertTrue(result.contains("com.alibaba.fastjson2.JSONObject"))
        assertTrue(result.contains("ParameterizedTypeImpl"))
        assertTrue(result.contains("@java.util.List@class"))
        assertTrue(result.contains("@com.example.User@class"))
        assertFalse(result.contains("parseArray"))
    }

    @Test
    fun `fastjson2 long list deserialization`() {
        val longListParam = MethodMetadata.ParamInfo(
            name = "ids",
            type = "java.util.List",
            kind = ParamTypeKind.COLLECTION,
            elementType = "java.lang.Long"
        )
        val result = JsonDeserializeOgnlBuilder.build("[1,2]", longListParam, JsonLibrary.FASTJSON2)
        assertEquals(
            "(@com.alibaba.fastjson2.JSONObject@parseObject(\"[1,2]\"," +
                "new com.alibaba.fastjson2.util.ParameterizedTypeImpl(" +
                "new java.lang.reflect.Type[]{(@java.lang.Long@class)},null,(@java.util.List@class))," +
                "@java.lang.reflect.Array@newInstance(@com.alibaba.fastjson2.JSONReader\$Feature@class,0)))",
            result
        )
    }

    @Test
    fun `fastjson2 array deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", arrayParam, JsonLibrary.FASTJSON2)
        assertEquals(
            "(@com.alibaba.fastjson2.JSONObject@parseObject(\"[{\\\"id\\\":1}]\"," +
                "@java.lang.reflect.Array@newInstance(@com.example.User@class,0).getClass()))",
            result
        )
    }

    @Test
    fun `gson array deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", arrayParam, JsonLibrary.GSON)
        assertEquals(
            "(#_raGson=new com.google.gson.Gson(),#_raGson.fromJson(\"[{\\\"id\\\":1}]\"," +
                "@java.lang.reflect.Array@newInstance(@com.example.User@class,0).getClass()))",
            result
        )
    }

    @Test
    fun `fastjson1 array deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", arrayParam, JsonLibrary.FASTJSON)
        assertEquals(
            "(@com.alibaba.fastjson.JSONObject@parseObject(\"[{\\\"id\\\":1}]\"," +
                "@java.lang.reflect.Array@newInstance(@com.example.User@class,0).getClass()))",
            result
        )
    }

    // 多维数组的 elementType 自带 [] 后缀，必须逐层构造数组 Class，
    // 否则会退化成非法的 `@X[]@class`
    @Test
    fun `gson two dimensional array deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[[{\"id\":1}]]", matrixParam, JsonLibrary.GSON)
        assertEquals(
            "(#_raGson=new com.google.gson.Gson(),#_raGson.fromJson(\"[[{\\\"id\\\":1}]]\"," +
                "@java.lang.reflect.Array@newInstance(" +
                "@java.lang.reflect.Array@newInstance(@com.example.User@class,0).getClass(),0).getClass()))",
            result
        )
    }

    @Test
    fun `fastjson2 primitive two dimensional array deserialization`() {
        val intMatrixParam = MethodMetadata.ParamInfo(
            name = "grid",
            type = "int[][]",
            kind = ParamTypeKind.ARRAY,
            elementType = "int[]"
        )
        val result = JsonDeserializeOgnlBuilder.build("[[1,2]]", intMatrixParam, JsonLibrary.FASTJSON2)
        assertEquals(
            "(@com.alibaba.fastjson2.JSONObject@parseObject(\"[[1,2]]\"," +
                "@java.lang.reflect.Array@newInstance(" +
                "@java.lang.reflect.Array@newInstance(@int@class,0).getClass(),0).getClass()))",
            result
        )
    }

    @Test
    fun `jackson two dimensional array deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[[{\"id\":1}]]", matrixParam, JsonLibrary.JACKSON)
        assertEquals(
            "(#_raMapper=new com.fasterxml.jackson.databind.ObjectMapper()," +
                "#_raMapper.readValue(\"[[{\\\"id\\\":1}]]\"," +
                "#_raMapper.getTypeFactory().constructArrayType(" +
                "@java.lang.reflect.Array@newInstance(@com.example.User@class,0).getClass())))",
            result
        )
    }

    // OGNL 没有 `@X[]@class` 这种语法（解析期 ParseException），任何分支都不能再产出它
    @Test
    fun `no branch uses invalid array class syntax`() {
        val mapParam = MethodMetadata.ParamInfo(
            name = "userMap",
            type = "java.util.Map",
            kind = ParamTypeKind.MAP,
            mapKeyType = "java.lang.String",
            mapValueType = "com.example.User"
        )
        // 元素/值类型本身就是数组（List<User[]>、Map<String, User[]>），类型名自带 []
        val listMatrixParam = MethodMetadata.ParamInfo(
            name = "grid",
            type = "java.util.List",
            kind = ParamTypeKind.COLLECTION,
            elementType = "com.example.User[]"
        )
        val mapMatrixParam = MethodMetadata.ParamInfo(
            name = "gridMap",
            type = "java.util.Map",
            kind = ParamTypeKind.MAP,
            mapKeyType = "java.lang.String",
            mapValueType = "com.example.User[]"
        )
        val primitiveMatrixParam = MethodMetadata.ParamInfo(
            name = "ints",
            type = "int[][]",
            kind = ParamTypeKind.ARRAY,
            elementType = "int[]"
        )
        val params = listOf(
            userParam, listParam, arrayParam, matrixParam, mapParam,
            listMatrixParam, mapMatrixParam, primitiveMatrixParam
        )
        for (library in JsonLibrary.values()) {
            for (param in params) {
                val json = if (param.kind == ParamTypeKind.MAP || param.kind == ParamTypeKind.OBJECT) {
                    "{\"id\":1}"
                } else {
                    "[{\"id\":1}]"
                }
                val expr = JsonDeserializeOgnlBuilder.build(json, param, library)
                assertFalse(
                    expr.contains("[]@class"),
                    "$library/${param.kind} generated invalid array class literal: $expr"
                )
            }
        }
    }

    @Test
    fun `fastjson1 collection deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", listParam, JsonLibrary.FASTJSON)
        assertTrue(result.contains("com.alibaba.fastjson.JSON@parseObject"))
        assertTrue(result.contains("com.alibaba.fastjson.util.ParameterizedTypeImpl"))
        assertTrue(result.contains("@java.util.List@class"))
    }

    @Test
    fun `fastjson2 map keeps key and value classes`() {
        val mapParam = MethodMetadata.ParamInfo(
            name = "userMap",
            type = "java.util.Map",
            kind = ParamTypeKind.MAP,
            mapKeyType = "java.lang.String",
            mapValueType = "com.example.User"
        )
        val result = JsonDeserializeOgnlBuilder.build("{\"u\":{\"id\":1}}", mapParam, JsonLibrary.FASTJSON2)
        assertTrue(result.contains("@java.util.Map@class"))
        assertTrue(result.contains("@java.lang.String@class"))
        assertTrue(result.contains("@com.example.User@class"))
    }

    @Test
    fun `fastjson2 set uses the declared collection class`() {
        val setParam = MethodMetadata.ParamInfo(
            name = "users",
            type = "java.util.Set",
            kind = ParamTypeKind.COLLECTION,
            elementType = "com.example.User"
        )
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", setParam, JsonLibrary.FASTJSON2)
        assertTrue(result.contains("@java.util.Set@class"))
        assertTrue(result.contains("@com.example.User@class"))
        assertFalse(result.contains("parseArray"))
    }

    @Test
    fun `json apostrophe is not a raw single quote`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"name\":\"it's\"}", userParam, JsonLibrary.JACKSON)
        assertFalse(result.contains("'"))
        assertTrue(result.contains("toChars(39)"))
    }

    @Test
    fun `auto mode probes multiple libraries`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1}", userParam, JsonLibrary.AUTO)
        assertTrue(result.contains("Class@forName"))
        assertTrue(result.contains("com.fasterxml.jackson.databind.ObjectMapper"))
        assertTrue(result.contains("com.google.gson.Gson"))
        assertTrue(result.contains("com.alibaba.fastjson2.JSONObject"))
        assertTrue(result.contains("com.alibaba.fastjson.JSONObject"))
    }

    @Test
    fun `auto mode fastjson2 collection uses parameterized type`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", listParam, JsonLibrary.AUTO)
        assertTrue(result.contains("com.alibaba.fastjson2.util.ParameterizedTypeImpl"))
        assertTrue(result.contains("@java.util.List@class"))
    }

    @Test
    fun `auto mode ends with null fallback`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1}", userParam, JsonLibrary.AUTO)
        assertFalse(result.endsWith(":null)"))
        assertTrue(result.contains(":null)"))
    }
}

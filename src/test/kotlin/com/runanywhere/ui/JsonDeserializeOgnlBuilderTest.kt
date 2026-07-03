package com.runanywhere.ui

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import com.runanywhere.settings.JsonLibrary
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
        assertTrue(result.contains("parseArray"))
    }

    @Test
    fun `fastjson1 collection deserialization`() {
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", listParam, JsonLibrary.FASTJSON)
        assertTrue(result.contains("com.alibaba.fastjson.JSONObject"))
        assertTrue(result.contains("parseArray"))
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
    fun `auto mode ends with null fallback`() {
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1}", userParam, JsonLibrary.AUTO)
        assertFalse(result.endsWith(":null)"))
        assertTrue(result.contains(":null)"))
    }
}

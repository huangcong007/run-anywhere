package com.runanywhere.ui

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.ParamTypeKind
import com.runanywhere.settings.JsonLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OgnlLiteralConverterTest {

    @Test
    fun `string is quoted and escaped`() {
        assertEquals("\"hello\"", OgnlLiteralConverter.toOgnlLiteral("hello", "java.lang.String", false))
        assertEquals("\"say \\\"hi\\\"\"", OgnlLiteralConverter.toOgnlLiteral("say \"hi\"", "String", false))
    }

    @Test
    fun `string apostrophe is concatenated without a raw quote`() {
        val literal = OgnlLiteralConverter.toOgnlLiteral("it's", "java.lang.String", false)
        assertFalse(literal.contains("'"))
        assertTrue(literal.contains("toChars(39)"))
    }

    @Test
    fun `numeric and enum literals are validated`() {
        val intParam = MethodMetadata.ParamInfo("n", "int", isPrimitive = true)
        assertNull(OgnlLiteralConverter.validate("12", intParam))
        assertTrue(OgnlLiteralConverter.validate("1); @java.lang.System@gc()", intParam) != null)

        val boolParam = MethodMetadata.ParamInfo("flag", "boolean", isPrimitive = true)
        assertNull(OgnlLiteralConverter.validate("true", boolParam))
        assertTrue(OgnlLiteralConverter.validate("yes", boolParam) != null)

        val enumParam = MethodMetadata.ParamInfo(
            "status", "com.example.Status", kind = ParamTypeKind.ENUM, isEnum = true
        )
        assertNull(OgnlLiteralConverter.validate("ACTIVE", enumParam))
        assertTrue(OgnlLiteralConverter.validate("ACTIVE.toString()", enumParam) != null)
    }

    @Test
    fun `empty input becomes null`() {
        assertEquals("null", OgnlLiteralConverter.toOgnlLiteral("", "int", true))
        assertEquals("null", OgnlLiteralConverter.toOgnlLiteral("  ", "java.lang.String", false))
    }

    @Test
    fun `long gets L suffix`() {
        assertEquals("123L", OgnlLiteralConverter.toOgnlLiteral("123", "long", true))
        assertEquals("456L", OgnlLiteralConverter.toOgnlLiteral("456L", "java.lang.Long", false))
    }

    @Test
    fun `enum uses ognl enum syntax`() {
        assertEquals(
            "@com.example.Status@ACTIVE",
            OgnlLiteralConverter.toOgnlLiteral("ACTIVE", "com.example.Status", false, isEnum = true)
        )
    }

    @Test
    fun `big decimal uses constructor`() {
        assertEquals(
            "new java.math.BigDecimal(\"99.99\")",
            OgnlLiteralConverter.toOgnlLiteral("99.99", "java.math.BigDecimal", false)
        )
    }

    @Test
    fun `object type uses configured json library`() {
        val param = MethodMetadata.ParamInfo(
            name = "dto",
            type = "com.example.User",
            kind = ParamTypeKind.OBJECT
        )
        val result = JsonDeserializeOgnlBuilder.build("{\"id\":1,\"name\":\"test\"}", param, JsonLibrary.JACKSON)
        assertTrue(result.contains("new com.fasterxml.jackson.databind.ObjectMapper()"))
        assertTrue(result.contains("@com.example.User@class"))
        assertTrue(result.contains("\\\"id\\\":1"))
    }

    @Test
    fun `list type uses jackson collection type`() {
        val param = MethodMetadata.ParamInfo(
            name = "items",
            type = "java.util.List",
            kind = ParamTypeKind.COLLECTION,
            elementType = "com.example.User"
        )
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", param, JsonLibrary.JACKSON)
        assertTrue(result.contains("constructCollectionType(@java.util.List@class,@com.example.User@class)"))
    }

    @Test
    fun `map type uses jackson map type`() {
        val param = MethodMetadata.ParamInfo(
            name = "data",
            type = "java.util.Map",
            kind = ParamTypeKind.MAP,
            mapKeyType = "java.lang.String",
            mapValueType = "com.example.User"
        )
        val result = JsonDeserializeOgnlBuilder.build("{\"u1\":{\"id\":1}}", param, JsonLibrary.JACKSON)
        assertTrue(result.contains("constructMapType(@java.util.Map@class,@java.lang.String@class,@com.example.User@class)"))
    }

    @Test
    fun `array type uses jackson array type`() {
        val param = MethodMetadata.ParamInfo(
            name = "arr",
            type = "com.example.User[]",
            kind = ParamTypeKind.ARRAY,
            elementType = "com.example.User"
        )
        val result = JsonDeserializeOgnlBuilder.build("[{\"id\":1}]", param, JsonLibrary.JACKSON)
        assertTrue(result.contains("constructArrayType(@com.example.User@class)"))
    }

    @Test
    fun `supported type detection`() {
        assertTrue(OgnlLiteralConverter.isSupportedInputType("java.lang.String", false))
        assertTrue(OgnlLiteralConverter.isSupportedInputType("com.example.Status", false, isEnum = true))
        assertTrue(
            OgnlLiteralConverter.isSupportedInputType(
                MethodMetadata.ParamInfo("x", "com.example.User", kind = ParamTypeKind.OBJECT)
            )
        )
        assertFalse(OgnlLiteralConverter.isSupportedInputType("com.example.User", false))
    }
}

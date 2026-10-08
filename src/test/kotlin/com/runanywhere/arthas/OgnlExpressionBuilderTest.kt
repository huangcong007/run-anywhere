package com.runanywhere.arthas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OgnlExpressionBuilderTest {

    @Test
    fun `static method uses ognl syntax`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.Utils",
            methodName = "add",
            parameters = emptyList(),
            isStatic = true,
            springBeanName = null
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("1", "2"))
        assertEquals("ognl '@com.example.Utils@add(1, 2)' -x 3", command)
    }

    @Test
    fun `spring bean method uses getBean`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.UserService",
            methodName = "findById",
            parameters = emptyList(),
            isStatic = false,
            springBeanName = "userService"
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("1L"))
        assertTrue(command.contains("containsBean(\"userService\")"))
        assertTrue(command.contains("getBean(\"userService\")"))
        assertTrue(command.contains("findById(1L)"))
        assertFalse(command.contains("instances[0].getBean"))
    }

    @Test
    fun `instance method uses vmtool getInstances`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.PlainService",
            methodName = "run",
            parameters = emptyList(),
            isStatic = false,
            springBeanName = null
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, emptyList())
        assertEquals(
            "vmtool --action getInstances --className com.example.PlainService " +
                "--express 'instances[0].run()' -x 3",
            command
        )
    }

    @Test
    fun `instance index is selectable`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.PlainService",
            methodName = "run",
            parameters = emptyList(),
            isStatic = false,
            springBeanName = null
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, emptyList(), instanceIndex = 2)
        assertTrue(command.contains("instances[2].run()"))
    }

    @Test
    fun `string apostrophe does not break the arthas quotes`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.Utils",
            methodName = "greet",
            parameters = emptyList(),
            isStatic = true,
            springBeanName = null
        )
        val arg = com.runanywhere.ui.OgnlLiteralConverter.toOgnlLiteral("it's", "java.lang.String", false)
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf(arg))
        val inner = command.removePrefix("ognl '").substringBeforeLast("' -x")
        assertFalse(inner.contains("'"))
        assertTrue(inner.contains("toChars(39)"))
    }

    @Test
    fun `void return uses expand level 0`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.UserService",
            methodName = "retryUploadByRecordId",
            parameters = emptyList(),
            isStatic = false,
            springBeanName = "materialService",
            isVoidReturn = true
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("123L"), expandLevel = 5)
        assertTrue(command.endsWith("-x 0"))
    }

    @Test
    fun `expand level comes from the caller`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.Utils",
            methodName = "add",
            parameters = emptyList(),
            isStatic = true,
            springBeanName = null
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("1"), expandLevel = 5)
        assertTrue(command.endsWith("-x 5"))
    }

    @Test
    fun `unique constructor uses new`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.User",
            methodName = "User",
            parameters = listOf(com.runanywhere.psi.MethodMetadata.ParamInfo("id", "long", isPrimitive = true)),
            isStatic = false,
            springBeanName = null,
            isConstructor = true
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("1L"))
        assertEquals("ognl 'new com.example.User(1L)' -x 3", command)
    }

    @Test
    fun `overloaded static method is pinned by parameter classes`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.Utils",
            methodName = "add",
            parameters = listOf(com.runanywhere.psi.MethodMetadata.ParamInfo("id", "java.lang.Long")),
            isStatic = true,
            springBeanName = null,
            overloaded = true
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("1L"))
        assertTrue(command.startsWith("ognl '"))
        assertTrue(command.contains("getDeclaredMethod(\"add\",new java.lang.Class[]{(@java.lang.Long@class)})"))
        assertTrue(command.contains("invoke(null,new java.lang.Object[]{(1L)})"))
        assertFalse(command.contains("@com.example.Utils@add("))
    }

    @Test
    fun `overloaded constructor uses getDeclaredConstructor`() {
        val metadata = com.runanywhere.psi.MethodMetadata(
            className = "com.example.User",
            methodName = "User",
            parameters = listOf(com.runanywhere.psi.MethodMetadata.ParamInfo("name", "java.lang.String")),
            isStatic = false,
            springBeanName = null,
            isConstructor = true,
            overloaded = true
        )
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("\"ada\""))
        assertTrue(command.contains("getDeclaredConstructor(new java.lang.Class[]{(@java.lang.String@class)})"))
        assertTrue(command.contains("newInstance(new java.lang.Object[]{(\"ada\")})"))
    }

    @Test
    fun `instance labels use simple name and identity hash`() {
        val command = OgnlExpressionBuilder.buildInstanceLabelCommand("com.example.User")
        assertTrue(command.contains("getSimpleName()"))
        assertTrue(command.contains("identityHashCode(#this)"))
        val inner = command.substringAfter("--express '").substringBeforeLast("' -x")
        assertFalse(inner.contains("'"))
    }
}

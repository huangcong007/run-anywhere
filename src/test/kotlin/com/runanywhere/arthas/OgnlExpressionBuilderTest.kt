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
        val command = OgnlExpressionBuilder.buildCommand(metadata, listOf("123L"))
        assertTrue(command.endsWith("-x 0"))
    }
}

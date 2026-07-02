package com.runanywhere.arthas

import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(command.contains("getBean(\"userService\")"))
        assertTrue(command.contains("findById(1L)"))
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
    fun `bean name with quotes is escaped`() {
        val escaped = OgnlExpressionBuilder.escapeForSingleQuotedExpress("my\"bean")
        assertEquals("my\\\"bean", escaped)
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

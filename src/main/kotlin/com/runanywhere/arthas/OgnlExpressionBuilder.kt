package com.runanywhere.arthas

import com.runanywhere.psi.MethodMetadata

/**
 * 根据 MethodMetadata 和 OGNL 参数构造 Arthas 命令。
 */
object OgnlExpressionBuilder {

    private const val DEFAULT_EXPAND_LEVEL = 3
    private const val VOID_EXPAND_LEVEL = 0

    fun buildCommand(metadata: MethodMetadata, ognlArgs: List<String>): String {
        val argsStr = ognlArgs.joinToString(", ")
        val expandLevel = if (metadata.isVoidReturn) VOID_EXPAND_LEVEL else DEFAULT_EXPAND_LEVEL
        return when {
            metadata.isStatic -> buildStaticCall(metadata.className, metadata.methodName, argsStr, expandLevel)
            metadata.springBeanName != null -> buildSpringBeanCall(metadata.springBeanName, metadata.methodName, argsStr, expandLevel)
            else -> buildInstanceCall(metadata.className, metadata.methodName, argsStr, expandLevel)
        }
    }

    private fun buildStaticCall(fqn: String, methodName: String, argsStr: String, expandLevel: Int): String {
        return "ognl '@$fqn@$methodName($argsStr)' -x $expandLevel"
    }

    private fun buildInstanceCall(fqn: String, methodName: String, argsStr: String, expandLevel: Int): String {
        return "vmtool --action getInstances --className $fqn " +
            "--express 'instances[0].$methodName($argsStr)' -x $expandLevel"
    }

    private fun buildSpringBeanCall(beanName: String, methodName: String, argsStr: String, expandLevel: Int): String {
        val escapedBeanName = escapeForSingleQuotedExpress(beanName)
        return "vmtool --action getInstances " +
            "--className org.springframework.context.ApplicationContext " +
            "--express 'instances[0].getBean(\"$escapedBeanName\").$methodName($argsStr)' -x $expandLevel"
    }

    internal fun escapeForSingleQuotedExpress(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

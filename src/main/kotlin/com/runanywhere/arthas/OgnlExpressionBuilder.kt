package com.runanywhere.arthas

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.ui.OgnlLiteralConverter

/**
 * 根据 MethodMetadata 和 OGNL 参数构造 Arthas 命令。
 */
object OgnlExpressionBuilder {

    private const val DEFAULT_EXPAND_LEVEL = 3
    private const val VOID_EXPAND_LEVEL = 0

    fun buildCommand(
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        instanceIndex: Int = 0
    ): String {
        val argsStr = ognlArgs.joinToString(", ")
        val expandLevel = if (metadata.isVoidReturn) VOID_EXPAND_LEVEL else DEFAULT_EXPAND_LEVEL
        return when {
            metadata.isStatic -> buildStaticCall(metadata.className, metadata.methodName, argsStr, expandLevel)
            metadata.springBeanName != null -> buildSpringBeanCall(
                metadata.springBeanName, metadata.methodName, argsStr, expandLevel
            )
            else -> buildInstanceCall(
                metadata.className, metadata.methodName, argsStr, expandLevel, instanceIndex
            )
        }
    }

    fun buildInstanceCountCommand(className: String): String =
        "vmtool --action getInstances --className $className --express 'instances.size()' -x 0"

    private fun buildStaticCall(fqn: String, methodName: String, argsStr: String, expandLevel: Int): String {
        return "ognl ${arthasSingleQuoted("@$fqn@$methodName($argsStr)")} -x $expandLevel"
    }

    private fun buildInstanceCall(
        fqn: String,
        methodName: String,
        argsStr: String,
        expandLevel: Int,
        instanceIndex: Int
    ): String {
        return "vmtool --action getInstances --className $fqn " +
            "--express ${arthasSingleQuoted("instances[$instanceIndex].$methodName($argsStr)")} -x $expandLevel"
    }

    private fun buildSpringBeanCall(beanName: String, methodName: String, argsStr: String, expandLevel: Int): String {
        val beanExpr = OgnlLiteralConverter.ognlStringExpr(beanName)
        val express = "instances.{? #this.containsBean($beanExpr)}[0].getBean($beanExpr).$methodName($argsStr)"
        return "vmtool --action getInstances " +
            "--className org.springframework.context.ApplicationContext " +
            "--express ${arthasSingleQuoted(express)} -x $expandLevel"
    }

    /** Arthas 参数用单引号包裹。内容里不应再出现裸单引号。 */
    private fun arthasSingleQuoted(content: String): String = "'$content'"
}

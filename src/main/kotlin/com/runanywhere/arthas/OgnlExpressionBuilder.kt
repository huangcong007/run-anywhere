package com.runanywhere.arthas

import com.runanywhere.psi.MethodMetadata
import com.runanywhere.ui.OgnlLiteralConverter

/**
 * 根据 MethodMetadata 和 OGNL 参数构造 Arthas 命令。
 */
object OgnlExpressionBuilder {

    private const val DEFAULT_EXPAND_LEVEL = 3
    private const val VOID_EXPAND_LEVEL = 0
    const val MIN_EXPAND_LEVEL = 0
    const val MAX_EXPAND_LEVEL = 10

    fun buildCommand(
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        instanceIndex: Int = 0,
        expandLevel: Int = DEFAULT_EXPAND_LEVEL
    ): String {
        val level = if (metadata.isVoidReturn) {
            VOID_EXPAND_LEVEL
        } else {
            expandLevel.coerceIn(MIN_EXPAND_LEVEL, MAX_EXPAND_LEVEL)
        }
        val invocation = invocationExpr(metadata, ognlArgs, instanceIndex)
        return when {
            metadata.isConstructor || metadata.isStatic ->
                "ognl ${arthasSingleQuoted(invocation)} -x $level"
            metadata.springBeanName != null ->
                "vmtool --action getInstances " +
                    "--className org.springframework.context.ApplicationContext " +
                    "--express ${arthasSingleQuoted(invocation)} -x $level"
            else ->
                "vmtool --action getInstances --className ${metadata.className} " +
                    "--express ${arthasSingleQuoted(invocation)} -x $level"
        }
    }

    fun buildInstanceCountCommand(className: String): String =
        "vmtool --action getInstances --className $className --express 'instances.size()' -x 0"

    fun buildInstanceLabelCommand(className: String): String {
        val express =
            "instances.{ #this.getClass().getSimpleName() + \"@\" + @java.lang.System@identityHashCode(#this) }"
        return "vmtool --action getInstances --className $className " +
            "--express ${arthasSingleQuoted(express)} -x 2"
    }

    private fun invocationExpr(
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        instanceIndex: Int
    ): String {
        if (metadata.isConstructor) {
            return if (metadata.overloaded) {
                reflectiveConstructor(metadata, ognlArgs)
            } else {
                "new ${metadata.className}(${ognlArgs.joinToString(", ")})"
            }
        }
        if (metadata.overloaded) {
            val target = when {
                metadata.isStatic -> "null"
                metadata.springBeanName != null -> "#_raTarget"
                else -> "instances[$instanceIndex]"
            }
            val call = reflectiveMethod(metadata, ognlArgs, target)
            return if (metadata.springBeanName != null) {
                "(#_raTarget=${beanLookup(metadata.springBeanName)},$call)"
            } else {
                call
            }
        }
        val argsStr = ognlArgs.joinToString(", ")
        return when {
            metadata.isStatic -> "@${metadata.className}@${metadata.methodName}($argsStr)"
            metadata.springBeanName != null ->
                "${beanLookup(metadata.springBeanName)}.${metadata.methodName}($argsStr)"
            else -> "instances[$instanceIndex].${metadata.methodName}($argsStr)"
        }
    }

    private fun beanLookup(beanName: String): String {
        val beanExpr = OgnlLiteralConverter.ognlStringExpr(beanName)
        return "instances.{? #this.containsBean($beanExpr)}[0].getBean($beanExpr)"
    }

    private fun reflectiveMethod(
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        targetExpr: String
    ): String {
        val nameExpr = OgnlLiteralConverter.ognlStringExpr(metadata.methodName)
        return "(#_raMember=@${metadata.className}@class.getDeclaredMethod($nameExpr,${classArray(metadata.parameters)})," +
            "#_raMember.setAccessible(true)," +
            "#_raMember.invoke($targetExpr,${objectArray(ognlArgs)}))"
    }

    private fun reflectiveConstructor(metadata: MethodMetadata, ognlArgs: List<String>): String {
        return "(#_raMember=@${metadata.className}@class.getDeclaredConstructor(${classArray(metadata.parameters)})," +
            "#_raMember.setAccessible(true)," +
            "#_raMember.newInstance(${objectArray(ognlArgs)}))"
    }

    private fun classArray(parameters: List<MethodMetadata.ParamInfo>): String {
        if (parameters.isEmpty()) return "new java.lang.Class[0]"
        val items = parameters.joinToString(",") { "(${classLiteral(it.type)})" }
        return "new java.lang.Class[]{$items}"
    }

    private fun objectArray(ognlArgs: List<String>): String {
        if (ognlArgs.isEmpty()) return "new java.lang.Object[0]"
        return "new java.lang.Object[]{${ognlArgs.joinToString(",") { "($it)" }}}"
    }

    /** 参数类型擦除后的 Class。数组不能写成 `@X[]@class`。 */
    private fun classLiteral(typeName: String): String {
        var base = typeName
        var dimensions = 0
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

    /** Arthas 参数用单引号包裹。内容里不应再出现裸单引号。 */
    private fun arthasSingleQuoted(content: String): String = "'$content'"
}

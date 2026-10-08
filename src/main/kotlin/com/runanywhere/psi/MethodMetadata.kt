package com.runanywhere.psi

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

data class MethodMetadata(
    val className: String,
    val methodName: String,
    val parameters: List<ParamInfo>,
    val isStatic: Boolean,
    val springBeanName: String?,
    val isVoidReturn: Boolean = false
) {
    data class ParamInfo(
        val name: String,
        val type: String,
        val kind: ParamTypeKind = ParamTypeKind.SIMPLE,
        val elementType: String? = null,
        val mapKeyType: String? = null,
        val mapValueType: String? = null,
        val isPrimitive: Boolean = false,
        val isEnum: Boolean = false
    )

    companion object {
        internal val SPRING_BEAN_ANNOTATIONS = setOf(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration",
            "org.apache.rocketmq.spring.annotation.RocketMQMessageListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.jms.annotation.JmsListener"
        )

        /** Spring 注解中可能声明 Bean 名的属性名（按优先级）。 */
        internal val BEAN_NAME_ATTRIBUTES = listOf("value", "name")
    }
}

object MethodMetadataExtractor {

    fun extract(method: PsiMethod): MethodMetadata? {
        val containingClass = method.containingClass ?: return null
        val className = containingClass.qualifiedName ?: return null
        val methodName = method.name
        val isStatic = method.modifierList?.hasExplicitModifier(PsiModifier.STATIC) == true

        val parameters = method.parameterList.parameters.map { psiParam ->
            val analyzed = ParameterTypeAnalyzer.analyze(psiParam.type)
            MethodMetadata.ParamInfo(
                name = psiParam.name ?: "arg",
                type = analyzed.type,
                kind = analyzed.kind,
                elementType = analyzed.elementType,
                mapKeyType = analyzed.mapKeyType,
                mapValueType = analyzed.mapValueType,
                isPrimitive = analyzed.isPrimitive,
                isEnum = analyzed.isEnum
            )
        }

        val springBeanName = findSpringBeanName(containingClass)
        val isVoidReturn = method.returnType?.canonicalText == "void"
        return MethodMetadata(className, methodName, parameters, isStatic, springBeanName, isVoidReturn)
    }

    /**
     * 简化泛型类型显示，保留外层类型名。
     * 例：java.util.List<java.lang.String> → java.util.List
     */
    /**
     * 去掉泛型实参，保留尖括号之外的数组括号。
     * 例：java.util.List<java.lang.String>[] → java.util.List[]
     */
    internal fun simplifyType(canonicalText: String): String {
        val simplified = StringBuilder()
        var depth = 0
        for (ch in canonicalText) {
            when (ch) {
                '<' -> depth++
                '>' -> if (depth > 0) depth--
                else -> if (depth == 0 && !ch.isWhitespace()) simplified.append(ch)
            }
        }
        return simplified.toString().ifEmpty { canonicalText }
    }

    private fun findSpringBeanName(containingClass: com.intellij.psi.PsiClass): String? {
        for (annotation in containingClass.annotations) {
            val fqn = annotation.qualifiedName ?: continue
            if (fqn !in MethodMetadata.SPRING_BEAN_ANNOTATIONS) continue

            for (attrName in MethodMetadata.BEAN_NAME_ATTRIBUTES) {
                val beanName = BeanNameResolver.extractBeanNameFromAttribute(
                    annotation.findAttributeValue(attrName)?.text
                )
                if (beanName != null) return beanName
            }

            return BeanNameResolver.springDefaultBeanName(nestedSimpleNames(containingClass))
        }
        return null
    }

    /** 从外层到内层的简单类名，用于生成 Spring 默认 Bean 名（Outer.Inner → outer.Inner）。 */
    private fun nestedSimpleNames(psiClass: com.intellij.psi.PsiClass): List<String> {
        val names = ArrayList<String>()
        var current: com.intellij.psi.PsiClass? = psiClass
        while (current != null) {
            val name = current.name ?: return emptyList()
            names.add(name)
            current = current.containingClass
        }
        names.reverse()
        return names
    }
}

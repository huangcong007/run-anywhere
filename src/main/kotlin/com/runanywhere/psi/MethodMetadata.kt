package com.runanywhere.psi

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

data class MethodMetadata(
    val className: String,
    val methodName: String,
    val parameters: List<ParamInfo>,
    val isStatic: Boolean,
    val springBeanName: String?,
    val isVoidReturn: Boolean = false,
    val isConstructor: Boolean = false,
    val overloaded: Boolean = false
) {
    data class ParamInfo(
        val name: String,
        val type: String,
        val kind: ParamTypeKind = ParamTypeKind.SIMPLE,
        val elementType: String? = null,
        val mapKeyType: String? = null,
        val mapValueType: String? = null,
        val isPrimitive: Boolean = false,
        val isEnum: Boolean = false,
        val typeDesc: TypeDesc? = null
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

    private const val BEAN_ANNOTATION = "org.springframework.context.annotation.Bean"

    private val TOO_BROAD_RETURN_TYPES = setOf(
        "java.lang.Object",
        "java.io.Serializable",
        "java.lang.Cloneable",
        "java.lang.Comparable"
    )

    fun extract(method: PsiMethod): MethodMetadata? {
        val containingClass = method.containingClass ?: return null
        val className = containingClass.qualifiedName ?: return null
        val methodName = method.name
        val isStatic = method.modifierList.hasExplicitModifier(PsiModifier.STATIC)

        val parameters = method.parameterList.parameters.map { psiParam ->
            val analyzed = ParameterTypeAnalyzer.analyze(psiParam.type)
            MethodMetadata.ParamInfo(
                name = psiParam.name,
                type = analyzed.type,
                kind = analyzed.kind,
                elementType = analyzed.elementType,
                mapKeyType = analyzed.mapKeyType,
                mapValueType = analyzed.mapValueType,
                isPrimitive = analyzed.isPrimitive,
                isEnum = analyzed.isEnum,
                typeDesc = analyzed.typeDesc
            )
        }

        val springBeanName = if (method.isConstructor) {
            null
        } else {
            findSpringBeanName(containingClass) ?: findBeanFactoryName(containingClass)
        }
        val overloaded = if (method.isConstructor) {
            containingClass.constructors.size > 1
        } else {
            containingClass.findMethodsByName(method.name, false).size > 1
        }
        val isVoidReturn = method.returnType?.canonicalText == "void"
        return MethodMetadata(
            className = className,
            methodName = methodName,
            parameters = parameters,
            isStatic = isStatic,
            springBeanName = springBeanName,
            isVoidReturn = isVoidReturn,
            isConstructor = method.isConstructor,
            overloaded = overloaded
        )
    }

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

    /**
     * 类上没有组件注解时，查找项目里返回该类型的 `@Bean` 方法。
     * 多个无关名字不猜测；唯一名字，或唯一等于 Spring 默认名的名字才会用。
     */
    private fun findBeanFactoryName(target: PsiClass): String? {
        return try {
            runReadActionBlocking { resolveBeanFactoryName(target) }
        } catch (_: IndexNotReadyException) {
            null
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveBeanFactoryName(target: PsiClass): String? {
        val project = target.project
        val beanAnnotation = JavaPsiFacade.getInstance(project).findClass(
            BEAN_ANNOTATION,
            GlobalSearchScope.allScope(project)
        ) ?: return null
        val methods = AnnotatedElementsSearch.searchPsiMethods(
            beanAnnotation,
            GlobalSearchScope.projectScope(project)
        ).findAll()
        val names = ArrayList<String>()
        for (factoryMethod in methods) {
            if (!matchesBeanReturn(target, factoryMethod)) continue
            names.add(factoryBeanName(factoryMethod))
        }
        return BeanNameResolver.chooseFactoryBeanName(
            names,
            BeanNameResolver.springDefaultBeanName(nestedSimpleNames(target))
        )
    }

    private fun matchesBeanReturn(target: PsiClass, factoryMethod: PsiMethod): Boolean {
        val returnType = factoryMethod.returnType as? PsiClassType ?: return false
        val returnClass = returnType.resolve() ?: return false
        val returnFqn = returnClass.qualifiedName ?: return false
        if (returnFqn in TOO_BROAD_RETURN_TYPES) return false
        return isSameOrSubtype(target, returnFqn)
    }

    private fun factoryBeanName(method: PsiMethod): String {
        val annotation = method.annotations.firstOrNull { it.qualifiedName == BEAN_ANNOTATION }
        if (annotation != null) {
            for (attrName in MethodMetadata.BEAN_NAME_ATTRIBUTES) {
                val explicit = BeanNameResolver.firstExplicitName(annotation.findAttributeValue(attrName)?.text)
                if (explicit != null) return explicit
            }
        }
        return method.name
    }

    private fun isSameOrSubtype(target: PsiClass, superFqn: String): Boolean {
        if (target.qualifiedName == superFqn) return true
        val visited = HashSet<PsiClass>()
        fun visit(start: PsiClass?): Boolean {
            var current = start
            while (current != null) {
                if (!visited.add(current)) return false
                if (current.qualifiedName == superFqn) return true
                for (iface in current.interfaces) {
                    if (visit(iface)) return true
                }
                current = current.superClass
            }
            return false
        }
        return visit(target)
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

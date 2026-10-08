package com.runanywhere.psi

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType

/**
 * 从 PSI 类型推断参数分类与泛型信息。
 */
internal object ParameterTypeAnalyzer {

    private val PRIMITIVE_TYPES = setOf(
        "byte", "short", "int", "long", "float", "double", "char", "boolean"
    )

    private val SIMPLE_REFERENCE_TYPES = setOf(
        "java.lang.String", "String",
        "java.lang.Boolean", "Boolean",
        "java.lang.Byte", "Byte",
        "java.lang.Short", "Short",
        "java.lang.Integer", "Integer",
        "java.lang.Long", "Long",
        "java.lang.Float", "Float",
        "java.lang.Double", "Double",
        "java.lang.Character", "Character",
        "java.math.BigDecimal",
        "java.math.BigInteger"
    )

    private val COLLECTION_TYPES = setOf(
        "java.util.Collection",
        "java.util.List",
        "java.util.Set",
        "java.util.ArrayList",
        "java.util.LinkedList",
        "java.util.HashSet",
        "java.util.LinkedHashSet",
        "java.util.TreeSet"
    )

    private val MAP_TYPES = setOf(
        "java.util.Map",
        "java.util.HashMap",
        "java.util.LinkedHashMap",
        "java.util.TreeMap",
        "java.util.concurrent.ConcurrentHashMap"
    )

    fun analyze(psiType: PsiType): AnalyzedParamType {
        when (psiType) {
            is PsiPrimitiveType -> {
                val name = psiType.canonicalText
                return AnalyzedParamType(
                    type = name,
                    kind = ParamTypeKind.SIMPLE,
                    isPrimitive = true
                )
            }
            is PsiArrayType -> {
                val componentFqn = typeFqn(psiType.componentType)
                return AnalyzedParamType(
                    type = "$componentFqn[]",
                    kind = ParamTypeKind.ARRAY,
                    elementType = componentFqn
                )
            }
            is PsiClassType -> return analyzeClassType(psiType)
            else -> {
                val fqn = MethodMetadataExtractor.simplifyType(psiType.canonicalText)
                return AnalyzedParamType(type = fqn, kind = ParamTypeKind.OBJECT)
            }
        }
    }

    private fun analyzeClassType(psiType: PsiClassType): AnalyzedParamType {
        val fqn = MethodMetadataExtractor.simplifyType(psiType.canonicalText)
        val resolved = psiType.resolve()

        if (resolved?.isEnum == true) {
            return AnalyzedParamType(type = fqn, kind = ParamTypeKind.ENUM, isEnum = true)
        }

        if (fqn in PRIMITIVE_TYPES) {
            return AnalyzedParamType(type = fqn, kind = ParamTypeKind.SIMPLE, isPrimitive = true)
        }

        if (fqn in SIMPLE_REFERENCE_TYPES) {
            return AnalyzedParamType(type = fqn, kind = ParamTypeKind.SIMPLE)
        }

        if (isMapType(fqn, resolved)) {
            val typeParams = psiType.parameters
            return AnalyzedParamType(
                type = fqn,
                kind = ParamTypeKind.MAP,
                mapKeyType = typeParams.getOrNull(0)?.let { typeFqn(it) } ?: "java.lang.Object",
                mapValueType = typeParams.getOrNull(1)?.let { typeFqn(it) } ?: "java.lang.Object"
            )
        }

        if (isCollectionType(fqn, resolved)) {
            val elementFqn = psiType.parameters.firstOrNull()?.let { typeFqn(it) } ?: "java.lang.Object"
            return AnalyzedParamType(
                type = fqn,
                kind = ParamTypeKind.COLLECTION,
                elementType = elementFqn
            )
        }

        return AnalyzedParamType(type = fqn, kind = ParamTypeKind.OBJECT)
    }

    private fun isMapType(fqn: String, resolved: PsiClass?): Boolean {
        if (fqn in MAP_TYPES) return true
        return resolved != null && isAssignableTo(resolved, "java.util.Map")
    }

    private fun isCollectionType(fqn: String, resolved: PsiClass?): Boolean {
        if (fqn in COLLECTION_TYPES) return true
        return resolved != null && isAssignableTo(resolved, "java.util.Collection")
    }

    /**
     * 沿父类链检查类名，并检查每一层自己声明的接口。
     * 这样 `class MyList extends ArrayList` 也能识别为 Collection。
     */
    private fun isAssignableTo(clazz: PsiClass, superFqn: String): Boolean {
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
        return visit(clazz)
    }

    private fun typeFqn(psiType: PsiType): String =
        MethodMetadataExtractor.simplifyType(psiType.canonicalText)

    data class AnalyzedParamType(
        val type: String,
        val kind: ParamTypeKind,
        val elementType: String? = null,
        val mapKeyType: String? = null,
        val mapValueType: String? = null,
        val isPrimitive: Boolean = false,
        val isEnum: Boolean = false
    )
}

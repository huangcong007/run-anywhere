package com.runanywhere.psi

/**
 * Spring 默认 Bean 名生成规则（与 java.beans.Introspector.decapitalize 一致）。
 */
object BeanNameResolver {

    /**
     * - 若类名为空，返回原值
     * - 若前两个字符都是大写字母，保持原样（如 URLService → URLService）
     * - 否则首字母小写（如 UserService → userService）
     */
    fun decapitalize(name: String): String {
        if (name.isEmpty()) return name
        if (name.length >= 2 && name[0].isUpperCase() && name[1].isUpperCase()) {
            return name
        }
        return name.replaceFirstChar { it.lowercaseChar() }
    }

    /**
     * Spring [org.springframework.context.annotation.AnnotationBeanNameGenerator] 的默认名。
     * [nestedSimpleNames] 从外层到内层，例如 ["Outer", "Inner"] → outer.Inner。
     */
    fun springDefaultBeanName(nestedSimpleNames: List<String>): String? {
        if (nestedSimpleNames.isEmpty() || nestedSimpleNames.any { it.isBlank() }) return null
        return decapitalize(nestedSimpleNames.joinToString("."))
    }

    /**
     * 从注解属性文本中提取 Bean 名（去掉引号）。
     */
    fun extractBeanNameFromAttribute(attributeText: String?): String? =
        attributeText?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
}

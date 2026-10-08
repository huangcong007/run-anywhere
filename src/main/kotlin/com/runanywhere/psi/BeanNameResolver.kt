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
     * 多个 `@Bean` 方法返回同一类型时，只有唯一候选或唯一符合默认名的候选才自动选用。
     */
    fun chooseFactoryBeanName(candidates: List<String>, defaultName: String?): String? {
        val distinct = candidates.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.size == 1) return distinct[0]
        if (defaultName.isNullOrBlank()) return null
        val preferred = distinct.filter { it == defaultName }
        return if (preferred.size == 1) preferred[0] else null
    }

    /**
     * 从注解属性文本中提取 Bean 名（去掉引号）。
     */
    fun extractBeanNameFromAttribute(attributeText: String?): String? =
        attributeText?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }

    /**
     * 只接受字符串字面量。`@Bean({"userService"})` 取第一个名字；常量引用交给方法名。
     */
    fun firstExplicitName(attributeText: String?): String? {
        val text = attributeText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val token = if (text.startsWith("{") && text.endsWith("}")) {
            text.substring(1, text.length - 1)
                .split(",")
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        } else {
            text
        }
        if (!token.startsWith("\"")) return null
        return token.removeSurrounding("\"").takeIf { it.isNotBlank() }
    }
}

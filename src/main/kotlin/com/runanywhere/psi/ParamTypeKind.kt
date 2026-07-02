package com.runanywhere.psi

/**
 * 方法参数的类型分类，用于决定输入方式与 OGNL 字面量生成策略。
 */
enum class ParamTypeKind {
    /** 基本类型、包装类、String、BigDecimal 等可直接输入字面量的类型 */
    SIMPLE,

    /** Java 枚举 */
    ENUM,

    /** 普通对象（POJO / DTO 等） */
    OBJECT,

    /** 数组 */
    ARRAY,

    /** List / Set / Collection */
    COLLECTION,

    /** Map */
    MAP
}

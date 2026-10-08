package com.runanywhere.psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BeanNameResolverTest {

    @Test
    fun `decapitalize standard class name`() {
        assertEquals("userService", BeanNameResolver.decapitalize("UserService"))
    }

    @Test
    fun `decapitalize keeps acronym prefix`() {
        assertEquals("URLService", BeanNameResolver.decapitalize("URLService"))
        assertEquals("OEUploadAdScheduler", BeanNameResolver.decapitalize("OEUploadAdScheduler"))
    }

    @Test
    fun `spring default bean name keeps outer class`() {
        assertEquals("userService", BeanNameResolver.springDefaultBeanName(listOf("UserService")))
        assertEquals("outer.Inner", BeanNameResolver.springDefaultBeanName(listOf("Outer", "Inner")))
        assertEquals("outer.URLHelper", BeanNameResolver.springDefaultBeanName(listOf("Outer", "URLHelper")))
        assertEquals("URLService", BeanNameResolver.springDefaultBeanName(listOf("URLService")))
    }

    @Test
    fun `extract bean name removes quotes`() {
        assertEquals("myBean", BeanNameResolver.extractBeanNameFromAttribute("\"myBean\""))
        assertEquals(null, BeanNameResolver.extractBeanNameFromAttribute("\"\""))
        assertEquals(null, BeanNameResolver.extractBeanNameFromAttribute(null))
    }

    @Test
    fun `factory bean name uses the only candidate`() {
        assertEquals("userService", BeanNameResolver.chooseFactoryBeanName(listOf("userService", "userService"), "other"))
        assertEquals(null, BeanNameResolver.chooseFactoryBeanName(listOf("repo", "other"), "userService"))
    }

    @Test
    fun `factory bean name prefers the spring default when several differ`() {
        assertEquals(
            "userService",
            BeanNameResolver.chooseFactoryBeanName(listOf("userService", "backup"), "userService")
        )
        assertEquals(null, BeanNameResolver.chooseFactoryBeanName(listOf("alpha", "beta"), "userService"))
        assertEquals(null, BeanNameResolver.chooseFactoryBeanName(emptyList(), "userService"))
    }

    @Test
    fun `explicit bean name reads the first string literal`() {
        assertEquals("userService", BeanNameResolver.firstExplicitName("{\"userService\", \"alias\"}"))
        assertEquals("userService", BeanNameResolver.firstExplicitName("\"userService\""))
        assertEquals(null, BeanNameResolver.firstExplicitName("{}"))
        assertEquals(null, BeanNameResolver.firstExplicitName("BeanIds.USER"))
    }
}

class TypeDescTest {

    @Test
    fun `nested generic displays inner arguments`() {
        val desc = TypeDesc(
            "java.util.Map",
            listOf(
                TypeDesc("java.lang.String"),
                TypeDesc("java.util.List", listOf(TypeDesc("com.example.User")))
            )
        )
        assertEquals("java.util.Map<java.lang.String, java.util.List<com.example.User>>", desc.display())
        assertTrue(desc.isNested())
        assertFalse(TypeDesc("java.util.List", listOf(TypeDesc("com.example.User"))).isNested())
    }
}

class MethodMetadataExtractorTest {

    @Test
    fun `simplify type strips generics and keeps array brackets`() {
        assertEquals("java.util.List", MethodMetadataExtractor.simplifyType("java.util.List<java.lang.String>"))
        assertEquals("java.lang.String", MethodMetadataExtractor.simplifyType("java.lang.String"))
        assertEquals(
            "java.util.List[]",
            MethodMetadataExtractor.simplifyType("java.util.List<java.lang.String>[]")
        )
        assertEquals(
            "java.util.List[][]",
            MethodMetadataExtractor.simplifyType("java.util.List<java.lang.String>[][]")
        )
        assertEquals("com.example.User[]", MethodMetadataExtractor.simplifyType("com.example.User[]"))
        assertEquals(
            "java.util.Map",
            MethodMetadataExtractor.simplifyType("java.util.Map<java.lang.String, java.util.List<com.example.User>>")
        )
    }
}

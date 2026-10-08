package com.runanywhere.psi

import org.junit.jupiter.api.Assertions.assertEquals
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

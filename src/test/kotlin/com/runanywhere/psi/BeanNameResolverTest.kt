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
    fun `extract bean name removes quotes`() {
        assertEquals("myBean", BeanNameResolver.extractBeanNameFromAttribute("\"myBean\""))
        assertEquals(null, BeanNameResolver.extractBeanNameFromAttribute("\"\""))
        assertEquals(null, BeanNameResolver.extractBeanNameFromAttribute(null))
    }
}

class MethodMetadataExtractorTest {

    @Test
    fun `simplify type strips generics`() {
        assertEquals("java.util.List", MethodMetadataExtractor.simplifyType("java.util.List<java.lang.String>"))
        assertEquals("java.lang.String", MethodMetadataExtractor.simplifyType("java.lang.String"))
    }
}

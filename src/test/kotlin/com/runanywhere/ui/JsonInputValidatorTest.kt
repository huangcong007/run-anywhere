package com.runanywhere.ui

import com.runanywhere.psi.ParamTypeKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonInputValidatorTest {

    @Test
    fun `object requires json object`() {
        assertTrue(JsonInputValidator.isValidJson("{\"id\":1}", ParamTypeKind.OBJECT))
        assertFalse(JsonInputValidator.isValidJson("[1,2]", ParamTypeKind.OBJECT))
        assertFalse(JsonInputValidator.isValidJson("not-json", ParamTypeKind.OBJECT))
    }

    @Test
    fun `collection requires json array`() {
        assertTrue(JsonInputValidator.isValidJson("[1,2,3]", ParamTypeKind.COLLECTION))
        assertFalse(JsonInputValidator.isValidJson("{\"id\":1}", ParamTypeKind.COLLECTION))
    }

    @Test
    fun `empty json is allowed`() {
        assertTrue(JsonInputValidator.isValidJson("", ParamTypeKind.OBJECT))
        assertTrue(JsonInputValidator.isValidJson("   ", ParamTypeKind.MAP))
    }
}

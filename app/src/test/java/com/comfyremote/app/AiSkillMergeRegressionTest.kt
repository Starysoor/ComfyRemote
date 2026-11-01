package com.comfyremote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSkillMergeRegressionTest {
    @Test
    fun builtinUpgradePreservesEditedBuiltinAndCustomSkills() {
        val editedBuiltin = AiSkillDef(AiSkillDef.DEFAULT_NATURAL_ID, "我的扩写", "edited system prompt", true)
        val custom = AiSkillDef("custom_keep", "My custom", "custom system prompt", false)

        val merged = AiSkillDef.mergeBuiltins(listOf(editedBuiltin, custom))

        assertEquals("edited system prompt", merged.first { it.id == AiSkillDef.DEFAULT_NATURAL_ID }.systemPrompt)
        assertTrue(merged.any { it.id == "custom_keep" && it.systemPrompt == "custom system prompt" && !it.builtin })
        assertTrue(merged.any { it.id == AiSkillDef.DEFAULT_EN_ID })
        assertTrue(merged.any { it.id == AiSkillDef.DEFAULT_NEG_ID })
        assertTrue(merged.any { it.id == AiSkillDef.DEFAULT_TRANSLATE_ID })
    }
}

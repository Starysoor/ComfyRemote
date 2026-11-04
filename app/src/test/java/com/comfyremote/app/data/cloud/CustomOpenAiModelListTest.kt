package com.comfyremote.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomOpenAiModelListTest {
    @Test
    fun refreshingCustomModelsDoesNotEraseOpaqueUserSelectedIds() {
        val models = mergeCustomModels(
            saved = listOf("my-vision-pro", "private-model-42"),
            discovered = listOf("deepseek-chat", "Qwen-Image", "my-vision-pro"),
        )
        assertEquals(listOf("my-vision-pro", "private-model-42", "Qwen-Image"), models)
    }

    @Test
    fun autodiscoveryStillExcludesObviousPureTextModels() {
        assertEquals(
            listOf("Qwen-Image", "gpt-image-2"),
            mergeCustomModels(emptyList(), listOf("deepseek-chat", "Qwen-Image", "gpt-image-2")),
        )
    }
}

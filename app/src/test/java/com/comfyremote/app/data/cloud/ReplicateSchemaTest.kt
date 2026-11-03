package com.comfyremote.app.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplicateSchemaTest {

    @Test
    fun openApiInputSchemaBecomesEditableRules() {
        val properties = JSONObject()
            .put("prompt", JSONObject().put("type", "string").put("description", "text prompt"))
            .put(
                "aspect_ratio",
                JSONObject().put("type", "string")
                    .put("enum", JSONArray().put("1:1").put("16:9"))
                    .put("default", "1:1"),
            )
            .put("num_outputs", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 4))
            .put("seed", JSONObject().put("type", "integer"))
            .put("image", JSONObject().put("type", "string").put("format", "uri"))
            .put("disable_safety_checker", JSONObject().put("type", "boolean"))
            .put("lora_weights", JSONObject().put("type", "object"))
        val input = JSONObject()
            .put("properties", properties)
            .put("required", JSONArray().put("prompt"))

        val rules = ReplicateSchema.rules(input)

        assertEquals("prompt", rules.first { it.name == "prompt" }.type)
        assertTrue(rules.first { it.name == "prompt" }.required)
        assertEquals(listOf("1:1", "16:9"), rules.first { it.name == "aspect_ratio" }.options)
        assertEquals("integer", rules.first { it.name == "num_outputs" }.type)
        assertEquals(1.0, rules.first { it.name == "num_outputs" }.min!!, 1e-9)
        assertEquals("image", rules.first { it.name == "image" }.type)
        assertEquals("boolean", rules.first { it.name == "disable_safety_checker" }.type)
        assertTrue(rules.none { it.name == "lora_weights" })
    }

    @Test
    fun schemaWithoutTextGetsFallbackPrompt() {
        val input = JSONObject()
            .put("properties", JSONObject().put("seed", JSONObject().put("type", "integer")))
            .put("required", JSONArray())

        val rules = ReplicateSchema.rules(input)

        assertEquals("prompt", rules.last().name)
        assertTrue(rules.last().required)
    }
}

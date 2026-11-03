package com.comfyremote.app.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTemplateTest {

    @Test
    fun templateRenderingKeepsTypedValuesAndReplacesTextPlaceholders() {
        val template = JSONObject()
            .put("model", "demo")
            .put("prompt", "{{prompt}}")
            .put("steps", "{{steps}}")
            .put("watermark", "{{watermark}}")
            .put("guidance", "{{guidance}}")
            .put("label", "run-{{name}}")
            .put("stepLabel", "run-{{steps}}")
        val rendered = TemplateValues.render(
            template,
            mapOf(
                "prompt" to "a castle",
                "steps" to "20",
                "watermark" to "true",
                "guidance" to "7.5",
                "name" to "x",
            ),
            "secret",
            typedValues = JSONObject()
                .put("prompt", "a castle")
                .put("steps", 20L)
                .put("watermark", true)
                .put("guidance", 7.5)
                .put("name", "x"),
        )!!

        assertEquals("a castle", rendered.getString("prompt"))
        assertEquals(20L, rendered.getLong("steps"))
        assertEquals(true, rendered.getBoolean("watermark"))
        assertEquals(7.5, rendered.getDouble("guidance"), 0.0)
        assertEquals("run-x", rendered.getString("label"))
        assertEquals("run-20", rendered.getString("stepLabel"))
    }

    @Test
    fun placeholdersAreDiscoveredFromNestedBody() {
        val body = JSONObject()
            .put("input", JSONObject().put("prompt", "{{prompt}}").put("image", "{{image}}"))
            .put("token", "{{token}}")

        assertEquals(setOf("prompt", "image"), TemplateValues.placeholders(body).toSet())
    }

    @Test
    fun samplePairsInferCommonPathsAndFields() {
        val config = TemplateValues.inferConfig(
            sampleRequest = """{"model":"demo","prompt":"hello","steps":20}""",
            sampleResponse = """{"data":{"task_id":"abc","status":"succeeded","videos":[{"url":"https://x/v.mp4"}]}}""",
        )

        assertEquals("data.task_id", config.optString("taskIdPath"))
        assertEquals("data.status", config.optString("statusPath"))
        assertEquals("data.videos", config.optString("resultUrlPath"))
        val fields = config.optJSONArray("fields")!!
        assertTrue((0 until fields.length()).any { fields.getJSONObject(it).getString("name") == "prompt" })
        assertEquals(
            "{{prompt}}",
            config.getJSONObject("bodyTemplate").getString("prompt"),
        )
    }

    @Test
    fun templateConfigParsesManualFieldsAndStatusValues() {
        val raw = JSONObject()
            .put("submitUrl", "https://example.com/submit")
            .put("bodyTemplate", JSONObject().put("prompt", "{{prompt}}"))
            .put("statusUrlTemplate", "https://example.com/status/{{taskId}}")
            .put("resultUrlPath", "result.images")
            .put("fields", JSONArray().put(
                JSONObject().put("name", "prompt").put("type", "prompt").put("required", true)
            ))
            .toString()

        val config = TemplateConfig.parse(raw, "token").getOrThrow()

        assertEquals("https://example.com/submit", config.submitUrl)
        assertEquals(1, config.fields.size)
        assertTrue(config.fields.single().required)
        assertEquals("prompt", config.fields.single().type)
    }

    @Test
    fun templateConfigParsesAnOptionalHealthUrl() {
        val raw = JSONObject()
            .put("submitUrl", "https://example.com/submit")
            .put("healthUrl", "https://example.com/health")
            .toString()

        val config = TemplateConfig.parse(raw, "token").getOrThrow()

        assertEquals("https://example.com/health", config.healthUrl)
    }

    @Test
    fun templateWithoutHealthUrlIsConfiguredButUnverified() {
        assertEquals(
            CloudHealthStatus.CONFIGURED_UNVERIFIED,
            templatePreflightHealth(true, "")!!.status,
        )
        assertEquals(
            CloudHealthStatus.NOT_CONFIGURED,
            templatePreflightHealth(false, "https://example.com/health")!!.status,
        )
        assertTrue(templatePreflightHealth(true, "https://example.com/health") == null)
    }
}

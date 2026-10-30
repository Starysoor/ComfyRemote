package com.comfyremote.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the UI->API converter against real workflow fixtures fetched from the
 * live ComfyUI install (node tools/fetch_fixtures.mjs refreshes them).
 */
class WorkflowConverterTest {

    private fun readRes(p: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(p)
        if (stream == null) {
            val files = java.io.File("app/src/test/resources/fixtures").list()?.joinToString(",") ?: "DIR-MISSING"
            throw AssertionError("resource missing: $p | loader=${javaClass.classLoader} | fixtures dir=[$files] | cwd=${java.io.File(".").absolutePath}")
        }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    private fun info(): JSONObject = JSONObject(readRes("fixtures/object_info_trimmed.json"))

    private fun fixtureNames(): List<String> = listOf(
        "moss_a.json",
        "moss_b.json",
        "moss_layout.json",
        "qwen_turbo.json",
        "qwen_i2i.json",
        "qwen_edit.json",
        "qwen_t2i_all.json",
        "sdxl_anime.json",
        "sdxl_hires.json",
        "anima_aesthetic.json",
        "anima_turbo.json",
    )

    @Test
    fun allFixturesConvertWithoutWarnings() {
        val info = info()
        fixtureNames().forEach { name ->
            val conv = WorkflowConverter.convert(readRes("fixtures/$name"), info)
            assertTrue("$name: warnings ${conv.warnings}", conv.warnings.isEmpty())
            assertTrue("$name: no api nodes", conv.prompt.length() > 0)
            assertTrue("$name: no text fields", conv.fields.any { it.kind == FieldKind.TEXT })
            assertTrue("$name: no seed fields", conv.fields.any { it.kind == FieldKind.SEED })
        }
    }

    @Test
    fun sdxlStructureMatchesExpected() {
        val conv = WorkflowConverter.convert(readRes("fixtures/sdxl_anime.json"), info())
        val texts = conv.fields.filter { it.kind == FieldKind.TEXT }
        val seeds = conv.fields.filter { it.kind == FieldKind.SEED }
        val params = conv.fields.filter { it.kind == FieldKind.PARAM }
        assertEquals(3, texts.size)   // positive, negative, filename_prefix
        assertEquals(1, seeds.size)   // KSampler seed
        assertEquals(1, conv.notes.size) // MarkdownNote captured
        val ks = conv.prompt.optJSONObject("5")!!.optJSONObject("inputs")!!
        for (k in listOf("seed", "steps", "cfg", "sampler_name", "scheduler", "denoise"))
            assertTrue("KSampler missing $k", ks.has(k))
        assertTrue("combo param options not populated", params.any { it.ptype == "combo" && it.options.isNotEmpty() })
        val sampler = params.first { it.input == "sampler_name" }
        assertTrue(sampler.options.contains("euler"))
    }

    @Test
    fun img2imgDiscoversImageField() {
        val conv = WorkflowConverter.convert(readRes("fixtures/qwen_i2i.json"), info())
        val images = conv.fields.filter { it.kind == FieldKind.IMAGE }
        assertEquals(1, images.size)
        assertEquals("example.png", images[0].value)
    }

    @Test
    fun hiresFixHasTwoSeeds() {
        val conv = WorkflowConverter.convert(readRes("fixtures/sdxl_hires.json"), info())
        assertEquals(2, conv.fields.count { it.kind == FieldKind.SEED })
    }

    @Test
    fun buildPromptOverridesTextAndRandomizesSeed() {
        val conv = WorkflowConverter.convert(readRes("fixtures/sdxl_anime.json"), info())
        val f = conv.fields.first { it.kind == FieldKind.TEXT && it.input == "text" && it.nodeId == "2" }
        val values = mapOf(f.key to "1girl, test override")
        val (random1, seeds1) = WorkflowConverter.buildPrompt(conv, values, randomSeed = true)
        assertEquals("1girl, test override", random1.optJSONObject("2")!!.optJSONObject("inputs")!!.optString("text"))
        val seedStr = seeds1.values.first().toLong()
        assertTrue(seedStr in 0 until WorkflowConverter.SEED_MAX)

        val (fixed, seeds2) = WorkflowConverter.buildPrompt(conv, seeds1, randomSeed = false)
        val ks = fixed.optJSONObject("5")!!.optJSONObject("inputs")!!
        assertEquals(seeds1.values.first().toLong(), ks.optLong("seed"))
        assertEquals(seeds1["5:seed"], seeds2["5:seed"])
    }

    @Test
    fun buildPromptAppliesIntAndComboParams() {
        val conv = WorkflowConverter.convert(readRes("fixtures/sdxl_anime.json"), info())
        val steps = conv.fields.first { it.input == "steps" }
        val sampler = conv.fields.first { it.input == "sampler_name" }
        val values = mapOf(steps.key to "10", sampler.key to "euler")
        val (prompt, _) = WorkflowConverter.buildPrompt(conv, values, randomSeed = true)
        val ks = prompt.optJSONObject("5")!!.optJSONObject("inputs")!!
        assertEquals(10L, ks.optLong("steps"))
        assertEquals("euler", ks.optString("sampler_name"))
    }

    @Test
    fun convertedPromptStillValidatesAgainstObjectInfo() {
        // every converted node must exist in object_info and only use known inputs
        val info = info()
        fixtureNames().forEach { name ->
            val conv = WorkflowConverter.convert(readRes("fixtures/$name"), info)
            conv.prompt.keys().forEach { id ->
                val node = conv.prompt.optJSONObject(id)!!
                val type = node.optString("class_type")
                assertTrue("$name: node $id type $type not in object_info", info.has(type))
                val def = info.optJSONObject(type)!!
                val schema = mutableSetOf<String>()
                listOf("required", "optional").forEach { sec ->
                    def.optJSONObject("input")?.optJSONObject(sec)?.let {
                        it.keys().forEach { k -> schema.add(k) }
                    }
                }
                node.optJSONObject("inputs")!!.keys().forEach { inputName ->
                    // dynamic AUTOGROW inputs (e.g. images.image_1) are exempt
                    val known = inputName in schema || inputName.contains(".")
                    assertTrue("$name: node $id unknown input $inputName", known)
                }
            }
        }
    }
}

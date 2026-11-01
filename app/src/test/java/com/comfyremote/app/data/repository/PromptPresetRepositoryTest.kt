package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.LabeledText
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPresetRepositoryTest {

    @Test
    fun legacyBookmarksLoadAndMigrateWithoutDataLoss() {
        val prefs = FakePreferences()
        val legacy = JSONArray().put(
            JSONObject()
                .put("id", "old-1")
                .put("title", "old prompt")
                .put("time", 123L)
                .put("prompts", JSONArray().put(JSONObject().put("label", "Prompt").put("value", "castle in fog")))
        )
        prefs.edit().putString("bookmarks", legacy.toString()).apply()

        val repo = PromptPresetRepository(prefs)
        val loaded = repo.load()

        assertEquals(1, loaded.size)
        assertEquals("old-1", loaded.single().id)
        assertEquals("castle in fog", loaded.single().prompts.single().value)

        repo.add(
            PromptPreset(
                id = "new-1",
                title = "new",
                prompts = listOf(LabeledText("Prompt", "new text")),
                workflowPath = "a.json",
                createdAt = 456L,
            )
        )

        assertNotNull(prefs.getString("promptPresets", null))
        assertFalse(prefs.contains("bookmarks"))
        assertEquals(setOf("old-1", "new-1"), PromptPresetRepository(prefs).load().map { it.id }.toSet())
    }

    @Test
    fun markUsedMovesPresetAheadOfOlderItems() {
        val prefs = FakePreferences()
        val repo = PromptPresetRepository(prefs)
        repo.add(PromptPreset("a", "A", listOf(LabeledText("Prompt", "a")), null, 100L))
        repo.add(PromptPreset("b", "B", listOf(LabeledText("Prompt", "b")), null, 200L))

        repo.markUsed("a")

        assertEquals("a", repo.load().first().id)
        assertTrue(repo.load().first().lastUsedAt != null)
    }

    @Test
    fun workflowPresetMetadataRoundTripsAndFavoriteSortsFirst() {
        val prefs = FakePreferences()
        val repo = PromptPresetRepository(prefs)
        repo.add(
            PromptPreset(
                id = "setup",
                title = "Portrait setup",
                prompts = listOf(LabeledText("Prompt", "portrait")),
                workflowPath = "portrait.json",
                createdAt = 100L,
                values = mapOf("1:text" to "portrait", "2:seed" to "42", "3:cfg" to "6.5"),
                randomSeed = false,
                tags = listOf("workflow", "portrait"),
            )
        )
        repo.add(PromptPreset("prompt", "Prompt only", listOf(LabeledText("Prompt", "x")), null, 200L))
        repo.toggleFavorite("setup")

        val restored = PromptPresetRepository(prefs).load()
        val setup = restored.first { it.id == "setup" }

        assertTrue(setup.isWorkflowPreset)
        assertTrue(setup.favorite)
        assertEquals("42", setup.values["2:seed"])
        assertEquals(false, setup.randomSeed)
        assertEquals(listOf("workflow", "portrait"), setup.tags)
        assertEquals("setup", restored.first().id)
    }

    private class FakePreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) updates[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) updates[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) updates[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) updates[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) updates[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) removals += key }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) values.clear()
                removals.forEach(values::remove)
                updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }
}

package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.AiSkillDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSkillBindingStoreTest {
    @Test
    fun blankBindingIsGlobalAndKeywordsFilterByWorkflowNameOrPath() {
        val store = AiSkillBindingStore(FakePreferences())
        val global = AiSkillDef("global", "Global", "global prompt", false)
        val sdxl = AiSkillDef("sdxl", "SDXL", "sdxl prompt", false)
        val video = AiSkillDef("video", "Video", "video prompt", false)
        val skills = listOf(global, sdxl, video)

        store.setPatterns("sdxl", listOf("SDXL", "illustrious"))
        store.setPatterns("video", listOf("video", "wan"))

        assertEquals(listOf("global", "sdxl"), store.filter(skills, "SDXL Portrait", "portrait.json").map { it.id })
        assertEquals(listOf("global", "video"), store.filter(skills, "Cinematic", "video/wan_i2v.json").map { it.id })
        assertEquals(listOf("global"), store.filter(skills, "Flux", "flux.json").map { it.id })
        assertTrue(store.patterns("global").isEmpty())
    }

    @Test
    fun bindingRoundTripsAndDeleteRestoresGlobalBehavior() {
        val prefs = FakePreferences()
        val store = AiSkillBindingStore(prefs)
        val skill = AiSkillDef("custom", "Custom", "prompt", false)
        store.setPatterns(skill.id, listOf("portrait", "portrait", " anime "))

        assertEquals(listOf("portrait", "anime"), AiSkillBindingStore(prefs).patterns(skill.id))
        assertTrue(store.filter(listOf(skill), "Landscape", "landscape.json").isEmpty())

        store.delete(skill.id)
        assertEquals(listOf(skill), store.filter(listOf(skill), "Landscape", "landscape.json"))
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

package com.comfyremote.app.data.repository

import android.content.SharedPreferences
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.AiSkills

/** Persists the AI expansion skill list (built-ins merged in on read). */
class AiSkillStore(private val prefs: SharedPreferences) {

    fun load(): List<AiSkillDef> = AiSkills.load(prefs)

    fun save(skills: List<AiSkillDef>) = AiSkills.save(prefs, skills)
}

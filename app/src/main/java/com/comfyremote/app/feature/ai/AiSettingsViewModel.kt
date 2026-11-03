package com.comfyremote.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.repository.AiSettings
import com.comfyremote.app.R
import com.comfyremote.app.ui.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
    val skills: List<AiSkillDef> = AiSkillDef.defaults(),
    val skillBindings: Map<String, List<String>> = emptyMap(),
    val testing: Boolean = false,
    val testResult: String? = null,
    val message: String? = null,
)

class AiSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        AiSettingsUiState(
            settings = container.aiSettingsStore.load(),
            skills = container.aiSkillStore.load(),
            skillBindings = container.aiSkillBindingStore.bindings(),
        )
    )
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    fun updateBase(v: String) = edit { it.copy(baseUrl = v) }
    fun updateKey(v: String) = edit { it.copy(apiKey = v) }
    fun updateModel(v: String) = edit { it.copy(model = v) }
    fun updateVisionModel(v: String) = edit { it.copy(visionModel = v) }

    fun applyPreset(baseUrl: String, model: String, visionModel: String) = edit {
        it.copy(baseUrl = baseUrl, model = model, visionModel = visionModel)
    }
    fun updateTemp(v: String) = edit { it.copy(temperatureText = v.filter { c -> c.isDigit() || c == '.' }) }
    fun updateMaxTokens(v: String) = edit { it.copy(maxTokensText = v.filter(Char::isDigit)) }
    fun updateExtra(v: String) = edit { it.copy(extraJson = v) }
    fun updateTagProfile(v: String) = edit { it.copy(tagProfile = v) }

    fun applyThinkingMode(mode: String) {
        val current = _state.value.settings
        val extra = runCatching {
            if (current.extraJson.isBlank()) JSONObject() else JSONObject(current.extraJson)
        }.getOrDefault(JSONObject())
        when (mode) {
            "glm_off" -> extra.put("thinking", JSONObject().put("type", "disabled"))
            "glm_on" -> extra.put("thinking", JSONObject().put("type", "enabled"))
            "low", "medium", "high" -> extra.put("reasoning_effort", mode)
            else -> {
                extra.remove("thinking")
                extra.remove("reasoning_effort")
            }
        }
        edit { it.copy(thinkingMode = mode, extraJson = extra.toString()) }
    }

    fun save() {
        val s = _state.value.settings.copy(
            baseUrl = _state.value.settings.baseUrl.trim(),
            apiKey = _state.value.settings.apiKey.trim(),
            model = _state.value.settings.model.trim(),
        )
        container.aiSettingsStore.save(s)
        _state.update { it.copy(settings = s, message = AppStrings.get(R.string.ai_saved)) }
    }

    fun test() {
        if (_state.value.testing) return
        val settings = _state.value.settings
        if (!settings.configured) {
            _state.update { it.copy(testResult = AppStrings.get(R.string.ai_fill_first)) }
            return
        }
        _state.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            runCatching { container.aiRepository.test(settings) }
                .onSuccess { reply -> _state.update { it.copy(testing = false, testResult = AppStrings.get(R.string.ai_test_ok, reply.take(80))) } }
                .onFailure { e -> _state.update { it.copy(testing = false, testResult = AppStrings.get(R.string.ai_test_failed, (e.message ?: "error").take(200))) } }
        }
    }

    fun patterns(skillId: String): List<String> = _state.value.skillBindings[skillId].orEmpty()

    fun saveSkill(def: AiSkillDef, patterns: List<String> = patterns(def.id)) {
        val list = _state.value.skills.toMutableList()
        val index = list.indexOfFirst { it.id == def.id }
        if (index >= 0) list[index] = def else list += def
        persistSkills(list)
        container.aiSkillBindingStore.setPatterns(def.id, patterns)
        refreshBindings()
    }

    fun addSkill(label: String, prompt: String, patterns: List<String>) {
        val id = "custom_${System.currentTimeMillis()}"
        persistSkills(_state.value.skills + AiSkillDef(id, label.trim(), prompt, false))
        container.aiSkillBindingStore.setPatterns(id, patterns)
        refreshBindings()
    }

    fun deleteSkill(id: String) {
        persistSkills(_state.value.skills.filter { it.id != id })
        container.aiSkillBindingStore.delete(id)
        refreshBindings()
    }

    fun resetSkill(id: String) {
        val def = AiSkillDef.defaults().firstOrNull { it.id == id } ?: return
        saveSkill(def, patterns(id))
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun edit(block: (AiSettings) -> AiSettings) {
        _state.update { it.copy(settings = block(it.settings), testResult = null) }
    }

    private fun persistSkills(skills: List<AiSkillDef>) {
        container.aiSkillStore.save(skills)
        _state.update { it.copy(skills = skills) }
    }

    private fun refreshBindings() {
        _state.update { it.copy(skillBindings = container.aiSkillBindingStore.bindings()) }
    }
}

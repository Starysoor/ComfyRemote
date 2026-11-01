package com.comfyremote.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.repository.AiSettings
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
    val skills: List<AiSkillDef> = AiSkillDef.defaults(),
    val testing: Boolean = false,
    val testResult: String? = null,
    val message: String? = null,
)

class AiSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        AiSettingsUiState(
            settings = container.aiSettingsStore.load(),
            skills = container.aiSkillStore.load(),
        )
    )
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    fun updateBase(v: String) = edit { it.copy(baseUrl = v) }
    fun updateKey(v: String) = edit { it.copy(apiKey = v) }
    fun updateModel(v: String) = edit { it.copy(model = v) }
    fun updateTemp(v: String) = edit { it.copy(temperatureText = v.filter { c -> c.isDigit() || c == '.' }) }
    fun updateMaxTokens(v: String) = edit { it.copy(maxTokensText = v.filter(Char::isDigit)) }
    fun updateExtra(v: String) = edit { it.copy(extraJson = v) }

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
        _state.update { it.copy(settings = s, message = L10n.t("AI 设置已保存", "AI settings saved")) }
    }

    fun test() {
        if (_state.value.testing) return
        val settings = _state.value.settings
        if (!settings.configured) {
            _state.update { it.copy(testResult = L10n.t("请先填写 Base URL、API Key 和模型", "Fill Base URL, API Key and model first")) }
            return
        }
        _state.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            runCatching { container.aiRepository.test(settings) }
                .onSuccess { reply -> _state.update { it.copy(testing = false, testResult = L10n.t("成功：", "OK: ") + reply.take(80)) } }
                .onFailure { e -> _state.update { it.copy(testing = false, testResult = L10n.t("失败：", "Failed: ") + (e.message ?: "error").take(200)) } }
        }
    }

    fun saveSkill(def: AiSkillDef) {
        val list = _state.value.skills.toMutableList()
        val index = list.indexOfFirst { it.id == def.id }
        if (index >= 0) list[index] = def else list += def
        persistSkills(list)
    }

    fun addSkill(label: String, prompt: String) {
        persistSkills(_state.value.skills + AiSkillDef("custom_${System.currentTimeMillis()}", label.trim(), prompt, false))
    }

    fun deleteSkill(id: String) = persistSkills(_state.value.skills.filter { it.id != id })

    fun resetSkill(id: String) {
        val def = AiSkillDef.defaults().firstOrNull { it.id == id } ?: return
        saveSkill(def)
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun edit(block: (AiSettings) -> AiSettings) {
        _state.update { it.copy(settings = block(it.settings), testResult = null) }
    }

    private fun persistSkills(skills: List<AiSkillDef>) {
        container.aiSkillStore.save(skills)
        _state.update { it.copy(skills = skills) }
    }
}

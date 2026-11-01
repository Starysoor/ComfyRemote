package com.comfyremote.app.feature.generate

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AiResult
import com.comfyremote.app.AiSkillDef
import com.comfyremote.app.AppContainer
import com.comfyremote.app.ComfyApi
import com.comfyremote.app.ComfyException
import com.comfyremote.app.ConvertedWorkflow
import com.comfyremote.app.FieldKind
import com.comfyremote.app.LabeledText
import com.comfyremote.app.PromptField
import com.comfyremote.app.WorkflowEntry
import com.comfyremote.app.data.generation.GenerationPhase
import com.comfyremote.app.data.generation.GenerationRequest
import com.comfyremote.app.data.generation.GenerationTask
import com.comfyremote.app.data.media.ImagePreprocessor
import com.comfyremote.app.data.repository.PromptPreset
import com.comfyremote.app.ui.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WorkflowDraft(
    val workflowPath: String,
    val workflowName: String,
    val fields: List<PromptField>,
    val values: Map<String, String> = emptyMap(),
    val randomSeed: Boolean = true,
    val warnings: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val nodeTitles: Map<String, String> = emptyMap(),
)

enum class ImageUploadPhase { UPLOADING, FAILED }

data class ImageUploadStatus(
    val phase: ImageUploadPhase,
    val message: String? = null,
)

data class GenerateUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val draft: WorkflowDraft? = null,
    val activeTaskId: String? = null,
    val skills: List<AiSkillDef> = AiSkillDef.defaults(),
    val aiBusy: String? = null,
    val aiResult: AiResult? = null,
    val presets: List<PromptPreset> = emptyList(),
    val presetQuery: String = "",
    val uploads: Map<String, ImageUploadStatus> = emptyMap(),
    val message: String? = null,
) {
    val filteredPresets: List<PromptPreset>
        get() {
            val q = presetQuery.trim()
            val path = draft?.workflowPath
            return presets.asSequence()
                .filter { p ->
                    q.isBlank() || p.title.contains(q, true) ||
                        p.prompts.any { it.value.contains(q, true) || it.label.contains(q, true) } ||
                        p.tags.any { it.contains(q, true) } ||
                        p.values.values.any { it.contains(q, true) }
                }
                .sortedWith(
                    compareByDescending<PromptPreset> { it.favorite }
                        .thenByDescending { it.workflowPath != null && it.workflowPath == path }
                        .thenByDescending { it.lastUsedAt ?: 0L }
                        .thenByDescending { it.createdAt }
                )
                .toList()
        }
}

class GenerateViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        GenerateUiState(
            skills = container.aiSkillStore.load(),
            presets = container.promptPresetRepository.load(),
        )
    )
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()
    val tasks: StateFlow<List<GenerationTask>> = container.generationManager.tasks

    private var converted: ConvertedWorkflow? = null
    private var loadedPath: String? = null
    private val appliedSeedTasks = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            container.promptPresetRepository.presets.collect { presets ->
                _state.update { it.copy(presets = presets) }
            }
        }
        viewModelScope.launch {
            container.incomingMedia.pending.collect { pending ->
                if (pending.isNotEmpty() && _state.value.draft?.fields?.any { it.kind == FieldKind.IMAGE } == true) {
                    consumeIncomingMedia()
                }
            }
        }
        viewModelScope.launch {
            container.generationManager.tasks.collect { list ->
                val activeId = _state.value.activeTaskId ?: return@collect
                val task = list.firstOrNull { it.taskId == activeId } ?: return@collect
                if (task.phase == GenerationPhase.SUCCEEDED && task.seedValues.isNotEmpty() && appliedSeedTasks.add(task.taskId)) {
                    updateDraft { draft ->
                        if (draft.workflowPath == task.request.workflowPath) {
                            draft.copy(values = draft.values + task.seedValues)
                        } else draft
                    }
                }
            }
        }
    }

    fun loadWorkflow(path: String, name: String) {
        if (path.isBlank()) return
        if (loadedPath == path && (_state.value.loading || _state.value.draft != null)) return
        loadedPath = path
        _state.update { it.copy(loading = true, error = null, draft = null) }
        viewModelScope.launch {
            runCatching { container.workflowRepository.convert(WorkflowEntry(path, name, "")) }
                .onSuccess { conv ->
                    converted = conv
                    val titles = conv.prompt.keys().asSequence().associateWith { nodeId ->
                        conv.prompt.optJSONObject(nodeId)?.optJSONObject("_meta")?.optString("title").orEmpty()
                    }
                    val transfer = container.draftTransferRepository.consume(path)
                    _state.update {
                        it.copy(
                            loading = false,
                            draft = WorkflowDraft(
                                workflowPath = path,
                                workflowName = name,
                                fields = conv.fields,
                                values = transfer?.values.orEmpty(),
                                randomSeed = transfer?.randomSeed ?: true,
                                warnings = conv.warnings,
                                notes = conv.notes,
                                nodeTitles = titles,
                            ),
                            activeTaskId = null,
                        )
                    }
                    consumeIncomingMedia()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "error") }
                }
        }
    }

    fun updateValue(key: String, value: String) = updateDraft { it.copy(values = it.values + (key to value)) }
    fun toggleRandomSeed() = updateDraft { it.copy(randomSeed = !it.randomSeed) }
    fun setPresetQuery(value: String) = _state.update { it.copy(presetQuery = value) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun dismissAiResult() = _state.update { it.copy(aiResult = null) }

    fun generate() {
        val draft = _state.value.draft ?: return
        val cv = converted ?: return
        if (!container.serverSession.connected) {
            _state.update { it.copy(message = L10n.t("服务器已断开", "Server disconnected")) }
            return
        }
        val currentId = _state.value.activeTaskId
        if (currentId != null && tasks.value.firstOrNull { it.taskId == currentId }?.isActive == true) return
        val request = GenerationRequest(
            serverUrl = container.serverSession.url,
            workflowPath = draft.workflowPath,
            workflowName = draft.workflowName,
            fieldValues = draft.values,
            randomSeed = draft.randomSeed,
            nodeTitles = draft.nodeTitles,
            fields = draft.fields,
            convertedPrompt = JSONObject(cv.prompt.toString()),
        )
        val taskId = container.generationManager.submit(request)
        _state.update { it.copy(activeTaskId = taskId) }
    }

    fun cancelActiveTask() {
        val id = _state.value.activeTaskId ?: return
        viewModelScope.launch { container.generationManager.cancel(id) }
    }

    fun retryActiveTask() {
        val id = _state.value.activeTaskId ?: return
        container.generationManager.retry(id)?.let { newId -> _state.update { it.copy(activeTaskId = newId) } }
    }

    fun aiExpand(fieldKey: String, currentText: String, skill: AiSkillDef) {
        if (currentText.isBlank()) {
            _state.update { it.copy(message = L10n.t("先输入一些提示词", "Type a prompt first")) }
            return
        }
        val settings = container.aiSettingsStore.load()
        if (!settings.configured) {
            _state.update { it.copy(message = L10n.t("请先配置 AI 设置", "Configure AI settings first")) }
            return
        }
        val busy = "$fieldKey|${skill.id}"
        _state.update { it.copy(aiBusy = busy) }
        viewModelScope.launch {
            runCatching { container.aiRepository.chat(skill.systemPrompt, currentText) }
                .onSuccess { out -> _state.update { it.copy(aiBusy = null, aiResult = AiResult(fieldKey, out)) } }
                .onFailure { e -> _state.update { it.copy(aiBusy = null, message = e.message ?: "AI error") } }
        }
    }

    fun applyAi(replace: Boolean, newText: String) {
        val result = _state.value.aiResult ?: return
        updateDraft { draft ->
            val field = draft.fields.firstOrNull { it.key == result.fieldKey }
            val current = draft.values[result.fieldKey] ?: field?.value.orEmpty()
            val merged = if (replace || current.isBlank()) newText else "$current, $newText"
            draft.copy(values = draft.values + (result.fieldKey to merged))
        }
        dismissAiResult()
    }

    /** Saves only text prompts; compatible with legacy Prompt Library behavior. */
    fun saveCurrentPreset() {
        val draft = _state.value.draft ?: return
        val prompts = currentPrompts(draft)
        if (prompts.isEmpty()) {
            _state.update { it.copy(message = L10n.t("当前没有可保存的提示词", "Nothing to save")) }
            return
        }
        container.promptPresetRepository.add(
            PromptPreset(
                id = "preset_${System.currentTimeMillis()}",
                title = prompts.first().value.take(32),
                prompts = prompts,
                workflowPath = draft.workflowPath,
                createdAt = System.currentTimeMillis(),
                tags = listOf("prompt"),
            )
        )
        _state.update { it.copy(message = L10n.t("已保存到提示词库", "Saved to prompt library")) }
    }

    /** Saves the entire editable workflow snapshot, including image/advanced params and seed mode. */
    fun saveCurrentWorkflowPreset() {
        val draft = _state.value.draft ?: return
        val values = draft.fields.associate { field -> field.key to (draft.values[field.key] ?: field.value) }
        val prompts = currentPrompts(draft)
        container.promptPresetRepository.add(
            PromptPreset(
                id = "workflow_${System.currentTimeMillis()}",
                title = (prompts.firstOrNull()?.value?.take(28)?.takeIf(String::isNotBlank) ?: draft.workflowName),
                prompts = prompts,
                workflowPath = draft.workflowPath,
                createdAt = System.currentTimeMillis(),
                values = values,
                randomSeed = draft.randomSeed,
                tags = listOf("workflow"),
            )
        )
        _state.update { it.copy(message = L10n.t("已保存工作流预设", "Workflow preset saved")) }
    }

    fun deletePreset(id: String) { container.promptPresetRepository.delete(id) }
    fun togglePresetFavorite(id: String) { container.promptPresetRepository.toggleFavorite(id) }

    fun applyPreset(preset: PromptPreset) {
        container.promptPresetRepository.markUsed(preset.id)
        updateDraft { draft ->
            if (preset.isWorkflowPreset && preset.workflowPath == draft.workflowPath) {
                val knownKeys = draft.fields.mapTo(hashSetOf()) { it.key }
                val accepted = preset.values.filterKeys { it in knownKeys }
                draft.copy(
                    values = draft.values + accepted,
                    randomSeed = preset.randomSeed ?: draft.randomSeed,
                )
            } else {
                val textFields = draft.fields.filter { it.kind == FieldKind.TEXT }
                val byLabel = preset.prompts.associateBy { it.label }
                var values = draft.values
                textFields.forEachIndexed { index, field ->
                    val prompt = byLabel[field.label] ?: preset.prompts.getOrNull(index)
                    if (prompt != null) values = values + (field.key to prompt.value)
                }
                draft.copy(values = values)
            }
        }
    }

    fun uploadImage(fieldKey: String, uri: Uri) {
        if (!container.serverSession.connected) return
        viewModelScope.launch {
            setUpload(fieldKey, ImageUploadStatus(ImageUploadPhase.UPLOADING))
            runCatching { uploadOne(container.serverSession.require(), uri, 0) }
                .onSuccess { name ->
                    updateValue(fieldKey, name)
                    clearUpload(fieldKey)
                }
                .onFailure { e -> setUpload(fieldKey, ImageUploadStatus(ImageUploadPhase.FAILED, e.message)) }
        }
    }

    fun uploadImagesBatch(uris: List<Uri>) {
        val draft = _state.value.draft ?: return
        val imageFields = draft.fields.filter { it.kind == FieldKind.IMAGE }
        if (uris.isEmpty() || imageFields.isEmpty() || !container.serverSession.connected) return
        viewModelScope.launch {
            imageFields.zip(uris).forEachIndexed { index, (field, uri) ->
                setUpload(field.key, ImageUploadStatus(ImageUploadPhase.UPLOADING))
                runCatching { uploadOne(container.serverSession.require(), uri, index) }
                    .onSuccess { name -> updateValue(field.key, name); clearUpload(field.key) }
                    .onFailure { e -> setUpload(field.key, ImageUploadStatus(ImageUploadPhase.FAILED, e.message)) }
            }
        }
    }

    suspend fun loadInputBitmap(name: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { container.serverSession.require().fetchInputImage(name) }.getOrNull()
    }

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.galleryExporter.savePng(bitmap) }
                .onSuccess { _state.update { it.copy(message = container.galleryExporter.savedMessage()) } }
                .onFailure { e -> _state.update { it.copy(message = container.galleryExporter.failMessage(e)) } }
        }
    }

    private fun currentPrompts(draft: WorkflowDraft): List<LabeledText> = draft.fields
        .filter { it.kind == FieldKind.TEXT }
        .map { LabeledText(it.label, draft.values[it.key] ?: it.value) }
        .filter { it.value.isNotBlank() }

    private fun consumeIncomingMedia() {
        val draft = _state.value.draft ?: return
        if (draft.fields.none { it.kind == FieldKind.IMAGE }) return
        val pending = container.incomingMedia.drain()
        if (pending.isNotEmpty()) uploadImagesBatch(pending)
    }

    private suspend fun uploadOne(api: ComfyApi, uri: Uri, seq: Int): String {
        val ctx = container.app
        val raw = withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw ComfyException(L10n.t("读取图片失败", "Failed to read image"))
        val (bytes, ext) = withContext(Dispatchers.IO) {
            ImagePreprocessor.prepare(raw, ctx.contentResolver.getType(uri))
        }
        return api.uploadImage(bytes, "mobile_${System.currentTimeMillis()}_$seq.$ext")
    }

    private fun updateDraft(block: (WorkflowDraft) -> WorkflowDraft) {
        _state.update { state -> state.copy(draft = state.draft?.let(block)) }
    }

    private fun setUpload(key: String, status: ImageUploadStatus) {
        _state.update { it.copy(uploads = it.uploads + (key to status)) }
    }

    private fun clearUpload(key: String) {
        _state.update { it.copy(uploads = it.uploads - key) }
    }
}

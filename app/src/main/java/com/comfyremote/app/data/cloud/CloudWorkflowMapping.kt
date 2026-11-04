package com.comfyremote.app.data.cloud

import com.comfyremote.app.FieldKind
import com.comfyremote.app.PromptField
import com.comfyremote.app.PromptValidationException
import com.comfyremote.app.data.reference.ReferenceRole
import com.comfyremote.app.data.reference.inferReferenceRole
import com.comfyremote.app.data.repository.WorkflowCapability
import com.comfyremote.app.data.repository.WorkflowCompatibilityIssue
import com.comfyremote.app.data.repository.WorkflowInsight
import org.json.JSONObject
import kotlin.random.Random

/** Maps AutoDL `input_rules` onto the same editable fields the local UI uses. */
object CloudWorkflowMapping {

    fun fields(spec: CloudWorkflowSpec): List<PromptField> = spec.rules.mapNotNull { rule ->
        val label = labelFor(rule.name)
        val value = rule.effectiveDefault.orEmpty()
        when {
            rule.isText -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.TEXT,
                value = value,
                multiline = rule.type == "prompt",
                required = rule.required,
            )
            rule.isSeed -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.SEED,
                value = value,
                required = rule.required,
            )
            rule.isImage -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.IMAGE,
                value = value,
                required = rule.required,
                referenceRole = if (rule.referenceRole != ReferenceRole.AUTO) {
                    rule.referenceRole
                } else {
                    inferReferenceRole(rule.name, label)
                },
            )
            rule.isAudio -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.AUDIO,
                value = value,
                required = rule.required,
            )
            rule.type == "integer" -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.PARAM,
                value = value,
                ptype = "int",
                min = rule.min?.toInt()?.toString(),
                max = rule.max?.toInt()?.toString(),
                required = rule.required,
            )
            rule.type == "number" -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.PARAM,
                value = value,
                ptype = "float",
                min = rule.min?.toString(),
                max = rule.max?.toString(),
                required = rule.required,
            )
            rule.type == "boolean" -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.PARAM,
                value = value.ifBlank { "false" },
                ptype = "bool",
                required = rule.required,
            )
            rule.type == "enum" -> PromptField(
                key = rule.name,
                nodeId = "cloud",
                input = rule.name,
                label = label,
                kind = FieldKind.PARAM,
                value = value.ifBlank { rule.options.firstOrNull().orEmpty() },
                ptype = "combo",
                options = rule.options,
                required = rule.required,
            )
            else -> null // video inputs are not editable yet
        }
    }

    fun warnings(spec: CloudWorkflowSpec): List<String> {
        val out = mutableListOf<String>()
        val unsupportedRequired = spec.rules.filter { it.isVideo && it.required }
        if (unsupportedRequired.isNotEmpty()) {
            out += "此工作流需要视频素材输入，当前版本暂不支持，无法提交"
        } else if (spec.rules.any { it.isVideo }) {
            out += "此工作流支持视频参考；当前版本会忽略这些可选素材"
        }
        return out
    }

    fun analyze(
        spec: CloudWorkflowSpec,
        fields: List<PromptField>,
        values: Map<String, String>,
    ): WorkflowInsight {
        val capabilities = linkedSetOf<WorkflowCapability>()
        val imageFields = fields.filter { it.kind == FieldKind.IMAGE }
        when (spec.kind) {
            "video" -> {
                capabilities += WorkflowCapability.VIDEO
                if (imageFields.isNotEmpty()) capabilities += WorkflowCapability.IMG2VIDEO
                else capabilities += WorkflowCapability.TXT2VIDEO
            }
            "audio" -> capabilities += WorkflowCapability.AUDIO
            else -> if (imageFields.isNotEmpty()) capabilities += WorkflowCapability.IMG2IMG
                else capabilities += WorkflowCapability.TXT2IMG
        }
        if (fields.any { it.kind == FieldKind.AUDIO }) capabilities += WorkflowCapability.AUDIO
        if (imageFields.size > 1) capabilities += WorkflowCapability.MULTI_REFERENCE

        val issues = mutableListOf<WorkflowCompatibilityIssue>()
        warnings(spec).forEach { issue ->
            issues += WorkflowCompatibilityIssue(issue, blocking = issue.contains("无法提交"))
        }
        fields.filter { it.required }.forEach { field ->
            val current = values[field.key] ?: field.value
            if (current.isBlank()) {
                val kind = when (field.kind) {
                    FieldKind.IMAGE -> "参考图片"
                    FieldKind.AUDIO -> "参考音频"
                    FieldKind.TEXT -> "提示词"
                    else -> "参数"
                }
                issues += WorkflowCompatibilityIssue("${field.label}: $kind 不能为空", blocking = true)
            }
        }
        return WorkflowInsight(capabilities, issues.distinctBy { it.message })
    }

    private fun labelFor(name: String): String = when {
        name == "prompt" -> "提示词 · Prompt"
        name == "prompt_text" -> "文本 · Text"
        name == "duration" -> "时长 · Duration (s)"
        name == "audio_duration" -> "音频时长 · Audio duration (s)"
        name == "resolution" -> "分辨率 · Resolution"
        name == "seed" -> "随机种子 · Seed"
        name == "first_frame" || name == "first_frame_image" -> "首帧 · First frame"
        name == "last_frame" || name == "last_frame_image" -> "尾帧 · Last frame"
        name == "image" -> "参考图 · Reference image"
        name == "mask" -> "蒙版 · Mask"
        name == "aspect_ratio" || name == "ratio" -> "画幅 · Aspect ratio"
        name == "image_size" || name == "size" -> "尺寸 · Size"
        name == "quality" -> "质量 · Quality"
        name == "background" -> "背景 · Background"
        name == "output_format" -> "输出格式 · Format"
        name == "n" || name == "batch_size" || name == "num_outputs" -> "数量 · Count"
        name == "negative_prompt" -> "负面提示词 · Negative prompt"
        name == "mode" -> "模式 · Mode"
        name == "generate_audio" -> "生成音频 · Audio"
        name == "watermark" -> "水印 · Watermark"
        name == "prompt_enhancement" -> "提示词增强 · Enhance"
        name == "num_inference_steps" -> "步数 · Steps"
        name == "guidance_scale" -> "引导强度 · Guidance"
        name == "style" -> "风格 · Style"
        name.startsWith("ref_image_") -> "参考图 ${name.removePrefix("ref_image_").toIntOrNull()?.plus(1) ?: ""} · Reference"
        name.startsWith("ref_audio_") -> "参考音频 ${name.removePrefix("ref_audio_").toIntOrNull()?.plus(1) ?: ""} · Audio"
        name.startsWith("ref_video_") -> "参考视频 ${name.removePrefix("ref_video_").toIntOrNull()?.plus(1) ?: ""} · Video"
        else -> name
    }
}

/** Builds the AutoDL request body from the editable field snapshot. */
object CloudPromptBuilder {

    suspend fun build(
        spec: CloudWorkflowSpec,
        fields: List<PromptField>,
        values: Map<String, String>,
        randomSeed: Boolean,
        resolveImage: (String) -> String?,
    ): Pair<JSONObject, Map<String, String>> {
        val body = JSONObject()
        val seeds = HashMap<String, String>()
        for (field in fields) {
            val raw = values[field.key] ?: field.value
            when (field.kind) {
                FieldKind.TEXT -> {
                    if (raw.isNotBlank()) body.put(field.input, raw)
                    else if (field.required) throw PromptValidationException("${field.label} 不能为空")
                }
                FieldKind.IMAGE, FieldKind.AUDIO -> {
                    if (raw.isNotBlank()) {
                        val resolved = resolveImage(raw)
                        if (resolved != null) body.put(field.input, resolved)
                        else if (field.required) throw PromptValidationException("${field.label} 读取失败")
                    } else if (field.required) {
                        throw PromptValidationException("${field.label} 不能为空")
                    }
                }
                FieldKind.SEED -> {
                    val used = if (randomSeed) {
                        Random.nextLong(1L, SEED_LIMIT)
                    } else {
                        raw.toLongOrNull() ?: 1L
                    }
                    body.put(field.input, used)
                    seeds[field.key] = used.toString()
                }
                FieldKind.PARAM -> when (field.ptype) {
                    "int" -> {
                        val value = raw.toLongOrNull()
                        if (value != null) {
                            enforceRange(spec, field, value.toDouble())
                            body.put(field.input, value)
                        } else if (field.required) {
                            throw PromptValidationException("${field.label} 需要整数")
                        }
                    }
                    "float" -> {
                        val value = raw.toDoubleOrNull()
                        if (value != null) {
                            enforceRange(spec, field, value)
                            body.put(field.input, value)
                        } else if (field.required) {
                            throw PromptValidationException("${field.label} 需要数字")
                        }
                    }
                    "bool" -> if (raw.isNotBlank()) {
                        body.put(field.input, raw.equals("true", ignoreCase = true))
                    } else if (field.required) {
                        throw PromptValidationException("${field.label} 不能为空")
                    }
                    "combo" -> if (raw.isNotBlank()) body.put(field.input, raw)
                    else if (field.required) throw PromptValidationException("${field.label} 不能为空")
                }
            }
        }
        val unsupported = spec.rules.filter { it.isVideo && it.required }
        if (unsupported.isNotEmpty()) {
            throw PromptValidationException(
                "此工作流需要视频素材（${unsupported.joinToString { it.name }}），当前版本暂不支持"
            )
        }
        return body to seeds
    }

    /** Mirrors AutoDL's declared min/max before the request is billed. */
    private fun enforceRange(spec: CloudWorkflowSpec, field: PromptField, value: Double) {
        val rule = spec.rule(field.input) ?: return
        val min = rule.min
        val max = rule.max
        if (min != null && value < min) {
            throw PromptValidationException("${field.label} 不能小于 ${formatLimit(min)}")
        }
        if (max != null && value > max) {
            throw PromptValidationException("${field.label} 不能大于 ${formatLimit(max)}")
        }
    }

    private fun formatLimit(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** AutoDL seed max is 999999999999999; stay strictly below it. */
    private const val SEED_LIMIT = 1_000_000_000_000_000L
}

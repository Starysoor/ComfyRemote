package com.comfyremote.app

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A user-editable prompt skill (two built-ins by default, any number custom). */
data class AiSkillDef(
    val id: String,
    val label: String,
    val systemPrompt: String,
    val builtin: Boolean,
) {
    companion object {
        const val DEFAULT_NATURAL_ID = "builtin_natural"
        const val DEFAULT_TAGS_ID = "builtin_tags"
        const val DEFAULT_EN_ID = "builtin_en_expand"
        const val DEFAULT_NEG_ID = "builtin_negative"
        const val DEFAULT_TRANSLATE_ID = "builtin_translate"

        val NATURAL_DEFAULT = AiSkillDef(
            DEFAULT_NATURAL_ID, "AI 扩写",
            "你是一位文生图提示词专家，为 MOSS / Qwen 系列文生图模型扩写提示词。这类模型吃自然语言长句（中英文皆可），不要用 Danbooru 标签堆砌。\n" +
                    "请把用户给出的简短想法扩写成一段 80~150 字的自然语言画面描述：以「一张…」开头，补充主体外观与动作、环境背景、构图视角、光线氛围、色调材质等细节。\n" +
                    "如果画面需要出现文字，用『写着“具体文字”』的格式写明文字内容与位置，文字要具体。\n" +
                    "只输出扩写后的中文提示词本身，不要任何解释、前缀或引号。",
            true,
        )

        val TAGS_DEFAULT = AiSkillDef(
            DEFAULT_TAGS_ID, "AI 标签",
            "你是 Danbooru 标签专家。把用户的中文画面描述转换为英文 Danbooru 风格标签行，用逗号+空格分隔，按重要性排序：\n" +
                    "先画质词（masterpiece, best quality, very aesthetic, absurdres），再人物数量与主体（如 1girl, 1boy, solo），然后外貌（发色、发型、瞳色）、服装、表情与动作、场景背景、构图与光照（如 looking at viewer, cowboy shot, sunset, cinematic lighting）。\n" +
                    "只使用真实存在的标准 Danbooru 标签拼写；不确定的概念用更通用的标签替代，不要自造标签。\n" +
                    "只输出一行标签，不要任何解释或句号。",
            true,
        )

        val EN_EXPAND_DEFAULT = AiSkillDef(
            DEFAULT_EN_ID, "AI 英文扩写",
            "You are a prompt engineer for text-to-image models that understand natural language (Flux, SD 3.5, etc.). Expand the user's short idea into a 60~120 word English scene description: cover the subject, environment, composition, lighting, mood and camera language with concrete nouns and verbs; avoid empty adjective stacking.\n" +
                    "Output only the English description itself, no explanations, no quotes.",
            true,
        )

        val NEGATIVE_DEFAULT = AiSkillDef(
            DEFAULT_NEG_ID, "AI 负面",
            "你是 Stable Diffusion 系列的负面提示词专家。根据用户的正向提示词（可能是标签或自然语言），输出与之匹配的英文负面提示词：包含画质劣化（lowres, blurry, jpeg artifacts 等）、解剖与手部错误（bad hands, extra fingers 等）、常见干扰物（watermark, signature, text 等）。不要包含与正向内容冲突或重复的描述，不要出现人物主体相关标签。\n" +
                    "只输出一行逗号分隔的英文负面标签，不要任何解释。",
            true,
        )

        val TRANSLATE_DEFAULT = AiSkillDef(
            DEFAULT_TRANSLATE_ID, "AI 翻译",
            "你是专业的文生图提示词翻译。把用户输入的中文提示词忠实翻译成英文：保留原有结构与细节，不添加、不删减、不改写风格；专有名词与已成型的英文标签（如 1girl）原样保留；如果输入已是英文则原样返回。\n" +
                    "只输出翻译结果本身，不要任何解释。",
            true,
        )

        fun defaults(): List<AiSkillDef> = listOf(NATURAL_DEFAULT, TAGS_DEFAULT, EN_EXPAND_DEFAULT, NEGATIVE_DEFAULT, TRANSLATE_DEFAULT)

        /** Ensures every built-in skill exists (appends missing ones, keeps user edits). */
        fun mergeBuiltins(stored: List<AiSkillDef>): List<AiSkillDef> {
            val out = stored.toMutableList()
            for (d in defaults()) {
                if (out.none { it.id == d.id }) out.add(d)
            }
            return out
        }

        private val TAGGED_WORKFLOWS = listOf("SDXL", "Anima", "Illustrious", "Pony")

        /** Anime-checkpoint workflows want the tag skill first, natural language otherwise. */
        fun recommendedOrder(skills: List<AiSkillDef>, wfName: String): List<AiSkillDef> {
            val first = if (TAGGED_WORKFLOWS.any { wfName.contains(it, ignoreCase = true) })
                DEFAULT_TAGS_ID else DEFAULT_NATURAL_ID
            return skills.sortedBy { if (it.id == first) 0 else 1 }
        }
    }
}

object AiSkills {
    private const val KEY = "aiSkills"

    fun load(prefs: SharedPreferences): List<AiSkillDef> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, null) ?: return@runCatching AiSkillDef.defaults())
        val out = mutableListOf<AiSkillDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label")
            if (label.isEmpty()) continue
            out.add(AiSkillDef(o.optString("id"), label, o.optString("prompt"), o.optBoolean("builtin", false)))
        }
        AiSkillDef.mergeBuiltins(out)
    }.getOrDefault(AiSkillDef.defaults())

    fun save(prefs: SharedPreferences, skills: List<AiSkillDef>) {
        val arr = JSONArray()
        skills.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("label", s.label).put("prompt", s.systemPrompt).put("builtin", s.builtin))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}

/** One AI result awaiting user confirmation (replace / append / dismiss). */
data class AiResult(val fieldKey: String, val text: String)

/** Tunables for the chat request, all optional. */
data class AiTunables(
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val extraJson: JSONObject? = null,
)

/**
 * Minimal OpenAI-compatible chat client. Works with DeepSeek, Zhipu GLM,
 * Moonshot, SiliconFlow, OpenAI, Ollama/LM Studio — anything exposing
 * POST {base}/chat/completions. The base URL should include the version
 * segment, e.g. https://api.deepseek.com/v1 or https://open.bigmodel.cn/api/paas/v4
 */
object AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun parseChatCompletion(body: String): String {
        val root = JSONObject(body)
        root.optJSONObject("error")?.let { err ->
            throw ComfyException("AI 接口返回错误：${err.optString("message").ifEmpty { body.take(200) }}")
        }
        val content = root.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
        if (content.isNullOrEmpty()) throw ComfyException("AI 返回内容为空：${body.take(200)}")
        return content.trim()
    }

    /** Builds the request body; extra JSON keys override the standard ones. */
    fun buildChatBody(
        model: String,
        systemPrompt: String,
        userText: String,
        t: AiTunables,
    ): JSONObject {
        val payload = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText))
            )
        t.temperature?.let { payload.put("temperature", it) }
        t.maxTokens?.takeIf { it > 0 }?.let { payload.put("max_tokens", it) }
        t.extraJson?.let { extra ->
            extra.keys().forEach { k -> payload.put(k, extra.opt(k)) }
        }
        return payload
    }

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        t: AiTunables = AiTunables(),
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildChatBody(model, systemPrompt, userText, t).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        http.newCall(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()
        ).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw ComfyException("HTTP ${r.code}: ${text.take(200)}")
            parseChatCompletion(text).trim()
        }
    }
}

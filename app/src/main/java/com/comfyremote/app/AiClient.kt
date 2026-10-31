package com.comfyremote.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Two built-in prompt skills. Natural language suits MOSS/Qwen (they "eat"
 * flowing sentences per the official workflow notes); Danbooru tags suit
 * SDXL/Anima/Illustrious-style anime checkpoints.
 */
enum class AiSkill(val label: String, val systemPrompt: String) {
    NATURAL(
        "✨ 扩写",
        "你是一位文生图提示词专家，为 MOSS / Qwen 系列文生图模型扩写提示词。这类模型吃自然语言长句（中英文皆可），不要用 Danbooru 标签堆砌。\n" +
                "请把用户给出的简短想法扩写成一段 80~150 字的自然语言画面描述：以「一张…」开头，补充主体外观与动作、环境背景、构图视角、光线氛围、色调材质等细节。\n" +
                "如果画面需要出现文字，用『写着“具体文字”』的格式写明文字内容与位置，文字要具体。\n" +
                "只输出扩写后的中文提示词本身，不要任何解释、前缀或引号。"
    ),
    TAGS(
        "🏷 标签",
        "你是 Danbooru 标签专家。把用户的中文画面描述转换为英文 Danbooru 风格标签行，用逗号+空格分隔，按重要性排序：\n" +
                "先画质词（masterpiece, best quality, very aesthetic, absurdres），再人物数量与主体（如 1girl, 1boy, solo），然后外貌（发色、发型、瞳色）、服装、表情与动作、场景背景、构图与光照（如 looking at viewer, cowboy shot, sunset, cinematic lighting）。\n" +
                "只使用真实存在的标准 Danbooru 标签拼写；不确定的概念用更通用的标签替代，不要自造标签。\n" +
                "只输出一行标签，不要任何解释或句号。"
    );

    companion object {
        /** Tags first for anime-checkpoint workflows, natural language otherwise. */
        fun recommendedFor(wfName: String): List<AiSkill> {
            val tagFirst = listOf("SDXL", "Anima", "Illustrious", "Pony")
                .any { wfName.contains(it, ignoreCase = true) }
            return if (tagFirst) listOf(TAGS, NATURAL) else listOf(NATURAL, TAGS)
        }
    }
}

/** One AI result awaiting user confirmation (replace / append / dismiss). */
data class AiResult(val fieldKey: String, val text: String)

/**
 * Minimal OpenAI-compatible chat client. Works with DeepSeek, Zhipu GLM,
 * Moonshot, SiliconFlow, OpenAI, Ollama/LM Studio — anything exposing
 * POST {base}/chat/completions. The base URL should include the version
 * segment, e.g. https://api.deepseek.com/v1 or https://open.bigmodel.cn/api/paas/v4
 */
object AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
    ): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject()
            .put("model", model)
            .put("temperature", 0.8)
            .put("stream", false)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText))
            )
        http.newCall(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        ).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw ComfyException("HTTP ${r.code}: ${text.take(200)}")
            parseChatCompletion(text).trim()
        }
    }
}

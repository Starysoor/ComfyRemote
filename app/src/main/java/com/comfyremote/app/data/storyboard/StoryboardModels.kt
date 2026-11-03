package com.comfyremote.app.data.storyboard

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One shot inside a multi-shot cloud video project. */
data class StoryboardShot(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String = "",
    /** Reference image for this shot, already copied to app storage when set. */
    val referencePath: String? = null,
    /** True when the user picked the reference manually; auto updates must not overwrite it. */
    val referenceManual: Boolean = false,
    /** Last frame extracted from a successful result; feeds the next shot. */
    val lastFramePath: String? = null,
    val status: String = "draft", // draft | running | done | failed
    val taskId: String? = null,
    val resultPath: String? = null,
    val error: String? = null,
)

/** A sequence of cloud video shots sharing one workflow and style prompt. */
data class StoryboardProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workflowId: String,
    val stylePrompt: String = "",
    val shots: List<StoryboardShot> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Pure JSON codec kept outside the store so it can be tested on the JVM. */
object StoryboardCodec {
    fun encode(projects: List<StoryboardProject>): String = JSONArray().apply {
        projects.forEach { put(projectToJson(it)) }
    }.toString()

    fun decode(raw: String): List<StoryboardProject> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::projectFromJson)
        }
    }.getOrDefault(emptyList())

    private fun projectToJson(project: StoryboardProject): JSONObject = JSONObject()
        .put("id", project.id)
        .put("title", project.title)
        .put("workflowId", project.workflowId)
        .put("stylePrompt", project.stylePrompt)
        .put("createdAt", project.createdAt)
        .put("updatedAt", project.updatedAt)
        .put("shots", JSONArray().apply { project.shots.forEach { put(shotToJson(it)) } })

    private fun projectFromJson(o: JSONObject): StoryboardProject? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val shots = mutableListOf<StoryboardShot>()
        val array = o.optJSONArray("shots") ?: JSONArray()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { shots += shotFromJson(it) }
        }
        return StoryboardProject(
            id = id,
            title = o.optString("title", "Storyboard"),
            workflowId = o.optString("workflowId"),
            stylePrompt = o.optString("stylePrompt"),
            shots = shots,
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun shotToJson(shot: StoryboardShot): JSONObject = JSONObject()
        .put("id", shot.id)
        .put("prompt", shot.prompt)
        .put("referencePath", shot.referencePath ?: JSONObject.NULL)
        .put("referenceManual", shot.referenceManual)
        .put("lastFramePath", shot.lastFramePath ?: JSONObject.NULL)
        .put("status", shot.status)
        .put("taskId", shot.taskId ?: JSONObject.NULL)
        .put("resultPath", shot.resultPath ?: JSONObject.NULL)
        .put("error", shot.error ?: JSONObject.NULL)

    private fun shotFromJson(o: JSONObject): StoryboardShot = StoryboardShot(
        id = o.optString("id", UUID.randomUUID().toString()),
        prompt = o.optString("prompt"),
        referencePath = nullableString(o, "referencePath"),
        referenceManual = o.optBoolean("referenceManual", false),
        lastFramePath = nullableString(o, "lastFramePath"),
        status = o.optString("status", "draft"),
        taskId = nullableString(o, "taskId"),
        resultPath = nullableString(o, "resultPath"),
        error = nullableString(o, "error"),
    )

    private fun nullableString(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}

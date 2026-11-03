package com.comfyremote.app.data.storyboard

import com.comfyremote.app.data.reference.ProjectAsset
import com.comfyremote.app.data.reference.ReferenceRole
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
    /** Semantic purpose of this shot's reference image. */
    val referenceRole: ReferenceRole = ReferenceRole.AUTO,
    /** Project asset ids used as this shot's multiple references. */
    val referenceAssetIds: List<String> = emptyList(),
    /** Per-shot workflow override; null uses the project workflow. */
    val workflowId: String? = null,
    /** Last used non-media field values for this shot. */
    val values: Map<String, String> = emptyMap(),
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
    val aspectRatio: String = "",
    val defaultValues: Map<String, String> = emptyMap(),
    val shots: List<StoryboardShot> = emptyList(),
    val assets: List<ProjectAsset> = emptyList(),
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
        .put("aspectRatio", project.aspectRatio)
        .put("defaultValues", JSONObject(project.defaultValues))
        .put("createdAt", project.createdAt)
        .put("updatedAt", project.updatedAt)
        .put("shots", JSONArray().apply { project.shots.forEach { put(shotToJson(it)) } })
        .put("assets", JSONArray().apply { project.assets.forEach { put(assetToJson(it)) } })

    private fun projectFromJson(o: JSONObject): StoryboardProject? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val shots = mutableListOf<StoryboardShot>()
        val array = o.optJSONArray("shots") ?: JSONArray()
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { shots += shotFromJson(it) }
        }
        val assets = mutableListOf<ProjectAsset>()
        o.optJSONArray("assets")?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { assets += assetFromJson(it) }
            }
        }
        return StoryboardProject(
            id = id,
            title = o.optString("title", "Storyboard"),
            workflowId = o.optString("workflowId"),
            stylePrompt = o.optString("stylePrompt"),
            aspectRatio = o.optString("aspectRatio"),
            defaultValues = stringMap(o.optJSONObject("defaultValues")),
            shots = shots,
            assets = assets,
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun shotToJson(shot: StoryboardShot): JSONObject = JSONObject()
        .put("id", shot.id)
        .put("prompt", shot.prompt)
        .put("referencePath", shot.referencePath ?: JSONObject.NULL)
        .put("referenceManual", shot.referenceManual)
        .put("referenceRole", shot.referenceRole.name)
        .put("referenceAssetIds", JSONArray().apply { shot.referenceAssetIds.forEach { put(it) } })
        .put("workflowId", shot.workflowId ?: JSONObject.NULL)
        .put("values", JSONObject(shot.values))
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
        referenceRole = ReferenceRole.fromStorage(o.optString("referenceRole")),
        referenceAssetIds = o.optJSONArray("referenceAssetIds")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }.orEmpty(),
        workflowId = nullableString(o, "workflowId"),
        values = stringMap(o.optJSONObject("values")),
        lastFramePath = nullableString(o, "lastFramePath"),
        status = o.optString("status", "draft"),
        taskId = nullableString(o, "taskId"),
        resultPath = nullableString(o, "resultPath"),
        error = nullableString(o, "error"),
    )

    private fun assetToJson(asset: ProjectAsset): JSONObject = JSONObject()
        .put("id", asset.id)
        .put("role", asset.role.name)
        .put("path", asset.path)
        .put("label", asset.label)
        .put("order", asset.order)
        .put("createdAt", asset.createdAt)

    private fun assetFromJson(o: JSONObject): ProjectAsset = ProjectAsset(
        id = o.optString("id", UUID.randomUUID().toString()),
        role = ReferenceRole.fromStorage(o.optString("role")),
        path = o.optString("path"),
        label = o.optString("label"),
        order = o.optInt("order", 0),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun stringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = linkedMapOf<String, String>()
        o.keys().forEach { key -> out[key] = o.optString(key) }
        return out
    }

    private fun nullableString(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }
}

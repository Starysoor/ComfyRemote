package com.comfyremote.app.data.storyboard

import com.comfyremote.app.data.cloud.AutoDlConfig
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
    /** Full provider workflow path override; null uses the project workflow. */
    val workflowPath: String? = null,
    /** Provider id for the workflow override. */
    val providerId: String? = null,
    /** Bare model/workflow id retained for display and legacy projects. */
    val workflowId: String? = null,
    /** Last used non-media field values for this shot. */
    val values: Map<String, String> = emptyMap(),
    /** Last frame extracted from a successful result; feeds the next shot. */
    val lastFramePath: String? = null,
    /** True once this frame has been written to project assets (and History for videos). */
    val tailFrameStored: Boolean = false,
    val status: String = "draft", // draft | running | done | failed
    val taskId: String? = null,
    val resultPath: String? = null,
    val error: String? = null,
)

/** A sequence of cloud video shots sharing one workflow and style prompt. */
data class StoryboardProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Legacy bare AutoDL workflow id. */
    val workflowId: String,
    /** Unified workflow path (`autodl:`, `autodl-model:`, `replicate:`, ...). */
    val workflowPath: String = "",
    val providerId: String = "",
    val stylePrompt: String = "",
    val aspectRatio: String = "",
    val defaultValues: Map<String, String> = emptyMap(),
    /** Optional "tail frame relay" gameplay; off leaves each shot's reference manual. */
    val chainFrames: Boolean = true,
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
        .put("workflowPath", project.workflowPath)
        .put("providerId", project.providerId)
        .put("stylePrompt", project.stylePrompt)
        .put("aspectRatio", project.aspectRatio)
        .put("defaultValues", JSONObject(project.defaultValues))
        .put("chainFrames", project.chainFrames)
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
        val legacyWorkflowId = o.optString("workflowId")
        val workflowPath = o.optString("workflowPath")
            .ifBlank { legacyWorkflowId.takeIf { it.isNotBlank() }?.let { AutoDlConfig.PATH_PREFIX + it }.orEmpty() }
        return StoryboardProject(
            id = id,
            title = o.optString("title", "Storyboard"),
            workflowId = legacyWorkflowId.ifBlank { workflowPath.substringAfter(':') },
            workflowPath = workflowPath,
            providerId = o.optString("providerId").ifBlank {
                if (workflowPath.startsWith(AutoDlConfig.PATH_PREFIX)) AutoDlConfig.PROVIDER else ""
            },
            stylePrompt = o.optString("stylePrompt"),
            aspectRatio = o.optString("aspectRatio"),
            defaultValues = stringMap(o.optJSONObject("defaultValues")),
            chainFrames = o.optBoolean("chainFrames", true),
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
        .put("workflowPath", shot.workflowPath ?: JSONObject.NULL)
        .put("providerId", shot.providerId ?: JSONObject.NULL)
        .put("workflowId", shot.workflowId ?: JSONObject.NULL)
        .put("values", JSONObject(shot.values))
        .put("lastFramePath", shot.lastFramePath ?: JSONObject.NULL)
        .put("tailFrameStored", shot.tailFrameStored)
        .put("status", shot.status)
        .put("taskId", shot.taskId ?: JSONObject.NULL)
        .put("resultPath", shot.resultPath ?: JSONObject.NULL)
        .put("error", shot.error ?: JSONObject.NULL)

    private fun shotFromJson(o: JSONObject): StoryboardShot {
        val legacyWorkflowId = nullableString(o, "workflowId")
        val workflowPath = nullableString(o, "workflowPath")
            ?: legacyWorkflowId?.let { AutoDlConfig.PATH_PREFIX + it }
        return StoryboardShot(
            id = o.optString("id", UUID.randomUUID().toString()),
            prompt = o.optString("prompt"),
            referencePath = nullableString(o, "referencePath"),
            referenceManual = o.optBoolean("referenceManual", false),
            referenceRole = ReferenceRole.fromStorage(o.optString("referenceRole")),
            referenceAssetIds = o.optJSONArray("referenceAssetIds")?.let { array ->
                (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty(),
            workflowPath = workflowPath,
            providerId = nullableString(o, "providerId")
                ?: workflowPath?.let {
                    if (it.startsWith(AutoDlConfig.PATH_PREFIX)) AutoDlConfig.PROVIDER else null
                },
            workflowId = legacyWorkflowId,
            values = stringMap(o.optJSONObject("values")),
            lastFramePath = nullableString(o, "lastFramePath"),
            tailFrameStored = o.optBoolean("tailFrameStored", false),
            status = o.optString("status", "draft"),
            taskId = nullableString(o, "taskId"),
            resultPath = nullableString(o, "resultPath"),
            error = nullableString(o, "error"),
        )
    }

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

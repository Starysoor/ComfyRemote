package com.comfyremote.app

import android.content.Context
import com.comfyremote.app.data.reference.ReferenceRole
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One past generation stored locally on the phone. */
data class HistoryItem(
    val id: String,          // ComfyUI prompt_id
    val wfPath: String,      // workflow file path on the server (for re-run)
    val wfName: String,
    val time: Long,
    val values: Map<String, String>, // field edits used for this run
    /** Source field metadata, used to map values onto a different workflow. */
    val fields: List<PromptField> = emptyList(),
    val randomSeed: Boolean,
    val images: List<String>,        // local file paths
    val summary: String,             // first prompt text, truncated
    val prompts: List<LabeledText> = emptyList(), // full text prompts, copyable
    val seeds: String = "",          // seeds actually used
    val serverUrl: String = "",      // submit-time server for multi-server history
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
)

/** JSON-file-backed result library; result PNGs saved under app-external files dir. */
class HistoryStore(
    private val dir: File,
    /** Files still referenced by storyboards (or other stores) must survive delete/prune. */
    private val protectedMedia: () -> Set<String> = { emptySet() },
) {

    constructor(
        context: Context,
        protectedMedia: () -> Set<String> = { emptySet() },
    ) : this(File(context.getExternalFilesDir(null), "history").apply { mkdirs() }, protectedMedia)

    private val indexFile = File(dir, "index.json")

    fun load(): List<HistoryItem> = runCatching {
        val arr = JSONArray(indexFile.takeIf { it.isFile }?.readText() ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HistoryItem(
                id = o.optString("id"),
                wfPath = o.optString("wfPath"),
                wfName = o.optString("wfName"),
                time = o.optLong("time"),
                values = strMap(o.optJSONObject("values")),
                fields = promptFields(o.optJSONArray("fields")),
                randomSeed = o.optBoolean("randomSeed", true),
                images = strList(o.optJSONArray("images")).filter { p -> File(p).isFile },
                summary = o.optString("summary"),
                prompts = labeledList(o.optJSONArray("prompts")),
                seeds = o.optString("seeds"),
                serverUrl = o.optString("serverUrl"),
                favorite = o.optBoolean("favorite", false),
                tags = strList(o.optJSONArray("tags")),
            )
        }
    }.getOrDefault(emptyList()).sortedWith(
        compareByDescending<HistoryItem> { it.favorite }.thenByDescending { it.time }
    )

    fun add(item: HistoryItem) {
        // Recovery can finish the same prompt twice around a process crash; prompt_id is the identity.
        val old = load().firstOrNull { it.id == item.id }
        val merged = if (old == null) item else item.copy(
            favorite = old.favorite,
            tags = old.tags,
        )
        val kept = (load().filter { it.id != item.id } + merged)
            .sortedWith(compareByDescending<HistoryItem> { it.favorite }.thenByDescending { it.time })
            .take(MAX_ITEMS)
        writeItems(kept)
        pruneImages(kept)
    }

    fun updateMetadata(id: String, favorite: Boolean? = null, tags: List<String>? = null) {
        val normalizedTags = tags?.map(String::trim)?.filter(String::isNotBlank)?.distinct()?.take(16)
        val list = load().map { item ->
            if (item.id != id) item else item.copy(
                favorite = favorite ?: item.favorite,
                tags = normalizedTags ?: item.tags,
            )
        }.sortedWith(compareByDescending<HistoryItem> { it.favorite }.thenByDescending { it.time })
        writeItems(list)
    }

    fun deleteMany(ids: Collection<String>) {
        val idSet = ids.toSet()
        val victims = load().filter { it.id in idSet }
        val protected = protectedMedia()
        victims.flatMap { it.images }
            .filter { it !in protected }
            .forEach { runCatching { File(it).delete() } }
        writeItems(load().filter { it.id !in idSet })
    }

    fun delete(id: String) {
        val victim = load().firstOrNull { it.id == id }
        val protected = protectedMedia()
        victim?.images?.filter { it !in protected }?.forEach { runCatching { File(it).delete() } }
        writeItems(load().filter { it.id != id })
    }

    fun newImageFile(id: String, idx: Int): File = File(dir, "${id}_${idx}.png")

    fun newMediaFile(id: String, idx: Int, extension: String): File {
        val ext = extension.trim().removePrefix(".").ifBlank { "png" }
        return File(dir, "${id}_${idx}.$ext")
    }

    private fun writeItems(items: List<HistoryItem>) {
        val raw = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(raw)
            tmp.delete()
        }
    }

    private fun pruneImages(kept: List<HistoryItem>) {
        val liveFiles = kept.flatMap { it.images }.toSet()
        val protected = protectedMedia()
        val cutoff = System.currentTimeMillis() - PRUNE_GRACE_MS
        dir.listFiles { f -> f.isFile && f.name != "index.json" && !f.name.endsWith(".tmp") }?.forEach { f ->
            // A runner may still be writing a file before its History entry exists.
            if (f.absolutePath !in liveFiles && f.absolutePath !in protected && f.lastModified() < cutoff) {
                f.delete()
            }
        }
    }

    private fun HistoryItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("wfPath", wfPath)
        .put("wfName", wfName)
        .put("time", time)
        .put("values", JSONObject(values))
        .put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })
        .put("randomSeed", randomSeed)
        .put("images", JSONArray().apply { images.forEach { put(it) } })
        .put("summary", summary)
        .put("prompts", JSONArray().apply { prompts.forEach { put(JSONObject().put("label", it.label).put("value", it.value)) } })
        .put("seeds", seeds)
        .put("serverUrl", serverUrl)
        .put("favorite", favorite)
        .put("tags", JSONArray().apply { tags.forEach { put(it) } })

    private fun strMap(o: JSONObject?): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        o?.keys()?.forEach { k -> m[k] = o.optString(k) }
        return m
    }

    private fun strList(a: JSONArray?): List<String> {
        val out = mutableListOf<String>()
        if (a != null) for (i in 0 until a.length()) out.add(a.optString(i))
        return out.filter { it.isNotBlank() }
    }

    private fun PromptField.toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("nodeId", nodeId)
        .put("input", input)
        .put("label", label)
        .put("kind", kind.name)
        .put("value", value)
        .put("multiline", multiline)
        .put("ptype", ptype)
        .put("options", JSONArray().apply { options.forEach { put(it) } })
        .put("min", min ?: JSONObject.NULL)
        .put("max", max ?: JSONObject.NULL)
        .put("required", required)
        .put("referenceRole", referenceRole.name)

    private fun promptFields(a: JSONArray?): List<PromptField> {
        val out = mutableListOf<PromptField>()
        if (a != null) for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: continue
            out += PromptField(
                key = key,
                nodeId = o.optString("nodeId"),
                input = o.optString("input"),
                label = o.optString("label"),
                kind = runCatching { FieldKind.valueOf(o.optString("kind")) }.getOrDefault(FieldKind.PARAM),
                value = o.optString("value"),
                multiline = o.optBoolean("multiline", false),
                ptype = o.optString("ptype"),
                options = strList(o.optJSONArray("options")),
                min = if (o.isNull("min")) null else o.optString("min"),
                max = if (o.isNull("max")) null else o.optString("max"),
                required = o.optBoolean("required", false),
                referenceRole = ReferenceRole.fromStorage(o.optString("referenceRole")),
            )
        }
        return out
    }

    private fun labeledList(a: JSONArray?): List<LabeledText> {
        val out = mutableListOf<LabeledText>()
        if (a != null) for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(LabeledText(o.optString("label"), o.optString("value")))
        }
        return out
    }

    companion object {
        private const val MAX_ITEMS = 200
        private const val PRUNE_GRACE_MS = 60 * 60_000L
    }
}

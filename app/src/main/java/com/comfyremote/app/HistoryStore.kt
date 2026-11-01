package com.comfyremote.app

import android.content.Context
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
class HistoryStore(context: Context) {

    private val dir: File = File(context.getExternalFilesDir(null), "history").apply { mkdirs() }
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
        victims.flatMap { it.images }.forEach { runCatching { File(it).delete() } }
        writeItems(load().filter { it.id !in idSet })
    }

    fun delete(id: String) {
        val victim = load().firstOrNull { it.id == id }
        victim?.images?.forEach { runCatching { File(it).delete() } }
        writeItems(load().filter { it.id != id })
    }

    fun newImageFile(id: String, idx: Int): File = File(dir, "${id}_${idx}.png")

    private fun writeItems(items: List<HistoryItem>) {
        indexFile.writeText(JSONArray().apply { items.forEach { put(it.toJson()) } }.toString())
    }

    private fun pruneImages(kept: List<HistoryItem>) {
        val liveFiles = kept.flatMap { it.images }.toSet()
        dir.listFiles { f -> f.isFile && f.name != "index.json" }?.forEach { f ->
            if (f.absolutePath !in liveFiles) f.delete()
        }
    }

    private fun HistoryItem.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("wfPath", wfPath)
        .put("wfName", wfName)
        .put("time", time)
        .put("values", JSONObject(values))
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
    }
}

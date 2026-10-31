package com.comfyremote.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

enum class FieldKind { TEXT, SEED, IMAGE, PARAM }

/**
 * A user-editable parameter discovered in the workflow: a text prompt, a seed,
 * an input image, or an "advanced" numeric/boolean/combo parameter.
 */
data class PromptField(
    val key: String,
    val nodeId: String,
    val input: String,
    val label: String,
    val kind: FieldKind,
    val value: String,
    val multiline: Boolean = false,
    /** PARAM only: "int" / "float" / "bool" / "combo" */
    val ptype: String = "",
    /** PARAM combo choices */
    val options: List<String> = emptyList(),
    val min: String? = null,
    val max: String? = null,
)

class ConvertedWorkflow(
    val prompt: JSONObject,
    val fields: List<PromptField>,
    val warnings: List<String>,
    val notes: List<String> = emptyList(),
)

/**
 * Converts ComfyUI UI-format workflows (nodes/links canvas JSON) into API-format
 * prompt dicts, using /object_info schemas to map widgets_values positions to
 * input names. A widget that was "converted to input" keeps a stale placeholder
 * in widgets_values, so widget values are consumed in schema order even when the
 * input is linked — the placeholder is discarded and the link is resolved instead.
 */
object WorkflowConverter {

    const val SEED_MAX = 281474976710656L // 2^48

    private val noteTypes = setOf("MarkdownNote", "Note")

    private sealed class Resolved
    private class NodeRef(val id: String, val slot: Int) : Resolved()
    private class Lit(val value: Any?) : Resolved()

    fun convert(wfJson: String, info: JSONObject): ConvertedWorkflow {
        val wf = JSONObject(wfJson)
        val warnings = mutableListOf<String>()

        // New frontends can collapse nodes into subgraphs; the subgraph-internal
        // nodes are absent from this JSON, so conversion would be incomplete.
        val defs = wf.optJSONObject("definitions")
        if (defs != null && (defs.optJSONArray("subgraphs")?.length() ?: 0) > 0) {
            warnings.add("此工作流包含子图（Subgraph），手机端无法完整转换：请在电脑网页版中右键子图 → Expand 展开后重新保存")
        }

        val nodesArr = wf.optJSONArray("nodes")
            ?: return apiFormatFields(wf, info)

        val nodes = HashMap<String, JSONObject>()
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            nodes[n.optString("id")] = n
        }
        val links = HashMap<String, Pair<String, Int>>()
        val linksArr = wf.optJSONArray("links") ?: JSONArray()
        for (i in 0 until linksArr.length()) {
            val l = linksArr.optJSONArray(i) ?: continue
            links[l.opt(0).toString()] = Pair(l.opt(1).toString(), l.optInt(2))
        }

        fun widgetFirst(n: JSONObject): Any? {
            val wv = n.optJSONArray("widgets_values")
            return if (wv != null && wv.length() > 0) wv.opt(0) else null
        }

        fun resolve(linkId: Int, depth: Int = 0): Resolved? {
            if (depth > 20) return null
            val l = links[linkId.toString()] ?: return null
            val n = nodes[l.first] ?: return null
            when (n.optString("type")) {
                "Reroute" -> {
                    val nl = n.optJSONArray("inputs")?.optJSONObject(0)?.opt("link")
                    return if (nl is Int) resolve(nl, depth + 1) else null
                }
                "PrimitiveNode" -> return Lit(widgetFirst(n))
            }
            if (n.optInt("mode", 0) == 4) { // bypassed: pass through
                val ins = n.optJSONArray("inputs")
                val e = ins?.optJSONObject(l.second) ?: ins?.optJSONObject(0)
                val nl = e?.opt("link")
                return if (nl is Int) resolve(nl, depth + 1) else null
            }
            return NodeRef(l.first, l.second)
        }

        val api = JSONObject()
        val fields = mutableListOf<PromptField>()
        val notes = mutableListOf<String>()

        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            if (n.optInt("mode", 0) == 2) continue // muted
            val type = n.optString("type")

            if (type in noteTypes) {
                widgetFirst(n)?.let { notes.add(it.toString()) }
                continue
            }

            val def = info.optJSONObject(type)
            if (def == null) {
                if (type != "PrimitiveNode" && type != "Reroute")
                    warnings.add("未知节点 $type #${n.optString("id")} 已跳过")
                continue
            }
            val id = n.optString("id")
            val title = n.optString("title").ifEmpty { def.optString("display_name", type) }

            val order = mutableListOf<String>()
            def.optJSONObject("input_order")?.let { io ->
                io.optJSONArray("required")?.let { a -> for (j in 0 until a.length()) order.add(a.optString(j)) }
                io.optJSONArray("optional")?.let { a -> for (j in 0 until a.length()) order.add(a.optString(j)) }
            }
            val schema = HashMap<String, JSONArray>()
            def.optJSONObject("input")?.let { inp ->
                inp.optJSONObject("required")?.let { ro ->
                    ro.keys().forEach { k -> ro.optJSONArray(k)?.let { schema[k] = it } }
                }
                inp.optJSONObject("optional")?.let { oo ->
                    oo.keys().forEach { k -> oo.optJSONArray(k)?.let { schema[k] = it } }
                }
            }

            val queue = ArrayDeque<Any?>()
            n.optJSONArray("widgets_values")?.let { wv ->
                for (j in 0 until wv.length()) queue.add(wv.opt(j))
            }

            val inputs = JSONObject()
            for (name in order) {
                val spec = schema[name] ?: continue
                val t = spec.opt(0)
                val cfg = (if (spec.length() > 1 && spec.opt(1) is JSONObject) spec.optJSONObject(1) else null)
                    ?: JSONObject()

                var link: Int? = null
                n.optJSONArray("inputs")?.let { ins ->
                    for (j in 0 until ins.length()) {
                        val e = ins.optJSONObject(j) ?: continue
                        if (e.optString("name") == name) {
                            link = e.opt("link") as? Int
                            break
                        }
                    }
                }
                val isCombo = t is JSONArray || t == "COMBO"
                val isWidget = !cfg.optBoolean("forceInput", false) && (
                        t == "INT" || t == "FLOAT" || t == "STRING" || t == "BOOLEAN" || isCombo ||
                                cfg.optBoolean("image_upload", false) || cfg.has("control_after_generate") ||
                                t == "COMFY_DYNAMICCOMBO_V3" ||
                                (cfg.has("socketless") && !cfg.optBoolean("socketless", true)))

                if (link != null) {
                    if (isWidget && queue.isNotEmpty()) queue.removeFirst() // stale placeholder
                    when (val r = resolve(link)) {
                        is Lit -> inputs.putOpt(name, wrapLiteral(r.value))
                        is NodeRef -> inputs.put(name, JSONArray().put(r.id).put(r.slot))
                        null -> {}
                    }
                    continue
                }
                if (!isWidget) continue

                var v: Any? = if (queue.isNotEmpty()) queue.removeFirst() else null
                if (v == null) v = cfg.opt("default")
                if (cfg.has("control_after_generate")) {
                    if (queue.isNotEmpty()) queue.removeFirst() // the control widget value
                    fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.SEED, v?.toString() ?: "0"))
                } else if (t == "STRING") {
                    fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.TEXT, v?.toString() ?: "", cfg.optBoolean("multiline", false)))
                } else if (isCombo && cfg.optBoolean("image_upload", false)) {
                    fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.IMAGE, v?.toString() ?: ""))
                } else {
                    val pt = when {
                        t == "INT" -> "int"
                        t == "FLOAT" -> "float"
                        t == "BOOLEAN" -> "bool"
                        isCombo -> "combo"
                        else -> null
                    }
                    if (pt != null) {
                        fields.add(
                            PromptField(
                                "$id:$name", id, name, "$title · $name", FieldKind.PARAM, v?.toString() ?: "",
                                ptype = pt, options = comboOptions(t, cfg),
                                min = cfg.opt("min")?.toString(), max = cfg.opt("max")?.toString()
                            )
                        )
                    }
                }
                inputs.putOpt(name, wrapLiteral(v))
            }

            // dynamic linked inputs not present in the schema (e.g. AUTOGROW images.image_1)
            n.optJSONArray("inputs")?.let { ins ->
                for (j in 0 until ins.length()) {
                    val e = ins.optJSONObject(j) ?: continue
                    val nm = e.optString("name")
                    val lk = e.opt("link")
                    if (lk is Int && !schema.containsKey(nm)) {
                        when (val r = resolve(lk)) {
                            is Lit -> inputs.putOpt(nm, wrapLiteral(r.value))
                            is NodeRef -> inputs.put(nm, JSONArray().put(r.id).put(r.slot))
                            null -> {}
                        }
                    }
                }
            }

            api.put(id, JSONObject()
                .put("class_type", type)
                .put("inputs", inputs)
                .put("_meta", JSONObject().put("title", title)))
        }
        return ConvertedWorkflow(api, fields, warnings, notes)
    }

    private fun comboOptions(t: Any?, cfg: JSONObject): List<String> {
        val out = mutableListOf<String>()
        if (t is JSONArray) for (i in 0 until t.length()) out.add(t.opt(i).toString())
        cfg.optJSONArray("options")?.let { a -> for (i in 0 until a.length()) out.add(a.optString(i)) }
        return out.filter { it.isNotEmpty() }.distinct()
    }

    private fun wrapLiteral(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is JSONArray, is JSONObject, is Boolean, is Int, is Long, is Double -> v
        is Float -> v.toDouble()
        else -> v.toString()
    }

    private fun apiFormatFields(api: JSONObject, info: JSONObject): ConvertedWorkflow {
        val fields = mutableListOf<PromptField>()
        api.keys().forEach { id ->
            val node = api.optJSONObject(id) ?: return@forEach
            val type = node.optString("class_type")
            val def = info.optJSONObject(type) ?: return@forEach
            val title = node.optJSONObject("_meta")?.optString("title")
                ?.ifEmpty { def.optString("display_name", type) } ?: type
            val ins = node.optJSONObject("inputs") ?: return@forEach
            ins.keys().forEach { name ->
                val spec = def.optJSONObject("input")?.optJSONObject("required")?.optJSONArray(name)
                    ?: def.optJSONObject("input")?.optJSONObject("optional")?.optJSONArray(name)
                    ?: return@forEach
                val t = spec.opt(0)
                val cfg = (if (spec.length() > 1 && spec.opt(1) is JSONObject) spec.optJSONObject(1) else null)
                    ?: JSONObject()
                val v = ins.opt(name)
                when {
                    cfg.has("control_after_generate") ->
                        fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.SEED, v?.toString() ?: "0"))
                    t == "STRING" ->
                        fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.TEXT, v?.toString() ?: "", cfg.optBoolean("multiline", false)))
                    (t is JSONArray || t == "COMBO") && cfg.optBoolean("image_upload", false) ->
                        fields.add(PromptField("$id:$name", id, name, "$title · $name", FieldKind.IMAGE, v?.toString() ?: ""))
                }
            }
        }
        return ConvertedWorkflow(api, fields, emptyList())
    }

    /**
     * Applies user edits on top of the converted prompt (deep copy).
     * Returns the prompt plus the seed values actually used (key -> seed string),
     * so the UI can show what to reuse for reproduction.
     */
    fun buildPrompt(
        converted: ConvertedWorkflow,
        values: Map<String, String>,
        randomSeed: Boolean
    ): Pair<JSONObject, Map<String, String>> {
        val out = JSONObject(converted.prompt.toString())
        val seeds = HashMap<String, String>()
        for (f in converted.fields) {
            val node = out.optJSONObject(f.nodeId) ?: continue
            val ins = node.optJSONObject("inputs") ?: continue
            when (f.kind) {
                FieldKind.TEXT -> values[f.key]?.let { ins.put(f.input, it) }
                FieldKind.IMAGE -> values[f.key]?.takeIf { it.isNotEmpty() }?.let { ins.put(f.input, it) }
                FieldKind.SEED -> {
                    val used = if (randomSeed) Random.nextLong(SEED_MAX)
                    else values[f.key]?.toLongOrNull() ?: ins.optLong(f.input, 0L)
                    ins.put(f.input, used)
                    seeds[f.key] = used.toString()
                }
                FieldKind.PARAM -> {
                    val raw = values[f.key] ?: continue
                    when (f.ptype) {
                        "int" -> raw.toLongOrNull()?.let { ins.put(f.input, it) }
                        "float" -> raw.toDoubleOrNull()?.let { ins.put(f.input, it) }
                        "bool" -> when (raw.lowercase()) {
                            "true" -> ins.put(f.input, true)
                            "false" -> ins.put(f.input, false)
                        }
                        "combo" -> if (raw.isNotEmpty()) ins.put(f.input, raw)
                    }
                }
            }
        }
        return Pair(out, seeds)
    }
}

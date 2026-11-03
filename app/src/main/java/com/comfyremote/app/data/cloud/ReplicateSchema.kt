package com.comfyremote.app.data.cloud

import org.json.JSONObject

/** Converts Replicate's OpenAPI input schema into Iris's editable field catalog. */
object ReplicateSchema {

    fun rules(input: JSONObject): List<CloudInputRule> {
        val properties = input.optJSONObject("properties") ?: return emptyList()
        val required = input.optJSONArray("required")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.toSet()
        }.orEmpty()
        val preferred = listOf(
            "prompt", "text", "image", "image_url", "init_image", "first_frame", "last_frame",
            "aspect_ratio", "width", "height", "num_outputs", "duration", "fps", "resolution",
            "num_inference_steps", "guidance_scale", "seed", "output_format", "audio",
        )
        val names = properties.keys().asSequence()
            .sortedBy { name -> preferred.indexOf(name).let { if (it < 0) preferred.size else it } }
            .take(24)
            .toList()
        val rules = names.mapNotNull { name -> ruleFor(name, properties.optJSONObject(name), name in required) }
        return if (rules.any { it.isText || it.isImage }) {
            rules
        } else {
            rules + CloudInputRule("prompt", "prompt", required = true)
        }
    }

    private fun ruleFor(name: String, prop: JSONObject?, required: Boolean): CloudInputRule? {
        prop ?: return null
        val type = prop.optString("type")
        if (type == "array" || type == "object") return null
        val defaultValue = when (val raw = prop.opt("default")) {
            null, JSONObject.NULL -> null
            is String -> raw
            else -> raw.toString()
        }
        val enumValues = prop.optJSONArray("enum")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        }.orEmpty()
        val lower = name.lowercase()
        val kind = when {
            enumValues.isNotEmpty() -> "enum"
            type == "integer" -> "integer"
            type == "number" -> "number"
            type == "boolean" -> "boolean"
            lower.contains("image") || lower.contains("frame") || prop.optString("format") == "uri" -> "image"
            lower.contains("audio") -> "audio"
            lower.contains("video") -> "video"
            lower.contains("prompt") || lower == "text" -> "prompt"
            else -> "string"
        }
        return CloudInputRule(
            name = name,
            type = kind,
            required = required,
            defaultValue = defaultValue,
            min = prop.opt("minimum")?.takeIf { it is Number }?.toString()?.toDoubleOrNull(),
            max = prop.opt("maximum")?.takeIf { it is Number }?.toString()?.toDoubleOrNull(),
            options = enumValues,
        )
    }
}

package com.comfyremote.app.data.generation

import com.comfyremote.app.ConvertedWorkflow
import com.comfyremote.app.WorkflowConverter
import org.json.JSONObject

/**
 * Builds the exact ComfyUI prompt submitted for a generation request.
 *
 * Keeping this boundary explicit prevents a subtle regression where the runner
 * rebuilt a ConvertedWorkflow with an empty field list, silently dropping the
 * user's prompt edits, uploaded image names, parameters, and seed policy.
 */
internal object GenerationPromptBuilder {
    fun build(request: GenerationRequest): Pair<JSONObject, Map<String, String>> =
        WorkflowConverter.buildPrompt(
            ConvertedWorkflow(
                prompt = request.convertedPrompt,
                fields = request.fields,
                warnings = emptyList(),
            ),
            values = request.fieldValues,
            randomSeed = request.randomSeed,
        )
}

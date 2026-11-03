package com.comfyremote.app.data.generation

import com.comfyremote.app.ComfyException
import com.comfyremote.app.ConvertedWorkflow
import com.comfyremote.app.WorkflowConverter
import com.comfyremote.app.data.cloud.CloudPromptBuilder
import com.comfyremote.app.data.cloud.CloudWorkflowSpec
import org.json.JSONObject

/**
 * Builds the exact request submitted for a generation request.
 *
 * Keeping this boundary explicit prevents a subtle regression where the runner
 * rebuilt a ConvertedWorkflow with an empty field list, silently dropping the
 * user's prompt edits, uploaded image names, parameters, and seed policy.
 */
internal object GenerationPromptBuilder {
    suspend fun build(
        request: GenerationRequest,
        resolveCloudImage: (String) -> String? = { null },
    ): Pair<JSONObject, Map<String, String>> {
        if (request.provider != "comfyui") {
            val spec = CloudWorkflowSpec.fromJson(request.convertedPrompt)
                ?: throw ComfyException("云端工作流定义缺失，请重新打开该工作流")
            return CloudPromptBuilder.build(
                spec = spec,
                fields = request.fields,
                values = request.fieldValues,
                randomSeed = request.randomSeed,
                resolveImage = resolveCloudImage,
            )
        }
        return WorkflowConverter.buildPrompt(
            ConvertedWorkflow(
                prompt = request.convertedPrompt,
                fields = request.fields,
                warnings = emptyList(),
            ),
            values = request.fieldValues,
            randomSeed = request.randomSeed,
        )
    }
}

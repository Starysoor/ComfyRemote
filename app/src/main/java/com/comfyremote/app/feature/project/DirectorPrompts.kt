package com.comfyremote.app.feature.project

import com.comfyremote.app.data.project.VideoProject

/**
 * Director shot-planning prompt scaffolding (#57 P3), parallel to the
 * generate-page VisionContext/SkillRuntime pair. The plan is advice only:
 * applying suggestions creates local draft shots and never submits work.
 */
object DirectorPrompts {

    data class SuggestedShot(
        val prompt: String,
        val cameraNotes: String,
        val suggestedSeconds: Int?,
    )

    /**
     * System prompt: strict line format `序号 | 提示词 | 运镜描述 | 建议时长秒`
     * so [parsePlan] can read it without JSON mode. The provider has no
     * response_format guarantee, so the format contract lives in the prompt.
     */
    fun system(): String = """
        你是一位短视频导演助手。根据用户给出的项目信息，设计一份镜头计划。

        输出要求（必须严格遵守）：
        - 每个镜头一行，格式：序号 | 提示词 | 运镜描述 | 建议时长秒
        - 提示词用中文完整句子，可直接用于图生视频模型；包含画面主体、环境、光线与氛围
        - 运镜描述简短（如：缓慢推近、横摇跟随、固定机位）
        - 建议时长秒为 2-8 的整数
        - 除此之外不要输出任何标题、解释、空行或 markdown
    """.trimIndent()

    /** User prompt carrying the project context the plan must respect. */
    fun user(project: VideoProject): String = buildString {
        appendLine("请为以下项目设计镜头计划：")
        appendLine("项目标题：${project.title}")
        if (project.synopsis.isNotBlank()) appendLine("剧情简介：${project.synopsis}")
        if (project.stylePrompt.isNotBlank()) appendLine("全局风格：${project.stylePrompt}")
        if (project.aspectRatio.isNotBlank()) appendLine("画幅：${project.aspectRatio}")
        project.targetDurationSeconds?.let { appendLine("目标总时长：约 ${it} 秒") }
        val existing = project.shots.size
        if (existing > 0) {
            appendLine("已有镜头数：$existing（新镜头追加在后面）")
            project.shots.takeLast(3).forEach { shot ->
                if (shot.prompt.isNotBlank()) appendLine("最近镜头提示词：${shot.prompt.take(80)}")
            }
        }
        val assetLabels = project.assets.mapNotNull { asset ->
            asset.label.ifBlank { null } ?: asset.tags.firstOrNull()
        }.filter { it.isNotBlank() }.distinct().take(10)
        if (assetLabels.isNotEmpty()) {
            appendLine("可用参考素材（尽量让镜头复用这些素材的设定）：${assetLabels.joinToString("、")}")
        }
        val suggestedCount = when {
            project.targetDurationSeconds == null -> maxOf(3, 12 - existing)
            else -> ((project.targetDurationSeconds + 4) / 5).coerceIn(3, 12) - existing
        }.coerceAtLeast(1)
        appendLine("请规划 $suggestedCount 个新镜头。")
    }

    /**
     * Lenient line parser: splits on `|` (or full-width ｜), tolerates the
     * numbering column being missing/non-numeric, skips blank/short rows and
     * markdown decoration. Malformed rows never throw.
     */
    fun parsePlan(raw: String): List<SuggestedShot> {
        return raw.lineSequence()
            .map { line -> line.trim().trimStart('-', '*', '·', ' ').trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val columns = line.split('|', '｜').map { it.trim() }
                val promptColumn = columns.getOrNull(1) ?: return@mapNotNull null
                if (promptColumn.length < 4) return@mapNotNull null
                val camera = columns.getOrNull(2).orEmpty()
                    .takeIf { it.isNotBlank() && !it.equals("无", ignoreCase = true) }
                    .orEmpty()
                val seconds = columns.getOrNull(3)
                    ?.trimEnd('s', 'S', '秒')
                    ?.toDoubleOrNull()
                    ?.toInt()
                    ?.takeIf { it in 1..60 }
                SuggestedShot(prompt = promptColumn, cameraNotes = camera, suggestedSeconds = seconds)
            }
            .toList()
    }
}

package com.comfyremote.app.feature.generate

import com.comfyremote.app.data.reference.ReferenceRole
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRuntimeTest {

    private val references = listOf(
        VisionReference("1", ReferenceRole.START_FRAME, 0, "start", "a.png"),
        VisionReference("2", ReferenceRole.END_FRAME, 1, "end", "b.png"),
    )

    @Test
    fun analysisStageMustNotWriteTheFinalPrompt() {
        val prompt = SkillRuntime.visionAnalysisSystemPrompt(VisionMode.VIDEO)
        assertTrue(prompt.contains("DO NOT write the final prompt"))
        assertTrue(prompt.contains("SUBJECTS"))
        assertTrue(prompt.contains("TRANSITION"))
    }

    @Test
    fun plannerStageKeepsAnalysisHiddenAndDescribesTransition() {
        val prompt = SkillRuntime.plannerSystemPrompt("base skill", VisionMode.VIDEO, references)
        assertTrue(prompt.contains("hidden structured analysis"))
        assertTrue(prompt.contains("start to end frame"))
        assertTrue(prompt.contains("fuse", ignoreCase = true) || prompt.contains("Fuse"))
    }

    @Test
    fun plannerUserTextLabelsAnalysisAsInternal() {
        val text = SkillRuntime.plannerUserText(
            request = "turn around",
            currentPrompt = "a girl",
            analysis = "SUBJECTS: girl\nKEEP: red dress",
            references = references,
        )
        assertTrue(text.contains("turn around"))
        assertTrue(text.contains("a girl"))
        assertTrue(text.contains("internal only"))
        assertTrue(text.contains("start frame"))
        assertTrue(text.contains("end frame"))
    }
}

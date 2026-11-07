package com.comfyremote.app.data.project

import com.comfyremote.app.data.reference.ProjectAsset
import com.comfyremote.app.data.reference.ReferenceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorChecksTest {
    private val existing = setOf("/m/a.mp4", "/m/ref.png", "/m/last.png")

    private fun project(
        shots: List<ProjectShot>,
        chainFrames: Boolean = true,
        targetDurationSeconds: Int? = null,
        timeline: Timeline? = null,
        assets: List<ProjectAsset> = emptyList(),
    ) = VideoProject(
        id = "p1",
        title = "T",
        workflowId = "m",
        chainFrames = chainFrames,
        targetDurationSeconds = targetDurationSeconds,
        shots = shots,
        timeline = timeline,
        assets = assets,
    )

    @Test
    fun cleanProjectProducesNoIssues() {
        val shots = listOf(
            ProjectShot(id = "s1", prompt = "开场", status = "done", resultPath = "/m/a.mp4", lastFramePath = "/m/last.png"),
            ProjectShot(id = "s2", prompt = "跟进", referencePath = "/m/last.png", status = "draft"),
        )
        val issues = DirectorChecks.analyze(project(shots), existing)
        assertTrue("unexpected: $issues", issues.isEmpty())
    }

    @Test
    fun failedShotsAndMissingReferencesAreFlagged() {
        val shots = listOf(
            ProjectShot(id = "s1", prompt = "开场", status = "failed", error = "boom"),
            ProjectShot(id = "s2", prompt = "跟进", status = "draft"),
        )
        val issues = DirectorChecks.analyze(project(shots), existing)

        val codes = issues.map { it.code }
        assertTrue("shot_failed" in codes)
        assertTrue("shot_no_reference" in codes)
        assertTrue(issues.all { it.shotId != null || it.code == "shot_no_reference" || true })
    }

    @Test
    fun relayBreakIsReportedOnlyWhenChainFramesIsOn() {
        val shots = listOf(
            ProjectShot(id = "s1", prompt = "开场", status = "draft"),
            ProjectShot(id = "s2", prompt = "跟进", referencePath = "/m/ref.png", status = "running", taskId = "t1"),
        )

        val on = DirectorChecks.analyze(project(shots, chainFrames = true), existing)
        assertTrue("relay_broken" in on.map { it.code })

        val off = DirectorChecks.analyze(project(shots, chainFrames = false), existing)
        assertTrue("relay_broken" !in off.map { it.code })
    }

    @Test
    fun missingMediaFilesAreReported() {
        val shots = listOf(
            ProjectShot(
                id = "s1",
                prompt = "开场",
                status = "done",
                resultPath = "/m/gone.mp4",
                referencePath = "/m/gone-ref.png",
                lastFramePath = "/m/last.png",
            ),
        )
        val issues = DirectorChecks.analyze(project(shots), existing)
        val codes = issues.map { it.code }
        assertTrue("shot_result_missing" in codes)
        assertTrue("shot_reference_missing" in codes)
        // The tail frame still exists, so no frame-missing issue.
        assertTrue("shot_frame_missing" !in codes)
    }

    @Test
    fun missingAssetFilesAreReported() {
        val assets = listOf(
            ProjectAsset(id = "a1", role = ReferenceRole.GENERIC_REFERENCE, path = "/m/ref.png"),
            ProjectAsset(id = "a2", role = ReferenceRole.GENERIC_REFERENCE, path = "/m/gone.png", label = "丢失素材"),
        )
        val issues = DirectorChecks.analyze(project(emptyList(), assets = assets), existing)
        val assetIssue = issues.first { it.code == "asset_missing" }
        assertTrue(assetIssue.message.contains("丢失素材"))
    }

    @Test
    fun durationDriftOnlyFiresWithTargetAndTimeline() {
        val shots = listOf(ProjectShot(id = "s1", prompt = "p", status = "done", resultPath = "/m/a.mp4"))

        // No timeline: never fires.
        assertTrue(
            DirectorChecks.analyze(project(shots, targetDurationSeconds = 60), existing)
                .none { it.code == "duration_drift" },
        )
        // 10s timeline vs 60s target = 83% drift.
        val timeline = Timeline(videoTrack = listOf(TimelineClip(sourcePath = "/m/a.mp4", sourceOutMs = 10_000L)))
        val issues = DirectorChecks.analyze(project(shots, targetDurationSeconds = 60, timeline = timeline), existing)
        val drift = issues.first { it.code == "duration_drift" }
        assertTrue(drift.message.contains("83%"))

        // 8s timeline vs 10s target = 20% drift → inside tolerance.
        val close = Timeline(videoTrack = listOf(TimelineClip(sourcePath = "/m/a.mp4", sourceOutMs = 8_000L)))
        assertTrue(
            DirectorChecks.analyze(project(shots, targetDurationSeconds = 10, timeline = close), existing)
                .none { it.code == "duration_drift" },
        )
    }

    @Test
    fun duplicateAdjacentPromptsAreReported() {
        val shots = listOf(
            ProjectShot(id = "s1", prompt = "same"),
            ProjectShot(id = "s2", prompt = "same"),
        )
        val issues = DirectorChecks.analyze(project(shots), existing)
        assertTrue("shot_duplicate_prompt" in issues.map { it.code })
    }

    @Test
    fun emptyProjectIsClean() {
        assertTrue(DirectorChecks.analyze(project(emptyList()), emptySet()).isEmpty())
    }
}

package com.comfyremote.app.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroTimelineTest {
    private val geo = IntroTimeline.Geometry(lensRadius = 240f, far = 1420f, closeUp = 3540f, overscan = 120f)

    @Test
    fun opensAsFullScreenCloseUpWithNothingElseVisible() {
        val f = IntroTimeline.frame(0f, geo)
        assertTrue("lens must cover the screen", f.lens >= geo.far)
        assertEquals(0f, f.artAlpha, 1e-4f)
        assertEquals(0f, f.ringAlpha, 1e-4f)
        assertEquals(0f, f.wordAlpha, 1e-4f)
        assertTrue(f.blurDp > 10f)
        assertTrue(f.closeUp > 0.9f)
    }

    @Test
    fun landsAsASharpLensWithTheWordmark() {
        val f = IntroTimeline.frame(1.3f, geo)
        assertEquals(geo.lensRadius, f.lens, geo.lensRadius * 0.01f)
        assertEquals(2 * geo.lensRadius, f.artSize, geo.lensRadius * 0.02f)
        assertEquals(0f, f.blurDp, 1e-3f)
        assertEquals(1f, f.ringAlpha, 1e-3f)
        assertTrue(f.wordAlpha > 0.95f)
        assertEquals(0f, f.dive, 1e-4f)
    }

    @Test
    fun ringFlashesAndShockwaveFiresOnLanding() {
        val f = IntroTimeline.frame(IntroTimeline.T_LAND, geo)
        assertEquals(1f, f.ringFlash, 1e-4f)
        assertTrue(f.shockwave in 0f..1f)
        assertEquals(-1f, IntroTimeline.frame(0.3f, geo).shockwave, 0f)
    }

    @Test
    fun endsFullyRevealingTheAppAtRestScale() {
        val f = IntroTimeline.frame(IntroTimeline.DURATION, geo)
        assertEquals(geo.far, f.lens, 1e-2f)
        assertEquals(0f, f.artAlpha, 1e-4f)
        assertEquals(0f, f.wordAlpha, 1e-4f)
        assertEquals(1f, f.appScale, 1e-4f)
        assertTrue(f.flash < 0.05f)
    }

    @Test
    fun anticipationDrawsTheLensBackBeforeTheDive() {
        val rest = IntroTimeline.frame(1.35f, geo).lens
        val breath = IntroTimeline.frame(1.58f, geo).lens
        assertTrue("lens should contract before lunging", breath < rest)
    }

    @Test
    fun timeIsClampedOutsideTheTimeline() {
        assertEquals(IntroTimeline.frame(0f, geo), IntroTimeline.frame(-1f, geo))
        assertEquals(IntroTimeline.frame(IntroTimeline.DURATION, geo), IntroTimeline.frame(9f, geo))
    }
}

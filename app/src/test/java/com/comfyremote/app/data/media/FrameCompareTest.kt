package com.comfyremote.app.data.media

import com.comfyremote.app.data.project.DirectorChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCompareTest {
    private fun solid(color: Int): IntArray = IntArray(FrameStrip.GRID * FrameStrip.GRID) { color }

    @Test
    fun identicalGridsHaveZeroDistance() {
        val grid = IntArray(FrameStrip.GRID * FrameStrip.GRID) { 0xFF336699.toInt() }
        assertEquals(0.0, FrameStrip.gridDistance(grid, grid.copyOf()), 1e-9)
    }

    @Test
    fun sizeMismatchCountsAsMaximalDistance() {
        val a = IntArray(FrameStrip.GRID * FrameStrip.GRID)
        val b = IntArray(FrameStrip.GRID * FrameStrip.GRID - 1)
        assertEquals(255.0, FrameStrip.gridDistance(a, b), 1e-9)
    }

    @Test
    fun blackVsWhiteIsMaximal() {
        val distance = FrameStrip.gridDistance(solid(0xFF000000.toInt()), solid(0xFFFFFFFF.toInt()))
        assertEquals(255.0, distance, 0.5)
    }

    @Test
    fun similarShadesStayBelowTheContinuityThreshold() {
        val a = solid(0xFF2A3B4C.toInt())
        val b = solid(0xFF2F4051.toInt())
        assertTrue(FrameStrip.gridDistance(a, b) < DirectorChecks.CONTINUITY_DIFF_THRESHOLD)
    }

    @Test
    fun invertedColorsExceedTheContinuityThreshold() {
        val distance = FrameStrip.gridDistance(solid(0xFF101010.toInt()), solid(0xFFEEEEEE.toInt()))
        assertTrue(distance > DirectorChecks.CONTINUITY_DIFF_THRESHOLD)
    }
}

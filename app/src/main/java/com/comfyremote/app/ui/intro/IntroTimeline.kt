package com.comfyremote.app.ui.intro

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure timing model for the launch intro, kept free of Compose so it can be unit tested.
 *
 * Beats: close-up (out of focus) → ignition streak → expo pull-back that racks focus and
 * lands as a lens → ring impact + shockwave → wordmark converges → anticipation → dive
 * through the pupil into the app. All times are seconds.
 */
internal object IntroTimeline {
    const val DURATION = 2.2f

    const val T_PULL = 0.42f
    const val T_PULL_END = 1.0f
    const val T_LAND = 0.86f
    const val T_DIVE = 1.55f
    const val T_DIVE_END = 2.0f

    /** Vertical position of the lens centre as a fraction of the screen height. */
    const val CENTER_Y = 0.44f

    /** Points in the artwork, normalized and relative to the image centre. */
    const val PUPIL_X = 216f / 432f - 0.5f
    const val PUPIL_Y = 205f / 432f - 0.5f
    const val GLINT_X = 210f / 432f - 0.5f
    const val GLINT_Y = 157f / 432f - 0.5f

    /**
     * @param lensRadius resting lens radius (px)
     * @param far radius that covers the whole screen from the lens centre (px)
     * @param closeUp size of the artwork in the opening close-up (px)
     * @param overscan extra radius so the opening lens edge is off-screen (px)
     */
    data class Geometry(val lensRadius: Float, val far: Float, val closeUp: Float, val overscan: Float)

    data class Frame(
        /** Current lens (clip) radius in px; reaches [Geometry.far] at the end. */
        val lens: Float,
        /** Artwork size in px. */
        val artSize: Float,
        /** 0..1: how strongly the artwork is centred on the pupil instead of its middle. */
        val pupilFocus: Float,
        /** 0..1: how much of a full-screen close-up the art currently is. */
        val closeUp: Float,
        val dive: Float,
        val artAlpha: Float,
        /** Rack-focus blur in dp. */
        val blurDp: Float,
        val brightness: Float,
        val ignition: Float,
        val ignitionAlpha: Float,
        val pull: Float,
        val ringAlpha: Float,
        val ringFlash: Float,
        /** 0..1 progress of the landing shockwave, or -1 when not visible. */
        val shockwave: Float,
        val wordAlpha: Float,
        val wordConverge: Float,
        val wordBlurDp: Float,
        val subtitleAlpha: Float,
        val subtitleConverge: Float,
        val wordExit: Float,
        val flash: Float,
        /** Scale applied to the app underneath while it is revealed. */
        val appScale: Float,
        /** Sweep-gradient rotation for the ring, in radians. */
        val rotation: Float,
    )

    fun frame(time: Float, g: Geometry): Frame {
        val t = time.coerceIn(0f, DURATION)
        val r = g.lensRadius

        val creep = lerp(0.94f, 1f, outCubic(seg(t, 0f, T_PULL)))
        val pull = outExpo(seg(t, T_PULL, T_PULL_END))
        val q = seg(t, T_LAND - 0.04f, T_LAND + 0.36f)
        val kick = 1f - 0.045f * sin(PI.toFloat() * q) * (1f - q)
        val dive = inQuart(seg(t, T_DIVE, T_DIVE_END))
        val antic = 1f - 0.06f * sin(PI.toFloat() * seg(t, 1.4f, 1.78f))

        var size = lerp(g.closeUp * creep, 2 * r, pull) * kick * antic
        var lens = min(g.far, lerp(g.far + g.overscan, r, outExpo(seg(t, T_PULL, T_PULL_END - 0.08f)))) * kick * antic
        lens = lerp(lens, g.far, dive)
        size *= lerp(1f, 7.5f, dive)
        val closeUp = ((size - 2 * r) / (g.closeUp - 2 * r)).coerceIn(0f, 1f)

        val artAlpha = outCubic(seg(t, 0f, 0.22f)) * (1f - outCubic(seg(t, T_DIVE + 0.3f, T_DIVE_END + 0.03f)))
        val blur = lerp(16f, 0f, outCubic(seg(t, T_PULL + 0.04f, T_LAND))) + 10f * dive
        val brightness = lerp(0.3f, 1f, outCubic(seg(t, 0.3f, T_LAND)))

        val ig = seg(t, 0.26f, 0.46f)
        val igAlpha = outCubic(ig) * (1f - outCubic(seg(t, 0.5f, 1.0f)))

        val ringAlpha = outCubic(seg(t, T_LAND - 0.12f, T_LAND + 0.05f)) *
            (1f - outCubic(seg(t, T_DIVE + 0.1f, T_DIVE_END)))
        val ringFlash = gauss(t - T_LAND, 0.09f)
        val sw = seg(t, T_LAND - 0.02f, T_LAND + 0.6f)

        val wIn = seg(t, 0.92f, 1.38f)
        val wordExit = inCubic(seg(t, T_DIVE - 0.02f, T_DIVE + 0.16f))
        val sub = outCubic(seg(t, 1.06f, 1.45f))

        return Frame(
            lens = lens,
            artSize = size,
            pupilFocus = max(closeUp, dive),
            closeUp = closeUp,
            dive = dive,
            artAlpha = artAlpha,
            blurDp = blur,
            brightness = brightness,
            ignition = ig,
            ignitionAlpha = igAlpha,
            pull = pull,
            ringAlpha = ringAlpha,
            ringFlash = ringFlash,
            shockwave = if (sw > 0f && sw < 1f) sw else -1f,
            wordAlpha = outCubic(seg(t, 0.92f, 1.2f)) * (1f - wordExit),
            wordConverge = outExpo(wIn),
            wordBlurDp = lerp(5f, 0f, outExpo(wIn)),
            subtitleAlpha = sub * (1f - wordExit),
            subtitleConverge = sub,
            wordExit = wordExit,
            flash = if (t > T_DIVE) gauss(t - (T_DIVE_END - 0.1f), 0.1f) else 0f,
            appScale = appScale(t),
            rotation = (-PI / 2).toFloat() + t * 1.1f,
        )
    }

    /** Scale of the app underneath: it settles from slightly zoomed-in as the lens opens. */
    fun appScale(t: Float): Float = lerp(1.14f, 1f, outCubic(seg(t, T_DIVE + 0.25f, DURATION)))

    fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
    fun outCubic(k: Float): Float = 1f - (1f - k).pow(3)
    fun outExpo(k: Float): Float = if (k >= 1f) 1f else 1f - 2f.pow(-10f * k)
    private fun inQuart(k: Float): Float = k.pow(4)
    private fun inCubic(k: Float): Float = k.pow(3)
    private fun gauss(x: Float, w: Float): Float = exp(-(x / w) * (x / w))
}

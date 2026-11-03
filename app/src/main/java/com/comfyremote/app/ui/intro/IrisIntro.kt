package com.comfyremote.app.ui.intro

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import com.comfyremote.app.R
import com.comfyremote.app.data.generation.GenerationNotificationController
import com.comfyremote.app.ui.IrisPalette
import com.comfyremote.app.ui.intro.IntroTimeline.lerp
import com.comfyremote.app.ui.intro.IntroTimeline.outExpo
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object IrisIntro {
    /**
     * Only a plain launcher cold start gets the intro: notification taps and share
     * intents go straight to their destination, and it is skipped when the user has
     * disabled it or turned system animations off.
     */
    fun shouldPlay(context: Context, intent: Intent?, enabled: Boolean): Boolean {
        if (!enabled || intent?.action != Intent.ACTION_MAIN) return false
        if (intent.hasExtra(GenerationNotificationController.EXTRA_TASK_ID)) return false
        val animatorScale = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        return animatorScale > 0f
    }
}

/**
 * Hosts [content] under the launch intro. The app composes underneath from the first
 * frame; the intro ends by diving through the lens, which reveals the live UI.
 */
@Composable
fun IrisIntroHost(
    play: Boolean,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    var playing by remember { mutableStateOf(play) }
    val clock = remember { Animatable(0f) }
    if (playing) {
        LaunchedEffect(Unit) {
            clock.animateTo(
                IntroTimeline.DURATION,
                tween((IntroTimeline.DURATION * 1000).roundToInt(), easing = LinearEasing),
            )
            playing = false
        }
        BackHandler { playing = false }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                if (playing) {
                    val s = IntroTimeline.appScale(clock.value)

                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, IntroTimeline.CENTER_Y)
                }
            },
        ) { content() }
        if (playing) {
            IntroOverlay(time = { clock.value }, dark = dark, onSkip = { playing = false })
        }
    }
}

private class IntroColors(val bg: Color, val bg2: Color, val text: Color, val sub: Color, val glow: BlendMode)

private val DarkIntro = IntroColors(IrisPalette.Ink, Color(0xFF14141C), IrisPalette.TextHi, Color(0xFF8E8EA3), BlendMode.Plus)
private val LightIntro = IntroColors(IrisPalette.Paper, Color.White, IrisPalette.TextHiLight, Color(0xFF6A6A7E), BlendMode.SrcOver)
private val Sweep = listOf(IrisPalette.Violet, IrisPalette.Cyan, IrisPalette.Rose, IrisPalette.Violet)

@Composable
private fun IntroOverlay(time: () -> Float, dark: Boolean, onSkip: () -> Unit) {
    val colors = if (dark) DarkIntro else LightIntro
    val eye = ImageBitmap.imageResource(R.mipmap.ic_launcher_art)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .pointerInput(Unit) { detectTapGestures { onSkip() } },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val geo = remember(w, h, density) {
            with(density) {
                val cx = w / 2f
                val cy = h * IntroTimeline.CENTER_Y
                IntroTimeline.Geometry(
                    lensRadius = 80.dp.toPx(),
                    far = hypot(max(cx, w - cx), max(cy, h - cy)) + 4.dp.toPx(),
                    closeUp = h * 1.475f,
                    overscan = 40.dp.toPx(),
                )
            }
        }
        val center = Offset(w / 2f, h * IntroTimeline.CENTER_Y)
        fun frame() = IntroTimeline.frame(time(), geo)

        // 1. Backdrop with the lens cut out, so the app shows through during the dive.
        Canvas(Modifier.fillMaxSize()) {
            val f = frame()
            val backdrop = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (f.dive > 0f) addOval(Rect(center, f.lens))
            }
            drawPath(backdrop, colors.bg)
            drawPath(
                backdrop,
                Brush.radialGradient(listOf(colors.bg2, colors.bg), center, 320.dp.toPx()),
            )
        }

        // 2. The artwork inside the lens: blurred layer, sharp circular clip.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val f = frame()
                    val lens = Path().apply { addOval(Rect(center, f.lens)) }
                    clipPath(lens) {
                        this@drawWithContent.drawContent()
                        lensVignettes(f, center)
                    }
                }
                .graphicsLayer {
                    val blur = frame().blurDp.dp.toPx() * 1.7f
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.5f) {
                        BlurEffect(blur, blur, TileMode.Decal)
                    } else {
                        null
                    }
                }
                .drawBehind {
                    val f = frame()
                    if (f.artAlpha <= 0f) return@drawBehind
                    val tl = artTopLeft(f, center)
                    val b = f.brightness
                    drawImage(
                        image = eye,
                        dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                        dstSize = IntSize(f.artSize.roundToInt(), f.artSize.roundToInt()),
                        alpha = f.artAlpha,
                        colorFilter = ColorFilter.colorMatrix(
                            ColorMatrix(floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
                        ),
                        filterQuality = FilterQuality.High,
                    )
                },
        )

        // 3. Light: ignition streak, ring, shockwave.
        Canvas(Modifier.fillMaxSize()) {
            val f = frame()
            ignition(f, center)
            ring(f, center, colors)
            shockwave(f, center, colors, geo.lensRadius)
        }

        // 4. Wordmark, converging out of a blur.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val blur = frame().wordBlurDp.dp.toPx() * 1.7f
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.5f) {
                        BlurEffect(blur, blur, TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            val f = frame()
            val lift = 6.dp.toPx() * f.wordExit
            if (f.wordAlpha > 0f) {
                val layout = measurer.measure(
                    "IRIS",
                    TextStyle(
                        color = colors.text,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (lerp(46f, 9f, f.wordConverge) / 27f).em,
                    ),
                )
                val baseline = center.y + geo.lensRadius + 68.dp.toPx() - lift
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, baseline - layout.firstBaseline),
                    alpha = f.wordAlpha,
                )
            }
            if (f.subtitleAlpha > 0f) {
                val layout = measurer.measure(
                    "灵瞳",
                    TextStyle(
                        color = colors.sub,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (lerp(12f, 7f, f.subtitleConverge) / 12f).em,
                    ),
                )
                val baseline = center.y + geo.lensRadius + 95.dp.toPx() - lift
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, baseline - layout.firstBaseline),
                    alpha = f.subtitleAlpha,
                )
            }
        }

        // 5. Pass-through flash at the pupil.
        Canvas(Modifier.fillMaxSize()) {
            val f = frame()
            if (f.flash < 0.01f) return@Canvas
            val r = lerp(60.dp.toPx(), geo.far * 1.2f, f.dive)
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.9f * f.flash),
                    0.45f to Color(0xFFB4A0FF).copy(alpha = 0.35f * f.flash),
                    1f to IrisPalette.Violet.copy(alpha = 0f),
                    center = center,
                    radius = r,
                ),
            )
        }
    }
}

private fun artTopLeft(f: IntroTimeline.Frame, center: Offset): Offset {
    val fx = IntroTimeline.PUPIL_X * f.pupilFocus
    val fy = IntroTimeline.PUPIL_Y * f.pupilFocus
    return Offset(center.x - f.artSize / 2f - fx * f.artSize, center.y - f.artSize / 2f - fy * f.artSize)
}

private fun DrawScope.lensVignettes(f: IntroTimeline.Frame, center: Offset) {
    val cinematic = 0.75f * f.closeUp * f.artAlpha
    if (cinematic > 0.01f) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = cinematic),
                center = center,
                radius = 470.dp.toPx(),
            ),
        )
    }
    val glass = 0.4f * (1f - f.closeUp) * f.artAlpha
    if (glass > 0.01f) {
        drawCircle(
            Brush.radialGradient(
                0.6f to Color(0x00060510),
                1f to Color(0xFF060510).copy(alpha = glass),
                center = center,
                radius = f.lens,
            ),
            radius = f.lens,
            center = center,
        )
    }
}

private fun DrawScope.ignition(f: IntroTimeline.Frame, center: Offset) {
    if (f.ignitionAlpha <= 0f) return
    val tl = artTopLeft(f, center)
    val glint = Offset(
        tl.x + f.artSize / 2f + IntroTimeline.GLINT_X * f.artSize,
        tl.y + f.artSize / 2f + IntroTimeline.GLINT_Y * f.artSize,
    )
    val a = f.ignitionAlpha
    val coreR = lerp(10.dp.toPx(), 70.dp.toPx(), outExpo(f.ignition)) * lerp(1f, 0.4f, f.pull)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.95f * a),
            0.2f to Color(0xFFB4A0FF).copy(alpha = 0.55f * a),
            1f to IrisPalette.Violet.copy(alpha = 0f),
            center = glint,
            radius = coreR,
        ),
        radius = coreR,
        center = glint,
        blendMode = BlendMode.Plus,
    )
    // Anamorphic streak: a radial gradient squashed vertically.
    val len = lerp(30.dp.toPx(), size.width * 0.95f, outExpo(f.ignition)) * lerp(1f, 0.55f, f.pull)
    scale(scaleX = 1f, scaleY = 0.022f, pivot = glint) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = a),
                0.18f to IrisPalette.Cyan.copy(alpha = 0.75f * a),
                0.55f to IrisPalette.Violet.copy(alpha = 0.45f * a),
                0.85f to IrisPalette.Rose.copy(alpha = 0.18f * a),
                1f to IrisPalette.Rose.copy(alpha = 0f),
                center = glint,
                radius = len,
            ),
            radius = len,
            center = glint,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.ring(f: IntroTimeline.Frame, center: Offset, colors: IntroColors) {
    if (f.ringAlpha <= 0f) return
    rotateRad(f.rotation, pivot = center) {
        val sweep = Brush.sweepGradient(Sweep, center)
        drawCircle(
            sweep,
            radius = f.lens,
            center = center,
            alpha = (f.ringAlpha * (0.14f + 0.5f * f.ringFlash)).coerceAtMost(1f),
            style = Stroke(width = (lerp(6f, 3f, f.dive) + 8f * f.ringFlash).dp.toPx(), cap = StrokeCap.Round),
            blendMode = colors.glow,
        )
        drawCircle(
            sweep,
            radius = f.lens,
            center = center,
            alpha = f.ringAlpha,
            style = Stroke(width = (lerp(1.4f, 0.8f, f.dive) + 1.2f * f.ringFlash).dp.toPx()),
        )
    }
}

private fun DrawScope.shockwave(f: IntroTimeline.Frame, center: Offset, colors: IntroColors, lensRadius: Float) {
    val p = f.shockwave
    if (p < 0f) return
    rotateRad(f.rotation + 1.2f, pivot = center) {
        drawCircle(
            Brush.sweepGradient(Sweep, center),
            radius = lensRadius + 120.dp.toPx() * outExpo(p),
            center = center,
            alpha = (1f - p) * (1f - p) * 0.85f,
            style = Stroke(width = lerp(2.2f, 0.4f, p).dp.toPx()),
            blendMode = colors.glow,
        )
    }
}

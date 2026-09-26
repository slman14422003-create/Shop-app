package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.shopmanager.app.ui.common.MotionSpecs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * REDESIGN ("حدث شكل ال splash screen لشكل جميل"): a fuller, more
 * deliberately "designed" one-shot entrance than the old flat
 * mark+wordmark row — a soft two-tone brand wash behind the mark, the mark
 * itself rendered as [KnotMark] (the same six-petal glyph as the new
 * launcher icon — see ic_launcher_foreground.png — so the very first thing
 * a person sees on cold start is visually the same shape they tapped from
 * the home screen), a gentle spin-and-settle on the mark instead of a
 * plain scale-fade, and the wordmark/credit staged in beneath it. Still a
 * single one-shot entrance: nothing loops or recomposes once [settled]
 * flips, and this remains a calm branded moment rather than a progress
 * readout (see MainActivity — the real init work and the update check both
 * happen independently and never extend how long this screen stays up).
 *
 * PERF: a handful of nodes (two Canvas draws + two Text nodes), no
 * gradient shader loops or blur — same cheap cost class as before.
 */
@Composable
fun AppSplashScreen(modifier: Modifier = Modifier) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }

    val markScale by animateFloatAsState(
        targetValue = if (settled) 1f else 0.6f,
        animationSpec = tween(520, delayMillis = 0, easing = MotionSpecs.claudeEasing),
        label = "splashMarkScale"
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(360, delayMillis = 0, easing = MotionSpecs.claudeEasing),
        label = "splashMarkAlpha"
    )
    val markSpin by animateFloatAsState(
        targetValue = if (settled) 0f else -70f,
        animationSpec = tween(560, delayMillis = 0, easing = MotionSpecs.claudeEasing),
        label = "splashMarkSpin"
    )
    val glowEntrance by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(700, delayMillis = 0, easing = MotionSpecs.claudeEasing),
        label = "splashGlow"
    )
    val textEntrance by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(460, delayMillis = 160, easing = MotionSpecs.claudeEasing),
        label = "splashText"
    )
    val creditEntrance by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(420, delayMillis = 300, easing = MotionSpecs.claudeEasing),
        label = "splashCredit"
    )

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val splashBackground = if (isDark) com.shopmanager.app.ui.theme.ClaudeBgDark1 else com.shopmanager.app.ui.theme.ClaudeBgLight1
    val markAccent = if (isDark) com.shopmanager.app.ui.theme.ClaudeOrangeDark else com.shopmanager.app.ui.theme.ClaudeOrangeLight
    val onDark = if (isDark) com.shopmanager.app.ui.theme.ClaudeTextPrimaryDark else com.shopmanager.app.ui.theme.ClaudeTextPrimaryLight
    val creditGrey = if (isDark) com.shopmanager.app.ui.theme.ClaudeTextSecondaryDark else com.shopmanager.app.ui.theme.ClaudeTextSecondaryLight

    Box(
        modifier
            .fillMaxSize()
            .background(splashBackground),
        contentAlignment = Alignment.Center
    ) {
        // A soft two-layer radial wash behind the mark — a wide, very
        // faint outer glow plus a tighter, slightly stronger inner one —
        // gives the mark some depth instead of sitting flat on a bare
        // background. Purely static once settled; no shimmer/pulse/loop.
        Canvas(
            Modifier
                .size(320.dp)
                .alpha(glowEntrance)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(markAccent.copy(alpha = 0.10f), markAccent.copy(alpha = 0f))
                ),
                radius = size.minDimension / 2f
            )
        }
        Canvas(
            Modifier
                .size(150.dp)
                .alpha(glowEntrance)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(markAccent.copy(alpha = 0.20f), markAccent.copy(alpha = 0f))
                ),
                radius = size.minDimension / 2f
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KnotMark(
                modifier = Modifier
                    .size(76.dp)
                    .alpha(markAlpha)
                    .scale(markScale)
                    .graphicsLayer { rotationZ = markSpin },
                color = markAccent
            )

            Spacer(Modifier.height(22.dp))

            Text(
                "إدارة المحل",
                color = onDark,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, letterSpacing = 0.2.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(textEntrance)
                    .graphicsLayerTranslateUp(textEntrance)
            )
        }

        // Small plain letter-spaced credit low on the screen — no
        // pill/chip background, no rule above it. Last thing to settle,
        // slowest of the beats above.
        Text(
            "SEMO STUDIO",
            color = creditGrey,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(creditEntrance)
        )
    }
}

/**
 * Settles the wordmark in from a few dp below its resting spot instead of
 * just fading in place. A graphicsLayer (single composited layer) rather
 * than an offset modifier, so it costs nothing beyond the alpha fade it
 * rides alongside.
 */
private fun Modifier.graphicsLayerTranslateUp(progress: Float): Modifier =
    this.then(
        Modifier.graphicsLayer {
            translationY = (1f - progress) * 10.dp.toPx()
        }
    )

/**
 * The splash mark: an original flat six-petal "knot" glyph — six rounded
 * capsules radiating evenly from a center hub — matching the shape of the
 * new adaptive launcher icon (see
 * drawable-xxxhdpi/ic_launcher_foreground.png, generated from the same
 * geometry) so the icon a person taps and the mark they land on read as
 * the same brand mark. Drawn as flat filled shapes with no gradient,
 * shadow or per-frame animation of its own — any motion (scale/spin/fade)
 * is applied to the whole Canvas from outside, so the glyph itself costs
 * the same tiny, one-time amount of work as a couple of Text nodes.
 */
@Composable
private fun KnotMark(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f

        val petalLen = s * 0.44f
        val petalWidth = s * 0.145f
        val hubRadius = s * 0.145f

        for (i in 0 until 6) {
            rotate(degrees = 60f * i, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx + s * 0.02f, cy - petalWidth / 2f),
                    size = Size(petalLen, petalWidth),
                    cornerRadius = CornerRadius(petalWidth / 2f, petalWidth / 2f)
                )
            }
        }

        drawCircle(color = color, radius = hubRadius, center = Offset(cx, cy))
    }
}

package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.shopmanager.app.ui.common.MotionSpecs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.R
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * REDESIGN ("صمم شاشة ال splash لتتطابق مع ايقونة التطبيق"): the mark on
 * this screen used to be [KnotMark] — an abstract six-petal glyph that was
 * only ever a stand-in and had drifted completely from the app's real
 * launcher icon (the mortar-and-pestle mark in
 * mipmap-xxxhdpi/ic_launcher.png / drawable-xxxhdpi/ic_launcher_foreground.png),
 * so the very first thing a person saw on cold start no longer matched what
 * they'd just tapped on the home screen. This now renders that exact same
 * icon asset ([R.drawable.ic_launcher_foreground] — already the full square
 * mark, background included, at every density) as the splash mark itself,
 * with a gentle scale-and-settle exactly like before. The glow behind it
 * now picks up the icon's own green instead of the unrelated brand-blue
 * accent, so the wash reads as coming from the mark rather than clashing
 * with it.
 *
 * Still a single one-shot entrance: nothing loops or recomposes once
 * [settled] flips, and this remains a calm branded moment rather than a
 * progress readout (see MainActivity — the real init work and the update
 * check both happen independently and never extend how long this screen
 * stays up).
 *
 * PERF: two Canvas draws (the glow) + one Image + two Text nodes, no
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
    // Matches the launcher icon's own green (see the doc comment above)
    // instead of the app's unrelated blue brand accent, so the glow reads
    // as radiating from the icon mark itself.
    val markAccent = if (isDark) com.shopmanager.app.ui.theme.ClaudeAccentGreenDark else com.shopmanager.app.ui.theme.ClaudeAccentGreenLight
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
            // The real launcher icon asset — see the doc comment above for
            // why this replaced the old abstract [KnotMark] glyph. Already
            // a complete square mark (background baked in) at every
            // density, so it's dropped in as-is with a matching corner
            // clip rather than redrawn from scratch.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .alpha(markAlpha)
                    .scale(markScale)
                    .graphicsLayer { rotationZ = markSpin }
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


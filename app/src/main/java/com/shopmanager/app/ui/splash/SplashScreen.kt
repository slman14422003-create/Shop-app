package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.LiquidGlassGlow
import com.shopmanager.app.ui.common.liquidGlassSurface

/**
 * "شاشة بداية حديثة": the in-app splash shown after the static system
 * splash screen hands off (see MainActivity — the OS splash is dismissed
 * the instant this Composable has a first frame ready).
 *
 * DESIGN: deliberately says nothing about *what* is loading — no step
 * checklist, no percentage, nothing that reads as a progress report. It's
 * purely a calm, branded moment (logo + name + a soft, indeterminate
 * spinner) that stays up for a short, fixed minimum (see
 * SPLASH_MIN_DISPLAY_MS in MainActivity) and then crossfades straight into
 * the real app the instant startup actually finishes. The background is
 * fully edge-to-edge (the status bar is transparent — see
 * SetSystemBarsColor/MainActivity), so the brand gradient and the floating
 * glass droplets below run all the way to the true top of the screen, with
 * nothing sitting behind the status bar icons but more of the same glass.
 *
 * PERF: everything here runs only once, for well under a second of real
 * screen time, while the background thread in MainActivity does the real
 * initialization — this screen itself does no I/O and holds no state of
 * its own, so it adds no measurable startup cost. The infinite animations
 * (logo pulse, drifting droplets) are skipped entirely on LOW tier, same
 * convention as [com.shopmanager.app.ui.common.liquidGlassSurface].
 */
@Composable
fun AppSplashScreen(modifier: Modifier = Modifier) {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW

    Box(
        modifier
            .fillMaxSize()
            .background(BrandGradient.brush()),
        contentAlignment = Alignment.Center
    ) {
        // A few softly blurred glass "droplets" drifting in the background —
        // purely decorative, never behind readable content, so there's
        // nothing here that could ever turn illegible (same safety rule as
        // LiquidGlassGlow everywhere else in the app). Skipped on LOW tier,
        // same as every other infinite animation in this screen.
        if (!isLowTier) {
            FloatingDroplet(size = 120.dp, alignment = Alignment.TopStart, offsetX = (-24).dp, offsetY = 60.dp, periodMs = 6200)
            FloatingDroplet(size = 90.dp, alignment = Alignment.BottomEnd, offsetX = 18.dp, offsetY = (-90).dp, periodMs = 5200)
            FloatingDroplet(size = 60.dp, alignment = Alignment.TopEnd, offsetX = (-36).dp, offsetY = 140.dp, periodMs = 7400)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            LiquidGlassLogo(isLowTier = isLowTier)

            Spacer(Modifier.height(28.dp))

            Text(
                "إدارة المحل",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "استقرار لإدارة المحل",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // Deliberately generic — a plain indeterminate spinner, not a
            // checklist of internal init steps. It only says "still
            // working", nothing more specific about what.
            CircularProgressIndicator(
                strokeWidth = 2.5.dp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            "تحت تطوير المعالج الفيزيائي سلمان",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}

/** One drifting, blurred glass circle in the splash background. Slow,
 * gentle vertical bob — never draws anything but the glow itself, so it's
 * safe to blur the whole node (see [LiquidGlassGlow]). */
@Composable
private fun FloatingDroplet(
    size: Dp,
    alignment: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    periodMs: Int
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dropletBob")
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "dropletBobValue"
    )
    Box(Modifier.fillMaxSize()) {
        LiquidGlassGlow(
            color = Color.White,
            blurRadius = 20.dp,
            modifier = Modifier
                .align(alignment)
                .offset(x = offsetX, y = offsetY + (bob * 10).dp)
                .size(size)
        )
    }
}

/** The glass logo orb: a soft blurred glow behind a liquid-glass circle
 * with the storefront glyph — the same "liquid glass" language as the
 * header, reused here so the splash and the rest of the app read as one
 * consistent redesign instead of two different visual styles. */
@Composable
private fun LiquidGlassLogo(isLowTier: Boolean) {
    val pulse: Float = if (isLowTier) {
        1f
    } else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "logoPulse")
        val value by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
            label = "logoPulseValue"
        )
        value
    }

    Box(contentAlignment = Alignment.Center) {
        LiquidGlassGlow(
            modifier = Modifier
                .size((110 * pulse).dp)
        )
        Box(
            Modifier
                .size(96.dp)
                .liquidGlassSurface(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Storefront,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

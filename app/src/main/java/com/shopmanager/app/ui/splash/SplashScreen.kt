package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.LiquidGlassGlow
import com.shopmanager.app.ui.common.liquidGlassSurface

/** One line of the splash checklist and whether that init step has
 * actually finished — driven from real state in MainActivity, not a
 * simulated/fake progress bar. */
data class SplashStepState(val label: String, val done: Boolean)

/**
 * "شاشة بداية حديثة": the in-app splash shown after the static system
 * splash screen hands off (see MainActivity — the OS splash is dismissed
 * the instant this Composable has a first frame ready). Unlike the static
 * system splash (just an icon on a flat color, by design — see the PERF
 * comment in MainActivity), this screen is alive: a liquid-glass logo orb,
 * the app name, and a checklist of the *real* startup steps ticking off as
 * MainActivity's background init actually completes them (Firebase,
 * notification channels, device performance-tier detection, background
 * sync/backup scheduling).
 *
 * PERF: everything here runs only once, for well under a second, while
 * the background thread in MainActivity does the real work — this screen
 * itself does no I/O and holds no state of its own beyond what's passed
 * in, so it adds no measurable startup cost of its own. The one infinite
 * animation (the glass logo's slow pulse) is skipped entirely on LOW tier,
 * same convention as [com.shopmanager.app.ui.common.liquidGlassSurface].
 */
@Composable
fun AppSplashScreen(steps: List<SplashStepState>, modifier: Modifier = Modifier) {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW

    Box(
        modifier
            .fillMaxSize()
            .background(BrandGradient.brush()),
        contentAlignment = Alignment.Center
    ) {
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

            Spacer(Modifier.height(40.dp))

            SplashChecklist(steps = steps)
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

@Composable
private fun SplashChecklist(steps: List<SplashStepState>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(vertical = 14.dp, horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (step.done) 0.95f else 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = step.done, enter = scaleIn() + fadeIn()) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = !step.done, enter = fadeIn(), exit = fadeOut()) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp,
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    step.label,
                    color = Color.White.copy(alpha = if (step.done) 0.95f else 0.65f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

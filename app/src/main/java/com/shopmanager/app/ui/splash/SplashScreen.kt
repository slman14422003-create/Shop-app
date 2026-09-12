package com.shopmanager.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.LiquidGlassGlow
import com.shopmanager.app.ui.common.liquidGlassSurface

/**
 * "شاشة بداية ثابتة متل واتساب": the in-app splash shown after the static
 * system splash screen hands off (see MainActivity — the OS splash is
 * dismissed the instant this Composable has a first frame ready).
 *
 * REDESIGN ("بدي ياها ثابتة بتصميم جميل متل واتساب"): this used to run its
 * own internal animation sequence every time it appeared — the logo/title/
 * subtitle/spinner popping in one after another with staggered delays,
 * a continuously pulsing logo, and three glass "droplets" endlessly
 * drifting up and down in the background. That's the opposite of what a
 * splash screen like WhatsApp's actually does: WhatsApp's splash is
 * completely still — the same static logo mark on the same flat brand
 * color every single time, with zero motion of its own, so it registers
 * instantly as "the app is opening" rather than as a mini animation to
 * watch play out. This screen is now exactly that: one fixed, motionless
 * composition (brand gradient + the app's liquid-glass logo mark + name),
 * full stop. The *appearance* and *disappearance* of this whole screen
 * still animate — see MainActivity's `AnimatedContent(isReady, ...)`,
 * which crossfades into/out of this composable — so opening the app still
 * feels smooth; it's only what happens *inside* this screen while it's up
 * that's now still, matching the brief instant the OS's own splash
 * (also static) already just showed.
 *
 * PERF: no ongoing animation at all now means literally zero recomposition
 * or per-frame work for as long as this is on screen (previously: an
 * infinite-transition-driven redraw loop on every non-LOW-tier device) —
 * simpler and cheaper than even the old LOW-tier fallback path was.
 */
@Composable
fun AppSplashScreen(modifier: Modifier = Modifier) {
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
            LiquidGlassLogo()

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
        }

        Text(
            "@slman",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        )
    }
}

/** The glass logo mark: a soft, fixed glow behind a liquid-glass circle
 * with the storefront glyph — the same "liquid glass" language as the
 * header, reused here so the splash and the rest of the app read as one
 * consistent design instead of two different styles. Deliberately static
 * (no pulse/animation of its own — see the class doc comment above) so
 * it reads the same way every single time the app opens, like a fixed
 * logo mark rather than a moving effect. */
@Composable
private fun LiquidGlassLogo() {
    Box(contentAlignment = Alignment.Center) {
        LiquidGlassGlow(modifier = Modifier.size(110.dp))
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

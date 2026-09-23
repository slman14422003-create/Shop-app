package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.BrandOnGradient
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
 *
 * REDESIGN v2 ("بيد تصممها بشكل أفضل"): still fundamentally the same idea
 * above — nothing here loops or keeps recomposing once it's settled — but
 * three concrete design upgrades:
 *  - a single, one-shot ~420ms fade+scale runs on the WHOLE composition
 *    together (logo/title/subtitle/credit all move as one group, never
 *    staggered piece by piece — staggering per element is exactly the
 *    "mini animation to watch play out" the REDESIGN above already ruled
 *    out). It's the same fade+scale shape MainActivity already uses for
 *    the splash→app hand-off, just applied to this screen's own entrance
 *    too, so appearing now matches the same motion language as
 *    disappearing instead of "instant pop in, smooth fade out".
 *  - one fixed, unblurred soft light pool sits high in the frame so the
 *    flat brand gradient reads as actually lit from one direction instead
 *    of a uniform flat wash — drawn once, never animated.
 *  - the glass logo picked up a thin outer ring for a bit more depth.
 * PERF: the entrance is a fixed one-shot (~420ms), not a per-frame loop,
 * so it costs the same tiny, one-time amount of work regardless of device
 * tier. It's deliberately NOT tier-gated like the rest of the app's
 * animations are: the device's performance tier isn't even known yet at
 * this point in startup (see MainActivity — DevicePerformance.detectTier
 * runs concurrently with this screen's own display), and a fixed-length
 * one-shot on a handful of text/icon nodes is cheap enough on any device
 * not to need gating anyway — unlike the continuously-running, open-ended
 * animation this screen deliberately dropped above.
 */
@Composable
fun AppSplashScreen(modifier: Modifier = Modifier) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val entrance by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "splashEntrance"
    )

    Box(
        modifier
            .fillMaxSize()
            .background(BrandGradient.brush()),
        contentAlignment = Alignment.Center
    ) {
        // DESIGN ("صممها بشكل أفضل"): one soft, fully static light pool set
        // high in the frame — gives the flat brand gradient a sense of a
        // single light source instead of reading as a uniform wash. Drawn
        // once and never touched again (no blur, no animation), so this is
        // the same one-time rendering cost class as the gradient itself.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-90).dp)
                .size(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandOnGradient.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .alpha(entrance)
                .scale(0.94f + entrance * 0.06f)
        ) {
            LiquidGlassLogo()

            Spacer(Modifier.height(28.dp))

            Text(
                "إدارة المحل",
                color = BrandOnGradient,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, letterSpacing = 0.2.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "استقرار لإدارة المحل",
                color = BrandOnGradient.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        // FEATURE: نص الاعتماد بالأسفل صار "تطوير المعالج الفيزيائي سلمان"
        // بدل "@slman" السابق.
        //
        // REDESIGN ("بدي ياه منسق بشكل اجمل واعلى قليلا"): this used to be
        // one plain, fairly dim line sitting right at the very bottom edge
        // (28.dp padding). Two changes: it now sits noticeably higher
        // (56.dp) so it isn't crowded against the bottom edge/gesture bar
        // on phones with a smaller nav-gesture inset, and it's dressed up
        // as a small pill — a thin hairline rule above it plus a subtle
        // translucent chip background — instead of bare floating text, so
        // it reads as a deliberate little credit badge rather than an
        // afterthought caption. Still fully static (no animation beyond
        // the one shared entrance fade this whole screen already does —
        // see the class doc above), so this costs nothing extra to draw.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .alpha(entrance)
        ) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(BrandOnGradient.copy(alpha = 0.35f))
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BrandOnGradient.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "تطوير المعالج الفيزيائي سلمان",
                    color = BrandOnGradient.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.3.sp),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** The glass logo mark: a soft, fixed glow behind a liquid-glass circle
 * with the storefront glyph — the same "liquid glass" language as the
 * header, reused here so the splash and the rest of the app read as one
 * consistent design instead of two different styles. Deliberately static
 * (no pulse/animation of its own — see the class doc comment above) so
 * it reads the same way every single time the app opens, like a fixed
 * logo mark rather than a moving effect.
 *
 * DESIGN ("تصميم أفضل"): a thin outer ring now sits between the glow and
 * the glass circle — a small extra layer of depth (a bezel around the
 * "lens") the single flat circle didn't have before. Just a static
 * border draw, no blur/animation of its own, so it costs nothing beyond
 * what the circle underneath already draws. */
@Composable
private fun LiquidGlassLogo() {
    Box(contentAlignment = Alignment.Center) {
        LiquidGlassGlow(modifier = Modifier.size(116.dp))
        Box(
            Modifier
                .size(104.dp)
                .border(1.dp, BrandOnGradient.copy(alpha = 0.28f), CircleShape)
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
                tint = BrandOnGradient,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

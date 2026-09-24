package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "شاشة بداية ثابتة متل واتساب": the in-app splash shown after the static
 * system splash screen hands off (see MainActivity — the OS splash is
 * dismissed the instant this Composable has a first frame ready).
 *
 * Fully static, single one-shot entrance — see the long history of that
 * decision in earlier revisions of this file. Nothing below loops or
 * recomposes once settled.
 *
 * REDESIGN ("نفس التصميم بس الخلفية برتقالية والرسمة بيضاء"): the splash
 * (system + in-app) and the app icon both use a flat brand-orange
 * background (`splash_background` — see colors.xml; the *system*
 * pre-Compose splash in themes.xml uses the same color so there's no
 * flash of a different tone before this frame draws) with a flat white
 * storefront mark on top — an awning with a zig-zag fringe over a
 * building front with two windows and a door (the same mark used for the
 * app icon — see the adaptive icon drawables), the door/windows "cut out"
 * back to the orange background. One flat color pair, no
 * gradient/shadow/glass/blur — Claude's own flat single-color mark
 * language, applied to an actual shop pictogram instead of Claude's own
 * mark.
 *  - the app name is set in a serif face (matching Claude's own wordmark
 *    treatment — see `AppTypography`'s `ClaudeSerif` for the same choice
 *    elsewhere in the app) instead of the bold sans title, and the
 *    subtitle line was dropped so the composition reads as just
 *    "mark + wordmark", like the reference.
 *  - the bottom credit is now a small plain letter-spaced caption reading
 *    "SEMO STUDIO" (no pill/chip background, no rule above it — bare text
 *    low on the screen, the same treatment Claude's own splash gives its
 *    "ANTHROPIC" line), replacing the previous "تطوير المعالج الفيزيائي
 *    سلمان" credit chip.
 *
 * PERF: still a fixed one-shot entrance on a handful of nodes (one Canvas
 * draw + two Text nodes) — same cost class as before, cheaper than the
 * old version since there's no gradient shader or blur to evaluate.
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

    val splashBackground = Color(0xFFDA7757)
    val onDark = Color.White

    Box(
        modifier
            .fillMaxSize()
            .background(splashBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .alpha(entrance)
                .scale(0.94f + entrance * 0.06f)
        ) {
            StorefrontMark(
                modifier = Modifier.size(104.dp),
                color = onDark,
                cutoutColor = splashBackground
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "إدارة المحل",
                color = onDark,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = 0.2.sp),
                textAlign = TextAlign.Center
            )
        }

        // FEATURE: نص الاعتماد بالأسفل صار "SEMO STUDIO" بنفس أسلوب
        // "ANTHROPIC" تحت شعار Claude — نص صغير مسافته بين الحروف واسعة،
        // بلا خلفية أو خط فاصل، ثابت بالكامل (يتبع فقط نفس `entrance`
        // العام لهذه الشاشة).
        Text(
            "SEMO STUDIO",
            color = onDark.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(entrance)
        )
    }
}

/**
 * The flat storefront glyph — same silhouette as the app icon (see the
 * adaptive-icon foreground drawable, generated from the same shape): a
 * rounded awning band, a zig-zag fringe hanging off it, a building front
 * below with two windows and a door. Drawn as a handful of flat filled
 * shapes with no blur/gradient/animation — the window/door "cut-outs" are
 * just drawn in [cutoutColor] (the splash's own background) on top, so
 * the whole mark costs the same tiny, one-time amount of work as a
 * couple of Text nodes.
 */
@Composable
private fun StorefrontMark(modifier: Modifier = Modifier, color: Color, cutoutColor: Color) {
    Canvas(modifier) {
        val w = size.minDimension * 0.92f
        val left = (size.width - w) / 2f
        val right = left + w
        val cx = size.width / 2f

        val awningTop = size.height * 0.06f
        val awningH = w * 0.20f
        val awningBottom = awningTop + awningH

        val fringeH = w * 0.14f
        val fringeBottom = awningBottom + fringeH

        val bodyTop = fringeBottom - size.height * 0.015f
        val bodyBottom = size.height * 0.96f

        val cornerAwning = w * 0.10f
        val cornerBody = w * 0.05f

        // 1) awning band — rounded corners all around, fringe covers the bottom
        drawRoundRect(
            color = color,
            topLeft = Offset(left, awningTop),
            size = Size(w, awningBottom + w * 0.04f - awningTop),
            cornerRadius = CornerRadius(cornerAwning, cornerAwning)
        )

        // 2) zig-zag fringe hanging off the awning
        val n = 6
        val seg = w / n
        val fringePath = Path().apply {
            moveTo(left, awningBottom)
            for (i in 0 until n) {
                val x0 = left + i * seg
                val xm = x0 + seg / 2f
                val x1 = x0 + seg
                lineTo(xm, fringeBottom)
                lineTo(x1, awningBottom)
            }
            lineTo(right, awningBottom)
            close()
        }
        drawPath(fringePath, color = color)

        // 3) building body — rounded bottom corners, square top so it
        // reads as one block with the fringe above it
        drawRoundRect(
            color = color,
            topLeft = Offset(left, bodyTop),
            size = Size(w, bodyBottom - bodyTop),
            cornerRadius = CornerRadius(cornerBody, cornerBody)
        )
        drawRect(
            color = color,
            topLeft = Offset(left, bodyTop),
            size = Size(w, w * 0.06f)
        )

        // 4) two windows (cut out to the background color)
        val win = w * 0.19f
        val winY0 = bodyTop + w * 0.11f
        val inset = w * 0.11f
        val winCorner = win * 0.20f
        drawRoundRect(
            color = cutoutColor,
            topLeft = Offset(left + inset, winY0),
            size = Size(win, win),
            cornerRadius = CornerRadius(winCorner, winCorner)
        )
        drawRoundRect(
            color = cutoutColor,
            topLeft = Offset(right - inset - win, winY0),
            size = Size(win, win),
            cornerRadius = CornerRadius(winCorner, winCorner)
        )

        // 5) door (cut out to the background color), full height to the base
        val doorW = w * 0.22f
        val doorTop = winY0 + win + w * 0.07f
        val doorCorner = doorW * 0.32f
        drawRoundRect(
            color = cutoutColor,
            topLeft = Offset(cx - doorW / 2f, doorTop),
            size = Size(doorW, bodyBottom - doorTop),
            cornerRadius = CornerRadius(doorCorner, doorCorner)
        )
        drawRect(
            color = cutoutColor,
            topLeft = Offset(cx - doorW / 2f, bodyBottom - doorCorner),
            size = Size(doorW, doorCorner)
        )
    }
}

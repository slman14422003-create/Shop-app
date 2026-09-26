package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.shopmanager.app.ui.common.MotionSpecs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.rotate
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
 * REDESIGN ("بدي ياها بستايل Claude هيك يعني حرفيا"): matches Claude's own
 * splash screen structure exactly instead of approximating it — a
 * background that follows the system theme just like the Phizyo reference
 * app's splash (`splash_background` — see colors.xml: warm cream in light
 * mode, exact near-black in dark mode, not a single fixed dark tone) with
 * the mark and wordmark sitting side by side in one row (not stacked), the
 * mark small and in the brand orange accent color instead of large and
 * white/stacked above the text, exactly like the small orange asterisk
 * next to "Claude". The mortar and pestle glyph itself is unchanged (see
 * MortarAndPestleMark) — only its size, color and position relative to the
 * wordmark changed to match the reference's proportions and layout.
 *  - the app name is set in a serif face (matching Claude's own wordmark
 *    treatment — see `AppTypography`'s `ClaudeSerif` for the same choice
 *    elsewhere in the app)
 *  - the bottom credit stays the small plain letter-spaced "SEMO STUDIO"
 *    caption (no pill/chip background, no rule above it — bare text low
 *    on the screen), the same treatment Claude's own splash gives its
 *    "ANTHROPIC" line, now in the same muted grey rather than a
 *    translucent white so it reads correctly on the near-black background.
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
        animationSpec = tween(380, easing = MotionSpecs.claudeEasing),
        label = "splashEntrance"
    )

    // REDESIGN ("صمم الالوان بتصميم ChatGPT"): unified with
    // ui/theme/Color.kt's ChatGPT-palette tokens (ClaudeBgDark1/Light1,
    // ClaudeOrangeDark/Light, ClaudeTextPrimaryDark/Light,
    // ClaudeTextSecondaryDark/Light) instead of the old cream/terracotta
    // ones — pure black / white splash, ChatGPT's link-blue accent — and
    // still follows light/dark system theme, not a single fixed background.
    //
    // CONSISTENCY FIX: this used to duplicate those tokens' hex values by
    // hand instead of importing them — if Color.kt's palette ever changes,
    // this screen would silently drift out of sync with the rest of the
    // app. Now reads the same constants Color.kt/Palette.kt do, so there's
    // one source of truth for the whole theme.
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .alpha(entrance)
                .scale(0.94f + entrance * 0.06f)
        ) {
            MortarAndPestleMark(
                modifier = Modifier.size(40.dp),
                color = markAccent,
                cutoutColor = splashBackground
            )

            Spacer(Modifier.width(14.dp))

            Text(
                "إدارة المحل",
                color = onDark,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, letterSpacing = 0.2.sp),
                textAlign = TextAlign.Center
            )
        }

        // FEATURE: نص الاعتماد بالأسفل "SEMO STUDIO" بنفس أسلوب
        // "ANTHROPIC" تحت شعار Claude — نص صغير مسافته بين الحروف واسعة،
        // بلا خلفية أو خط فاصل، بلون رمادي مطفي بدل الأبيض الشفاف (عشان
        // يبين صح فوق الخلفية الغامقة الجديدة)، ثابت بالكامل (يتبع فقط
        // نفس `entrance` العام لهذه الشاشة).
        Text(
            "SEMO STUDIO",
            color = creditGrey,
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
 * The flat mortar-and-pestle glyph — same silhouette as the app icon (see
 * the adaptive-icon foreground drawable, generated from the same shape):
 * a tapered bowl (mortar) with a pestle resting diagonally in it and a
 * few scattered grains above. Drawn as a handful of flat filled shapes
 * with no blur/gradient/animation — the bowl's rim "cut-out" is just
 * drawn in [cutoutColor] (the splash's own background) on top, the same
 * trick the old storefront glyph used for its windows/door — so the
 * whole mark costs the same tiny, one-time amount of work as a couple of
 * Text nodes.
 */
@Composable
private fun MortarAndPestleMark(modifier: Modifier = Modifier, color: Color, cutoutColor: Color) {
    Canvas(modifier) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val w = s * 0.66f

        // ---- bowl (mortar) ----
        val bowlW = w
        val bowlH = w * 0.52f
        val bowlLeft = cx - bowlW / 2f
        val bowlRight = cx + bowlW / 2f
        val bowlTop = cy
        val bowlBottom = bowlTop + bowlH
        val bowlCorner = bowlW * 0.16f

        drawRoundRect(
            color = color,
            topLeft = Offset(bowlLeft, bowlTop),
            size = Size(bowlW, bowlH),
            cornerRadius = CornerRadius(bowlCorner, bowlCorner)
        )

        // taper the bottom corners inward so it reads as a bowl, not a box
        val taperW = bowlW * 0.20f
        val taperH = bowlH * 0.62f
        val pad = s * 0.01f
        val leftTaper = Path().apply {
            moveTo(bowlLeft - pad, bowlBottom - taperH)
            lineTo(bowlLeft + taperW, bowlBottom + pad)
            lineTo(bowlLeft - pad, bowlBottom + pad)
            close()
        }
        val rightTaper = Path().apply {
            moveTo(bowlRight + pad, bowlBottom - taperH)
            lineTo(bowlRight - taperW, bowlBottom + pad)
            lineTo(bowlRight + pad, bowlBottom + pad)
            close()
        }
        drawPath(leftTaper, color = cutoutColor)
        drawPath(rightTaper, color = cutoutColor)

        // ---- pestle, resting diagonally with its head in the bowl ----
        val pestleLen = w * 0.98f
        val pestleWidth = w * 0.20f
        val dx = w * 0.10f
        val dy = -w * 0.22f
        val capsuleCenter = Offset(cx + dx, cy + dy)

        rotate(degrees = -34f, pivot = capsuleCenter) {
            drawRoundRect(
                color = color,
                topLeft = Offset(capsuleCenter.x - pestleLen / 2f, capsuleCenter.y - pestleWidth / 2f),
                size = Size(pestleLen, pestleWidth),
                cornerRadius = CornerRadius(pestleWidth / 2f, pestleWidth / 2f)
            )
            val headR = pestleWidth * 0.62f
            val headCx = capsuleCenter.x - pestleLen / 2f + headR * 0.65f
            drawCircle(color = color, radius = headR, center = Offset(headCx, capsuleCenter.y))
        }

        // bowl opening (rim) — cut back to the background on top of
        // everything so the pestle head reads as resting inside the bowl
        val rimW = bowlW * 0.74f
        val rimH = bowlH * 0.30f
        val rimTop = bowlTop - rimH * 0.42f
        drawOval(
            color = cutoutColor,
            topLeft = Offset(cx - rimW / 2f, rimTop),
            size = Size(rimW, rimH)
        )

        // ---- a few scattered spice grains ----
        drawCircle(color = color, radius = w * 0.045f, center = Offset(cx + w * 0.30f, cy - s * 0.20f))
        drawCircle(color = color, radius = w * 0.032f, center = Offset(cx + w * 0.42f, cy - s * 0.115f))
        drawCircle(color = color, radius = w * 0.030f, center = Offset(cx + w * 0.20f, cy - s * 0.26f))
    }
}

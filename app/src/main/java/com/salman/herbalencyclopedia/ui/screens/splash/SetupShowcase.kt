package com.salman.herbalencyclopedia.ui.screens.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.translate.TranslationRepository
import com.salman.herbalencyclopedia.ui.theme.AppMotion
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import com.salman.herbalencyclopedia.ui.util.LocalAppLanguage
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val SLIDE_MS = 3200L
private const val DRAW_MS = 2100

private data class ShowcaseSlide(val title: String, val body: String)

private val showcaseSlides = listOf(
    ShowcaseSlide("Discover herbs", "Browse medicinal herbs with clear details, uses and benefits."),
    ShowcaseSlide("Organized for you", "Every herb sits in a category so you find what you need fast."),
    ShowcaseSlide("Smart search", "Search by name, symptom or benefit and get instant results."),
    ShowcaseSlide("Meet Semo", "Your built-in assistant answers questions about herbs and blends."),
    ShowcaseSlide("Blends and favorites", "Save the herbs you love and explore ready-made natural blends.")
)

@Composable
private fun trEn(english: String): String {
    val language = LocalAppLanguage.current
    if (language == AppLanguage.ENGLISH || english.isBlank()) return english
    val context = LocalContext.current
    val repository = remember(context) { TranslationRepository.shared(context) }
    val translated by produceState(initialValue = english, english, language) {
        value = repository.translate(english, targetLang = "ar", sourceLang = "en")
    }
    return translated
}

@Composable
fun SetupShowcase(
    smoothMotionAllowed: Boolean,
    modifier: Modifier = Modifier,
    illustrationHeight: androidx.compose.ui.unit.Dp = 190.dp
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SLIDE_MS)
            page = (page + 1) % showcaseSlides.size
        }
    }

    val pulse: State<Float> = if (smoothMotionAllowed) {
        val transition = rememberInfiniteTransition(label = "showcasePulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = AppMotion.Smooth), RepeatMode.Reverse),
            label = "showcasePulseValue"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    val scheme = MaterialTheme.colorScheme
    val accent = scheme.inversePrimary
    val accent2 = scheme.tertiary

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(illustrationHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (smoothMotionAllowed) {
                        (fadeIn(tween(450, easing = AppMotion.Smooth)) +
                            scaleIn(tween(450, easing = AppMotion.Smooth), initialScale = 0.9f)) togetherWith
                            fadeOut(tween(250, easing = AppMotion.Smooth))
                    } else {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    }
                },
                label = "showcaseIllustration",
                modifier = Modifier.fillMaxSize()
            ) { index ->
                SlideIllustration(index, smoothMotionAllowed, pulse, accent, accent2)
            }
        }

        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (smoothMotionAllowed) {
                    (fadeIn(tween(400, delayMillis = 120, easing = AppMotion.Smooth)) +
                        slideInVertically(tween(400, delayMillis = 120, easing = AppMotion.Smooth)) { it / 4 }) togetherWith
                        fadeOut(tween(160, easing = AppMotion.Smooth))
                } else {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                }
            },
            label = "showcaseCaption",
            modifier = Modifier.heightIn(min = 72.dp)
        ) { index ->
            val slide = showcaseSlides[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = trEn(slide.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = trEn(slide.body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            showcaseSlides.indices.forEach { i ->
                val width by animateDpAsState(
                    targetValue = if (i == page) 22.dp else 7.dp,
                    animationSpec = if (smoothMotionAllowed) AppMotion.smooth(AppMotion.Standard) else tween(0),
                    label = "dotWidth$i"
                )
                Box(
                    modifier = Modifier
                        .size(width = width, height = 7.dp)
                        .background(Color.White.copy(alpha = if (i == page) 0.95f else 0.35f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SlideIllustration(
    index: Int,
    smoothMotionAllowed: Boolean,
    pulse: State<Float>,
    accent: Color,
    accent2: Color
) {
    val progress = remember { Animatable(if (smoothMotionAllowed) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (smoothMotionAllowed) progress.animateTo(1f, tween(DRAW_MS, easing = LinearEasing))
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = progress.value
        val pl = pulse.value
        when (index) {
            0 -> drawGrowingHerb(p, pl, accent, accent2)
            1 -> drawCategoryCards(p, pl, accent, accent2)
            2 -> drawSearchDemo(p, pl, accent)
            3 -> drawAssistantChat(p, pl, accent)
            else -> drawHeartAndBlends(p, pl, accent, accent2)
        }
    }
}

private fun ease(x: Float): Float = x * x * (3f - 2f * x)

private fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

private fun DrawScope.drawGrowingHerb(p: Float, pl: Float, accent: Color, accent2: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val base = h * 0.86f
    val top = h * 0.22f
    val sway = (pl - 0.5f) * 2f * 5.dp.toPx()

    drawOval(
        color = Color.White.copy(alpha = 0.18f * ease(seg(p, 0f, 0.2f))),
        topLeft = Offset(cx - w * 0.22f, base - 6.dp.toPx()),
        size = Size(w * 0.44f, 12.dp.toPx())
    )

    val stemP = ease(seg(p, 0.05f, 0.6f))
    val stemTopY = base - (base - top) * stemP
    val stemTopX = cx + sway * stemP
    val stem = Path().apply {
        moveTo(cx, base)
        quadraticTo(cx - sway * 0.6f, (base + stemTopY) / 2f, stemTopX, stemTopY)
    }
    drawPath(stem, color = Color.White.copy(alpha = 0.9f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))

    val leafCount = 6
    for (i in 0 until leafCount) {
        val frac = 0.18f + i * 0.13f
        val s = backOut(seg(p, 0.2f + i * 0.08f, 0.42f + i * 0.08f))
        if (s <= 0f) continue
        val side = if (i % 2 == 0) 1 else -1
        val y0 = base - (base - top) * frac
        val x0 = cx + sway * frac * 0.8f
        val len = (w * 0.2f - i * 3.dp.toPx()) * s
        val lw = 15.dp.toPx() * s
        val angle = if (side > 0) -28f - pl * 6f else 208f + pl * 6f
        rotate(degrees = angle, pivot = Offset(x0, y0)) {
            drawOval(
                color = (if (i % 2 == 0) accent else accent2).copy(alpha = 0.95f),
                topLeft = Offset(x0, y0 - lw / 2f),
                size = Size(len, lw)
            )
        }
    }

    val bloom = backOut(seg(p, 0.75f, 1f))
    if (bloom > 0f) {
        val petalR = 8.dp.toPx() * bloom
        val ring = 12.dp.toPx() * bloom
        for (k in 0 until 6) {
            val a = (k * 60f + pl * 20f) * (PI.toFloat() / 180f)
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = petalR,
                center = Offset(stemTopX + cos(a) * ring, stemTopY + sin(a) * ring)
            )
        }
        drawCircle(color = accent2, radius = 7.dp.toPx() * bloom, center = Offset(stemTopX, stemTopY))
    }
}

private fun DrawScope.drawCategoryCards(p: Float, pl: Float, accent: Color, accent2: Color) {
    val w = size.width
    val h = size.height
    val cell = minOf(w * 0.3f, h * 0.38f)
    val gap = 12.dp.toPx()
    val startX = w / 2f - cell - gap / 2f
    val startY = h / 2f - cell - gap / 2f
    val corner = CornerRadius(18.dp.toPx())
    for (i in 0 until 4) {
        val col = i % 2
        val row = i / 2
        val s = backOut(seg(p, i * 0.14f, i * 0.14f + 0.4f))
        if (s <= 0f) continue
        val bob = sin((pl + i * 0.25f) * PI.toFloat()) * 2.dp.toPx()
        val cx = startX + col * (cell + gap) + cell / 2f
        val cy = startY + row * (cell + gap) + cell / 2f + bob
        val cw = cell * s
        val tl = Offset(cx - cw / 2f, cy - cw / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.2f), topLeft = tl, size = Size(cw, cw), cornerRadius = corner)
        drawRoundRect(
            Color.White.copy(alpha = 0.5f),
            topLeft = tl,
            size = Size(cw, cw),
            cornerRadius = corner,
            style = Stroke(1.5.dp.toPx())
        )
        val tint = if (i % 2 == 0) accent else accent2
        drawCircle(tint, radius = cw * 0.2f, center = Offset(cx, cy - cw * 0.12f))
        drawRoundRect(
            Color.White.copy(alpha = 0.85f),
            topLeft = Offset(cx - cw * 0.28f, cy + cw * 0.2f),
            size = Size(cw * 0.56f, 4.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.5f),
            topLeft = Offset(cx - cw * 0.18f, cy + cw * 0.3f),
            size = Size(cw * 0.36f, 4.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

private fun DrawScope.drawSearchDemo(p: Float, pl: Float, accent: Color) {
    val w = size.width
    val h = size.height

    val barW = w * 0.7f
    val barH = 30.dp.toPx()
    val barTop = h * 0.12f
    val barLeft = (w - barW) / 2f
    val barAlpha = ease(seg(p, 0f, 0.15f))
    drawRoundRect(
        Color.White.copy(alpha = 0.22f * barAlpha),
        topLeft = Offset(barLeft, barTop),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barH / 2f)
    )
    val typed = ease(seg(p, 0.1f, 0.5f))
    drawRoundRect(
        Color.White.copy(alpha = 0.9f * barAlpha),
        topLeft = Offset(barLeft + 16.dp.toPx(), barTop + barH / 2f - 2.dp.toPx()),
        size = Size((barW - 32.dp.toPx()) * 0.6f * typed, 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx())
    )

    val dots = listOf(
        Offset(0.24f, 0.58f), Offset(0.38f, 0.74f), Offset(0.5f, 0.55f),
        Offset(0.62f, 0.76f), Offset(0.76f, 0.6f)
    )
    val moveT = ease(seg(p, 0.15f, 0.8f))
    val lensR = 26.dp.toPx()
    val lensX = lerpF(w * 0.22f, w * 0.76f, moveT)
    val lensY = h * 0.62f + sin(moveT * PI.toFloat() * 2f) * h * 0.06f
    dots.forEachIndexed { i, d ->
        val center = Offset(w * d.x, h * d.y)
        val near = hypot(center.x - lensX, center.y - lensY) < lensR
        val appear = ease(seg(p, 0.05f + i * 0.04f, 0.25f + i * 0.04f))
        val r = (if (near) 9.dp.toPx() else 6.dp.toPx()) * appear
        drawCircle(if (near) accent else Color.White.copy(alpha = 0.55f), radius = r, center = center)
    }

    val lensAlpha = ease(seg(p, 0.1f, 0.25f))
    drawCircle(
        Color.White.copy(alpha = 0.95f * lensAlpha),
        radius = lensR + pl * 2.dp.toPx(),
        center = Offset(lensX, lensY),
        style = Stroke(4.dp.toPx())
    )
    val hs = Offset(lensX + lensR * 0.72f, lensY + lensR * 0.72f)
    drawLine(
        Color.White.copy(alpha = 0.95f * lensAlpha),
        start = hs,
        end = Offset(hs.x + 14.dp.toPx(), hs.y + 14.dp.toPx()),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )

    val chip = backOut(seg(p, 0.8f, 1f))
    if (chip > 0f) {
        val cw = w * 0.36f * chip
        val ch = 24.dp.toPx() * chip
        val cx = w / 2f
        val cy = h * 0.9f
        drawRoundRect(
            accent.copy(alpha = 0.95f),
            topLeft = Offset(cx - cw / 2f, cy - ch / 2f),
            size = Size(cw, ch),
            cornerRadius = CornerRadius(ch / 2f)
        )
        val check = Path().apply {
            moveTo(cx - 6.dp.toPx() * chip, cy)
            lineTo(cx - 2.dp.toPx() * chip, cy + 4.dp.toPx() * chip)
            lineTo(cx + 6.dp.toPx() * chip, cy - 4.dp.toPx() * chip)
        }
        drawPath(check, Color.Black.copy(alpha = 0.7f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawAssistantChat(p: Float, pl: Float, accent: Color) {
    val w = size.width
    val h = size.height
    val lineH = 4.dp.toPx()
    val corner = CornerRadius(16.dp.toPx())

    val userS = backOut(seg(p, 0f, 0.2f))
    if (userS > 0f) {
        val bw = w * 0.5f * userS
        val bh = 44.dp.toPx() * userS
        val left = w * 0.92f - bw
        val top = h * 0.14f
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(left, top), Size(bw, bh), corner)
        drawRoundRect(
            Color.Black.copy(alpha = 0.35f),
            Offset(left + 14.dp.toPx(), top + bh * 0.35f),
            Size(bw - 28.dp.toPx(), lineH),
            CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            Color.Black.copy(alpha = 0.2f),
            Offset(left + 14.dp.toPx(), top + bh * 0.6f),
            Size((bw - 28.dp.toPx()) * 0.6f, lineH),
            CornerRadius(2.dp.toPx())
        )
    }

    val avatarS = ease(seg(p, 0.2f, 0.35f))
    val avatarC = Offset(w * 0.14f, h * 0.62f)
    if (avatarS > 0f) {
        drawCircle(accent, radius = 16.dp.toPx() * avatarS, center = avatarC)
        rotate(-35f, pivot = avatarC) {
            drawOval(
                Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(avatarC.x - 8.dp.toPx() * avatarS, avatarC.y - 4.dp.toPx() * avatarS),
                size = Size(16.dp.toPx() * avatarS, 8.dp.toPx() * avatarS)
            )
        }
    }

    val typing = seg(p, 0.25f, 0.4f) > 0f && seg(p, 0.6f, 0.65f) < 1f
    if (typing) {
        val bx = w * 0.24f
        val by = h * 0.5f
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(bx, by), Size(64.dp.toPx(), 28.dp.toPx()), CornerRadius(14.dp.toPx()))
        for (k in 0 until 3) {
            val a = 0.35f + 0.65f * sin(((pl + k * 0.33f) % 1f) * PI.toFloat())
            drawCircle(Color.White.copy(alpha = a), 4.dp.toPx(), Offset(bx + 18.dp.toPx() + k * 14.dp.toPx(), by + 14.dp.toPx()))
        }
    }

    val replyS = backOut(seg(p, 0.6f, 0.78f))
    if (replyS > 0f) {
        val bw = w * 0.62f * replyS
        val bh = 66.dp.toPx() * replyS
        val left = w * 0.24f
        val top = h * 0.46f
        drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(left, top), Size(bw, bh), corner)
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(left, top), Size(bw, bh), corner, style = Stroke(1.5.dp.toPx()))
        val widths = listOf(0.8f, 0.9f, 0.55f)
        widths.forEachIndexed { i, f ->
            val lp = ease(seg(p, 0.75f + i * 0.07f, 0.9f + i * 0.07f))
            drawRoundRect(
                Color.White.copy(alpha = 0.85f),
                Offset(left + 14.dp.toPx(), top + 14.dp.toPx() + i * 14.dp.toPx()),
                Size((bw - 28.dp.toPx()) * f * lp, lineH),
                CornerRadius(2.dp.toPx())
            )
        }
    }
}

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.4f, cy + s * 0.1f, cx - s * 1.0f, cy - s * 0.9f, cx, cy - s * 0.35f)
    cubicTo(cx + s * 1.0f, cy - s * 0.9f, cx + s * 1.4f, cy + s * 0.1f, cx, cy + s * 0.9f)
    close()
}

private fun DrawScope.drawHeartAndBlends(p: Float, pl: Float, accent: Color, accent2: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    val orbitIn = ease(seg(p, 0.3f, 0.7f))
    val orbitR = minOf(w, h) * 0.36f
    for (k in 0 until 4) {
        val a = (p * 200f + pl * 25f + k * 90f) * (PI.toFloat() / 180f)
        val ox = cx + cos(a) * orbitR * 1.25f * orbitIn
        val oy = cy + sin(a) * orbitR * 0.85f * orbitIn
        rotate(degrees = (a * 180f / PI.toFloat()) + 90f, pivot = Offset(ox, oy)) {
            drawOval(
                color = (if (k % 2 == 0) accent else accent2).copy(alpha = 0.95f * orbitIn),
                topLeft = Offset(ox - 6.dp.toPx(), oy - 11.dp.toPx()),
                size = Size(12.dp.toPx(), 22.dp.toPx())
            )
        }
    }

    val appear = backOut(seg(p, 0f, 0.45f))
    val beat = 1f + 0.07f * sin(pl * PI.toFloat())
    val s = minOf(w, h) * 0.2f * appear * beat
    if (s > 0f) {
        drawPath(heartPath(cx, cy, s * 1.05f), Color.White.copy(alpha = 0.25f))
        drawPath(heartPath(cx, cy, s), Color.White.copy(alpha = 0.95f))
    }

    val spark = ease(seg(p, 0.6f, 0.9f))
    val sparks = listOf(Offset(0.18f, 0.25f), Offset(0.82f, 0.22f), Offset(0.78f, 0.8f), Offset(0.2f, 0.78f))
    sparks.forEachIndexed { i, o ->
        val r = (5.dp.toPx() + 3.dp.toPx() * sin((pl + i * 0.3f) * PI.toFloat())) * spark
        val c = Offset(w * o.x, h * o.y)
        val col = Color.White.copy(alpha = 0.8f * spark)
        drawLine(col, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(col, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

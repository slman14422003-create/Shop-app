package com.shopmanager.app.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.assistant.RobotMood
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import kotlinx.coroutines.delay
import kotlin.random.Random

private val HeadFill = Color(0xFF1D1D1F)
private val ScreenFill = Color(0xFF000000)
private val EyeColor = Color(0xFFF5F5F5)
private val Yellow = Color(0xFFF2C94C)
val AssistantSuccessGreen = Color(0xFF7FD39A)
val AssistantWarnOrange = Color(0xFFF0A35E)

fun robotDescription(mood: RobotMood): String = when (mood) {
    RobotMood.READY -> "المساعد جاهز"
    RobotMood.THINKING -> "المساعد يفكر"
    RobotMood.SUCCESS -> "المساعد: تمت العملية بنجاح"
    RobotMood.CONFUSED -> "المساعد لم يفهم أو تنقصه معلومة"
    RobotMood.LISTENING -> "المساعد يستمع"
}

/**
 * الروبوت المتفاعل: رسم vector مسطّح بـ Canvas بلا صور. في وضع الأداء LOW (أو عند إيقاف الحركة من إعدادات المساعد)
 * لا توجد أي حركة، وتتبدّل الرسمة فقط عند تغيّر الحالة.
 */
@Composable
fun RobotAvatar(mood: RobotMood, modifier: Modifier = Modifier, size: Dp = 140.dp, animate: Boolean = true) {
    val animated = animate && LocalPerformanceTier.current != PerformanceTier.LOW
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    // وضع الأداء العالي (STANDARD): حركات إضافية — ميلان الرأس، تمايل الأنتينا ونبض توهّجها،
    // تنفّس الجسم، نظر الروبوت يمنة ويسرة، قفزة عند النجاح وهزّة رأس عند عدم الفهم.
    val rich = animated && LocalPerformanceTier.current == PerformanceTier.STANDARD

    val floatState: State<Float>
    val wanderState: State<Float>
    val tiltState: State<Float>
    val swayState: State<Float>
    val glowState: State<Float>
    val breatheState: State<Float>
    if (animated) {
        val t = rememberInfiniteTransition(label = "robot")
        floatState = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "float")
        wanderState = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "wander")
        if (rich) {
            tiltState = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
            swayState = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
            glowState = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
            breatheState = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
        } else {
            tiltState = remember { mutableFloatStateOf(0f) }
            swayState = remember { mutableFloatStateOf(0f) }
            glowState = remember { mutableFloatStateOf(0f) }
            breatheState = remember { mutableFloatStateOf(0f) }
        }
    } else {
        floatState = remember { mutableFloatStateOf(0f) }
        wanderState = remember { mutableFloatStateOf(0f) }
        tiltState = remember { mutableFloatStateOf(0f) }
        swayState = remember { mutableFloatStateOf(0f) }
        glowState = remember { mutableFloatStateOf(0f) }
        breatheState = remember { mutableFloatStateOf(0f) }
    }
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(animated, mood) {
        blink = false
        if (animated && mood == RobotMood.READY) {
            while (true) {
                delay(rich.let { if (it) 2600L else 4000L } + Random.nextLong(0L, 1000L))
                blink = true
                delay(140L)
                blink = false
                // رمشة مزدوجة أحياناً في الأداء العالي
                if (rich && Random.nextInt(4) == 0) {
                    delay(170L); blink = true; delay(120L); blink = false
                }
            }
        }
    }
    // نظرة جانبية عشوائية (READY فقط).
    val look = remember { Animatable(0f) }
    LaunchedEffect(rich, mood) {
        if (rich && mood == RobotMood.READY) {
            while (true) {
                delay(1800L + Random.nextLong(0L, 2200L))
                look.animateTo(if (Random.nextBoolean()) 1f else -1f, tween(360, easing = FastOutSlowInEasing))
                delay(800L + Random.nextLong(0L, 900L))
                look.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
        } else look.snapTo(0f)
    }
    // ردّ فعل عند تغيّر الحالة: قفزة (نجاح) أو هزّة (لم يفهم) أو إيماءة (يفكر).
    val kick = remember { Animatable(0f) }
    LaunchedEffect(rich, mood) {
        kick.snapTo(0f)
        if (rich && mood != RobotMood.READY) {
            kick.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            kick.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val description = robotDescription(mood)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val w = this.size.width
        val moodColor = when (mood) {
            RobotMood.READY -> Color.White
            RobotMood.THINKING -> Yellow
            RobotMood.SUCCESS -> AssistantSuccessGreen
            RobotMood.CONFUSED -> AssistantWarnOrange
            RobotMood.LISTENING -> Color(0xFF6EA8FE)
        }
        val bob = if (animated) floatState.value * w * 0.018f else 0f
        val k = kick.value
        val jump = if (mood == RobotMood.SUCCESS) -k * w * 0.07f else 0f
        val shake = if (mood == RobotMood.CONFUSED) k * 7f else if (mood == RobotMood.THINKING) k * 3f else 0f
        val tilt = tiltState.value * 2.4f + shake
        val breathe = 1f + breatheState.value * 0.014f
        translate(top = bob + jump) {
            rotate(tilt, Offset(w * .5f, w * .9f)) {
                scale(breathe, breathe, Offset(w * .5f, w * .9f)) {
                    drawRobot(w, mood, moodColor, outline, wanderState.value, blink, look.value, glowState.value, swayState.value * 7f)
                }
            }
        }
    }
}

private fun DrawScope.drawRobot(
    w: Float, mood: RobotMood, moodColor: Color, outline: Color, wander: Float, blink: Boolean,
    look: Float = 0f, glow: Float = 0f, sway: Float = 0f
) {
    val line = Stroke(width = (w * 0.012f).coerceAtLeast(1.5f))

    // الأنتينا
    rotate(sway, Offset(w * .5f, w * .2f)) {
        if (glow > 0f) drawCircle(moodColor.copy(alpha = 0.28f * (1f - glow)), w * (.036f + .05f * glow), Offset(w * .5f, w * .09f))
        drawLine(outline, Offset(w * .5f, w * .2f), Offset(w * .5f, w * .115f), strokeWidth = w * .014f, cap = StrokeCap.Round)
        drawCircle(moodColor, w * .036f, Offset(w * .5f, w * .09f))
        drawCircle(outline, w * .036f, Offset(w * .5f, w * .09f), style = line)
    }

    // الأذنان
    for (x in listOf(w * .115f, w * .835f)) {
        drawRoundRect(HeadFill, Offset(x, w * .34f), Size(w * .05f, w * .15f), CornerRadius(w * .02f))
        drawRoundRect(outline, Offset(x, w * .34f), Size(w * .05f, w * .15f), CornerRadius(w * .02f), style = line)
    }

    // الرأس
    drawRoundRect(HeadFill, Offset(w * .17f, w * .2f), Size(w * .66f, w * .44f), CornerRadius(w * .13f))
    drawRoundRect(outline, Offset(w * .17f, w * .2f), Size(w * .66f, w * .44f), CornerRadius(w * .13f), style = line)

    // شاشة الوجه
    drawRoundRect(ScreenFill, Offset(w * .23f, w * .26f), Size(w * .54f, w * .32f), CornerRadius(w * .09f))

    val eyeY = w * .385f
    val lx = w * .39f
    val rx = w * .61f
    val er = w * .036f
    val eyeStroke = Stroke(width = w * .018f, cap = StrokeCap.Round)

    when (mood) {
        RobotMood.READY -> {
            if (blink) {
                drawLine(EyeColor, Offset(lx - er, eyeY), Offset(lx + er, eyeY), strokeWidth = w * .014f, cap = StrokeCap.Round)
                drawLine(EyeColor, Offset(rx - er, eyeY), Offset(rx + er, eyeY), strokeWidth = w * .014f, cap = StrokeCap.Round)
            } else {
                val ld = look * w * .026f
                drawOval(EyeColor, Offset(lx - er + ld, eyeY - er * 1.3f), Size(er * 2, er * 2.6f))
                drawOval(EyeColor, Offset(rx - er + ld, eyeY - er * 1.3f), Size(er * 2, er * 2.6f))
            }
            smile(w, EyeColor, 0.05f, eyeStroke)
        }
        RobotMood.THINKING -> {
            val dx = wander * w * .022f
            drawOval(EyeColor, Offset(lx - er + dx, eyeY - er * 1.3f), Size(er * 2, er * 2.6f))
            drawOval(EyeColor, Offset(rx - er + dx, eyeY - er * 1.3f), Size(er * 2, er * 2.6f))
            drawLine(EyeColor, Offset(w * .45f, w * .5f), Offset(w * .55f, w * .5f), strokeWidth = w * .018f, cap = StrokeCap.Round)
        }
        RobotMood.SUCCESS -> {
            for (cx in listOf(lx, rx)) {
                val p = Path().apply {
                    moveTo(cx - er * 1.2f, eyeY + er * .5f)
                    quadraticTo(cx, eyeY - er * 1.6f, cx + er * 1.2f, eyeY + er * .5f)
                }
                drawPath(p, EyeColor, style = eyeStroke)
            }
            smile(w, EyeColor, 0.075f, eyeStroke)
        }
        RobotMood.LISTENING -> {
            val grow = 1f + kotlin.math.abs(look) * 0.12f
            drawOval(EyeColor, Offset(lx - er * grow, eyeY - er * 1.6f * grow), Size(er * 2 * grow, er * 3.2f * grow))
            drawOval(EyeColor, Offset(rx - er * grow, eyeY - er * 1.6f * grow), Size(er * 2 * grow, er * 3.2f * grow))
            drawCircle(EyeColor, w * .028f, Offset(w * .5f, w * .515f), style = eyeStroke)
        }
        RobotMood.CONFUSED -> {
            rotate(-16f, Offset(lx, eyeY)) { drawOval(EyeColor, Offset(lx - er, eyeY - er * 1.3f), Size(er * 2, er * 2.6f)) }
            rotate(16f, Offset(rx, eyeY)) { drawOval(EyeColor, Offset(rx - er, eyeY - er * 1.3f), Size(er * 2, er * 2.6f)) }
            val p = Path().apply {
                moveTo(w * .42f, w * .505f)
                quadraticTo(w * .46f, w * .475f, w * .5f, w * .505f)
                quadraticTo(w * .54f, w * .535f, w * .58f, w * .505f)
            }
            drawPath(p, EyeColor, style = eyeStroke)
        }
    }

    // الجسم
    drawRoundRect(HeadFill, Offset(w * .31f, w * .675f), Size(w * .38f, w * .24f), CornerRadius(w * .09f))
    drawRoundRect(outline, Offset(w * .31f, w * .675f), Size(w * .38f, w * .24f), CornerRadius(w * .09f), style = line)
    drawCircle(moodColor, w * .024f, Offset(w * .5f, w * .79f))
}

private fun DrawScope.smile(w: Float, color: Color, depth: Float, stroke: Stroke) {
    val p = Path().apply {
        moveTo(w * .43f, w * .485f)
        quadraticTo(w * .5f, w * (.485f + depth), w * .57f, w * .485f)
    }
    drawPath(p, color, style = stroke)
}

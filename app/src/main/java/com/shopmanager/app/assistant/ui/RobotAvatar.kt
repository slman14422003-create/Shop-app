package com.shopmanager.app.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.snapshotFlow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import androidx.compose.ui.graphics.Brush
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

private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f
private val CheekPink = Color(0xFFFF8FA3)
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
fun RobotAvatar(
    mood: RobotMood,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    animate: Boolean = true,
    levelState: State<Float>? = null,
    pokeKey: Int = 0
) {
    val haloBase = MaterialTheme.colorScheme.primary
    val haloCache = remember { HaloCache() }
    val animated = animate && com.shopmanager.app.data.performance.rememberInfiniteAnimationsAllowed()
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    // وضع الأداء العالي (HIGH): حركات إضافية — ميلان الرأس، تمايل الأنتينا ونبض توهّجها،
    // تنفّس الجسم، نظر الروبوت يمنة ويسرة، قفزة عند النجاح وهزّة رأس عند عدم الفهم.
    val rich = animated && LocalPerformanceTier.current == PerformanceTier.HIGH
    val listening = animated && mood == RobotMood.LISTENING

    val waveState: State<Float> = if (listening) {
        val tw = rememberInfiniteTransition(label = "listen")
        tw.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    } else remember { mutableFloatStateOf(0f) }
    val levelSmooth = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(listening, levelState) {
        if (listening && levelState != null) {
            snapshotFlow { levelState.value }.collect { raw ->
                levelSmooth.floatValue = RobotListenMath.smooth(levelSmooth.floatValue, RobotListenMath.normalizeLevel(raw))
            }
        } else levelSmooth.floatValue = 0f
    }

    val floatState: State<Float>
    val wanderState: State<Float>
    val tiltState: State<Float>
    val swayState: State<Float>
    val glowState: State<Float>
    val breatheState: State<Float>
    val clockState: State<Float>
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
        // الساعة اللانهائية: فقط عند الحاجة (الأداء العالي، أو نقاط التفكير/علامة الاستفهام) لتوفير المعالج في المتوازن.
        clockState = if (rich || mood == RobotMood.THINKING || mood == RobotMood.CONFUSED)
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "clock")
        else remember { mutableFloatStateOf(0f) }
    } else {
        floatState = remember { mutableFloatStateOf(0f) }
        wanderState = remember { mutableFloatStateOf(0f) }
        tiltState = remember { mutableFloatStateOf(0f) }
        swayState = remember { mutableFloatStateOf(0f) }
        glowState = remember { mutableFloatStateOf(0f) }
        breatheState = remember { mutableFloatStateOf(0f) }
        clockState = remember { mutableFloatStateOf(0f) }
    }
    // دخول ناعم عند أول ظهور (نبضة نابضة).
    val intro = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) {
        if (animated) { intro.snapTo(0f); intro.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) } else intro.snapTo(1f)
    }
    // تلويحة باليد من وقت لآخر (READY فقط، الأداء العالي).
    val waveArm = remember { Animatable(0f) }
    LaunchedEffect(rich, mood) {
        waveArm.snapTo(0f)
        if (rich && mood == RobotMood.READY) {
            delay(2500L)
            while (true) {
                waveArm.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                repeat(3) {
                    waveArm.animateTo(0.72f, tween(190, easing = FastOutSlowInEasing))
                    waveArm.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                }
                waveArm.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
                delay(6500L + Random.nextLong(0L, 4500L))
            }
        }
    }
    // ذراعان للأعلى عند النجاح + شرارات احتفال.
    val armsUp = remember { Animatable(0f) }
    LaunchedEffect(animated, mood) {
        armsUp.animateTo(if (animated && mood == RobotMood.SUCCESS) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(animated, mood) {
        burst.snapTo(0f)
        if (animated && mood == RobotMood.SUCCESS) burst.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }
    // قفزة عند اللمس.
    val hop = remember { Animatable(0f) }
    LaunchedEffect(pokeKey) {
        if (pokeKey > 0 && animated) {
            hop.snapTo(0f)
            hop.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            hop.animateTo(0f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessMediumLow))
        }
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
    LaunchedEffect(rich, animated, mood) {
        kick.snapTo(0f)
        if ((rich && mood != RobotMood.READY) || (animated && mood == RobotMood.LISTENING)) {
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
        val wv = waveState.value
        val lv = levelSmooth.floatValue
        val h = hop.value
        val jump = when {
            mood == RobotMood.SUCCESS -> -k * w * 0.07f
            listening -> -(k * w * 0.05f + lv * w * 0.018f)
            else -> 0f
        } - h * w * 0.09f
        val shake = if (mood == RobotMood.CONFUSED) k * 7f else if (mood == RobotMood.THINKING) k * 3f else 0f
        val listenTilt = if (listening) sin(wv * TWO_PI) * 2.2f + lv * 3f else 0f
        val tilt = tiltState.value * 2.4f + shake + listenTilt + h * 5f
        val breathe = 1f + breatheState.value * 0.014f + if (listening) lv * 0.03f + k * 0.05f else 0f
        val antennaSway = swayState.value * 7f + if (listening) sin(wv * TWO_PI * 2f) * 10f else 0f
        val antennaGlow = if (listening) (wv * 2f) % 1f else glowState.value
        val introV = intro.value
        val introScale = 0.55f + 0.45f * introV

        // هالة خلف الرأس بلون الحالة (تنبض مع الأداء العالي).
        if (rich) {
            val haloColor = if (mood == RobotMood.READY) haloBase else moodColor
            val a = 0.10f + 0.10f * glowState.value + 0.08f * armsUp.value
            if (haloCache.w != w || haloCache.color != haloColor) {
                haloCache.w = w; haloCache.color = haloColor
                haloCache.brush = Brush.radialGradient(listOf(haloColor, Color.Transparent), Offset(w * .5f, w * .45f), w * .62f)
            }
            haloCache.brush?.let { drawCircle(it, w * .62f, Offset(w * .5f, w * .45f), alpha = a) }
        }
        // ظل أرضي يتفاعل مع الحركة.
        run {
            val sc = (1f + (bob + jump) / (w * 0.06f)).coerceIn(0.6f, 1.15f)
            val sw = w * 0.36f * sc * introScale
            drawOval(outline.copy(alpha = 0.22f * sc.coerceAtMost(1f)), Offset(w * .5f - sw / 2f, w * .945f), Size(sw, w * .028f))
        }
        scale(introScale, introScale, Offset(w * .5f, w * .95f)) {
            translate(top = bob + jump) {
                rotate(tilt, Offset(w * .5f, w * .9f)) {
                    scale(breathe, breathe, Offset(w * .5f, w * .9f)) {
                        drawRobot(
                            w, mood, moodColor, outline, wanderState.value, blink, look.value, antennaGlow, antennaSway,
                            listening = listening, rich = rich, wave = wv, level = lv,
                            clock = clockState.value, armWave = waveArm.value, armsUp = armsUp.value, burst = burst.value
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRobot(
    w: Float, mood: RobotMood, moodColor: Color, outline: Color, wander: Float, blink: Boolean,
    look: Float = 0f, glow: Float = 0f, sway: Float = 0f,
    listening: Boolean = false, rich: Boolean = false, wave: Float = 0f, level: Float = 0f,
    clock: Float = 0f, armWave: Float = 0f, armsUp: Float = 0f, burst: Float = 0f
) {
    val line = Stroke(width = (w * 0.012f).coerceAtLeast(1.5f))

    if (listening) {
        val count = if (rich) RobotListenMath.RING_COUNT_RICH else RobotListenMath.RING_COUNT_LIGHT
        val ringStroke = Stroke(width = (w * 0.01f).coerceAtLeast(1.5f))
        for (i in 0 until count) {
            val p = RobotListenMath.ringProgress(wave, i, count)
            val inflate = w * RobotListenMath.ringInflate(p)
            drawRoundRect(
                moodColor.copy(alpha = RobotListenMath.ringAlpha(p, level)),
                Offset(w * .17f - inflate, w * .2f - inflate),
                Size(w * .66f + inflate * 2f, w * .44f + inflate * 2f),
                CornerRadius(w * .13f + inflate),
                style = ringStroke
            )
        }
    }

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

    if (listening && rich) {
        val arcStroke = Stroke(width = (w * 0.012f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        for (i in 0..1) {
            val r = w * (.055f + .045f * i + .02f * level)
            val a = RobotListenMath.earArcAlpha(wave, i, level)
            val c = moodColor.copy(alpha = a)
            drawArc(c, 135f, 90f, false, Offset(w * .115f - r, w * .415f - r), Size(r * 2f, r * 2f), style = arcStroke)
            drawArc(c, -45f, 90f, false, Offset(w * .885f - r, w * .415f - r), Size(r * 2f, r * 2f), style = arcStroke)
        }
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
            for (i in 0..2) {
                val ph = (clock + i * 0.2f) % 1f
                val dy = -sin(ph * PI_F) * w * .022f
                drawCircle(EyeColor, w * .014f, Offset(w * (.44f + i * .06f), w * .505f + dy))
            }
        }
        RobotMood.SUCCESS -> {
            drawCircle(CheekPink.copy(alpha = 0.40f), w * .03f, Offset(w * .325f, w * .505f))
            drawCircle(CheekPink.copy(alpha = 0.40f), w * .03f, Offset(w * .675f, w * .505f))
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
            val grow = 1f + kotlin.math.abs(look) * 0.12f + level * 0.22f
            val ld = if (listening) sin(wave * TWO_PI) * w * .012f else 0f
            drawOval(EyeColor, Offset(lx - er * grow + ld, eyeY - er * 1.6f * grow), Size(er * 2 * grow, er * 3.2f * grow))
            drawOval(EyeColor, Offset(rx - er * grow + ld, eyeY - er * 1.6f * grow), Size(er * 2 * grow, er * 3.2f * grow))
            drawCircle(EyeColor, w * (.022f + .02f * level), Offset(w * .5f, w * .515f), style = eyeStroke)
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

    // الذراعان (خلف الجسم): تأرجح هادئ، تلويح باليمنى، وارتفاع للأعلى عند النجاح.
    run {
        val idle = if (rich) sin(clock * TWO_PI) * 4f else 0f
        val rightAngle = if (armsUp > 0.01f) -(18f + 150f * armsUp) + sin(clock * TWO_PI * 2f) * 8f * armsUp
        else -(18f + armWave * 105f) + idle + armWave * sin(clock * TWO_PI * 3f) * 7f
        val leftAngle = if (armsUp > 0.01f) (18f + 150f * armsUp) - sin(clock * TWO_PI * 2f) * 8f * armsUp else 18f - idle
        val aw = w * .05f
        val ah = w * .14f
        drawArm(w, w * .325f, leftAngle, aw, ah, outline, line)
        drawArm(w, w * .675f, rightAngle, aw, ah, outline, line)
    }

    // الجسم
    drawRoundRect(HeadFill, Offset(w * .31f, w * .675f), Size(w * .38f, w * .24f), CornerRadius(w * .09f))
    drawRoundRect(outline, Offset(w * .31f, w * .675f), Size(w * .38f, w * .24f), CornerRadius(w * .09f), style = line)
    if (listening) {
        for (i in 0 until RobotListenMath.BAR_COUNT) {
            val x = w * (.5f + (i - 2) * .045f)
            val h = w * RobotListenMath.barHeight(i, wave, level)
            drawLine(moodColor, Offset(x, w * .79f - h), Offset(x, w * .79f + h), strokeWidth = w * .018f, cap = StrokeCap.Round)
        }
    } else {
        drawCircle(moodColor, w * .024f, Offset(w * .5f, w * .79f))
    }

    // شرارات احتفال عند النجاح.
    if (burst > 0.01f && burst < 0.999f) {
        val a = (1f - burst).coerceIn(0f, 1f)
        val st = Stroke(width = (w * .012f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        for (i in 0 until 6) {
            val ang = (i * 60f + 20f) * PI_F / 180f
            val dist = w * (.34f + .16f * burst)
            val cx = w * .5f + cos(ang) * dist
            val cy = w * .44f + sin(ang) * dist * .85f
            val r = w * .03f * (1f - .4f * burst)
            val c = (if (i % 2 == 0) Yellow else moodColor).copy(alpha = a)
            drawLine(c, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = st.width, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = st.width, cap = StrokeCap.Round)
        }
    }
    // علامة استفهام عائمة عند عدم الفهم.
    if (mood == RobotMood.CONFUSED) {
        val fy = sin(clock * TWO_PI) * w * .01f
        val c = moodColor.copy(alpha = 0.55f + 0.35f * abs(sin(clock * PI_F)))
        val qs = Stroke(width = (w * .016f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
        val qx = w * .83f
        val qy = w * .11f + fy
        val r = w * .04f
        drawArc(c, 180f, 220f, false, Offset(qx - r, qy - r), Size(r * 2f, r * 2f), style = qs)
        drawLine(c, Offset(qx + r * .55f, qy + r * .85f), Offset(qx, qy + r * 1.7f), strokeWidth = qs.width, cap = StrokeCap.Round)
        drawCircle(c, w * .009f, Offset(qx, qy + r * 2.3f))
    }
}

private fun DrawScope.smile(w: Float, color: Color, depth: Float, stroke: Stroke) {
    val p = Path().apply {
        moveTo(w * .43f, w * .485f)
        quadraticTo(w * .5f, w * (.485f + depth), w * .57f, w * .485f)
    }
    drawPath(p, color, style = stroke)
}

/** يحفظ فرشاة الهالة بين الإطارات بدل إنشاء shader جديد كل إطار. */
private class HaloCache {
    var w: Float = 0f
    var color: Color = Color.Unspecified
    var brush: Brush? = null
}

private fun DrawScope.drawArm(w: Float, px: Float, ang: Float, aw: Float, ah: Float, outline: Color, line: Stroke) {
    val py = w * .715f
    rotate(ang, Offset(px, py)) {
        drawRoundRect(HeadFill, Offset(px - aw / 2f, py - aw * .3f), Size(aw, ah), CornerRadius(aw / 2f))
        drawRoundRect(outline, Offset(px - aw / 2f, py - aw * .3f), Size(aw, ah), CornerRadius(aw / 2f), style = line)
    }
}

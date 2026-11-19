package com.shopmanager.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.LocalSemanticColors

@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    if (LocalPerformanceTier.current != PerformanceTier.HIGH) {
        // LOW: لا نوابض ولا تحجيم، لكن يبقى تأكيد اللمس: خفوت قصير (75ms) يُقرأ في مرحلة الرسم فقط.
        val pressed by interactionSource.collectIsPressedAsState()
        val a = animateFloatAsState(
            targetValue = if (pressed) 0.72f else 1f,
            animationSpec = tween(75, easing = MotionSpecs.claudeEasing),
            label = "pressAlphaLow"
        )
        return this.graphicsLayer {
            alpha = a.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    }
    val scale = remember { Animatable(1f) }
    val target = MotionSpecs.pressScale()
    val spec = MotionSpecs.quickSpring<Float>()
    LaunchedEffect(interactionSource, target) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(target, spec)
                is PressInteraction.Release, is PressInteraction.Cancel -> scale.animateTo(1f, spec)
            }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * استقرار قيمة رقمية عند تغيّرها: تلاشٍ خفيف + إزاحة رأسية صغيرة (5dp) تُقرأ في graphicsLayer
 * (مرحلة الرسم، بلا إعادة تركيب). لا عدّ تصاعدي ولا أرقام وسيطة. HIGH فقط وبلا reduceMotion.
 * أول قيمة تُعرض بلا حركة؛ كل تغيّر بعدها يشغّل الاستقرار.
 */
@Composable
fun Modifier.valueSettle(key: Any?): Modifier {
    if (LocalPerformanceTier.current != PerformanceTier.HIGH || MotionSpecs.reduceMotion()) return this
    val progress = remember { Animatable(1f) }
    val seen = remember { booleanArrayOf(false) }
    LaunchedEffect(key) {
        if (!seen[0]) { seen[0] = true; return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MotionSpecs.slowMs, easing = MotionSpecs.claudeEasing))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = 0.35f + 0.65f * p
        translationY = (1f - p) * 5.dp.toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/**
 * true أثناء تشغيل انتقال شاشات (المدخل الحالي في المكدس ليس RESUMED بعد).
 * يُقرأ داخل graphicsLayer/drawBehind فقط، فلا يُعاد تركيب أي بطاقة عند تبدّله.
 */
val LocalNavMotionActive = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

@Composable
fun AnimatedAmount(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = appAmountStyle(AppAmountRowSize),
    color: Color = Color.Unspecified,
    suffix: String = "",
    maxLines: Int = 1,
    format: (Double) -> String = { Formatters.number(it) }
) {
    Text(
        text = format(amount) + suffix,
        modifier = modifier.valueSettle(amount),
        style = style.tabular(),
        color = color,
        maxLines = maxLines,
        softWrap = false
    )
}

@Composable
fun AnimatedCheckmark(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = LocalSemanticColors.current.success
) {
    val high = LocalPerformanceTier.current == PerformanceTier.HIGH && !MotionSpecs.reduceMotion()
    val progress = remember { Animatable(if (high) 0f else 1f) }
    LaunchedEffect(high) {
        if (high) progress.animateTo(1f, tween(420, easing = MotionSpecs.claudeEasing))
    }
    Canvas(modifier.size(size)) {
        val p = progress.value
        val stroke = this.size.minDimension * 0.11f
        val inset = stroke / 2f
        val arcSweep = 360f * (p / 0.6f).coerceIn(0f, 1f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = arcSweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val w = this.size.width
        val h = this.size.height
        val a = Offset(w * 0.28f, h * 0.52f)
        val b = Offset(w * 0.45f, h * 0.68f)
        val c = Offset(w * 0.73f, h * 0.36f)
        val t = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        if (t > 0f) {
            val firstLeg = (t / 0.4f).coerceIn(0f, 1f)
            val secondLeg = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            drawLine(color, a, Offset(a.x + (b.x - a.x) * firstLeg, a.y + (b.y - a.y) * firstLeg), stroke, StrokeCap.Round)
            if (secondLeg > 0f) {
                drawLine(color, b, Offset(b.x + (c.x - b.x) * secondLeg, b.y + (c.y - b.y) * secondLeg), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = LocalSemanticColors.current.success,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val high = LocalPerformanceTier.current == PerformanceTier.HIGH
    val animated = remember { Animatable(if (high) 0f else progress) }
    val entranceMs = MotionSpecs.entranceMillis()
    LaunchedEffect(progress, high) {
        if (high) animated.animateTo(progress, tween(entranceMs, easing = MotionSpecs.claudeEasing))
        else animated.snapTo(progress)
    }
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            color, -90f, 360f * animated.value.coerceIn(0f, 1f), false,
            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SkeletonCrossfade(
    loading: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (LocalPerformanceTier.current != PerformanceTier.HIGH) {
        if (loading) skeleton() else content()
    } else {
        Crossfade(
            targetState = loading,
            animationSpec = MotionSpecs.contentTween(),
            label = "skeletonCrossfade"
        ) { isLoading -> if (isLoading) skeleton() else content() }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.personSharedBounds(personId: String): Modifier {
    if (LocalPerformanceTier.current != PerformanceTier.HIGH) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@personSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "person_$personId"),
            animatedVisibilityScope = visibility
        )
    }
}

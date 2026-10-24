package com.salman.herbalencyclopedia.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * "الزجاج الحقيقي": بدل تدرّج شفاف تقريبي (الزجاج القديم في [LiquidGlassSurface])
 * الذي لا يعكس ما تحته فعلياً، هذا الملف يسجّل الخلفية الحيّة للشاشة
 * (خلفية AmbientBackground + محتوى NavHost) في طبقة رسم واحدة عبر
 * [Modifier.glassBackdropSource]، ثم يسمح لأي عنصر عائم فوقها (الشريط
 * السفلي، الشريط العلوي) بأخذ نسخة مموَّهة فعلياً منها عبر
 * [Modifier.glassBackdropBlur] — تماماً كزجاج iOS/Android الحديث حيث
 * ترى ألوان الواجهة وهي تتحرك خلف الزجاج بدل بقعة لون ثابتة.
 *
 * يعتمد على واجهة GraphicsLayer المُقدَّمة في Compose UI 1.7 (متوفرة عبر
 * compose-bom 2024.09 فما فوق)، والتمويه الحقيقي (RenderEffect) لا يتوفر
 * إلا على أندرويد 12 فأعلى؛ لذلك كل استدعاء هنا يُستخدم دائماً خلف تحقّق
 * `Build.VERSION.SDK_INT >= S` تماماً كبقية طبقات "الزجاج السائل" الأخرى
 * بالمشروع (انظر LiquidGlass.kt وAmbientBackground.kt)، مع رجوع تلقائي
 * للزجاج التقريبي القديم على ما دون ذلك.
 */
@Stable
class GlassBackdropState {
    /** طبقة الرسم المسجَّلة لخلفية الشاشة، تُملأ من [Modifier.glassBackdropSource]. */
    var layer by mutableStateOf<androidx.compose.ui.graphics.layer.GraphicsLayer?>(null)

    /** موضع مصدر الخلفية بالنسبة لجذر الشاشة، لمحاذاة الترجمة عند القراءة. */
    var sourceOrigin by mutableStateOf(Offset.Zero)
}

/** مصدر افتراضي منفصل (بلا اتصال فعلي) يُستخدم فقط إن نُسي تزويد الحالة الحقيقية. */
val LocalGlassBackdrop = compositionLocalOf { GlassBackdropState() }

/** الهامش السفلي الذي يجب أن يحجزه أي محتوى قابل للتمرير (LazyColumn/Grid)
 *  كي يستقر آخر عنصر فيه فوق الشريط العائم السفلي مرتاحاً، مع بقاء إمكانية
 *  التمرير خطوة إضافية بحيث تظهر (وتُموَّه) نهاية القائمة فعلياً خلف الزجاج
 *  أثناء الحركة نفسها — بدل توقّف القائمة تماماً قبل منطقة الشريط وكأن ما
 *  خلفه صورة خلفية ثابتة منفصلة عنها. القيمة الفعلية تُزوَّد من
 *  HerbalNavGraph (ارتفاع الشريط الحقيقي المقاس)، صفر حين لا يوجد شريط
 *  عائم (تابلت/شريط جانبي).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * يُوضع على الحاوية الأكبر التي تمثّل "خلفية التطبيق الحيّة" (هنا:
 * AmbientBackground الممتدة كامل الشاشة خلف NavHost) كي تُسجَّل كل إطار
 * في طبقة رسم منفصلة يعاد استخدامها لاحقاً عند رسم الزجاج، بلا أي تكلفة
 * إضافية على الرسم الطبيعي على الشاشة (التسجيل والرسم الفعلي يحدثان معاً).
 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = composed {
    val graphicsLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    this
        .onGloballyPositioned { coordinates ->
            state.sourceOrigin = coordinates.positionInRoot()
        }
        .drawWithContent {
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            graphicsLayer.record(density = density, layoutDirection = layoutDirection, size = IntSize(w, h)) {
                this@drawWithContent.drawContent()
            }
            state.layer = graphicsLayer
            drawLayer(graphicsLayer)
        }
}

/**
 * يُوضع على عنصر عائم زجاجي (الشريط السفلي/العلوي) ليرسم خلفه نسخة
 * مموَّهة فعلياً من [GlassBackdropState.layer] بدل تدرّج لوني تقريبي.
 * يُنشئ طبقة رسم منفصلة خاصة به (لا يعدّل طبقة المصدر نفسها) كي لا يؤثّر
 * التمويه هنا على رسم الخلفية الطبيعي على الشاشة أو على أي مستهلك آخر
 * بنصف قطر تمويه مختلف.
 */
fun Modifier.glassBackdropBlur(
    state: GlassBackdropState,
    blurRadius: Dp = 26.dp,
    tint: Color = Color.White,
    tintAlpha: Float = 0.30f
): Modifier = composed {
    // التمويه الحقيقي (RenderEffect) غير متاح إلا من أندرويد 12 (API 31)
    // فما فوق — قيد من نظام التشغيل نفسه. جُرِّب سابقاً رسم نفس المحتوى
    // الحي خلف الشريط بلا تمويه على الإصدارات الأقدم (شفافية فقط بلا
    // RenderEffect) كتعويض، لكن النتيجة الفعلية كانت أسوأ من الشكل
    // التقريبي القديم: نافذة صغيرة تكشف شريحة *حادة غير مموَّهة* مما تحتها
    // (نص/ألوان القائمة خلفها) تُقرأ كخلل بصري/بقعة بيضاء غريبة بدل زجاج،
    // بالضبط ما ظهر بلقطة الشاشة. لذلك رجعنا لتعطيل هذا المكوّن بالكامل
    // على ما قبل أندرويد 12، فيستخدم [LiquidGlassSurface] عندها تلقائياً
    // تدرّجه التقريبي المصمت الغني (المُحسَّن هذه الجولة بتشبّع أعلى) بدل
    // أي محاولة لعرض محتوى حي حادّ الحواف.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@composed this

    val consumerLayer = rememberGraphicsLayer()
    var myPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val blurPx = with(density) { blurRadius.toPx() }
    val renderEffect = remember(blurPx) {
        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }

    this
        .onGloballyPositioned { myPositionInRoot = it.positionInRoot() }
        .drawWithContent {
            val source = state.layer
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (source != null && w > 0 && h > 0) {
                val offset = state.sourceOrigin - myPositionInRoot
                consumerLayer.record(density = density, layoutDirection = layoutDirection, size = IntSize(w, h)) {
                    translate(offset.x, offset.y) {
                        drawLayer(source)
                    }
                }
                consumerLayer.renderEffect = renderEffect
                drawLayer(consumerLayer)
                drawRect(tint.copy(alpha = tintAlpha))
            }
            drawContent()
        }
}

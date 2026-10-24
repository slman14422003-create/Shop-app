package com.salman.herbalencyclopedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.theme.rememberPressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbCard(
    herb: Herb,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource, pressedScale = 0.97f)
    val shape = RoundedCornerShape(20.dp)

    // بطاقة العشبة أصبحت الآن على طراز الزجاج السائل نفسه المستخدم في
    // GlassButton بدل Card عادية بخلفية Material3 مسطّحة، لتوحيد الهوية
    // البصرية مع بقية التطبيق. Surface هنا شفافة وتحمل فقط النقر/الظل،
    // بينما LiquidGlassSurface يرسم طبقات الزجاج نفسها وتحترم وضع الأداء
    // (عالي/اقتصادي) تلقائياً.
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(pressScale)
            .fillMaxWidth()
            .shadow(1.dp, shape),
        shape = shape,
        color = Color.Transparent
    ) {
        LiquidGlassSurface(
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.surfaceContainer,
            // بطاقة صف قصيرة (~80dp)، وليست بطاقة شبكة مربّعة كبيرة — انظر
            // توثيق [compact] في LiquidGlassSurface لسبب هذا التبديل (كان
            // سبب ظهور "مستطيل أبيض" غير متسق فوق بطاقات الأعشاب).
            compact = true,
            // هذه البطاقة تتكرّر بالعشرات في قائمة قابلة للتمرير؛ انظر
            // توثيق [blurBubbles] في LiquidGlassSurface — تعطيلها هنا يزيل
            // طبقة تمويه GPU منفصلة لكل صف ظاهر فيبقى التمرير سلساً حتى في
            // وضع "أداء عالٍ"، بلا أي تغيير على تدرّج/حدّ الزجاج نفسه.
            blurBubbles = false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // صورة العشبة محتوى فوق طبقة الزجاج (content) وليست جزءاً
                // من الطبقة المموَّهة، فتبقى واضحة تماماً وغير متأثرة بالتمويه.
                HerbThumbnail(imageUrl = herb.imageUrl, size = 76.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = herb.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // شارة بأهم فائدة (العنوان قبل النقطتين) ثم وصف نظيف بسطرين،
                    // بدل نص خام يبدأ برمز "○" ويُقطع في منتصف الجملة.
                    val summary = remember(herb.benefits) { summarizeBenefits(herb.benefits) }
                    if (summary.headline != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = summary.headline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (summary.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // نبضة "قلب" نابضة عند الإضافة للمفضلة بدل تبديل مفاجئ للأيقونة.
                val heartScale = remember { Animatable(1f) }
                LaunchedEffect(isFavorite) {
                    if (isFavorite) {
                        heartScale.snapTo(0.7f)
                        heartScale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    } else {
                        heartScale.snapTo(1f)
                    }
                }
                GlassIconButton(onClick = onToggleFavorite, size = 40.dp) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.scale(heartScale.value)
                    )
                }
            }
        }
    }
}

/** يدمج الأسطر بمسافة (وفاصلة نقطة عند الحاجة) بدل رمز "•" الغريب. */
private fun joinSentences(parts: List<String>): String =
    parts.filter { it.isNotEmpty() }.reduceOrNull { acc, next ->
        val end = acc.last()
        if (end in ".،؛!؟:…") "$acc $next" else "$acc. $next"
    } ?: ""

private class BenefitsSummary(val headline: String?, val description: String)

/** يزيل رموز التعداد (○ • · - ...) ويفصل العنوان (قبل أول ":") عن الوصف. */
private fun summarizeBenefits(raw: String): BenefitsSummary {
    val bulletChars = "○●•·▪◦■□-–—*✓✔►▶ \t"
    val lines = raw.lineSequence()
        .map { it.trim().trimStart { c -> c in bulletChars }.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (lines.isEmpty()) return BenefitsSummary(null, "")
    val first = lines.first()
    val colon = first.indexOf(':')
    return if (colon in 3..30) {
        val rest = joinSentences(listOf(first.substring(colon + 1).trim()) + lines.drop(1))
        BenefitsSummary(first.substring(0, colon).trim(), rest)
    } else {
        BenefitsSummary(null, joinSentences(lines))
    }
}

/**
 * @param previewOnClick إذا كانت true وتوجد صورة فعلية، يفتح الضغط على
 * الصورة معاينة بحجم كامل الشاشة (انظر [ImagePreviewDialog]) بدل تجاهل
 * الضغطة أو تفعيل نقرة الصف كاملاً (مفيد تحديداً في شاشة تفاصيل العشبة،
 * حيث الصورة ليست جزءاً من صف قابل للنقر أصلاً). في بطاقة القائمة
 * (HerbCard) تبقى false افتراضياً لأن نقرة الصف بالكامل تفتح التفاصيل.
 */
@Composable
fun HerbThumbnail(
    imageUrl: String?,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    previewOnClick: Boolean = false
) {
    // مربّع بزوايا مدوّرة وقصّ موحّد (Crop) بدل دائرة بإطار أبيض تختلف
    // خلفيات صورها؛ نصف القطر يتناسب مع الحجم ليبدو متناسقاً في كل مكان.
    val shape = RoundedCornerShape(size * 0.26f)
    var showPreview by remember { mutableStateOf(false) }

    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Spa,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // تعتيم خفيف للصور في الوضع الداكن كي لا تتنافر خلفياتها البيضاء
            // مع الثيم، وإطار رفيع يحدّد حافة الصورة.
            colorFilter = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(0.86f, 0.86f, 0.86f, 1f) }
                ) else null,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .then(
                    if (previewOnClick) Modifier.clickable { showPreview = true } else Modifier
                )
        )
    }

    if (showPreview && !imageUrl.isNullOrBlank()) {
        ImagePreviewDialog(imageUrl = imageUrl, onDismiss = { showPreview = false })
    }
}

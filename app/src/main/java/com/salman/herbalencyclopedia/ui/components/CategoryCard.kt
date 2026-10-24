package com.salman.herbalencyclopedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.ui.theme.rememberPressScale

/**
 * قبل هذا التعديل كانت كل بطاقة تصنيف تعرض نفس الأيقونة (Eco) بنفس
 * التدرّج اللوني، فتبدو التصنيفات متطابقة بصرياً رغم اختلاف أسمائها،
 * وحقل category.icon القادم من Firestore لم يكن يُستخدم في أي مكان.
 * هذه الخريطة تربط كلمات دالة من اسم التصنيف (أو حقل icon إن توفّر)
 * بأيقونة ولون مميزين، مع لون احتياطي متنوع حسب ترتيب التصنيف حتى لا
 * تتكرر الألوان بلا داعٍ.
 */
private val categoryPalette = listOf(
    // درجات خضراء/زيتونية/فيروزية مع ذهبي دافئ واحد — بدون أحمر/وردي يوحي
    // بالخطر لتصنيفات لا علاقة لها بالسمية.
    Color(0xFF2E7D32), Color(0xFF00796B), Color(0xFF558B2F),
    Color(0xFFB7791F), Color(0xFF00695C), Color(0xFF33691E),
    Color(0xFF8D6E1F), Color(0xFF00838F)
)

private fun iconFor(category: Category): ImageVector {
    val key = (category.icon ?: category.name)
    val n = category.name
    return when {
        // أسماء الفصائل النباتية (حلمية، شفوية، كتانية...) لا تطابق أي كلمة
        // أدناه، فكانت كلها تسقط على أيقونة Eco نفسها. أيقونات نباتية خاصة
        // بكل فصيلة تعرفها، وتناوب ثابت بالـhash لأي فصيلة جديدة.
        n.contains("حلم") || n.contains("خيم") -> Icons.Filled.Grain
        n.contains("شفو") || n.contains("نعناع") -> Icons.Filled.Grass
        n.contains("كتان") -> Icons.Filled.LocalFlorist
        n.contains("خباز") -> Icons.Filled.FilterVintage
        n.contains("سمس") -> Icons.Filled.Spa
        n.contains("رعي") -> Icons.Filled.Park
        n.contains("بقول") -> Icons.Filled.Eco
        key.contains("digest", true) || n.contains("هضم") || n.contains("معدة") -> Icons.Filled.Restaurant
        key.contains("immun", true) || n.contains("مناع") -> Icons.Filled.Shield
        key.contains("skin", true) || n.contains("جلد") || n.contains("بشرة") -> Icons.Filled.Face
        key.contains("respirat", true) || n.contains("تنفس") || n.contains("صدر") -> Icons.Filled.Air
        key.contains("nerv", true) || n.contains("أعصاب") || n.contains("نوم") -> Icons.Filled.Psychology
        key.contains("heart", true) || n.contains("قلب") || n.contains("دور") -> Icons.Filled.Favorite
        key.contains("pain", true) || n.contains("ألم") || n.contains("التهاب") -> Icons.Filled.Healing
        key.contains("women", true) || n.contains("نسائية") || n.contains("حمل") -> Icons.Filled.Female
        key.contains("weight", true) || n.contains("وزن") || n.contains("تخسيس") -> Icons.Filled.MonitorWeight
        key.contains("energy", true) || n.contains("طاقة") || n.contains("نشاط") -> Icons.Filled.Bolt
        key.contains("kidney", true) || n.contains("كلى") || n.contains("بول") -> Icons.Filled.WaterDrop
        key.contains("bone", true) || n.contains("عظام") || n.contains("مفاصل") -> Icons.Filled.Accessibility
        key.contains("liver", true) || n.contains("كبد") || n.contains("سموم") -> Icons.Filled.FilterAlt
        else -> botanicalIcons[Math.floorMod(category.name.hashCode(), botanicalIcons.size)]
    }
}

private val botanicalIcons = listOf(
    Icons.Filled.Eco, Icons.Filled.Yard, Icons.Filled.Forest,
    Icons.Filled.Nature, Icons.Filled.EmojiNature, Icons.Filled.Spa
)

private fun colorFor(category: Category): Color =
    categoryPalette[(category.id.hashCode().takeIf { category.id.isNotBlank() } ?: category.name.hashCode())
        .let { Math.floorMod(it, categoryPalette.size) }]

@Composable
fun CategoryCard(
    category: Category,
    herbCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = colorFor(category)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource, pressedScale = 0.97f)

    // الشكل السابق (صفّ بعرض الشاشة الكامل بخلفية زجاجية شبه معتمة) كان
    // يبدو كمستطيل ضخم فارغ بصرياً: مساحة اللون شبه الموحّدة بين الأيقونة
    // والحافة اليمنى بلا أي عنصر آخر تكسر رتابتها. هذه النسخة أخفّ وأصغر
    // ارتفاعاً (Surface مسطّحة بلون بطاقة عادي بدل طبقات الزجاج الثقيلة)،
    // وتضيف شارة صغيرة بعدد الأعشاب على يمين الاسم، فيمتلئ الصفّ بعناصر
    // مفيدة بدل الفراغ، ويبقى التصنيف مقروءاً كصفّ واحد ضمن قائمة (تحت
    // بعضها) بدل شبكة متعددة الأعمدة.
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(pressScale)
            .fillMaxWidth()
            .shadow(1.dp, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // إصلاح تباين/عمق: تدرّج 0.24→0.10 كان يذوب تقريباً في خلفية
            // البطاقة (surfaceContainer) بعد أن أصبحت هي نفسها أغمق قليلاً/
            // أكثر تشبّعاً (انظر Theme.kt)، فبدا مربّع الأيقونة كبقعة لون
            // خافتة بلا حضور بصري واضح رغم أن accent نفسه لون قوي مائة
            // بالمئة. رفع طرفي التدرّج (0.55→0.28) يجعل المربّع "بطاقة لون"
            // فعلية تُميَّز فوراً عن خلفية الصف، مع حدّ خفيف بلون accent
            // نفسه يفصلها بصرياً عن السطح تحتها بدل الاعتماد على التدرّج وحده.
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.28f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(category), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // شارة صغيرة بلون التصنيف بدل نص عادي أسفل العنوان، لتمييز
            // العدد بصرياً وملء الفراغ على يمين الصفّ. نفس إصلاح التباين
            // أعلاه: 0.14 كانت شبه غير مرئية كخلفية مستقلة عن النص نفسه؛
            // 0.22 مع حدّ خفيف يعطي الشارة حدوداً واضحة كعنصر منفصل بدل
            // بقعة لون باهتة خلف الرقم فقط.
            // شارة العدد: خلفية صلبة بلون التصنيف ونص أبيض بدل نص بلون التصنيف
            // نفسه فوق خلفية شبه شفافة (تباين ضعيف جداً على الأخضر الداكن).
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent
            ) {
                Text(
                    "$herbCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 30.dp)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

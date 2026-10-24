package com.salman.herbalencyclopedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.theme.rememberPressScale

/**
 * مجموعة أزرار موحّدة على طراز "الزجاج السائل" (Liquid Glass): خلفية
 * زجاجية بطبقات حقيقية (تمويه + توهّج + لمعان متحرك عبر [LiquidGlassSurface])
 * بدل تدرّج شفاف بسيط، بحواف مستديرة بالكامل (كبسولة). هذه المكوّنات بديل
 * مباشر لأزرار Material3 القياسية (Button / OutlinedButton / IconButton)
 * لتوحيد شكل الأزرار في التطبيق بالكامل، وتحترم وضع الأداء المختار من
 * الإعدادات تلقائياً عبر LiquidGlassSurface.
 */

/** زر أساسي مملوء بكبسولة زجاجية بلون الهوية (primary). */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val disabledContainer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val disabledContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(pressScale)
            .heightIn(min = 50.dp)
            .shadow(if (enabled) 3.dp else 0.dp, CircleShape),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else disabledContent
    ) {
        if (enabled) {
            LiquidGlassSurface(
                shape = CircleShape,
                modifier = Modifier,
                tint = containerColor,
                // زر الإجراء الأساسي يظهر كنسخة واحدة بارزة بالشاشة (وليس
                // بالعشرات كبطاقات القوائم)، فتكلفة اللمعان المتحرك هنا
                // نسخة واحدة فقط — يستحق الإبقاء عليه لإحساس "حي" بالعنصر
                // الأهم بالشاشة.
                sheen = true
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    content()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .background(disabledContainer)
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

/** زر ثانوي بحدّ زجاجي شفاف الخلفية، مناسب للإجراءات الأقل أهمية. */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 13.dp),
    content: @Composable RowScope.() -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(pressScale)
            .heightIn(min = 50.dp)
            .shadow(if (enabled) 3.dp else 0.dp, CircleShape),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    ) {
        LiquidGlassSurface(
            shape = CircleShape,
            tint = surface,
            borderAlpha = 0.16f
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

/**
 * زر أيقونة دائري بمظهر كبسولة زجاجية — بديل مباشر لـ IconButton القياسي،
 * يُستخدم في الأشرطة العلوية وبطاقات الأعشاب وكل مكان يحتوي على أيقونة قابلة للنقر.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 42.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    // انظر توثيق [LiquidGlassSurface.blurBubbles]: هذا الزر يتكرّر بالعشرات
    // (زر المفضلة بكل صف عشبة، زرّا التعديل/الحذف بكل صف بلوحة الإدارة...)،
    // فالقيمة الافتراضية هنا false بعكس بقية أزرار LiquidGlassSurface الفريدة
    // (GlassButton الأساسي، الشريط العلوي...) — نفس الشكل الزجاجي تماماً
    // لكن بلا طبقة تمويه GPU مكرَّرة لكل نسخة أثناء التمرير.
    blurBubbles: Boolean = false,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.scale(pressScale).size(size),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    ) {
        LiquidGlassSurface(
            shape = CircleShape,
            modifier = Modifier.fillMaxSize(),
            tint = containerColor,
            borderAlpha = 0.14f,
            blurBubbles = blurBubbles
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides (if (enabled) contentColor else contentColor.copy(alpha = 0.38f))) {
                    content()
                }
            }
        }
    }
}

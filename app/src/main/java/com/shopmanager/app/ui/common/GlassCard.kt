package com.shopmanager.app.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.glassHairlineColor

/**
 * Card panel used for list rows/forms (DebtRow, LinkedNoteRow, AddDebtCard,
 * PersonDetailScreen's summary/info cards, ...).
 *
 * BUG FIXED / RE-UNIFIED ("بادق التفاصيل في ملفات ما تعدلت مع تصميم
 * الواجهة"): this file predates the later app-wide move to Claude's actual
 * flat design language and never got updated when the rest of the app did.
 * It still forced a heavy 8.dp drop shadow plus a top-to-bottom two-tone
 * gradient fill — exactly the "distinct floating card" look Claude.ai's own
 * cards don't have. Every other panel in the app (see
 * [Modifier.liquidGlassSurface]'s own doc comment, and the flat
 * `surfaceContainerHigh` fill [claudeLightScheme]/[claudeDarkScheme]
 * describe as the reference's literal, non-gradient `glass_fill_strong`)
 * already reads as "separated by tone, not by shadow" — a single flat fill
 * plus a hairline border, at most 2.dp of lift. GlassCard now matches
 * that: flat single-color fill, and a default elevation that falls back to
 * [liquidGlassSurface]'s own Claude-style 2.dp instead of overriding it.
 *
 * [containerColor] lets a caller reuse this same flat-card look for a
 * differently-toned panel (e.g. an error/warning banner using
 * `colorScheme.errorContainer`) instead of every non-neutral card in the
 * app reaching for a raw `androidx.compose.material3.ElevatedCard` with
 * its own default Material tonal-elevation shadow — see the settings
 * screen's "server unreachable" / "notifications blocked" banners, which
 * used to do exactly that.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 2.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .liquidGlassSurface(
                shape = shape,
                baseBrush = SolidColor(containerColor),
                elevation = elevation,
                highlight = false
            )
            // BORDERS UNIFIED WHITE ("بدي حدود... بكل التطبيق يكون لونها
            // ابيض... بشفافية خفيفة"): every card/box border app-wide
            // switched from the theme's (warm-tan in light mode,
            // near-black in dark mode) outlineVariant token to a flat
            // white hairline at a light, consistent alpha — see the same
            // change across MaterialRow/PersonRow/NoteRow/catalog rows/
            // dialogs/segmented tabs.
            //
            // LIGHT-MODE CONTRAST FIX ("اصلح تباين الوضع النهاري"): that
            // white hairline is invisible in light mode, where this card's
            // own `surfaceContainerHigh` fill is already white — every
            // card in the app lost its edge entirely. [glassHairlineColor]
            // keeps dark mode exactly as it was and swaps to a scaled-down
            // dark hairline only in light mode.
            .border(1.dp, glassHairlineColor(0.5f), shape)
    ) {
        content()
    }
}

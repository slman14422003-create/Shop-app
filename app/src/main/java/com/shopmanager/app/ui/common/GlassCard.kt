package com.shopmanager.app.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalGlassMode

/**
 * "الكلاس الزجاجي" (Glassmorphism card) — the flat, hairline-bordered
 * `Surface` pattern used for list rows/forms (DebtRow, LinkedNoteRow,
 * AddDebtCard, ...) wrapped as one reusable glass panel instead, built
 * entirely on top of [liquidGlassSurface] — the same frosted-glass engine
 * already behind every header/top-bar/dialog in the app — so a bar, a
 * dialog, and now a card all read as the exact same physical material,
 * with a single definition to tune instead of three separate ones.
 *
 * Maps every property of the requested CSS Glassmorphism spec onto its
 * Compose equivalent:
 *   - translucent fill        -> `baseAlpha`, tinted by the current
 *                                 surface tone rather than the loud brand
 *                                 gradient a header/top-bar paints with
 *   - `backdrop-filter: blur` -> [liquidGlassSurface]'s own real
 *                                 Gaussian-blurred highlight/droplet layer
 *   - 1px translucent border  -> the same soft top-bright/bottom-fade
 *                                 gradient rim [liquidGlassSurface] draws
 *   - soft floating shadow    -> `elevation`
 *   - 16-28px corner radius   -> `shape` (defaults to a 20dp "pebble",
 *                                 same family as [GlassAlertDialog]'s
 *                                 28dp sheet radius)
 *
 * This only actually LOOKS like translucent glass when Settings -> المظهر
 * has "الزجاج" (GLASS) selected ([LocalGlassMode]) — every other color
 * mode (MANUAL/CLASSIC/DYNAMIC, the app's default) falls back to the
 * exact same flat tone + hairline-border look every other list in the
 * app already uses (Dashboard/Settings/DebtsScreen/NotesScreen/
 * MaterialsScreen — see the "iOS 26 REDESIGN" notes on those Surfaces),
 * so dropping this into one screen never looks broken or out of place
 * for anyone who isn't using GLASS mode.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    val glassModeActive = LocalGlassMode.current
    val toneColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier
            .liquidGlassSurface(
                shape = shape,
                // A flat "gradient" (both stops the same tone) instead of
                // the brand hue every header paints with - a whole list of
                // rows glowing in the app's accent color would read as
                // loud/rainbow rather than as frosted glass. GLASS mode's
                // own translucency + blurred highlight/droplets are what
                // carry the "glass" read; the fill underneath just needs
                // to be a neutral surface tone.
                baseBrush = Brush.linearGradient(listOf(toneColor, toneColor)),
                baseAlpha = 0.55f,
                elevation = if (glassModeActive) elevation else 0.dp
            )
            .let {
                // liquidGlassSurface already skips its own rim border
                // outside GLASS mode (default rimColor = White reads as
                // "no border" there - see its own doc), so the same plain
                // hairline every flat card elsewhere uses stands in for it
                // instead - nothing here loses its border on switching
                // color modes.
                if (glassModeActive) it
                else it.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            }
    ) {
        content()
    }
}

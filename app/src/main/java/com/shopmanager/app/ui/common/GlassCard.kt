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
 *   - `backdrop-filter: blur` -> Compose has no real backdrop-sampling API
 *                                 (no way to read what's actually behind
 *                                 this exact spot on screen), so this is a
 *                                 translucency-only stand-in, not a literal
 *                                 blur of whatever scrolls underneath. The
 *                                 one genuinely blurred surface in the app
 *                                 is [GlassAlertDialog], which can afford a
 *                                 real screenshot-and-blur trick because a
 *                                 dialog briefly owns the whole screen -
 *                                 not something a normal scrolling list row
 *                                 can do. [liquidGlassSurface]'s own
 *                                 highlight/droplet layer is deliberately
 *                                 left OFF here (`highlight = false`,
 *                                 matching every other glass surface in the
 *                                 app) - it draws as scattered, independently
 *                                 blurred light patches rather than one even
 *                                 tint, which is exactly the "طبقة وحدة"
 *                                 (one uniform layer) complaint this fixed.
 *   - 1px translucent border  -> the same gradient rim [liquidGlassSurface]
 *                                 draws for every other panel in the app
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
                // own translucency is what carries the "glass" read; the
                // fill underneath just needs to be a neutral surface tone.
                baseBrush = Brush.linearGradient(listOf(toneColor, toneColor)),
                baseAlpha = 0.55f,
                elevation = if (glassModeActive) elevation else 0.dp,
                // BUG FIXED (screenshot: uneven "fog" patches instead of
                // one uniform tinted layer): every other glass surface in
                // the app already renders with `highlight = false` — this
                // card is what's left as one flat, evenly-lit sheet of
                // glass instead of the blotchy blurred light patches
                // [liquidGlassSurface]'s `highlight = true` path draws.
                highlight = false
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

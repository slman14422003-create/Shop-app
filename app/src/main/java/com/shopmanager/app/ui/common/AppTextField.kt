package com.shopmanager.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * BUG FIXED ("حتى المربع بالصورة تحسه اندرويد خام"): every form field in the
 * app used [androidx.compose.material3.OutlinedTextField] — a stroked
 * rectangle whose label breaks the border line when focused. That specific
 * combination (visible outline + label overlapping the edge) is a distinctly
 * Material/Android pattern with no iOS equivalent, and it's what read as
 * "raw Android" in a dialog whose rounded shape/dark palette otherwise
 * already matched the rest of the app.
 *
 * iOS form fields (Settings.app, any sheet with text input) never draw a
 * border: a flat, softly-filled rounded rectangle holding the value, with a
 * small static caption sitting above it instead of a floating label. This
 * reproduces that — caption [Text] above a borderless filled [TextField],
 * both riding the same rounded-corner language (14.dp) as the app's cards —
 * as a drop-in replacement for OutlinedTextField wherever a caller wants the
 * iOS look. `label` is a plain string (not a slot) on purpose: the whole
 * point is that it's a static caption, never a floating/animating label.
 *
 * BUG FIXED ("لعنت أم الشاشة"): the field's fill first shipped as
 * `surfaceContainerHigh` — which is *also* Material3's own default
 * [androidx.compose.material3.AlertDialog] background color. Every caller
 * of this field so far is inside a plain AlertDialog, so the field was
 * exactly the same color as the dialog behind it: no border and no visible
 * fill meant the field was completely invisible, reading as a big dead gap
 * of empty space with no visible box to tap into — worse than the
 * OutlinedTextField it replaced. `surfaceContainerHighest` is the next step
 * up Material3's own tonal-elevation ladder, so it's guaranteed lighter
 * than whatever `surfaceContainerHigh`-or-lower surface this field sits on
 * (a plain AlertDialog today, any other card/sheet later) while staying in
 * the same neutral family — visible without turning into a harsh outline.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // Lets callers wire up an IME "تم/Done" action (e.g. submit a dialog
    // on Enter) — several form dialogs need this and previously had to
    // fall back to a raw TextField/OutlinedTextField just to get it,
    // which is exactly the inconsistency this component exists to avoid.
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    // Multi-line support (e.g. a notes field) — ignored while singleLine
    // is true, same contract as the underlying TextField.
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    // A small leading icon (e.g. a calendar glyph on a date field) drawn
    // inside the field, same slot Material's fields expose — kept as a
    // plain ImageVector rather than a full @Composable slot since every
    // real usage in this app is just "one small icon, tinted to match".
    leadingIcon: ImageVector? = null,
    // Tints the fill toward colorScheme.error (e.g. a wrong PIN) instead
    // of forcing every caller that needs error state back onto a raw
    // TextField/OutlinedTextField just to get it.
    isError: Boolean = false,
    // Hides the caption above the field for the rare case where a caller
    // already provides context another way (e.g. a section title right
    // above it) and a floating placeholder alone is enough — the caption
    // stays on by default since a static label above the field, rather
    // than a Material floating one, is this component's whole point.
    showLabel: Boolean = true
) {
    Column(modifier) {
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = placeholder?.let {
                { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
            },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            isError = isError,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                errorContainerColor = MaterialTheme.colorScheme.errorContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

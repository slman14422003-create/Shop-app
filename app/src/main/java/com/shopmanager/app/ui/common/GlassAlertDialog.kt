package com.shopmanager.app.ui.common

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Replacement for [androidx.compose.material3.AlertDialog], kept with the
 * same call signature as the most commonly used AlertDialog parameters so
 * existing call sites only needed their function name changed. Previously
 * rendered as a translucent, screenshot-blurred "frosted glass" panel;
 * that whole effect has been removed, so this is now a plain, flat, fully
 * opaque dialog panel — same 28.dp rounded corners and springy pop-in
 * entrance, same full-width divided button row, no transparency and no
 * blur.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        // Springy pop-in instead of Dialog's default hard cut.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.86f,
            animationSpec = MotionSpecs.popInSpring(),
            label = "glassDialogScale"
        )
        val contentAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(180),
            label = "glassDialogAlpha"
        )

        val resolvedContainer = if (containerColor.isSpecified()) containerColor
        else MaterialTheme.colorScheme.surfaceContainerHigh
        val resolvedTitleColor = if (titleContentColor.isSpecified()) titleContentColor
        else MaterialTheme.colorScheme.onSurface
        val resolvedTextColor = if (textContentColor.isSpecified()) textContentColor
        else MaterialTheme.colorScheme.onSurfaceVariant

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(contentAlpha)
                    .widthIn(min = 260.dp, max = 340.dp)
                    .liquidGlassSurface(
                        shape = shape,
                        baseBrush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(resolvedContainer, resolvedContainer)
                        ),
                        elevation = 24.dp,
                        highlight = false,
                        rimColor = MaterialTheme.colorScheme.outlineVariant
                    )
            ) {
                Column {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                        icon?.let {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                            }
                        }
                        title?.let {
                            Box(
                                Modifier.fillMaxWidth().padding(top = if (icon != null) 10.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides resolvedTitleColor) { it() }
                                }
                            }
                        }
                        text?.let {
                            Box(Modifier.fillMaxWidth().padding(top = if (title != null) 10.dp else 0.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                    CompositionLocalProvider(LocalContentColor provides resolvedTextColor) { it() }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), thickness = 1.dp)
                    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dismissButton != null) {
                            DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                                dismissButton()
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), thickness = 1.dp)
                        }
                        DialogButtonCell(Modifier.weight(1f).fillMaxHeight()) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/**
 * Tapping anywhere in a button's half of the row must trigger the action,
 * not just the word: a plain `Box(contentAlignment = Center)` gives its
 * child loose constraints, so a TextButton only wraps its own short label
 * and centers at its own small size, leaving the rest of the cell dead
 * space. This measures the child with tight constraints fixed to the
 * cell's full size instead, so the TextButton's own Surface (which
 * actually carries the ripple/click handling) fills the entire cell and
 * only then centers its short text label inside itself.
 */
@Composable
private fun DialogButtonCell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val forced = Constraints.fixed(constraints.maxWidth, constraints.maxHeight)
        val placeable = measurables.first().measure(forced)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(0, 0)
        }
    }
}

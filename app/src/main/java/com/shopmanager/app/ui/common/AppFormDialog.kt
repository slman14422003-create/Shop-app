package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import com.shopmanager.app.data.performance.MotionSpecs

@Composable
fun AppFormDialog(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "حفظ",
    dismissLabel: String = "إلغاء",
    isSaving: Boolean = false,
    emphasisColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val dialogView = LocalView.current
        val blurOn = blurEnabled()
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null && blurOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
            }
        }
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.9f,
            animationSpec = MotionSpecs.popInSpring(),
            label = "formDialogScale"
        )
        val contentAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = MotionSpecs.contentTween(),
            label = "formDialogAlpha"
        )

        val cs = MaterialTheme.colorScheme
        val accent = appAccent()
        val emphasis = if (emphasisColor == Color.Unspecified) accent else emphasisColor
        val tileColor by animateColorAsState(targetValue = emphasis, label = "formDialogTile")

        Box(Modifier.fillMaxSize()) {
            if (blurOn) {
                BlurBackdrop(
                    Modifier.fillMaxSize(),
                    tint = if (cs.background.luminance() < 0.5f) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.34f)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .alpha(contentAlpha)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .widthIn(max = 460.dp)
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(30.dp),
                            baseBrush = SolidColor(cs.surfaceContainerHigh),
                            elevation = 0.dp
                        )
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(tileColor.copy(alpha = 0.16f))
                                    .border(1.dp, tileColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(headerIcon, contentDescription = null, tint = tileColor, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                    maxLines = 2
                                )
                                if (subtitle != null) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        content()
                        Spacer(Modifier.height(20.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable(enabled = !isSaving, role = Role.Button, onClick = onConfirm),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSaving) {
                                AppSpinner(size = 20.dp, color = Color.White)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        confirmLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(cs.surfaceContainerHighest)
                                .clickable(enabled = !isSaving, role = Role.Button, onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dismissLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

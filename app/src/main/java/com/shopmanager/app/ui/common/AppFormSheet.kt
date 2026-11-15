package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFormSheet(
    title: String,
    subtitle: String? = null,
    headerIcon: ImageVector,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "حفظ",
    isSaving: Boolean = false,
    emphasisColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val savingNow = rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> !savingNow.value || target != SheetValue.Hidden }
    )
    val cs = MaterialTheme.colorScheme
    val accent = appAccent()
    val emphasis = if (emphasisColor == Color.Unspecified) accent else emphasisColor
    val tileColor by animateColorAsState(targetValue = emphasis, label = "formSheetTile")

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = cs.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = AppCardLargeRadius, topEnd = AppCardLargeRadius)
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = AppScreenPadding, end = AppScreenPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(tileColor.copy(alpha = 0.16f))
                        .border(1.dp, tileColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(headerIcon, contentDescription = null, tint = tileColor, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 2
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }

            content()

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
        }
    }
}

@Composable
fun PrioritySelector(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val labels = listOf("عادية", "هامة", "هامة جداً")
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = level == index
            val tone = if (index == 0) cs.onSurface else priorityColor(index)
            val bg by animateColorAsState(
                targetValue = when {
                    !active -> Color.Transparent
                    index == 0 -> cs.surfaceContainerHighest
                    else -> tone
                },
                label = "prioritySegmentBg"
            )
            val fg by animateColorAsState(
                targetValue = when {
                    !active -> cs.onSurfaceVariant
                    index == 0 -> cs.onSurface
                    index == 1 -> Color(0xFF2B2100)
                    else -> Color.White
                },
                label = "prioritySegmentFg"
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onLevelChange(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index > 0) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (active) fg else tone,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

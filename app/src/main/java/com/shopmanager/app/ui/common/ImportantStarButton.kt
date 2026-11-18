package com.shopmanager.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.shopmanager.app.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.LocalIsDarkTheme

val ImportantRed: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFF5A5F) else Color(0xFFD7263D)

val ImportantYellow: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFC53D) else Color(0xFFE0A100)

@Composable
fun priorityColor(level: Int): Color = when (level) {
    2 -> ImportantRed
    1 -> ImportantYellow
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun ImportantStarButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    tone: Color = ImportantYellow
) {
    val cs = MaterialTheme.colorScheme
    val red = tone
    val bg by animateColorAsState(
        targetValue = if (checked) red.copy(alpha = 0.18f) else cs.surfaceContainerHighest,
        label = "starBg"
    )
    val tint by animateColorAsState(
        targetValue = if (checked) red else cs.onSurfaceVariant,
        label = "starTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "starScale"
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (checked) "مادة هامة" else "وضع كمادة هامة",
            tint = tint,
            modifier = Modifier.size(size * 0.5f).scale(scale)
        )
    }
}

@Composable
fun ImportantToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: Int = 2
) {
    val cs = MaterialTheme.colorScheme
    val red = priorityColor(level)
    val container by animateColorAsState(
        targetValue = if (checked) red.copy(alpha = 0.12f) else cs.surfaceContainerHighest,
        label = "importantCardBg"
    )
    val border by animateColorAsState(
        targetValue = if (checked) red.copy(alpha = 0.75f) else Color.Transparent,
        label = "importantCardBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (checked) red else cs.onSurface,
        label = "importantCardTitle"
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "importantCardStar"
    )
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppGroupLargeRadius),
        color = container,
        border = BorderStroke(1.5.dp, border)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (checked) red else cs.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (checked) Color.White else cs.onSurfaceVariant,
                    modifier = Modifier.size(26.dp).scale(scale)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (level == 2) "مادة هامة جداً" else "مادة هامة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
                Text(
                    if (level == 2) {
                        if (checked) "نجمة حمراء — أهم من الهامة وتظهر أولاً" else "نجمة حمراء — أهم من المادة الهامة"
                    } else {
                        if (checked) "نجمة صفراء — تظهر في تبويب الهامة" else "نجمة صفراء — مادة مهمة"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

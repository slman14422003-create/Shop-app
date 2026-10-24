package com.salman.herbalencyclopedia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.ui.theme.rememberPressScale
import com.salman.herbalencyclopedia.ui.util.tr

/** بطاقة خلطة، بنفس طراز الزجاج السائل المستخدم في [HerbCard]. */
@Composable
fun BlendCard(
    blend: Blend,
    ingredientCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource, pressedScale = 0.97f)
    val shape = RoundedCornerShape(20.dp)

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(pressScale)
            .fillMaxWidth()
            .shadow(1.dp, shape),
        shape = shape,
        color = Color.Transparent
    ) {
        LiquidGlassSurface(
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
            tint = MaterialTheme.colorScheme.surfaceContainer,
            // نفس إصلاح HerbCard: بطاقة صف قصيرة، انظر توثيق [compact] في
            // LiquidGlassSurface.
            compact = true,
            // نفس إصلاح HerbCard أيضاً لتقطّع التمرير في وضع "أداء عالٍ" —
            // انظر توثيق [blurBubbles] في LiquidGlassSurface.
            blurBubbles = false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlendThumbnail(imageUrl = blend.imageUrl)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = blend.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$ingredientCount ${tr("مكوّنات من الأعشاب")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BlendThumbnail(imageUrl: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val shape = CircleShape
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Blender,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
    }
}

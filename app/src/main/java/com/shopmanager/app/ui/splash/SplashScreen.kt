package com.shopmanager.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopmanager.app.R
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.ui.common.LogoMotion
import com.shopmanager.app.ui.theme.LocalBrandGradientColors

/** Total splash animation length (ms) before the 60Hz scale. */
private const val SPLASH_ANIM_MS = 900

@Composable
fun AppSplashScreen(modifier: Modifier = Modifier, motion: LogoMotion = LogoMotion.ONCE) {
    val progress = remember { Animatable(0f) }
    val reduce = MotionSpecs.reduceMotion()
    val scale = MotionSpecs.durationScale()
    LaunchedEffect(reduce, motion) {
        if (reduce || motion == LogoMotion.NONE) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = (SPLASH_ANIM_MS * scale).toInt(),
                    easing = MotionSpecs.emphasizedEasing
                )
            )
        }
    }
    fun slice(from: Float, to: Float): Float = ((progress.value - from) / (to - from)).coerceIn(0f, 1f)

    val cs = MaterialTheme.colorScheme
    val brand = LocalBrandGradientColors.current
    val brandA = brand.first()
    val brandB = brand.last()

    val pop = remember { Animatable(if (motion == LogoMotion.NONE) 1f else 0.72f) }
    val popSpec = MotionSpecs.popInSpring()
    LaunchedEffect(motion, reduce) {
        if (motion == LogoMotion.NONE || reduce) pop.snapTo(1f)
        else pop.animateTo(1f, popSpec)
    }
    val ring = if (motion == LogoMotion.LOOP) {
        rememberInfiniteTransition(label = "splashRing").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(MotionSpecs.floatMs, easing = LinearEasing)),
            label = "ring"
        )
    } else null

    Box(
        modifier
            .fillMaxSize()
            .background(cs.surface)
            // Soft brand glow rising from the top and bottom corners; static, so no per-frame cost.
            .drawBehind {
                val glow = slice(0f, 0.6f)
                drawRect(
                    Brush.radialGradient(
                        listOf(brandA.copy(alpha = 0.20f * glow), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.36f),
                        radius = size.minDimension * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(brandB.copy(alpha = 0.10f * glow), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 1.02f),
                        radius = size.minDimension * 0.8f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(168.dp)
                    .drawBehind {
                        ring?.let { r ->
                            val t = r.value
                            drawCircle(
                                brandA.copy(alpha = 0.30f * (1f - t)),
                                radius = size.minDimension / 2f * (1f + 0.5f * t)
                            )
                        }
                    }
                    .graphicsLayer {
                        val p = slice(0f, 0.6f)
                        val s = (0.88f + 0.12f * p) * pop.value
                        scaleX = s
                        scaleY = s
                        alpha = p
                    },
                contentAlignment = Alignment.Center
            ) {
                // Outer halo ring.
                Box(
                    Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(brandA.copy(alpha = 0.22f), brandB.copy(alpha = 0.06f))
                            )
                        )
                        .border(1.dp, brandA.copy(alpha = 0.25f), CircleShape)
                )
                // Icon tile.
                Box(
                    Modifier
                        .size(120.dp)
                        .shadow(18.dp, RoundedCornerShape(34.dp), ambientColor = brandA, spotColor = brandA)
                        .clip(RoundedCornerShape(34.dp))
                        .background(cs.surfaceContainerHigh)
                        .border(1.dp, cs.onSurface.copy(alpha = 0.08f), RoundedCornerShape(34.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_splash_mark),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "إدارة المحل",
                color = cs.onSurface,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = slice(0.25f, 0.8f)
                    alpha = p
                    translationY = (1f - p) * 16.dp.toPx()
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "الديون  •  المواد  •  الأسعار",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = slice(0.4f, 0.95f)
                    alpha = p
                    translationY = (1f - p) * 10.dp.toPx()
                }
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer { alpha = slice(0.45f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thin progress track that fills with the intro animation.
            Box(
                Modifier
                    .width(96.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = progress.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                        }
                        .background(Brush.horizontalGradient(listOf(brandB, brandA)))
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "SEMO STUDIO",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

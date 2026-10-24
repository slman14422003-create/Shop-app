package com.salman.herbalencyclopedia.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * معاينة صورة العشبة بحجم كامل الشاشة عند الضغط عليها — الصورة تظهر
 * داخل إطار دائري بحدود متدرّجة اللون، وخلفها **شاشة العشبة الحقيقية**
 * (الاسم، الفوائد، إلخ) تبقى ظاهرة وواضحة بدون أي تمويه أو تعتيم يذكر —
 * فقط تعتيم بسيط جداً يعطي تركيز على الصورة بدون ما يخفي التفاصيل
 * خلفها. يتم هذا بإلغاء التعتيم الافتراضي لنافذة الـ Dialog نفسها
 * (setDimAmount) بدل رسم خلفية صناعية فوق الشاشة.
 *
 * تُستخدم من [HerbThumbnail] عبر [previewOnClick] وأيضاً من معاينة
 * الصورة في شاشة إضافة/تعديل العشبة الخاصة بالمطوّر
 * (AdminEditHerbScreen)، لتفادي تكرار نفس منطق الحوار في مكانين.
 */
@Composable
fun ImagePreviewDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // إلغاء التعتيم الافتراضي لنافذة الـ Dialog نفسها، عشان الشاشة
        // الحقيقية خلف المعاينة (اسم العشبة، الفوائد...) تبقى ظاهرة
        // وواضحة، ونتحكم إحنا فقط بمقدار تعتيم بسيط اختياري بالأسفل.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
        }

        var visible by remember { mutableStateOf(false) }
        var dismissing by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { visible = true }

        val entranceProgress by animateFloatAsState(
            targetValue = if (visible && !dismissing) 1f else 0f,
            // كانت مدة/منحنى مستقلّين (300ms، EaseOutCubic) بمعزل عن نظام
            // الحركة الموحّد للتطبيق (AppMotion في Animations.kt) الذي تعتمد
            // عليه كل بقية الشاشات — استخدام نفس المنحنى/المدة القياسية هنا
            // يجعل هذا الانتقال (فتح معاينة صورة) يشعر بنفس "لمسة" أي حركة
            // أخرى بالتطبيق بدل أن يبدو مختلفاً قليلاً في السرعة والإحساس.
            animationSpec = com.salman.herbalencyclopedia.ui.theme.AppMotion.smooth(
                com.salman.herbalencyclopedia.ui.theme.AppMotion.Quick
            ),
            label = "imagePreviewEntrance",
            finishedListener = { value -> if (value == 0f && dismissing) onDismiss() }
        )

        fun animateDismiss() {
            if (!dismissing) {
                dismissing = true
                visible = false
            }
        }

        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val maxOffsetBase = with(density) { (configuration.screenWidthDp.dp / 2).toPx() }

        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary

        Box(
            modifier = Modifier
                .fillMaxSize()
                // تعتيم خفيف جداً فقط، لتمييز طبقة المعاينة — الشاشة
                // اللي وراها تبقى مقروءة بوضوح من خلاله.
                .background(Color.Black.copy(alpha = 0.22f * entranceProgress))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        val maxOffset = maxOffsetBase * (newScale - 1f)
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                        if (newScale <= 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1f) animateDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // الإطار الدائري: حلقة تدرّج بلون الثيم، وداخلها الصورة
            // الحادة مقصوصة دائرياً.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .graphicsLayer(
                        scaleX = scale * (0.85f + 0.15f * entranceProgress),
                        scaleY = scale * (0.85f + 0.15f * entranceProgress),
                        translationX = offsetX,
                        translationY = offsetY,
                        alpha = entranceProgress
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                        radius = size.minDimension / 2f - strokeWidth / 2f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                    )
                }

                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { imageState = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(CircleShape)
                )

                if (imageState is AsyncImagePainter.State.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = primary
                    )
                }
            }

            IconButton(
                onClick = { animateDismiss() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(44.dp)
                    .graphicsLayer(alpha = entranceProgress)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.85f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق المعاينة", tint = Color.White)
            }
        }
    }
}

package com.shopmanager.app.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shopmanager.app.data.chat.ChatImageCodec
import com.shopmanager.app.data.chat.ChatMessage
import com.shopmanager.app.data.chat.ChatSearch
import com.shopmanager.app.data.chat.ConversionPlan
import com.shopmanager.app.data.chat.ConvertKind
import com.shopmanager.app.data.chat.ImageSizing
import com.shopmanager.app.data.chat.LinkPreviewRepository
import com.shopmanager.app.data.chat.LinkPreviewResult
import com.shopmanager.app.data.chat.MessageType
import com.shopmanager.app.data.chat.PinnedRef
import com.shopmanager.app.ui.common.GlassAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun highlightedText(text: String, query: String, color: Color, strong: Boolean): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val ranges = ChatSearch.ranges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var pos = 0
        for (r in ranges) {
            if (r.first > pos) append(text.substring(pos, r.first))
            withStyle(
                SpanStyle(
                    background = color.copy(alpha = if (strong) 0.65f else 0.35f),
                    fontWeight = FontWeight.Bold
                )
            ) { append(text.substring(r.first, r.last + 1)) }
            pos = r.last + 1
        }
        if (pos < text.length) append(text.substring(pos))
    }
}

@Composable
fun ChatSearchHeader(
    query: String,
    total: Int,
    index: Int,
    onQuery: (String) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(66.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "إغلاق البحث", tint = cs.onSurface) }
        Box(
            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                cursorBrush = SolidColor(cs.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("ابحث في المحادثة...", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        inner()
                    }
                }
            )
        }
        Text(
            when {
                query.isBlank() -> ""
                total == 0 -> "لا نتائج"
                else -> "${index + 1} من $total"
            },
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = total > 0, role = Role.Button, onClick = onOlder),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.KeyboardArrowUp, "النتيجة الأقدم", tint = if (total > 0) cs.onSurface else cs.onSurfaceVariant) }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = total > 0, role = Role.Button, onClick = onNewer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.KeyboardArrowDown, "النتيجة الأحدث", tint = if (total > 0) cs.onSurface else cs.onSurfaceVariant) }
    }
}

@Composable
fun PinnedBar(pinned: PinnedRef, name: String, onClick: () -> Unit, onUnpin: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PushPin, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("رسالة مثبّتة · $name", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface, maxLines = 1)
            Text(
                when (pinned.type) {
                    MessageType.VOICE -> "رسالة صوتية"
                    MessageType.IMAGE -> "📷 " + pinned.text.ifBlank { "صورة" }
                    MessageType.TEXT -> pinned.text
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onUnpin),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Close, "إلغاء التثبيت", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun QuickReplyRow(replies: List<String>, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(replies, key = { it }) { r ->
            Text(
                r,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh)
                    .clickable(role = Role.Button) { onPick(r) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ChatImageContent(m: ChatMessage, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (w, h) = ImageSizing.displaySize(m.imageW, m.imageH, 240, 320)
    val bmp by produceState<Bitmap?>(ChatImageCodec.cached("${m.id}@480"), m.id, m.imageB64) {
        if (value == null) value = withContext(Dispatchers.Default) { ChatImageCodec.decode(m.id, m.imageB64, 480) }
    }
    Box(
        Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), "صورة", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun ChatImageViewer(m: ChatMessage, onDismiss: () -> Unit) {
    val bmp by produceState<Bitmap?>(ChatImageCodec.cached("${m.id}@1024"), m.id) {
        if (value == null) value = withContext(Dispatchers.Default) { ChatImageCodec.decode(m.id, m.imageB64, 1024) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) {
                Image(
                    b.asImageBitmap(), "صورة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1f) { ox = 0f; oy = 0f } else { ox += pan.x; oy += pan.y }
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy }
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            Box(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).size(44.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)).clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
            if (m.text.isNotBlank()) {
                Text(
                    m.text, color = Color.White, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun LinkPreviewCard(url: String, fg: Color) {
    val result by produceState<LinkPreviewResult?>(LinkPreviewRepository.cached(url), url) {
        if (value == null) value = LinkPreviewRepository.load(url)
    }
    val r = result
    if (r == null) return
    val meta = r.meta ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        r.thumb?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(meta.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta.host, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

@Composable
fun ConvertKindDialog(onPick: (ConvertKind) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحويل الرسالة إلى") },
        text = {
            Column {
                TextButton(shape = RectangleShape, onClick = { onPick(ConvertKind.SHORTAGE) }) { Text("إضافة للنواقص") }
                TextButton(shape = RectangleShape, onClick = { onPick(ConvertKind.NOTE) }) { Text("ملاحظة هامة") }
                TextButton(shape = RectangleShape, onClick = { onPick(ConvertKind.DEBT) }) { Text("دين لعميل") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun ConvertDialog(
    state: ConvertUi,
    onOption: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val plan = state.plan
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state.kind) {
                    ConvertKind.SHORTAGE -> "إضافة للنواقص"
                    ConvertKind.NOTE -> "ملاحظة هامة"
                    ConvertKind.DEBT -> "تسجيل دين"
                }
            )
        },
        text = {
            when {
                plan == null || (state.busy && plan !is ConversionPlan.Ready) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("جاري التحليل...")
                }
                plan is ConversionPlan.Ready -> Text(plan.summary)
                plan is ConversionPlan.Missing -> Text(plan.message)
                plan is ConversionPlan.Suggest -> Column {
                    Text(plan.message)
                    plan.options.forEach { o ->
                        TextButton(shape = RectangleShape, onClick = { onOption(o.text) }) { Text(o.label) }
                    }
                }
                else -> Unit
            }
        },
        confirmButton = {
            if (plan is ConversionPlan.Ready) {
                TextButton(shape = RectangleShape, enabled = !state.busy, onClick = onConfirm) { Text("تأكيد") }
            }
        },
        dismissButton = { TextButton(shape = RectangleShape, onClick = onDismiss) { Text(if (plan is ConversionPlan.Ready) "إلغاء" else "إغلاق") } }
    )
}

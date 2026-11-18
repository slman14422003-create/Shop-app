package com.shopmanager.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.shopmanager.app.data.chat.ConvertKind
import com.shopmanager.app.data.chat.LinkExtractor
import com.shopmanager.app.data.chat.MessageConverter
import com.shopmanager.app.data.chat.PlaybackSpeeds
import com.shopmanager.app.data.notifications.NotificationHelper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.shopmanager.app.ui.common.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.chat.ChatOpenTracker
import com.shopmanager.app.data.notifications.NotifKind
import com.shopmanager.app.data.notifications.ChatSounds
import com.shopmanager.app.data.notifications.NotificationSounds
import com.shopmanager.app.data.chat.ChatMessage
import com.shopmanager.app.data.chat.MessageType
import com.shopmanager.app.data.chat.ReplyRef
import com.shopmanager.app.data.chat.VoicePlayState
import com.shopmanager.app.data.chat.VoicePlayer
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.BlurContextMenu
import com.shopmanager.app.ui.common.BlurMenuAction
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VoiceContent(
    m: ChatMessage,
    fg: Color,
    bg: Color,
    play: VoicePlayState,
    onToggle: () -> Unit,
    onSpeed: () -> Unit,
    onLong: () -> Unit
) {
    if (m.isExpiredVoice || m.audioB64.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.VoiceOverOff, null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Text(
                if (m.isExpiredVoice) "انتهت صلاحية الرسالة الصوتية" else "جارٍ تحميل الصوت...",
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.8f)
            )
        }
        return
    }
    val isCurrent = play.id == m.id
    val playing = isCurrent && play.playing
    val progress = if (isCurrent && play.durationMs > 0) (play.positionMs.toFloat() / play.durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (isCurrent && play.positionMs > 0) play.positionMs.toLong() else m.durationMs

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(fg)
                .combinedClickable(role = Role.Button, onClick = onToggle, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل",
                tint = bg,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Waveform(m.wave, progress, fg, Modifier.width(150.dp).height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(shownMs), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                    Text(
                        PlaybackSpeeds.label(play.speed),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.16f))
                            .clickable(onClick = onSpeed)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                
            }
        }
    }
}

@Composable
internal fun Waveform(wave: List<Int>, progress: Float, color: Color, modifier: Modifier) {
    val bars = if (wave.isEmpty()) List(40) { 10 + (it * 7) % 30 } else wave
    Canvas(modifier) {
        val n = bars.size
        val gap = 2.dp.toPx()
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val filled = progress * n
        bars.forEachIndexed { i, v ->
            val h = size.height * (v.coerceIn(8, 100) / 100f)
            val x = if (rtl) size.width - (i + 1) * bw - i * gap else i * (bw + gap)
            drawRoundRect(
                color = if (i < filled) color else color.copy(alpha = 0.38f),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

@Composable
internal fun LiveWave(levels: List<Int>, paused: Boolean, color: Color, modifier: Modifier) {
    val maxBars = 48
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val bw = ((size.width - gap * (maxBars - 1)) / maxBars).coerceAtLeast(1f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val offsetSlots = maxBars - levels.size
        levels.forEachIndexed { i, v ->
            val slot = offsetSlots + i
            val h = size.height * (v.coerceIn(8, 100) / 100f)
            val x = if (rtl) size.width - (slot + 1) * bw - slot * gap else slot * (bw + gap)
            drawRoundRect(
                color = color.copy(alpha = if (paused) 0.35f else 0.85f),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

@Composable
internal fun ComposerBanner(title: String, subtitle: String, icon: ImageVector, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.55f)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, "إغلاق", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    rec: RecordingUi,
    replyTo: ChatMessage?,
    replyName: String,
    editing: ChatMessage?,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onMicStart: () -> Boolean,
    onWillCancel: (Boolean) -> Unit,
    onLock: () -> Unit,
    onMicEnd: (send: Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onAttach: () -> Unit,
    sendingImage: Boolean,
    quickReplies: List<String>,
    onQuickReply: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!rec.active) {
            if (replyTo != null) {
                ComposerBanner(
                    title = "رد على $replyName",
                    subtitle = when (replyTo.type) {
                        MessageType.VOICE -> "رسالة صوتية"
                        MessageType.IMAGE -> "📷 " + replyTo.text.ifBlank { "صورة" }
                        MessageType.TEXT -> replyTo.text
                    },
                    icon = Icons.AutoMirrored.Filled.Reply,
                    onClose = onCancelReply
                )
            }
            if (editing != null) {
                ComposerBanner(
                    title = "تعديل الرسالة",
                    subtitle = editing.text,
                    icon = Icons.Default.Edit,
                    onClose = onCancelEdit
                )
            }
        }
        if (!rec.active && !rec.locked && text.isEmpty() && editing == null && quickReplies.isNotEmpty()) {
            QuickReplyRow(quickReplies, onQuickReply)
        }
        if (rec.locked) {
            LockedRecorder(rec, onCancel = { onMicEnd(false) }, onTogglePause = onTogglePause, onSend = { onMicEnd(true) })
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!rec.active && editing == null) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
                            .clickable(enabled = !sendingImage, role = Role.Button, onClick = onAttach),
                        contentAlignment = Alignment.Center
                    ) {
                        if (sendingImage) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onSurfaceVariant)
                        else Icon(Icons.Default.AddPhotoAlternate, "إرسال صورة", tint = cs.onSurface, modifier = Modifier.size(24.dp))
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainerHigh),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (rec.active) {
                        RecordingStrip(rec)
                    } else {
                        BasicTextField(
                            value = text,
                            onValueChange = { if (it.length <= 2000) onTextChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                            cursorBrush = SolidColor(cs.onSurface),
                            maxLines = 5,
                            decorationBox = { inner ->
                                Box(Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
                                    if (text.isEmpty()) {
                                        Text("اكتب رسالة...", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
                if (text.isBlank()) {
                    MicButton(rec.active, onMicStart, onWillCancel, onLock, onMicEnd)
                } else {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(cs.onSurface)
                            .clickable(role = Role.Button, onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (editing != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                            if (editing != null) "حفظ التعديل" else "إرسال",
                            tint = cs.surface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecDot(paused: Boolean) {
    val cs = MaterialTheme.colorScheme
    val animate = com.shopmanager.app.data.performance.rememberInfiniteAnimationsAllowed() && !paused
    val pulse: State<Float>? = if (animate) {
        val t = rememberInfiniteTransition(label = "recPulse")
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot")
    } else null
    Box(Modifier.size(10.dp).graphicsLayer { alpha = if (paused) 0.5f else (pulse?.value ?: 1f) }.clip(CircleShape).background(cs.error))
}

@Composable
internal fun RecordingStrip(rec: RecordingUi) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecDot(paused = false)
        Spacer(Modifier.width(10.dp))
        Text(formatDuration(rec.elapsedMs), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
        Spacer(Modifier.weight(1f))
        if (rec.willCancel) {
            Text("أفلت للإلغاء", style = MaterialTheme.typography.bodyMedium, color = cs.error, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("اسحب للإلغاء", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LockedRecorder(
    rec: RecordingUi,
    onCancel: () -> Unit,
    onTogglePause: () -> Unit,
    onSend: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecDot(paused = rec.paused)
            Spacer(Modifier.width(10.dp))
            Text(formatDuration(rec.elapsedMs), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Spacer(Modifier.width(14.dp))
            LiveWave(rec.levels, rec.paused, cs.onSurface, Modifier.weight(1f).height(32.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, "إلغاء التسجيل", tint = cs.error, modifier = Modifier.size(26.dp))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClick = onTogglePause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (rec.paused) Icons.Default.Mic else Icons.Default.Pause,
                    if (rec.paused) "متابعة التسجيل" else "إيقاف مؤقت",
                    tint = cs.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(cs.onSurface).clickable(role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = cs.surface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun LockHint(fraction: Float, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHighest),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Icon(
            if (fraction > 0.6f) Icons.Default.Lock else Icons.Default.LockOpen,
            null,
            tint = cs.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Icon(
            Icons.Default.KeyboardArrowUp,
            null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(22.dp).alpha(1f - fraction)
        )
    }
}

/**
 * زر المايك بأسلوب واتساب: ضغط مطوّل = تسجيل والإفلات = إرسال، السحب نحو المنتصف ثم الإفلات = إلغاء،
 * والسحب لأعلى = قفل التسجيل (تظهر واجهة تسجيل بدون إبقاء الإصبع).
 */
@Composable
internal fun MicButton(
    recording: Boolean,
    onStart: () -> Boolean,
    onWillCancel: (Boolean) -> Unit,
    onLock: () -> Unit,
    onEnd: (send: Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val cancelPx = with(density) { 90.dp.toPx() }
    val lockPx = with(density) { 80.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val startState = rememberUpdatedState(onStart)
    val willCancelState = rememberUpdatedState(onWillCancel)
    val lockState = rememberUpdatedState(onLock)
    val endState = rememberUpdatedState(onEnd)
    var lockFraction by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(if (recording) 1.25f else 1f, tween(com.shopmanager.app.data.performance.MotionSpecs.fastMs, easing = com.shopmanager.app.data.performance.MotionSpecs.claudeEasing), label = "micScale")

    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            LockHint(
                lockFraction,
                Modifier
                    .requiredSize(44.dp, 104.dp)
                    .offset(y = (-(84f + 24f * lockFraction)).dp)
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(cs.onSurface)
                .pointerInput(rtl, cancelPx, lockPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!startState.value()) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        var cancel = false
                        var locked = false
                        lockFraction = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            val toward = if (rtl) dx else -dx
                            lockFraction = (-dy / lockPx).coerceIn(0f, 1f)
                            if (lockFraction >= 1f && !cancel) {
                                locked = true
                                change.consume()
                                break
                            }
                            val c = toward > cancelPx
                            if (c != cancel) {
                                cancel = c
                                willCancelState.value(c)
                            }
                            change.consume()
                        }
                        lockFraction = 0f
                        if (locked) lockState.value() else endState.value(!cancel)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, "اضغط مطولاً للتسجيل", tint = cs.surface, modifier = Modifier.size(24.dp))
        }
    }
}

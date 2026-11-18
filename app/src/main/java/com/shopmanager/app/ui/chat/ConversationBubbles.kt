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

@Composable
internal fun ConversationHeader(
    name: String,
    status: String,
    highlight: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(66.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
        ChatAvatar(name, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (highlight) LocalSemanticColors.current.success else cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Box {
            ScreenIconButton(Icons.Default.MoreVert, "المزيد", { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("بحث في المحادثة") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    onClick = { menu = false; onSearch() }
                )
                DropdownMenuItem(
                    text = { Text("مسح المحادثة") },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                    onClick = { menu = false; onClear() }
                )
            }
        }
    }
}

@Composable
internal fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    m: ChatMessage,
    mine: Boolean,
    read: Boolean,
    play: VoicePlayState,
    myUid: String,
    otherName: String,
    highlight: Boolean,
    query: String,
    currentMatch: Boolean,
    transcript: String?,
    transcribing: Boolean,
    linkPreviews: Boolean,
    onImage: () -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onLongPress: (Rect) -> Unit,
    onReply: () -> Unit,
    onJump: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val triggerPx = with(density) { 56.dp.toPx() }
    val maxPx = with(density) { 80.dp.toPx() }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var crossed by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val replyNow = rememberUpdatedState(onReply)

    val openMenu: () -> Unit = {
        val c = coords
        if (c != null && c.isAttached) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongPress(Rect(c.localToScreen(Offset.Zero), c.size.toSize()))
        }
    }

    val frac = (abs(dragPx) / triggerPx).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth()) {
        if (frac > 0f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .align(if ((dragPx > 0f) != rtl) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 14.dp)
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = frac
                        scaleX = 0.6f + 0.4f * frac
                        scaleY = 0.6f + 0.4f * frac
                    }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragPx.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { crossed = false },
                        onDragEnd = {
                            if (abs(dragPx) >= triggerPx) replyNow.value()
                            crossed = false
                            val from = dragPx
                            scope.launch { animate(from, 0f, animationSpec = tween(com.shopmanager.app.data.performance.MotionSpecs.baseMs, easing = com.shopmanager.app.data.performance.MotionSpecs.claudeEasing)) { v, _ -> dragPx = v } }
                        },
                        onDragCancel = {
                            crossed = false
                            val from = dragPx
                            scope.launch { animate(from, 0f, animationSpec = tween(com.shopmanager.app.data.performance.MotionSpecs.baseMs, easing = com.shopmanager.app.data.performance.MotionSpecs.claudeEasing)) { v, _ -> dragPx = v } }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragPx = (dragPx + delta).coerceIn(-maxPx, maxPx)
                            if (!crossed && abs(dragPx) >= triggerPx) {
                                crossed = true
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                },
            horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End
        ) {
            Column(horizontalAlignment = if (mine) Alignment.Start else Alignment.End) {
                BubbleBody(
                    m = m,
                    mine = mine,
                    read = read,
                    play = play,
                    replyName = m.reply?.let { if (it.fromUid == myUid) "أنت" else otherName }.orEmpty(),
                    highlight = highlight,
                    onTogglePlay = onTogglePlay,
                    onSpeed = onSpeed,
                    onLong = openMenu,
                    onJump = onJump,
                    query = query,
                    currentMatch = currentMatch,
                    transcript = transcript,
                    transcribing = transcribing,
                    linkPreviews = linkPreviews,
                    onImage = onImage,
                    modifier = Modifier
                        .onGloballyPositioned { coords = it }
                        .combinedClickable(onClick = {}, onLongClick = openMenu)
                )
                if (m.reactions.isNotEmpty()) ReactionChips(m.reactions)
            }
        }
    }
}

@Composable
internal fun ReactionChips(reactions: Map<String, String>) {
    val cs = MaterialTheme.colorScheme
    val distinct = reactions.values.distinct().take(3)
    Row(
        Modifier
            .padding(top = 3.dp, start = 8.dp, end = 8.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        distinct.forEach { Text(it, fontSize = 14.sp) }
        if (reactions.size > 1) {
            Text(reactions.size.toString(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BubbleBody(
    m: ChatMessage,
    mine: Boolean,
    read: Boolean,
    play: VoicePlayState,
    replyName: String,
    highlight: Boolean,
    onTogglePlay: () -> Unit,
    onSpeed: () -> Unit,
    onLong: () -> Unit,
    onJump: (String) -> Unit,
    modifier: Modifier,
    query: String = "",
    currentMatch: Boolean = false,
    transcript: String? = null,
    transcribing: Boolean = false,
    linkPreviews: Boolean = false,
    onImage: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val base = if (mine) cs.onSurface else cs.surfaceContainerHigh
    val bg = if (highlight) lerp(base, cs.primary, 0.3f) else base
    val fg = if (mine) cs.surface else cs.onSurface
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 5.dp else 18.dp,
        bottomEnd = if (mine) 18.dp else 5.dp
    )
    Box(
        modifier
            .widthIn(max = 290.dp)
            .clip(shape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            m.reply?.let { r ->
                ReplyQuote(r, replyName, fg, onClick = { onJump(r.id) })
                Spacer(Modifier.height(6.dp))
            }
            when (m.type) {
                MessageType.VOICE -> {
                    VoiceContent(m, fg, bg, play, onTogglePlay, onSpeed, onLong)
                    if (transcribing) {
                        Text("جاري النسخ النصي...", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Start).padding(top = 4.dp))
                    }
                    transcript?.let {
                        Text(
                            highlightedText(it, query, cs.primary, currentMatch),
                            modifier = Modifier.align(Alignment.Start).padding(top = 6.dp).widthIn(max = 230.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = fg.copy(alpha = 0.9f)
                        )
                    }
                }
                MessageType.IMAGE -> {
                    ChatImageContent(m, onImage)
                    if (m.text.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            highlightedText(m.text, query, cs.primary, currentMatch),
                            modifier = Modifier.align(Alignment.Start),
                            style = MaterialTheme.typography.bodyLarge,
                            color = fg
                        )
                    }
                }
                MessageType.TEXT -> {
                    Text(
                        highlightedText(m.text, query, cs.primary, currentMatch),
                        modifier = Modifier.align(Alignment.Start),
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg
                    )
                    if (linkPreviews) {
                        LinkExtractor.firstUrl(m.text)?.let { url ->
                            Spacer(Modifier.height(6.dp))
                            LinkPreviewCard(url, fg)
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.edited) {
                    Text("معدّلة", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.6f))
                }
                Text(
                    formatClock(m.createdAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.6f)
                )
                if (mine) {
                    Icon(
                        imageVector = when {
                            m.pending -> Icons.Default.Schedule
                            read -> Icons.Default.DoneAll
                            else -> Icons.Default.Done
                        },
                        contentDescription = when {
                            m.pending -> "قيد الإرسال"
                            read -> "تمت القراءة"
                            else -> "تم الإرسال"
                        },
                        tint = fg.copy(alpha = if (read) 1f else 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReplyQuote(reply: ReplyRef, name: String, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(fg.copy(alpha = 0.7f)))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg.copy(alpha = 0.9f),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reply.type == MessageType.VOICE) {
                    Icon(Icons.Default.Mic, null, tint = fg.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
                }
                Text(
                    when (reply.type) {
                        MessageType.VOICE -> "رسالة صوتية"
                        MessageType.IMAGE -> "📷 " + reply.text.ifBlank { "صورة" }
                        MessageType.TEXT -> reply.text
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

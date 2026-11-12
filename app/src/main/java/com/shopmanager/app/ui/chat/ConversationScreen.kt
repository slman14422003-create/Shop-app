package com.shopmanager.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material3.Text
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

private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

private sealed interface ChatItem {
    val key: String

    data class Msg(val m: ChatMessage) : ChatItem {
        override val key get() = m.id
    }

    data class Day(val label: String, override val key: String) : ChatItem
}

private class MessageMenu(val m: ChatMessage, val mine: Boolean, val anchor: Rect)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val msgState by viewModel.messages.collectAsStateWithLifecycle()
    val other by viewModel.other.collectAsStateWithLifecycle()
    val typing by viewModel.otherTyping.collectAsStateWithLifecycle()
    val otherRead by viewModel.otherReadMs.collectAsStateWithLifecycle()
    val rec by viewModel.recording.collectAsStateWithLifecycle()
    val toast by viewModel.message.collectAsStateWithLifecycle()
    val play by VoicePlayer.state.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }

    var text by rememberSaveable { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MessageMenu?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    ChatPresenceEffect { viewModel.heartbeat() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) viewModel.setActive(true)
            if (e == Lifecycle.Event.ON_PAUSE) viewModel.setActive(false)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.setActive(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            viewModel.setActive(false)
        }
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(editing?.id) { editing?.let { text = it.text } }

    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(1200)
            highlightId = null
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.showHint("اضغط مطولاً على المايك وتحدّث ثم أفلت للإرسال")
        else viewModel.showHint("اسمح بصلاحية المايك من إعدادات التطبيق لإرسال رسائل صوتية")
    }

    val chatItems = remember(msgState.messages) {
        val out = ArrayList<ChatItem>(msgState.messages.size + 4)
        var prev: ChatMessage? = null
        for (m in msgState.messages) {
            if (prev == null || !sameDay(prev.createdAtMs, m.createdAtMs)) {
                out.add(ChatItem.Day(formatDaySeparator(m.createdAtMs), "day_${m.id}"))
            }
            out.add(ChatItem.Msg(m))
            prev = m
        }
        out.asReversed()
    }

    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    var unseen by remember { mutableIntStateOf(0) }

    val newestId = msgState.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        val last = msgState.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.fromUid == viewModel.me || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
        else unseen++
    }
    LaunchedEffect(atBottom) { if (atBottom) unseen = 0 }

    val status = when {
        typing -> "يكتب الآن..."
        other?.isOnline(now) == true -> "متصل الآن"
        other != null -> formatLastSeen(other!!.lastSeenMs, now)
        else -> ""
    }

    fun nameOf(uid: String) = if (uid == viewModel.me) "أنت" else viewModel.otherName

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            ConversationHeader(
                name = viewModel.otherName,
                status = status,
                highlight = typing || other?.isOnline(now) == true,
                onBack = onBack,
                onClear = { confirmClear = true },
                onSignOut = { confirmSignOut = true }
            )
        },
        bottomBar = {
            InputBar(
                text = text,
                onTextChange = { text = it; viewModel.onTextChanged(it) },
                onSend = { viewModel.sendText(text); text = "" },
                rec = rec,
                replyTo = replyTo,
                replyName = replyTo?.let { nameOf(it.fromUid) }.orEmpty(),
                editing = editing,
                onCancelReply = viewModel::cancelReply,
                onCancelEdit = { viewModel.cancelEdit(); text = "" },
                onMicStart = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val ok = viewModel.startRecording()
                        if (ok) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        ok
                    } else {
                        permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        false
                    }
                },
                onWillCancel = viewModel::setWillCancel,
                onLock = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.lockRecording()
                },
                onMicEnd = { send -> viewModel.finishRecording(send) },
                onTogglePause = viewModel::togglePauseRecording
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!msgState.loading && msgState.messages.isEmpty()) {
                AppEmptyState(Icons.Default.Forum, "ابدأ المحادثة مع ${viewModel.otherName}\nاكتب رسالة أو اضغط مطولاً على المايك")
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chatItems, key = { it.key }, contentType = { if (it is ChatItem.Day) "day" else "msg" }) { item ->
                        when (item) {
                            is ChatItem.Day -> DaySeparator(item.label)
                            is ChatItem.Msg -> {
                                val m = item.m
                                val mine = m.fromUid == viewModel.me
                                MessageBubble(
                                    m = m,
                                    mine = mine,
                                    read = mine && otherRead > 0 && m.createdAtMs <= otherRead,
                                    play = play,
                                    myUid = viewModel.me,
                                    otherName = viewModel.otherName,
                                    highlight = highlightId == m.id,
                                    onTogglePlay = { VoicePlayer.toggle(context, m.id, m.audioB64) },
                                    onSpeed = { VoicePlayer.cycleSpeed() },
                                    onLongPress = { anchor -> menu = MessageMenu(m, mine, anchor) },
                                    onReply = { viewModel.startReply(m) },
                                    onJump = { id ->
                                        val idx = chatItems.indexOfFirst { it is ChatItem.Msg && it.m.id == id }
                                        if (idx >= 0) {
                                            highlightId = id
                                            scope.launch { listState.animateScrollToItem(idx) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (msgState.hasMore) {
                        item(key = "older") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = viewModel::loadOlder) {
                                    Text("عرض الرسائل الأقدم", color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !atBottom && msgState.messages.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest)
                            .clickable(role = Role.Button) { scope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "إلى آخر رسالة", tint = cs.onSurface, modifier = Modifier.size(26.dp))
                    }
                    if (unseen > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(cs.onSurface)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (unseen > 99) "99+" else unseen.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.surface
                            )
                        }
                    }
                }
            }
        }
    }

    menu?.let { mm ->
        val m = mm.m
        val actions = buildList {
            add(BlurMenuAction("رد", Icons.AutoMirrored.Filled.Reply) { viewModel.startReply(m) })
            if (m.type == MessageType.TEXT) {
                add(
                    BlurMenuAction("نسخ", Icons.Default.ContentCopy) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("message", m.text))
                        viewModel.showHint("تم نسخ الرسالة")
                    }
                )
            }
            if (mm.mine && m.type == MessageType.TEXT && !m.pending) {
                add(BlurMenuAction("تعديل", Icons.Default.Edit) { viewModel.startEdit(m) })
            }
            add(BlurMenuAction("حذف", Icons.Default.Delete, danger = true) { deleteTarget = m })
        }
        BlurContextMenu(
            anchor = mm.anchor,
            actions = actions,
            reactions = REACTIONS,
            selectedReaction = m.reactions[viewModel.me],
            onReact = { emoji -> viewModel.react(m, emoji) },
            onDismiss = { menu = null },
            preview = {
                BubbleBody(
                    m = m,
                    mine = mm.mine,
                    read = mm.mine && otherRead > 0 && m.createdAtMs <= otherRead,
                    play = play,
                    replyName = m.reply?.let { nameOf(it.fromUid) }.orEmpty(),
                    highlight = false,
                    onTogglePlay = {},
                    onSpeed = {},
                    onLong = {},
                    onJump = {},
                    modifier = Modifier
                )
            }
        )
    }

    deleteTarget?.let { t ->
        val mineT = t.fromUid == viewModel.me
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف الرسالة") },
            text = {
                Column {
                    Text(if (mineT) "احذف الرسالة للجميع، أو من عندك فقط." else "ستُحذف الرسالة من عندك فقط، ولن تتأثر نسخة الطرف الآخر.")
                    if (mineT) {
                        TextButton(
                            shape = RectangleShape,
                            onClick = { deleteTarget = null; viewModel.deleteForMe(t) }
                        ) { Text("حذف عندي فقط", color = cs.onSurface) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = {
                        deleteTarget = null
                        if (mineT) viewModel.deleteForEveryone(t) else viewModel.deleteForMe(t)
                    }
                ) { Text(if (mineT) "حذف للجميع" else "حذف") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }

    if (confirmClear) {
        GlassAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح المحادثة") },
            text = { Text("سيتم مسح كل الرسائل من عندك فقط، ولن تتأثر نسخة الطرف الآخر.") },
            confirmButton = { TextButton(shape = RectangleShape, onClick = { confirmClear = false; viewModel.clearChat() }) { Text("مسح") } },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }

    if (confirmSignOut) {
        GlassAlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("تسجيل الخروج") },
            text = { Text("هل تريد تسجيل الخروج من الدردشة؟ يمكنك الدخول مجدداً بنفس الحساب.") },
            confirmButton = { TextButton(shape = RectangleShape, onClick = { confirmSignOut = false; onSignOut() }) { Text("خروج") } },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { confirmSignOut = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ConversationHeader(
    name: String,
    status: String,
    highlight: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSignOut: () -> Unit
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
                    text = { Text("مسح المحادثة") },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                    onClick = { menu = false; onClear() }
                )
                DropdownMenuItem(
                    text = { Text("تسجيل الخروج") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                    onClick = { menu = false; onSignOut() }
                )
            }
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
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
private fun MessageBubble(
    m: ChatMessage,
    mine: Boolean,
    read: Boolean,
    play: VoicePlayState,
    myUid: String,
    otherName: String,
    highlight: Boolean,
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
                            scope.launch { animate(from, 0f, animationSpec = tween(160)) { v, _ -> dragPx = v } }
                        },
                        onDragCancel = {
                            crossed = false
                            val from = dragPx
                            scope.launch { animate(from, 0f, animationSpec = tween(160)) { v, _ -> dragPx = v } }
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
private fun ReactionChips(reactions: Map<String, String>) {
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
private fun BubbleBody(
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
    modifier: Modifier
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
            if (m.type == MessageType.VOICE) {
                VoiceContent(m, fg, bg, play, onTogglePlay, onSpeed, onLong)
            } else {
                Text(
                    m.text,
                    modifier = Modifier.align(Alignment.Start),
                    style = MaterialTheme.typography.bodyLarge,
                    color = fg
                )
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
private fun ReplyQuote(reply: ReplyRef, name: String, fg: Color, onClick: () -> Unit) {
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
                    if (reply.type == MessageType.VOICE) "رسالة صوتية" else reply.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceContent(
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
                if (isCurrent) {
                    Text(
                        speedLabel(play.speed),
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
}

@Composable
private fun Waveform(wave: List<Int>, progress: Float, color: Color, modifier: Modifier) {
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
private fun LiveWave(levels: List<Int>, paused: Boolean, color: Color, modifier: Modifier) {
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
private fun ComposerBanner(title: String, subtitle: String, icon: ImageVector, onClose: () -> Unit) {
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
private fun InputBar(
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
    onTogglePause: () -> Unit
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
                    subtitle = if (replyTo.type == MessageType.VOICE) "رسالة صوتية" else replyTo.text,
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
        if (rec.locked) {
            LockedRecorder(rec, onCancel = { onMicEnd(false) }, onTogglePause = onTogglePause, onSend = { onMicEnd(true) })
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
private fun RecDot(paused: Boolean) {
    val cs = MaterialTheme.colorScheme
    val animate = LocalPerformanceTier.current != PerformanceTier.LOW && !paused
    val pulse = if (animate) {
        val t = rememberInfiniteTransition(label = "recPulse")
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot").value
    } else 1f
    Box(Modifier.size(10.dp).alpha(if (paused) 0.5f else pulse).clip(CircleShape).background(cs.error))
}

@Composable
private fun RecordingStrip(rec: RecordingUi) {
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
private fun LockedRecorder(
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
private fun LockHint(fraction: Float, modifier: Modifier) {
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
private fun MicButton(
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
    val scale by animateFloatAsState(if (recording) 1.25f else 1f, tween(150), label = "micScale")

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

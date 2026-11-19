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


internal val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

internal sealed interface ChatItem {
    val key: String

    data class Msg(val m: ChatMessage) : ChatItem {
        override val key get() = m.id
    }

    data class Day(val label: String, override val key: String) : ChatItem
}

internal class MessageMenu(val m: ChatMessage, val mine: Boolean, val anchor: Rect)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    converter: MessageConverter? = null
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
    val search by viewModel.search.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val transcribing by viewModel.transcribing.collectAsStateWithLifecycle()
    val convert by viewModel.convert.collectAsStateWithLifecycle()
    val undoHint by viewModel.undoHint.collectAsStateWithLifecycle()
    val sendingImage by viewModel.sendingImage.collectAsStateWithLifecycle()
    var viewerMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var convertPick by remember { mutableStateOf<ChatMessage?>(null) }
    val quickReplies = remember { viewModel.quickReplies }
    val linkPreviews = remember { viewModel.linkPreviewEnabled }
    androidx.compose.runtime.SideEffect { viewModel.converter = converter }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.sendImage(uri)
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }

    var text by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MessageMenu?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    ChatPresenceEffect { viewModel.heartbeat() }

    val openLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel.chatId, openLifecycleOwner) {
        val chatId = viewModel.chatId
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ChatOpenTracker.openChatId = chatId
                    NotificationHelper.cancelChatNotification(context, chatId)
                }
                Lifecycle.Event.ON_PAUSE -> if (ChatOpenTracker.openChatId == chatId) ChatOpenTracker.openChatId = null
                else -> Unit
            }
        }
        openLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            openLifecycleOwner.lifecycle.removeObserver(observer)
            if (ChatOpenTracker.openChatId == chatId) ChatOpenTracker.openChatId = null
        }
    }

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

    LaunchedEffect(undoHint) {
        undoHint?.let { h ->
            val r = snackbar.showSnackbar(h.text, actionLabel = "تراجع", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) viewModel.runUndo(h)
            viewModel.clearUndoHint()
        }
    }

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

    LaunchedEffect(search.currentId) {
        val id = search.currentId ?: return@LaunchedEffect
        val idx = chatItems.indexOfFirst { it is ChatItem.Msg && it.m.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    fun jumpTo(id: String) {
        val idx = chatItems.indexOfFirst { it is ChatItem.Msg && it.m.id == id }
        if (idx >= 0) {
            highlightId = id
            scope.launch { listState.animateScrollToItem(idx) }
        }
    }

    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    var unseen by remember { mutableIntStateOf(0) }

    val newestId = msgState.messages.lastOrNull()?.id
    var toneSeenId by remember { mutableStateOf<String?>(null) }
    var toneReady by remember { mutableStateOf(false) }
    LaunchedEffect(newestId) {
        val last = msgState.messages.lastOrNull() ?: return@LaunchedEffect
        if (toneReady && last.id != toneSeenId && last.fromUid != viewModel.me && !last.pending) {
            ChatSounds.playIncoming(context)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        toneSeenId = last.id
        toneReady = true
        if (last.fromUid == viewModel.me || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
        else unseen++
    }
    LaunchedEffect(atBottom) { if (atBottom) unseen = 0 }

    val status = when {
        typing -> "يكتب الآن..."
        other?.isOnline(now) == true -> "متصل الآن"
        other != null -> other?.let { formatLastSeen(it.lastSeenMs, now) }.orEmpty()
        else -> ""
    }

    fun nameOf(uid: String) = if (uid == viewModel.me) "أنت" else viewModel.otherName

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            Column {
                if (search.active) {
                    ChatSearchHeader(
                        query = search.query,
                        total = search.matches.size,
                        index = search.index,
                        onQuery = viewModel::setSearchQuery,
                        onOlder = { viewModel.stepSearch(true) },
                        onNewer = { viewModel.stepSearch(false) },
                        onClose = viewModel::closeSearch
                    )
                } else {
                    ConversationHeader(
                        name = viewModel.otherName,
                        status = status,
                        highlight = typing || other?.isOnline(now) == true,
                        onBack = onBack,
                        onClear = { confirmClear = true },
                        onSearch = viewModel::openSearch
                    )
                }
                pinned?.let { p ->
                    PinnedBar(
                        pinned = p,
                        name = if (p.fromUid == viewModel.me) "أنت" else viewModel.otherName,
                        onClick = { jumpTo(p.id) },
                        onUnpin = {
                            msgState.messages.firstOrNull { it.id == p.id }?.let(viewModel::togglePin)
                                ?: viewModel.unpin()
                        }
                    )
                }
            }
        },
        bottomBar = {
            InputBar(
                text = text,
                onTextChange = { text = it; viewModel.onTextChanged(it) },
                onSend = {
                    if (text.isNotBlank() && editing == null) ChatSounds.playSend(context)
                    viewModel.sendText(text)
                    text = ""
                },
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
                onTogglePause = viewModel::togglePauseRecording,
                onAttach = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                sendingImage = sendingImage,
                quickReplies = quickReplies,
                onQuickReply = { r ->
                    ChatSounds.playSend(context)
                    viewModel.sendQuickReply(r)
                }
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
                                    highlight = highlightId == m.id || search.currentId == m.id,
                                    query = if (search.active) search.query else "",
                                    currentMatch = search.currentId == m.id,
                                    transcript = transcripts[m.id],
                                    transcribing = m.id in transcribing,
                                    linkPreviews = linkPreviews,
                                    onImage = { viewerMsg = m },
                                    onTogglePlay = { VoicePlayer.toggle(context, m.id, m.audioB64) },
                                    onSpeed = { VoicePlayer.cycleSpeed() },
                                    onLongPress = { anchor -> menu = MessageMenu(m, mine, anchor) },
                                    onReply = { viewModel.startReply(m) },
                                    onJump = { id -> jumpTo(id) }
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
            add(
                BlurMenuAction(if (pinned?.id == m.id) "إلغاء التثبيت" else "تثبيت", Icons.Default.PushPin) { viewModel.togglePin(m) }
            )
            if (m.type == MessageType.VOICE && !m.isExpiredVoice && m.audioB64.isNotEmpty()) {
                add(BlurMenuAction("نسخ نصي", Icons.Default.Subtitles) { viewModel.transcribe(m) })
            }
            if (m.type == MessageType.TEXT && m.text.isNotBlank() && converter != null) {
                add(BlurMenuAction("تحويل إلى عنصر", Icons.Default.SwapHoriz) { convertPick = m })
            }
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
                    modifier = Modifier.wrapContentWidth(if (mm.mine) Alignment.Start else Alignment.End, unbounded = true)
                )
            }
        )
    }

    viewerMsg?.let { vm -> ChatImageViewer(vm) { viewerMsg = null } }

    convertPick?.let { cm ->
        ConvertKindDialog(
            onPick = { kind -> convertPick = null; viewModel.startConvert(kind, cm) },
            onDismiss = { convertPick = null }
        )
    }

    convert?.let { cv ->
        ConvertDialog(
            state = cv,
            onOption = viewModel::chooseConvertOption,
            onConfirm = viewModel::confirmConvert,
            onDismiss = viewModel::dismissConvert
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
}

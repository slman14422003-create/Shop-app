package com.shopmanager.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forum
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.chat.ChatMessage
import com.shopmanager.app.data.chat.MessageType
import com.shopmanager.app.data.chat.VoicePlayState
import com.shopmanager.app.data.chat.VoicePlayer
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

private sealed interface ChatItem {
    val key: String

    data class Msg(val m: ChatMessage) : ChatItem {
        override val key get() = m.id
    }

    data class Day(val label: String, override val key: String) : ChatItem
}

/** شاشة المحادثة: رسائل نصية وصوتية بين مستخدمين فقط، بتصميم التطبيق. */
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

    val msgState by viewModel.messages.collectAsStateWithLifecycle()
    val other by viewModel.other.collectAsStateWithLifecycle()
    val typing by viewModel.otherTyping.collectAsStateWithLifecycle()
    val otherRead by viewModel.otherReadMs.collectAsStateWithLifecycle()
    val rec by viewModel.recording.collectAsStateWithLifecycle()
    val toast by viewModel.message.collectAsStateWithLifecycle()
    val play by VoicePlayer.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }

    var text by rememberSaveable { mutableStateOf("") }
    var confirmSignOut by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    ChatPresenceEffect { viewModel.heartbeat() }

    // "نشطة" فقط والشاشة ظاهرة، لتُعلَّم الرسائل الواردة كمقروءة ولا تُعلَّم وأنا خارج التطبيق.
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
        out.asReversed() // القائمة معكوسة (الأحدث أسفل) لذا نُدخلها من الأحدث إلى الأقدم
    }

    // انتقال لآخر رسالة عند وصول رسالة جديدة إن كنت قرب الأسفل، أو دائماً إن كانت منّي.
    val newestId = msgState.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        val last = msgState.messages.lastOrNull() ?: return@LaunchedEffect
        if (last.fromUid == viewModel.me || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    val status = when {
        typing -> "يكتب الآن..."
        other?.isOnline(now) == true -> "متصل الآن"
        other != null -> formatLastSeen(other!!.lastSeenMs, now)
        else -> ""
    }

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
                onSignOut = { confirmSignOut = true }
            )
        },
        bottomBar = {
            InputBar(
                text = text,
                onTextChange = { text = it; viewModel.onTextChanged(it) },
                onSend = { viewModel.sendText(text); text = "" },
                rec = rec,
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
                onMicEnd = { send -> viewModel.finishRecording(send) }
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
                                    onTogglePlay = { VoicePlayer.toggle(context, m.id, m.audioB64) },
                                    onCopy = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("message", m.text))
                                        viewModel.showHint("تم نسخ الرسالة")
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
        }
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

// ───────────────────────── الترويسة ─────────────────────────

@Composable
private fun ConversationHeader(
    name: String,
    status: String,
    highlight: Boolean,
    onBack: () -> Unit,
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

// ───────────────────────── الفقاعات ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: ChatMessage,
    mine: Boolean,
    read: Boolean,
    play: VoicePlayState,
    onTogglePlay: () -> Unit,
    onCopy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (mine) cs.onSurface else cs.surfaceContainerHigh
    val fg = if (mine) cs.surface else cs.onSurface
    // رسائلي عند البداية (يمين في RTL) وذيل الفقاعة عند الزاوية السفلية القريبة من الحافة.
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 5.dp else 18.dp,
        bottomEnd = if (mine) 18.dp else 5.dp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(bg)
                .then(
                    if (m.type == MessageType.TEXT) Modifier.combinedClickable(onClick = {}, onLongClick = onCopy)
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (m.type == MessageType.VOICE) {
                    VoiceContent(m, fg, bg, play, onTogglePlay)
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
}

@Composable
private fun VoiceContent(m: ChatMessage, fg: Color, bg: Color, play: VoicePlayState, onToggle: () -> Unit) {
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
                .clickable(role = Role.Button, onClick = onToggle),
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
            Text(formatDuration(shownMs), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
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

// ───────────────────────── شريط الإدخال ─────────────────────────

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    rec: RecordingUi,
    onMicStart: () -> Boolean,
    onWillCancel: (Boolean) -> Unit,
    onMicEnd: (send: Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            MicButton(rec.active, onMicStart, onWillCancel, onMicEnd)
        } else {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface)
                    .clickable(role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = cs.surface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun RecordingStrip(rec: RecordingUi) {
    val cs = MaterialTheme.colorScheme
    val animate = LocalPerformanceTier.current != PerformanceTier.LOW
    val pulse = if (animate) {
        val t = rememberInfiniteTransition(label = "recPulse")
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot").value
    } else 1f
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(cs.error))
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

/**
 * زر المايك: ضغط مطوّل = تسجيل، الإفلات = إرسال، والسحب نحو منتصف الشاشة ثم الإفلات = إلغاء.
 * (المايك عند حافة الشاشة، فاتجاه الإلغاء "نحو المنتصف" ويُحسب حسب اتجاه الواجهة RTL/LTR.)
 */
@Composable
private fun MicButton(
    recording: Boolean,
    onStart: () -> Boolean,
    onWillCancel: (Boolean) -> Unit,
    onEnd: (send: Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val cancelPx = with(density) { 90.dp.toPx() }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
    val startState = rememberUpdatedState(onStart)
    val willCancelState = rememberUpdatedState(onWillCancel)
    val endState = rememberUpdatedState(onEnd)
    val scale by animateFloatAsState(if (recording) 1.25f else 1f, tween(150), label = "micScale")

    Box(
        Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(cs.onSurface)
            .pointerInput(rtl, cancelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!startState.value()) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    var cancel = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val toward = if (rtl) dx else -dx
                        val c = toward > cancelPx
                        if (c != cancel) {
                            cancel = c
                            willCancelState.value(c)
                        }
                        change.consume()
                    }
                    endState.value(!cancel)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Mic, "اضغط مطولاً للتسجيل", tint = cs.surface, modifier = Modifier.size(24.dp))
    }
}

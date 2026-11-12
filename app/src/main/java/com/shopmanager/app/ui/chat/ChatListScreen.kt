package com.shopmanager.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.chat.ChatSummary
import com.shopmanager.app.data.chat.MessageType
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppFootnote
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSearchBar
import com.shopmanager.app.ui.common.BlurContextMenu
import com.shopmanager.app.ui.common.BlurMenuAction
import com.shopmanager.app.ui.common.GlassSnackbarHost
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape

private class ChatMenuTarget(val chat: ChatSummary, val shape: Shape, val anchor: Rect)

/**
 * الصفحة الرابعة في التطبيق (كانت "ملاحظات هامة" وأصبحت "الدردشة المحلية"): تعرض حسب الحالة
 * شاشة الدخول/إنشاء الحساب، أو قائمة المحادثات. زر + أعلى الشاشة يفتح اختيار مستخدم لبدء محادثة،
 * وقائمة ⋮ فيها تسجيل الخروج.
 */
@Composable
fun ChatRoot(
    viewModel: ChatSessionViewModel,
    onOpenDrawer: () -> Unit,
    onOpenConversation: (uid: String, name: String) -> Unit,
    onOpenUserPicker: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val listState by viewModel.chatList.collectAsStateWithLifecycle()
    val chatsError by viewModel.chatsError.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menuTarget by remember { mutableStateOf<ChatMenuTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatSummary?>(null) }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    val signedIn = session is ChatSessionState.SignedIn
    if (signedIn) ChatPresenceEffect { viewModel.heartbeat() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            ScreenTopBar(
                title = "الدردشة المحلية",
                reservedIcons = 1,
                navigation = {
                    ScreenIconButton(Icons.Default.Menu, "القائمة", onOpenDrawer)
                },
                actions = if (signedIn) {
                    {
                        ScreenIconButton(Icons.Default.Add, "محادثة جديدة", onOpenUserPicker)
                    }
                } else null
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (session) {
                ChatSessionState.Loading, ChatSessionState.SignedOut -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                }

                is ChatSessionState.SignedIn -> Column(Modifier.fillMaxSize()) {
                    AppSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = { query = "" },
                        placeholder = "بحث في المحادثات...",
                        showBackButton = false,
                        modifier = Modifier.padding(horizontal = AppScreenPadding, vertical = 8.dp)
                    )
                    if (chatsError != null) {
                        AppFootnote(chatsError ?: "", Modifier.padding(horizontal = AppScreenPadding, vertical = 4.dp))
                    }
                    val filtered = remember(listState.chats, query, pinned) {
                        val base = if (query.isBlank()) listState.chats
                        else listState.chats.filter { it.otherName.contains(query.trim(), ignoreCase = true) }
                        base.sortedByDescending { it.chatId in pinned }
                    }
                    when {
                        listState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                        }

                        filtered.isEmpty() -> AppEmptyState(
                            icon = if (query.isNotBlank()) Icons.Default.Search else Icons.Default.Forum,
                            text = if (query.isNotBlank()) "لا توجد نتائج"
                            else "لا توجد محادثات بعد\nاضغط \"+\" لبدء محادثة مع أحد المستخدمين"
                        )

                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = AppScreenPadding, end = AppScreenPadding, top = 4.dp, bottom = 28.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                        ) {
                            itemsIndexed(filtered, key = { _, c -> c.chatId }, contentType = { _, _ -> "chat" }) { index, chat ->
                                val rowShape = groupedRowShape(index, filtered.lastIndex)
                                ChatRow(
                                    chat = chat,
                                    shape = rowShape,
                                    pinned = chat.chatId in pinned,
                                    onClick = { onOpenConversation(chat.otherUid, chat.otherName) },
                                    onLongClick = { anchor -> menuTarget = ChatMenuTarget(chat, rowShape, anchor) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuTarget?.let { t ->
        val isPinned = t.chat.chatId in pinned
        BlurContextMenu(
            anchor = t.anchor,
            actions = listOf(
                BlurMenuAction(
                    if (isPinned) "إلغاء التثبيت" else "تثبيت المحادثة",
                    if (isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin
                ) { viewModel.togglePin(t.chat.chatId) },
                BlurMenuAction("مسح المحادثة", Icons.Default.Delete, danger = true) { deleteTarget = t.chat }
            ),
            onDismiss = { menuTarget = null },
            preview = { ChatRow(t.chat, t.shape, isPinned, onClick = {}, onLongClick = {}) }
        )
    }

    deleteTarget?.let { chat ->
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("مسح المحادثة") },
            text = { Text("سيتم مسح كل رسائل المحادثة مع ${chat.otherName} من عندك فقط، ولن تتأثر نسخة الطرف الآخر.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { viewModel.clearChat(chat.chatId); deleteTarget = null }) { Text("مسح") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: ChatSummary,
    shape: Shape,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val hasUnread = chat.unread > 0
    val haptics = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    AppRowSurface(
        shape = shape,
        modifier = Modifier
            .onGloballyPositioned { coords = it }
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    val c = coords
                    if (c != null && c.isAttached) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(Rect(c.localToScreen(Offset.Zero), c.size.toSize()))
                    }
                }
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(chat.otherName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chat.otherName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chat.lastType == MessageType.VOICE) {
                        Icon(Icons.Default.Mic, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "رسالة صوتية",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasUnread) cs.onSurface else cs.onSurfaceVariant,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            (if (chat.lastFromMe) "أنت: " else "") + chat.lastText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasUnread) cs.onSurface else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) {
                        Icon(Icons.Default.PushPin, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        formatListTime(chat.lastTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) cs.onSurface else cs.onSurfaceVariant
                    )
                }
                if (hasUnread) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(cs.onSurface)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (chat.unread > 99) "99+" else chat.unread.toString(),
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

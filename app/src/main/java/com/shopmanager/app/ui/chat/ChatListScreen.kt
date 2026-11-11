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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.shopmanager.app.ui.common.GlassAlertDialog
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape

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
    val authState by viewModel.auth.collectAsStateWithLifecycle()
    val listState by viewModel.chatList.collectAsStateWithLifecycle()
    val chatsError by viewModel.chatsError.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val signedIn = session is ChatSessionState.SignedIn
    if (signedIn) ChatPresenceEffect { viewModel.heartbeat() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        containerColor = Color.Transparent,
        topBar = {
            ScreenTopBar(
                title = "الدردشة المحلية",
                reservedIcons = if (signedIn) 2 else 1,
                navigation = {
                    ScreenIconButton(Icons.Default.Menu, "القائمة", onOpenDrawer)
                },
                actions = if (signedIn) {
                    {
                        ScreenIconButton(Icons.Default.Add, "محادثة جديدة", onOpenUserPicker)
                        Box {
                            ScreenIconButton(Icons.Default.MoreVert, "المزيد", { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("تسجيل الخروج") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                                    onClick = { menuOpen = false; confirmSignOut = true }
                                )
                            }
                        }
                    }
                } else null
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (session) {
                ChatSessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                }

                ChatSessionState.SignedOut -> ChatAuthScreen(
                    state = authState,
                    onSignUp = viewModel::signUp,
                    onSignIn = viewModel::signIn,
                    onForgot = viewModel::sendReset,
                    onClearMessages = viewModel::clearAuthMessages
                )

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
                    val filtered = remember(listState.chats, query) {
                        if (query.isBlank()) listState.chats
                        else listState.chats.filter { it.otherName.contains(query.trim(), ignoreCase = true) }
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
                                ChatRow(
                                    chat = chat,
                                    shape = groupedRowShape(index, filtered.lastIndex),
                                    onClick = { onOpenConversation(chat.otherUid, chat.otherName) }
                                )
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
            text = { Text("هل تريد تسجيل الخروج من الدردشة؟ يمكنك الدخول مجدداً في أي وقت بنفس الحساب.") },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("خروج") }
            },
            dismissButton = { TextButton(shape = RectangleShape, onClick = { confirmSignOut = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hasUnread = chat.unread > 0
    AppRowSurface(shape = shape, onClick = onClick) {
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
                Text(
                    formatListTime(chat.lastTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) cs.onSurface else cs.onSurfaceVariant
                )
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

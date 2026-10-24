package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.BlockedUser
import com.salman.herbalencyclopedia.data.model.Feedback
import com.salman.herbalencyclopedia.ui.components.EmptyView
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LoadingView
import com.salman.herbalencyclopedia.ui.components.TopBarBrandTitle
import com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import java.text.SimpleDateFormat
import java.util.Locale
import com.salman.herbalencyclopedia.ui.util.tr

/**
 * صندوق ملاحظات المستخدمين — يظهر فقط للأدمن (مربوط في الإعدادات، ولا
 * يُقرأ من Firestore لغير حساب الأدمن أصلاً عبر firestore.rules). يعرض
 * كل ملاحظة بشكل منسّق: بخصوص أي عشبة/خلطة، من أرسلها (أو "مجهول")،
 * ومتى، مع إمكانية الحذف بعد معالجتها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeedbackScreen(
    feedback: List<Feedback>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onDelete: (Feedback) -> Unit,
    // ── حظر مُرسِل مسيء أو متلاعب: يمنعه فقط من إرسال ملاحظات جديدة (لا يحذف
    // ما أرسله سابقاً، ذلك قرار منفصل عبر onDelete أعلاه). blockedUsers تأتي
    // حيّة من AppViewModel.blockedUsers، وهي القائمة الكاملة (لا مجرّد
    // المعرّفات) عمداً: لو حذف الأدمن كل ملاحظات شخص محظور، تبقى هذه القائمة
    // — عبر زر "المحظورون" بالأعلى — الطريقة الوحيدة لرؤيته وفكّ حظره لاحقاً،
    // إذ لن يظهر له أي أثر آخر في هذه الشاشة بعد حذف ملاحظاته. ──
    blockedUsers: List<BlockedUser> = emptyList(),
    onBlock: (uid: String, senderName: String?) -> Unit = { _, _ -> },
    onUnblock: (uid: String) -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<Feedback?>(null) }
    var pendingBlock by remember { mutableStateOf<Feedback?>(null) }
    var showBlockedList by remember { mutableStateOf(false) }
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.uid }.toSet() }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy، HH:mm", Locale("ar")) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                large = true,
                title = {
                    TopBarBrandTitle(
                        icon = Icons.Filled.Inbox,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = tr("ملاحظات المستخدمين"),
                        subtitle = tr("${feedback.size} ملاحظة")
                    )
                },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                },
                actions = {
                    if (blockedUsers.isNotEmpty()) {
                        GlassIconButton(onClick = { showBlockedList = true }) {
                            Icon(Icons.Filled.Block, contentDescription = tr("المحظورون (${blockedUsers.size})"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading && feedback.isEmpty() -> LoadingView(Modifier.padding(padding).fillMaxSize())
            error != null && feedback.isEmpty() -> EmptyView(error, Modifier.padding(padding).fillMaxSize())
            feedback.isEmpty() -> EmptyView(tr("لا توجد ملاحظات حالياً"), Modifier.padding(padding).fillMaxSize())
            else -> ResponsiveScreenContent(windowInfo = rememberWindowSizeInfo(), modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(feedback, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tr("بخصوص: ${item.targetName}"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val typeLabel = if (item.targetType == "blend") tr("خلطة") else tr("عشبة")
                                    Text(
                                        typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val isBlocked = !item.senderUid.isNullOrBlank() && item.senderUid in blockedIds
                                // ملاحظات قديمة أُرسِلت قبل إضافة sender_uid لا تحمل هوية جهاز يمكن حظرها.
                                if (!item.senderUid.isNullOrBlank()) {
                                    GlassIconButton(
                                        onClick = { if (isBlocked) onUnblock(item.senderUid!!) else pendingBlock = item },
                                        size = 36.dp
                                    ) {
                                        Icon(
                                            if (isBlocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                                            contentDescription = tr(if (isBlocked) "فك الحظر" else "حظر هذا المُرسِل"),
                                            tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                GlassIconButton(onClick = { pendingDelete = item }, size = 36.dp) {
                                    Icon(Icons.Filled.Delete, contentDescription = tr("حذف"), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(item.message, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.senderName.isNullOrBlank()) Icons.Filled.PersonOff else Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.senderName?.ifBlank { null } ?: tr("مرسل مجهول"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!item.senderUid.isNullOrBlank() && item.senderUid in blockedIds) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        tr("• محظور"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                item.createdAt?.toDate()?.let { date ->
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        dateFormat.format(date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("حذف هذه الملاحظة؟")) },
            text = { Text(tr("لا يمكن التراجع عن هذا الإجراء.")) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item)
                    pendingDelete = null
                }) { Text(tr("حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr("إلغاء")) }
            }
        )
    }

    pendingBlock?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingBlock = null },
            title = { Text(tr("حظر هذا المُرسِل؟")) },
            text = {
                Text(tr("لن يتمكّن هذا الجهاز من إرسال ملاحظات جديدة بعد الآن. يمكن فك الحظر لاحقاً في أي وقت، ولن يؤثّر هذا على ملاحظاته السابقة."))
            },
            confirmButton = {
                TextButton(onClick = {
                    item.senderUid?.let { onBlock(it, item.senderName) }
                    pendingBlock = null
                }) { Text(tr("حظر"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlock = null }) { Text(tr("إلغاء")) }
            }
        )
    }

    if (showBlockedList) {
        AlertDialog(
            onDismissRequest = { showBlockedList = false },
            title = { Text(tr("المحظورون (${blockedUsers.size})")) },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text(tr("لا يوجد أحد محظور حالياً."))
                } else {
                    // قائمة مستقلة تماماً عن Feedback المعروضة أعلاه عمداً — تبقى
                    // متاحة حتى لو حذف الأدمن كل ملاحظات هذا الشخص لاحقاً، فهي
                    // الطريقة الوحيدة عندها لفكّ حظره مجدداً.
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(blockedUsers, key = { it.uid }) { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        user.senderName?.ifBlank { null } ?: tr("مرسل مجهول"),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        user.blockedAt?.toDate()?.let { dateFormat.format(it) } ?: user.uid.take(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onUnblock(user.uid) }) { Text(tr("فك الحظر")) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedList = false }) { Text(tr("إغلاق")) }
            }
        )
    }
}

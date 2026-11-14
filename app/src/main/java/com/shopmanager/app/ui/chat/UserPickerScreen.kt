package com.shopmanager.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.app.data.chat.ChatUser
import com.shopmanager.app.ui.common.AppEmptyState
import com.shopmanager.app.ui.common.AppGroupGap
import com.shopmanager.app.ui.common.AppRowSurface
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppSearchBar
import com.shopmanager.app.ui.common.ScreenIconButton
import com.shopmanager.app.ui.common.ScreenTopBar
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.delay

/** زر + أعلى قائمة المحادثات: كل المستخدمين المسجّلين مع بحث بالاسم، واللمس يفتح/يُنشئ المحادثة. */
@Composable
fun UserPickerScreen(
    viewModel: UserPickerViewModel,
    onBack: () -> Unit,
    onPick: (uid: String, name: String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000L); value = System.currentTimeMillis() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            ScreenTopBar(
                title = "محادثة جديدة",
                navigation = { ScreenIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AppSearchBar(
                query = query,
                onQueryChange = { viewModel.query.value = it },
                onClose = { viewModel.query.value = "" },
                placeholder = "ابحث باسم المستخدم...",
                showBackButton = false,
                modifier = Modifier.padding(horizontal = AppScreenPadding, vertical = 8.dp)
            )
            when {
                state.loading && state.users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
                }

                state.error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::reload) { Text("إعادة المحاولة", color = MaterialTheme.colorScheme.onSurface) }
                }

                state.users.isEmpty() -> AppEmptyState(
                    icon = if (query.isBlank()) Icons.Default.PersonSearch else Icons.Default.Search,
                    text = if (query.isBlank()) "لا يوجد مستخدمون آخرون بعد\nادعُ أحداً لإنشاء حساب في الدردشة" else "لا توجد نتائج"
                )

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = AppScreenPadding, end = AppScreenPadding, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(AppGroupGap)
                ) {
                    itemsIndexed(state.users, key = { _, u -> u.uid }) { index, user ->
                        UserRow(user, now, groupedRowShape(index, state.users.lastIndex)) { onPick(user.uid, user.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: ChatUser, now: Long, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val online = user.isOnline(now)
    AppRowSurface(shape = shape, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(user.name, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (online) "متصل الآن" else formatLastSeen(user.lastSeenMs, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (online) LocalSemanticColors.current.success else cs.onSurfaceVariant
                )
            }
        }
    }
}


package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.model.BlockedUser
import com.salman.herbalencyclopedia.data.model.Feedback

// نسخة "public" — راجع تعليق AdminListScreen.kt في هذا المجلد للتفاصيل الكاملة.
@Composable
fun AdminFeedbackScreen(
    feedback: List<Feedback>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onDelete: (Feedback) -> Unit,
    blockedUsers: List<BlockedUser> = emptyList(),
    onBlock: (uid: String, senderName: String?) -> Unit = { _, _ -> },
    onUnblock: (uid: String) -> Unit = {}
) {
    LaunchedEffect(Unit) { onBack() }
}

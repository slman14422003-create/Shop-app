package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Herb

// نسخة "public" — راجع تعليق AdminListScreen.kt في هذا المجلد للتفاصيل الكاملة.
@Composable
fun AdminEditBlendScreen(
    existingBlend: Blend?,
    herbs: List<Herb>,
    onBack: () -> Unit,
    onSave: (Blend, (Boolean, String?) -> Unit) -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

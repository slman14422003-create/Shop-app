package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb

// نسخة "public" — راجع تعليق AdminListScreen.kt في هذا المجلد للتفاصيل الكاملة.
@Composable
fun AdminEditHerbScreen(
    existingHerb: Herb?,
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (Herb, (Boolean, String?) -> Unit) -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

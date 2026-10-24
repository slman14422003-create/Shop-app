package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.model.Herb

// نسخة "public": شاشات الأدمن كلها غير موجودة فعلياً في هذه النسخة (لا زر
// يقود إليها بعد التعديل في HomeScreen/SettingsScreen، وisAdmin دائماً false
// هنا فلن يُستدعى هذا المسار في NavGraph أصلاً). أُبقي فقط على نفس توقيع
// الدالة كي يبقى HerbalNavGraph.kt مشتركاً بلا تعديل بين النكهتين.
@Composable
fun AdminListScreen(
    herbs: List<Herb>,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (Herb) -> Unit,
    onDelete: (Herb) -> Unit,
    onTools: () -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

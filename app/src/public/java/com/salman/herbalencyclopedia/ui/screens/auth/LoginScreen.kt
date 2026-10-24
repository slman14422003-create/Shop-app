package com.salman.herbalencyclopedia.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

// نسخة "public": لا شاشة تسجيل دخول فعلية. أُبقي على نفس توقيع الدالة فقط
// كي يبقى HerbalNavGraph.kt (ملف مشترك بين النكهتين) بلا أي تعديل. هذا
// المسار لا يُستدعى منطقياً أصلاً في هذه النسخة (لا يوجد أي زر "تسجيل دخول"
// يقود إليه — راجع التعديل في SettingsScreen.kt)، وحتى لو وُصِل إليه بطريقة
// ما فإنه يعود فوراً دون عرض أي واجهة.
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLogin: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSuccess: () -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

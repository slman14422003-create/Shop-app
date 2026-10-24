package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.model.AppUpdateConfig
import com.salman.herbalencyclopedia.ui.UpdateCheckState

// نسخة "public" — راجع تعليق AdminListScreen.kt في هذا المجلد للتفاصيل الكاملة.
// ملاحظة: ميزة "التحقق من التحديثات" نفسها (رابط التحديثات) تبقى فعّالة
// بالكامل للمستخدم العادي — هذه فقط شاشة *إعدادات* الأدمن لتغيير رابط
// التحديث، وهي ما يُزال هنا، وليست ميزة التحديث نفسها.
@Composable
fun AdminUpdateScreen(
    config: AppUpdateConfig,
    testState: UpdateCheckState = UpdateCheckState.Idle,
    onBack: () -> Unit,
    onSave: (AppUpdateConfig, (Boolean, String?) -> Unit) -> Unit,
    onTestNow: (android.content.Context, AppUpdateConfig) -> Unit = { _, _ -> }
) {
    LaunchedEffect(Unit) { onBack() }
}

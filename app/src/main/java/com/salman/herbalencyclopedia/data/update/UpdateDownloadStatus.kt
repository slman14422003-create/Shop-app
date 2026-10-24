package com.salman.herbalencyclopedia.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * حالة تحميل التحديث المشتركة على مستوى العملية بالكامل (Singleton).
 *
 * السبب: قبل هذا التعديل كان التحميل يتم داخل AppViewModel مباشرة، وهذا
 * يعني أنه يتوقف بمجرد إغلاق شاشة التطبيق (لأن الـ ViewModel لا "يعيش"
 * بمعزل عن نشاط التطبيق بشكل موثوق، ولا يعمل إطلاقاً لو أُغلق التطبيق
 * فعلياً من الخلفية). الآن [UpdateDownloadService] — خدمة أمامية
 * (Foreground Service) حقيقية — هي من تقوم بالتحميل الفعلي وتستمر حتى
 * لو خرج المستخدم من التطبيق تماماً، وتكتب تقدّمها هنا. AppViewModel
 * يعرض هذه الحالة نفسها لشاشة الإعدادات دون أي تغيير يُذكر من ناحية
 * الواجهة.
 */
object UpdateDownloadStatus {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state

    /** آخر ملف .apk نزل بنجاح — يقرأه installUpdate() لتسليمه لمثبّت النظام. */
    @Volatile
    var downloadedApk: File? = null

    fun update(newState: UpdateDownloadState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = UpdateDownloadState.Idle
        downloadedApk = null
    }
}

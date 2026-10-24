package com.salman.herbalencyclopedia.data.update

/**
 * حالة تحميل تحديث التطبيق (ملف الـ .apk) — نُقلت هنا من AppViewModel
 * (طبقة الواجهة) لأنها الآن حالة مشتركة يكتبها [UpdateDownloadService]
 * (خدمة تعمل بالخلفية) ويقرأها AppViewModel، عبر [UpdateDownloadStatus].
 */
sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val progress: Int) : UpdateDownloadState()
    data object ReadyToInstall : UpdateDownloadState()
    data class Failed(val message: String) : UpdateDownloadState()
}

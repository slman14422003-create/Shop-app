package com.salman.herbalencyclopedia.data.repository

import android.content.Context
import com.salman.herbalencyclopedia.data.translate.TranslationRepository

/**
 * Minimal manual DI container. Kept intentionally simple (no Hilt/Koin)
 * to avoid extra annotation-processing build complexity for this project size.
 */
class AppContainer(context: Context) {
    // مرجع التطبيق العام (Application Context) — يُستخدم في AppViewModel
    // لاستدعاءات تحتاج Context خارج سياق واجهة مستخدم مباشرة (مثل التحقق
    // التلقائي الصامت من التحديثات عند بدء التطبيق، راجع
    // AppViewModel.checkForUpdateSilently)، بعكس بقية استدعاءات فحص/تحميل
    // التحديث اليدوية التي تستلم Context من الشاشة نفسها (LocalContext.current).
    val appContext: Context = context.applicationContext
    val herbRepository: HerbRepository by lazy { HerbRepository() }
    val authRepository: AuthRepository by lazy { AuthRepository(context.applicationContext) }
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(context.applicationContext)
    }
    val updateRepository: UpdateRepository by lazy { UpdateRepository() }
    val feedbackRepository: FeedbackRepository by lazy { FeedbackRepository() }
    // إحصائية إدمن فقط: كم جهاز يستخدم الموسوعة — راجع DeviceStatsRepository
    // وAppViewModel.init (النبضة عند كل بدء تشغيل) وAdminToolsScreen (العرض).
    val deviceStatsRepository: DeviceStatsRepository by lazy { DeviceStatsRepository() }
    // سجل أعطال عن بعد — راجع CrashLogRepository وHerbalApp.onCreate
    // (معترِض الاستثناءات) وAdminToolsScreen (عرض/مسح السجل).
    val crashLogRepository: CrashLogRepository by lazy { CrashLogRepository() }
    // علَم "وضع الصيانة" — راجع MaintenanceRepository وAppViewModel.init
    // (يُقرأ مرة عند بدء تشغيل نسخة public) وAdminToolsScreen (تبديل الأدمن).
    val maintenanceRepository: MaintenanceRepository by lazy { MaintenanceRepository() }
    // مزامنة "تعلّم سيمو الذاتي" بين الأجهزة عبر Firestore — انظر
    // SemoLearningRepository وAppViewModel.init للسلك الفعلي.
    val semoLearningRepository: SemoLearningRepository by lazy { SemoLearningRepository() }
    // مزامنة "الحالات المدرَّبة يدوياً" (تدريب المطوّر من أدوات المطور،
    // نسخة full فقط) لكل الأجهزة المتصلة بما فيها نسخة public — قراءة
    // مفتوحة للجميع، كتابة محمية للمطوّر حصراً (راجع firestore.rules
    // وSemoTrainedRepository وAppViewModel.init).
    val semoTrainedRepository: SemoTrainedRepository by lazy { SemoTrainedRepository() }
    // معرفة سيمو المكتسبة من الإنترنت (أعشاب جديدة/أسماء بديلة/أسئلة بلا جواب).
    val semoWebHerbRepository: SemoWebHerbRepository by lazy { SemoWebHerbRepository() }
    // ترجمة بيانات الأعشاب/التصنيفات/الخلطات إلى الإنجليزية عبر واجهة جوجل
    // المجانية، مع تخزين مؤقت دائم — راجع AppViewModel.translateHerbs وما شابه.
    val translationRepository: TranslationRepository by lazy {
        TranslationRepository.shared(context.applicationContext)
    }
}


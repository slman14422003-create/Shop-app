package com.salman.herbalencyclopedia.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object Home : Screen("home")
    data object AllHerbs : Screen("all_herbs")
    data object Favorites : Screen("favorites")
    data object Settings : Screen("settings")
    data object Search : Screen("search")
    data object SemoAssistant : Screen("semo_assistant")
    data object Help : Screen("help")
    data object Support : Screen("support")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object Terms : Screen("terms")
    data object CategoryHerbs : Screen("category/{categoryId}/{categoryName}") {
        fun createRoute(categoryId: String, categoryName: String) = "category/$categoryId/${android.net.Uri.encode(categoryName)}"
    }
    data object HerbDetail : Screen("herb/{herbId}") { fun createRoute(herbId: String) = "herb/$herbId" }
    data object Login : Screen("login")
    data object Admin : Screen("admin")
    data object AdminEdit : Screen("admin/edit/{herbId}") { const val NEW = "new"; fun createRoute(herbId: String) = "admin/edit/$herbId" }
    data object AdminTools : Screen("admin/tools")
    data object AdminUpdate : Screen("admin/update")
    // ── فصل "الحالات المدرَّبة" و"تعلّم سيمو الذاتي" في شاشتين مستقلتين
    // بدل بطاقتين ضمن قائمة "أدوات الإدارة" الطويلة نفسها — كل واحدة
    // تُفتح الآن بزر خاص بها (نفس مبدأ AdminUpdate) بدل الظهور دوماً
    // مدمجتين مع بقية الأدوات. ──
    data object AdminTrainedCases : Screen("admin/trained_cases")
    data object AdminSemoLearning : Screen("admin/semo_learning")
    data object AdminSemoWeb : Screen("admin/semo_web")

    // ── الخلطات (Blends) ──────────────────────────────────────────────
    data object Blends : Screen("blends")
    data object BlendDetail : Screen("blend/{blendId}") { fun createRoute(blendId: String) = "blend/$blendId" }
    data object AdminEditBlend : Screen("admin/blend/{blendId}") { const val NEW = "new"; fun createRoute(blendId: String) = "admin/blend/$blendId" }

    // ── ملاحظات المستخدمين (Feedback) ────────────────────────────────
    data object SendFeedback : Screen("feedback/{targetType}/{targetId}/{targetName}") {
        fun createRoute(targetType: String, targetId: String, targetName: String) =
            "feedback/$targetType/$targetId/${android.net.Uri.encode(targetName)}"
    }
    data object AdminFeedback : Screen("admin/feedback")
}

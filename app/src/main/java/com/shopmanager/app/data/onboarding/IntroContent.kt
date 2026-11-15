package com.shopmanager.app.data.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector

data class IntroFeature(
    val icon: ImageVector,
    val title: String,
    val body: String
)

data class IntroPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val points: List<String> = emptyList()
)

/**
 * ملاحظة إصدار واحدة. [id] رقم تسلسلي يزيد مع كل إصدار فيه ميزات جديدة (1، 2، 3...) —
 * مستقل عن versionCode. المستخدم يرى كل الإصدارات التي id أكبر من آخر id شاهده.
 * [minVersionCode] اختياري: لا تظهر الملاحظة قبل هذا البناء.
 */
data class ReleaseNotes(
    val id: Int,
    val title: String,
    val subtitle: String,
    val features: List<IntroFeature>,
    val minVersionCode: Long = 0L
)

/**
 * ===== كيف تضيف تحديثاً جديداً =====
 * أضف عنصراً جديداً في نهاية [releases] بـ id أكبر من السابق، واكتب ميزاته.
 * عند أول فتح بعد التحديث يرى المستخدم القديم هذه الميزات فقط، بحركة متتابعة.
 * المستخدم الجديد (أول تثبيت) يرى [welcomePages] فقط ولا يرى ملاحظات الإصدار.
 */
object IntroContent {

    val welcomePages: List<IntroPage> = listOf(
        IntroPage(
            icon = Icons.Default.Storefront,
            title = "أهلاً بك في إدارة المحل",
            body = "كل ما يحتاجه محلك في مكان واحد، بسيط وسريع.",
            points = listOf("الديون والعملاء", "النواقص والأسعار", "الملاحظات والتذكيرات")
        ),
        IntroPage(
            icon = Icons.Default.Groups,
            title = "ديون العملاء",
            body = "سجّل الديون والدفعات لكل عميل، وتابع من عليه أكثر.",
            points = listOf(
                "إضافة دين أو دفعة بلمسة واحدة",
                "الأكبر ديناً يظهر أولاً",
                "تذكير واتساب للمتأخرين عن السداد"
            )
        ),
        IntroPage(
            icon = Icons.Default.Inventory2,
            title = "قائمة النواقص",
            body = "قائمة مشتريات حيّة لما ينقص المحل، مرتبة ومنظمة.",
            points = listOf(
                "اختر الوحدة: كيلو، نص كيلو، ربع، لوقية",
                "نجمة للمواد الهامة لتبقى في الأعلى",
                "المواد مرتبة حسب المجلدات"
            )
        ),
        IntroPage(
            icon = Icons.Default.ManageSearch,
            title = "فحص النواقص الذكي",
            body = "يمر على موادك واحدة واحدة ويسألك: موجودة أم ناقصة؟",
            points = listOf(
                "يتعلم من عاداتك ويقترح عليك",
                "تأكيد سريع وانتقال للمادة التالية",
                "في النهاية تُضاف كل النواقص دفعة واحدة"
            )
        ),
        IntroPage(
            icon = Icons.Default.Sell,
            title = "الأسعار والمشاركة",
            body = "اكتب سعر الكيلو مرة واحدة، والتطبيق يحسب الباقي.",
            points = listOf(
                "السعر يُضرب تلقائياً بوزن كل مادة",
                "إجمالي القائمة يظهر فوراً",
                "شارك القائمة كصورة منسقة حسب المجلدات"
            )
        ),
        IntroPage(
            icon = Icons.Default.Lock,
            title = "آمن ومتزامن",
            body = "بياناتك محفوظة ومحمية على كل أجهزتك.",
            points = listOf(
                "قفل بالرقم السري أو البصمة",
                "مزامنة فورية بين الأجهزة",
                "نسخ احتياطي تلقائي"
            )
        )
    )

    val releases: List<ReleaseNotes> = listOf(
        ReleaseNotes(
            id = 1,
            title = "ما الجديد",
            subtitle = "تحسينات جديدة لتجربة أجمل وأسهل",
            features = listOf(
                IntroFeature(
                    Icons.Default.Home,
                    "شاشة رئيسية جديدة",
                    "تصميم أنظف: بحث في الأعلى، بطاقة الديون، وأزرار سريعة لأهم الأعمال."
                ),
                IntroFeature(
                    Icons.Default.Share,
                    "مشاركة أجمل",
                    "صورة القائمة صارت مرتبة حسب المجلدات مع ملخص الأوزان والإجمالي."
                ),
                IntroFeature(
                    Icons.Default.Tune,
                    "عدّاد أوضح",
                    "الرقم في المنتصف والوحدة تحته، وعند اختيار نص كيلو تختفي الكمية."
                ),
                IntroFeature(
                    Icons.Default.Sell,
                    "حفظ الأسعار من البحث",
                    "زر الحفظ صار داخل شريط البحث ويُظهر عدد الأسعار المعدّلة."
                ),
                IntroFeature(
                    Icons.Default.AutoAwesome,
                    "شاشة بداية وشرح للميزات",
                    "سبلاش جديد، وشرح متتابع للمستخدم الجديد وللميزات عند كل تحديث."
                )
            )
        )
    )

    val latestReleaseId: Int get() = releases.maxOfOrNull { it.id } ?: 0
}

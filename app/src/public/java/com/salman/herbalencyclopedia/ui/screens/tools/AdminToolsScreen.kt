package com.salman.herbalencyclopedia.ui.screens.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.salman.herbalencyclopedia.data.ai.AiConfig
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.model.UnansweredQuestion
import com.salman.herbalencyclopedia.data.model.WebHerb

// نسخة "public" — راجع تعليق AdminListScreen.kt (مجلد ui/screens/admin) للتفاصيل
// الكاملة. نفس التوقيع تماماً (بنفس القيم الافتراضية) حتى يبقى استدعاء هذه
// الدالة في HerbalNavGraph.kt مطابقاً حرفياً بين النكهتين.
@Composable
fun AdminToolsScreen(
    categories: List<Category>,
    herbs: List<Herb>,
    blends: List<com.salman.herbalencyclopedia.data.model.Blend> = emptyList(),
    onMigrateImages: ((Boolean, String?) -> Unit) -> Unit = {},
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onUpdateCategory: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onDeleteAllHerbs: ((Boolean, String?) -> Unit) -> Unit,
    onDeleteAllData: ((Boolean, String?) -> Unit) -> Unit,
    onTestConnection: ((Boolean, String?) -> Unit) -> Unit,
    onClearFavorites: () -> Unit,
    onRestoreBackup: (String, (Boolean, String?) -> Unit) -> Unit,
    onUpdateSettingsClick: () -> Unit,
    aiSimilarityThreshold: Float = AiConfig.defaultSimilarityThreshold.toFloat(),
    aiSearchThreshold: Float = AiConfig.defaultSearchThreshold.toFloat(),
    aiExtraStopWords: Set<String> = emptySet(),
    onSetAiSimilarityThreshold: (Float) -> Unit = {},
    onSetAiSearchThreshold: (Float) -> Unit = {},
    onSetAiExtraStopWords: (Set<String>) -> Unit = {},
    onResetAiSettings: () -> Unit = {},
    onOpenTrainedCases: () -> Unit = {},
    onOpenSemoLearning: () -> Unit = {},
    onOpenSemoWeb: () -> Unit = {},
    aiOnlineEnabled: Boolean = AiConfig.defaultOnlineEnabled,
    aiOnlineApiKey: String = "",
    aiOnlineModel: String = AiConfig.defaultOnlineModel,
    aiOnlineBaseUrl: String = AiConfig.defaultOnlineBaseUrl,
    onSetAiOnlineEnabled: (Boolean) -> Unit = {},
    onSetAiOnlineApiKey: (String) -> Unit = {},
    onSetAiOnlineModel: (String) -> Unit = {},
    onSetAiOnlineBaseUrl: (String) -> Unit = {},
    onResetAiOnlineSettings: () -> Unit = {},
    deviceStatsLoading: Boolean = false,
    deviceStatsTotal: Long? = null,
    deviceStatsActive7d: Long? = null,
    deviceStatsError: String? = null,
    onRefreshDeviceStats: () -> Unit = {},
    deviceGrowthLoading: Boolean = false,
    deviceGrowth: List<Pair<String, Int>> = emptyList(),
    deviceGrowthError: String? = null,
    onRefreshDeviceGrowth: () -> Unit = {},
    crashLogsLoading: Boolean = false,
    crashLogs: List<com.salman.herbalencyclopedia.data.repository.CrashLog> = emptyList(),
    crashLogsError: String? = null,
    onRefreshCrashLogs: () -> Unit = {},
    onClearCrashLogs: ((Boolean, String?) -> Unit) -> Unit = { _ -> },
    maintenanceEnabled: Boolean = false,
    maintenanceMessage: String = "",
    onSaveMaintenanceConfig: (Boolean, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    LaunchedEffect(Unit) { onBack() }
}

// نسخة "public" لشاشتي "الحالات المدرَّبة" و"تعلّم سيمو الذاتي" — نفس
// التوقيع تماماً الموجود في نسخة "full" (راجع AdminToolsScreen.kt هناك)
// حتى يبقى استدعاؤهما في HerbalNavGraph.kt مطابقاً حرفياً بين النكهتين.
// كانتا مفقودتين هنا فقط، وهذا هو سبب فشل compilePublicReleaseKotlin.
@Composable
fun SemoTrainedCasesScreen(
    onBack: () -> Unit,
    synonyms: Map<String, String>,
    trainedExamples: List<TrainedExample>,
    trainedThreshold: Float,
    onSynonymsChange: (Map<String, String>) -> Unit,
    onTrainedExamplesChange: (List<TrainedExample>) -> Unit,
    onTrainedThresholdChange: (Float) -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
fun SemoSelfLearningScreen(
    onBack: () -> Unit,
    autoLearnedExamples: List<TrainedExample>,
    autoLearnEnabled: Boolean,
    trainedExamples: List<TrainedExample>,
    onAutoLearnedExamplesChange: (List<TrainedExample>) -> Unit,
    onAutoLearnEnabledChange: (Boolean) -> Unit,
    onDemoteLearnedExample: (String) -> Unit,
    onPromoteToTrained: (TrainedExample) -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

@Composable
fun SemoWebKnowledgeScreen(
    onBack: () -> Unit,
    webHerbs: List<WebHerb>,
    unanswered: List<UnansweredQuestion>,
    onPromote: (WebHerb) -> Unit,
    onApprove: (String) -> Unit,
    onDeleteWebHerb: (String) -> Unit,
    onDeleteUnanswered: (String) -> Unit
) {
    LaunchedEffect(Unit) { onBack() }
}

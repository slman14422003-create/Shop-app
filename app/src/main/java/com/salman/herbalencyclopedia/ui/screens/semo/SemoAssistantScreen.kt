package com.salman.herbalencyclopedia.ui.screens.semo

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.salman.herbalencyclopedia.ui.util.tr
import com.salman.herbalencyclopedia.data.ai.AiConfig
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.OnlineAssistant
import com.salman.herbalencyclopedia.data.ai.SemoReplyPolicy
import com.salman.herbalencyclopedia.data.ai.TrainedExample
import com.salman.herbalencyclopedia.data.ai.local.LocalLlmManager
import com.salman.herbalencyclopedia.data.ai.local.LocalRephraser
import com.salman.herbalencyclopedia.data.search.Grounding
import com.salman.herbalencyclopedia.data.search.HybridSearch
import com.salman.herbalencyclopedia.data.search.NameSuggester
import com.salman.herbalencyclopedia.data.search.SearchTuning
import com.salman.herbalencyclopedia.data.search.SourceRef
import com.salman.herbalencyclopedia.ui.theme.LocalPerformanceMode
import com.salman.herbalencyclopedia.data.ai.hasActiveInternetConnection
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.model.WebHerb
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.theme.AppMotion
import com.salman.herbalencyclopedia.ui.theme.rememberSmoothMotionAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ATTACHED = 3

/**
 * عبارات "سيمو يفكّر" المعروضة كفقاعة حقيقية (لا مجرد نقاط متحرّكة) قبل
 * البحث الفعلي في الموسوعة — انظر [HerbAssistant.needsThinking] وsendMessage
 * أدناه. عشوائية الاختيار حتى لا تتكرر نفس الجملة بالضبط في كل مرة.
 */
private val thinkingPhrasePool = listOf(
    "أممم، حسناً، دعني أفكر وأبحث في الموسوعة 🌿",
    "لحظة، سأبحث في بيانات الموسوعة عن أفضل إجابة لك 🔎",
    "حسناً، دعني أراجع كل ما لدي عن هذا في الموسوعة 🌿",
    "طيب، سأبحث بعمق في الموسوعة قبل أن أجيبك 🔎"
)

/**
 * [learnable] = هل هذا الرد ناتج عن بحث حر فعلي (وليس ترحيباً/شكراً/حالة
 * مدرَّبة مسبقاً)؟ في هذه الحالة فقط تُعرض أزرار تقييم 👍/👎 تحت الفقاعة،
 * وتقييم المستخدم هو ما يغذّي "تعلّم سيمو الذاتي" (انظر [HerbAssistant.recordFeedback]).
 * [feedback] = null قبل أي تقييم، true بعد 👍، false بعد 👎 (لإخفاء الأزرار
 * واستبدالها برسالة تأكيد قصيرة بعد أول تقييم).
 */
private data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val sourceQuestion: String = "",
    val learnable: Boolean = false,
    val feedback: Boolean? = null,
    // ── فقاعة "أفكّر وأبحث" المؤقتة (انظر sendMessage): تُعرض فور استلام
    // سؤال يحتاج بحثاً فعلياً ([HerbAssistant.needsThinking])، بنفس مُعرِّف
    // [id] الذي يُستبدَل محتواه لاحقاً بالإجابة الحقيقية بمجرد جهوزها —
    // بدل إضافة فقاعة جديدة منفصلة، فيبدو الأمر كأن سيمو "أكمل تفكيره" في
    // نفس الفقاعة لا أنه أرسل رسالتين. ──
    val isPlaceholder: Boolean = false,
    val sources: List<SourceRef> = emptyList(),
    val fullText: String? = null,
    val expanded: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val streaming: Boolean = false,
    val id: Long = System.nanoTime()
)

/**
 * سيمو — واجهة دردشة ذكاء اصطناعي كاملة الشاشة، وليست "شاشة مقارنة".
 * الدردشة هي الواجهة الأساسية دائماً: يمكن سؤال سيمو عن أي عشبة موجودة في
 * الموسوعة مباشرة بذكر اسمها في السؤال، دون أي حاجة لاختيار شيء مسبقاً.
 * إرفاق عشبة أو أكثر (اختياري تماماً، عبر زر "إرفاق" في الأعلى) يفيد فقط
 * في تضييق نطاق الإجابة على أعشاب بعينها؛ أما "المقارنة" المنظّمة بين أكثر
 * من عشبة فلا تحدث إطلاقاً إلا إذا طلبها المستخدم صراحة — إما بإرفاق
 * عشبتين أو أكثر، أو بذكر اسمَي عشبتين معاً في نص السؤال نفسه (مثل: "قارن
 * بين الزنجبيل والقرفة"). لا يوجد أي شرط لإضافة عشبتين لتفعيل الدردشة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemoAssistantScreen(
    herbs: List<Herb>,
    onBack: () -> Unit,
    // ── توسيع "صلاحيات" سيمو ليشمل كل الموسوعة: يمرَّر هنا حيّاً من
    // UiState.blends (نفس مصدر شاشة الخلطات)، فيدخل ضمن نطاق البحث الحر
    // العام تلقائياً — دون أي تأثير على منطق مقارنة الأعشاب المحدَّدة، الذي
    // يبقى كما هو تماماً. ──
    blends: List<Blend> = emptyList(),
    // ── تعلّم سيمو الذاتي: قائمة الحالات المتعلَّمة تُمرَّر حيّة من
    // DataStore عبر HerbalNavGraph، وأي تقييم 👍/👎 هنا يُحدّثها فوراً عبر
    // onAutoLearnedExamplesChange لتُحفظ وتُطبَّق في كل محادثة قادمة محلياً
    // على هذا الجهاز. ──
    onAutoLearnedExamplesChange: (List<TrainedExample>) -> Unit = {},
    // ── مزامنة التعلّم بين الأجهزة: يُستدعى بنفس لحظة التقييم (helpful=true
    // بعد 👍، false بعد 👎) بجانب onAutoLearnedExamplesChange أعلاه، منفصلاً
    // عمداً عنه — الحفظ المحلي عبر onAutoLearnedExamplesChange مضمون
    // ومستقل دوماً حتى بلا إنترنت، بينما هذا الاستدعاء يرفع نفس الحالة
    // (fire-and-forget) إلى مجموعة "semo_learned" المشتركة على Firestore
    // (انظر AppViewModel.contributeSemoLearning/demoteSemoLearning) كي
    // تستفيد منها بقية الأجهزة دون تعلّمها من الصفر. ──
    onFeedbackRecorded: (question: String, answer: String, helpful: Boolean) -> Unit = { _, _, _ -> },
    // ── معرفة الإنترنت: أعشاب/أسماء بديلة تعلّمها سيمو سابقاً (تُدمَج بالموسوعة محلياً)، وإشعارات
    // تعلّم عشبة جديدة أو سؤال بلا جواب (تُرفَع لـFirestore عبر AppViewModel). ──
    webHerbs: List<WebHerb> = emptyList(),
    onWebHerbLearned: (WebHerb) -> Unit = {},
    onUnanswered: (String) -> Unit = {},
    onOpenHerb: (String) -> Unit = {}
) {
    val allHerbs = remember(herbs, webHerbs) { herbs + webHerbs.mapNotNull { it.toHerb() } }
    var attachedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val attached = attachedIds.mapNotNull { id -> herbs.firstOrNull { it.id == id } }
    var showAttachPicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val smoothAllowed = rememberSmoothMotionAllowed()
    val ecoMode = !LocalPerformanceMode.current.isHighQuality

    DisposableEffect(Unit) {
        onDispose { LocalLlmManager.release() }
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isThinking by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // ── ذاكرة المحادثة: آخر عشبة/أعشاب كانت "محور تركيز" الحديث فعلياً
    // (سواء عبر إرفاق يدوي أو ذكر اسم صريح في السؤال). تُستخدم لاحقاً في
    // sendMessage عندما يسأل المستخدم سؤال متابعة واضح (انظر
    // [HerbAssistant.isFollowUpQuestion]) دون تكرار اسم العشبة، فيبقى سيمو
    // "متذكّراً" موضوع الحديث بدل السقوط في بحث عام بكل الموسوعة فقط لأن
    // الاسم لم يُكرَّر. تُصفَّر تلقائياً حين ينتقل الحديث فعلياً لسؤال عام
    // جديد لا علاقة له بالعشبة السابقة (انظر منطق التحديث في sendMessage).
    var focusHerbs by remember { mutableStateOf<List<Herb>>(emptyList()) }

    fun sendMessage(raw: String) {
        val question = raw.trim()
        if (question.isBlank() || isThinking) return
        val priorTurns = messages
            .filter { !it.isPlaceholder && it.text.isNotBlank() }
            .map { OnlineAssistant.ChatTurn(it.isUser, if (it.isUser) it.text else (it.fullText ?: it.text)) }
        messages = messages + ChatMessage(question, isUser = true)
        inputText = ""
        isThinking = true
        scope.launch {
            delay((400L..750L).random())

            // ── "أفكّر قبل أن أجيب": بنفس شروط `if` الأولى التي يستخدمها
            // [HerbAssistant.answerDetailed] لتمييز الردود الفورية الجاهزة
            // (ترحيب/شكر/حالة مدرَّبة) عن الأسئلة التي تحتاج بحثاً فعلياً —
            // فقط النوع الثاني يستحق فقاعة "دعني أفكر وأبحث" قبل الإجابة،
            // تماماً كما يوضّح مساعد جيد خطته قبل تنفيذها بدل إجابة تظهر
            // فجأة بلا سياق.
            val showThinkingBubble = withContext(Dispatchers.Default) {
                HerbAssistant.needsThinking(question)
            }
            var thinkingMessageId: Long? = null
            if (showThinkingBubble) {
                val placeholder = ChatMessage(
                    text = thinkingPhrasePool.random(),
                    isUser = false,
                    isPlaceholder = true
                )
                thinkingMessageId = placeholder.id
                messages = messages + placeholder
            }
            // ═══ المسار الجديد: الموسوعة أولاً، ثم الإنترنت لتعليم سيمو ═══
            // ١) كل جواب يُبنى أولاً محلياً من الموسوعة (مع أعشاب الإنترنت التي تعلّمها سيمو سابقاً).
            // ٢) فقط إن لم تُوجد العشبة محلياً (أو كان التطابق المحلي ضعيفاً) والاتصال متاح،
            //    يبحث Gemini في الإنترنت، فتُحفَظ العشبة في "semo_web_herbs" وتصير متاحة أوفلاين.
            // ٣) لا يُحفَظ أي جواب من Gemini كحالة مدرَّبة ذاتياً بعد الآن (كان يلوّث الموسوعة المشتركة).
            val effectiveQuestion = withContext(Dispatchers.Default) {
                val extra = webHerbs.filter { it.isAliasOnly }
                    .filter { w -> w.allNames.any { n -> n.length >= 3 && question.contains(n, ignoreCase = true) } }
                    .map { it.aliasOf }.distinct()
                if (extra.isEmpty()) question else question + " " + extra.joinToString(" ")
            }

            val mentioned = if (attached.isEmpty()) {
                withContext(Dispatchers.Default) { HerbAssistant.relevantHerbs(effectiveQuestion, allHerbs) }
            } else emptyList()
            val hasExplicitHerb = attached.isNotEmpty() || mentioned.isNotEmpty()

            // سؤال متابعة قصير دون ذكر العشبة ("طيب شو أضرارها؟") يعيد استخدام آخر عشبة تحدّثنا عنها.
            val useFollowUp = showThinkingBubble && !hasExplicitHerb && focusHerbs.isNotEmpty() &&
                withContext(Dispatchers.Default) { HerbAssistant.isFollowUpQuestion(effectiveQuestion) }

            val contextHerbs: List<Herb>
            val contextBlends: List<Blend>
            val allowCompare: Boolean
            when {
                attached.isNotEmpty() -> { contextHerbs = attached; contextBlends = emptyList(); allowCompare = true }
                mentioned.isNotEmpty() -> { contextHerbs = mentioned; contextBlends = emptyList(); allowCompare = true }
                useFollowUp -> { contextHerbs = focusHerbs; contextBlends = emptyList(); allowCompare = true }
                else -> { contextHerbs = allHerbs; contextBlends = blends; allowCompare = false }
            }

            if (showThinkingBubble) {
                focusHerbs = when {
                    hasExplicitHerb -> contextHerbs
                    useFollowUp -> focusHerbs
                    else -> emptyList()
                }
            }

            var groundQuestion = effectiveQuestion
            var groundRestrict: List<Herb>? =
                if (attached.isNotEmpty() || mentioned.isNotEmpty() || useFollowUp) contextHerbs else null

            // الجواب المحلي أولاً (من الموسوعة مباشرة).
            var reply = withContext(Dispatchers.Default) {
                HerbAssistant.answerDetailed(effectiveQuestion, contextHerbs, allowCompare, contextBlends)
            }

            // هل العشبة غير موجودة محلياً؟ (لا اسم عشبة ولا متابعة، والتطابق ضعيف أو السؤال يبدو استعلام عشبة)
            var weakLocal = false
            var shouldSearchWeb = false
            if (showThinkingBubble && !hasExplicitHerb && !useFollowUp) {
                val flags = withContext(Dispatchers.Default) {
                    if (HerbAssistant.hasTrainedMatch(effectiveQuestion) || HerbAssistant.isSafetyCritical(question)) {
                        false to false
                    } else {
                        val strong = HerbAssistant.isLocalAnswerStrong(
                            HerbAssistant.localSearchStrength(effectiveQuestion, allHerbs, blends)
                        )
                        (!strong) to (!strong || HerbAssistant.looksLikeHerbLookup(effectiveQuestion))
                    }
                }
                weakLocal = flags.first
                shouldSearchWeb = flags.second
            }

            var answeredFromWeb = false
            var understoodLocally = false
            if (shouldSearchWeb && AiConfig.onlineEnabled && AiConfig.onlineApiKey.isNotBlank() &&
                withContext(Dispatchers.IO) { hasActiveInternetConnection(context) }
            ) {
                val known = allHerbs.map { it.name }

                // الخطوة ٢ — فهم السؤال أولاً: تحويل العامية/الأخطاء/الأسماء البديلة إلى أسماء دقيقة
                // من الموسوعة وصياغة قياسية، ثم الجواب دائماً من نصوص الموسوعة محلياً.
                val earlyGrounding = withContext(Dispatchers.Default) {
                    HybridSearch.rank(effectiveQuestion, allHerbs, groundRestrict)
                }
                val understood = if (earlyGrounding.bestScore < SearchTuning.onlineExpandThreshold) {
                    withContext(Dispatchers.IO) { OnlineAssistant.understandQuestion(question, known) }
                } else null
                if (understood != null) {
                    val matchedHerbs = understood.herbs.mapNotNull { m ->
                        val s = m.trim()
                        allHerbs.firstOrNull { it.name.trim().equals(s, ignoreCase = true) }
                            ?: s.takeIf { it.length >= 3 }?.let { part -> allHerbs.firstOrNull { it.name.contains(part, ignoreCase = true) } }
                    }.distinctBy { it.id }.take(3)
                    val rewritten = understood.rewritten.ifBlank { question }
                    if (matchedHerbs.isNotEmpty()) {
                        focusHerbs = matchedHerbs
                        reply = withContext(Dispatchers.Default) {
                            HerbAssistant.answerDetailed(rewritten, matchedHerbs, matchedHerbs.size > 1, emptyList())
                        }
                        groundQuestion = rewritten
                        groundRestrict = matchedHerbs
                        understoodLocally = true
                    } else if (weakLocal && rewritten != question) {
                        val strength = withContext(Dispatchers.Default) {
                            HerbAssistant.localSearchStrength(rewritten, allHerbs, blends)
                        }
                        if (HerbAssistant.isLocalAnswerStrong(strength)) {
                            reply = withContext(Dispatchers.Default) {
                                HerbAssistant.answerDetailed(rewritten, allHerbs, false, blends)
                            }
                            groundQuestion = rewritten
                            groundRestrict = null
                            understoodLocally = true
                        }
                    }
                }

              if (!understoodLocally) {
                val lookup = withContext(Dispatchers.IO) { OnlineAssistant.lookupHerb(question, known) }
                when (lookup) {
                    is OnlineAssistant.WebLookup.Found -> {
                        val matched = lookup.existingMatch?.let { m ->
                            allHerbs.firstOrNull { it.name.trim().equals(m.trim(), ignoreCase = true) }
                        }
                        if (matched != null) {
                            // الاسم المسؤول عنه هو اسم بديل لعشبة موجودة: يتعلّمه سيمو ويجيب من الموسوعة مباشرة.
                            val aliasNames = (listOf(lookup.herb.name) + lookup.herb.aliases)
                            onWebHerbLearned(
                                WebHerb(
                                    id = OnlineAssistant.webHerbId("alias_" + lookup.herb.name),
                                    name = lookup.herb.name,
                                    aliases = aliasNames.drop(1),
                                    aliasOf = matched.name
                                )
                            )
                            focusHerbs = listOf(matched)
                            reply = withContext(Dispatchers.Default) {
                                HerbAssistant.answerDetailed(question, listOf(matched), true, emptyList())
                            }
                            answeredFromWeb = true
                        } else {
                            val learned = lookup.herb
                            val asHerb = learned.toHerb()
                            if (asHerb != null) {
                                onWebHerbLearned(learned)
                                focusHerbs = listOf(asHerb)
                                val fromWebHerb = withContext(Dispatchers.Default) {
                                    HerbAssistant.answerDetailed(question, listOf(asHerb), true, emptyList())
                                }
                                reply = HerbAssistant.AssistantReply(
                                    "🌐 لم أجد «${learned.name}» في الموسوعة، فبحثت عنها في الإنترنت وحفظتها " +
                                        "عندي (بانتظار مراجعة المطوّر):\n\n" + fromWebHerb.text,
                                    false
                                )
                                answeredFromWeb = true
                            }
                        }
                    }
                    OnlineAssistant.WebLookup.NotHerb -> {
                        // ليست عشبة محدّدة. إن كان الجواب المحلي ضعيفاً فقط: Gemini يجيب من أقرب أعشاب الموسوعة.
                        if (weakLocal) {
                            val generalGrounding = withContext(Dispatchers.Default) {
                                HybridSearch.rank(effectiveQuestion, allHerbs, null)
                            }
                            if (!generalGrounding.isEmpty) {
                                val general = OnlineAssistant.answerGrounded(question, generalGrounding.points, priorTurns)
                                if (general != null && general.text.isNotBlank()) {
                                    reply = general
                                    answeredFromWeb = true
                                }
                            }
                        }
                    }
                    null -> Unit // فشل الاتصال: يبقى الجواب المحلي بلا أي رسالة خطأ.
                }
              }
            }

            // سؤال لم نجد له جواباً لا محلياً ولا عبر الإنترنت: يُسجَّل للمطوّر ليعرف ما ينقص الموسوعة.
            // الخطوة ٣ — تدقيق أخير على أي رد (تنبيه طبي للحالات الحساسة) قبل عرضه.
            val safetyCritical = withContext(Dispatchers.Default) {
                HerbAssistant.isSafetyCritical(question) || HerbAssistant.hasTrainedMatch(effectiveQuestion)
            }
            val compareMode = allowCompare && (attached.size >= 2 || mentioned.size >= 2)
            var finalGrounding = Grounding.EMPTY
            if (showThinkingBubble && !safetyCritical && !answeredFromWeb) {
                finalGrounding = withContext(Dispatchers.Default) {
                    HybridSearch.rank(groundQuestion, allHerbs, groundRestrict)
                }
                val groundedEnough = !finalGrounding.isEmpty && (!weakLocal || understoodLocally)
                if (groundedEnough && !compareMode) {
                    val points = finalGrounding.points
                    var rephrased = false
                    if (LocalLlmManager.isUsable(context, ecoMode)) {
                        val placeholderId = thinkingMessageId
                        var lastUiUpdate = 0L
                        val localText = LocalRephraser.rephrase(groundQuestion, points) { partial ->
                            val now = System.currentTimeMillis()
                            if (smoothAllowed && placeholderId != null && partial.isNotBlank() && now - lastUiUpdate >= 80L) {
                                lastUiUpdate = now
                                messages = messages.map { msg ->
                                    if (msg.id == placeholderId) msg.copy(text = partial, isPlaceholder = false, streaming = true) else msg
                                }
                            }
                        }
                        if (localText != null) {
                            reply = HerbAssistant.AssistantReply(localText, false)
                            rephrased = true
                        }
                    }
                    if (!rephrased && AiConfig.onlineEnabled && AiConfig.onlineApiKey.isNotBlank() &&
                        withContext(Dispatchers.IO) { hasActiveInternetConnection(context) }
                    ) {
                        val online = OnlineAssistant.answerGrounded(groundQuestion, points, priorTurns)
                        if (online != null && online.text.isNotBlank()) {
                            reply = online
                            rephrased = true
                        }
                    }
                }
            }

            reply = withContext(Dispatchers.Default) { HerbAssistant.verifyReply(question, reply) }

            if (weakLocal && !answeredFromWeb && !understoodLocally) onUnanswered(question)

            val shaped = if (showThinkingBubble && !safetyCritical) {
                SemoReplyPolicy.shape(reply.text)
            } else {
                SemoReplyPolicy.Shaped(reply.text, null)
            }
            val refusal = reply.text.contains("لم أجد") || reply.text.contains("لست متأكد")
            val replySources = if (showThinkingBubble && !safetyCritical && !refusal && !finalGrounding.isEmpty) {
                finalGrounding.sources()
            } else emptyList()
            val nameSuggestions = if (weakLocal && !answeredFromWeb && !understoodLocally && !hasExplicitHerb) {
                withContext(Dispatchers.Default) { NameSuggester.suggest(question, allHerbs, 3) }
            } else emptyList()

            messages = if (thinkingMessageId != null) {
                messages.map { msg ->
                    if (msg.id == thinkingMessageId) {
                        msg.copy(
                            text = shaped.shortText,
                            fullText = shaped.fullText,
                            sources = replySources,
                            suggestions = nameSuggestions,
                            sourceQuestion = question,
                            learnable = reply.learnable,
                            isPlaceholder = false,
                            streaming = false
                        )
                    } else msg
                }
            } else {
                messages + ChatMessage(
                    text = shaped.shortText,
                    isUser = false,
                    fullText = shaped.fullText,
                    sources = replySources,
                    suggestions = nameSuggestions,
                    sourceQuestion = question,
                    learnable = reply.learnable
                )
            }
            isThinking = false
        }
    }

    fun expandMessage(messageId: Long) {
        messages = messages.map { msg ->
            val full = msg.fullText
            if (msg.id == messageId && full != null) msg.copy(text = full, expanded = true) else msg
        }
    }

    fun followQuestionFor(original: String, herbName: String): String {
        val fields = HerbAssistant.intentFields(original)
        return when {
            "الاستخدام" in fields -> "كيف أستخدم $herbName؟"
            "التحذيرات" in fields || "الأضرار" in fields -> "ما تحذيرات وأضرار $herbName؟"
            else -> "ما فوائد $herbName؟"
        }
    }

    fun rateMessage(messageId: Long, helpful: Boolean) {
        val target = messages.firstOrNull { it.id == messageId } ?: return
        scope.launch {
            // نفس السبب أعلاه: [HerbAssistant.recordFeedback] يعيد تحليل نص
            // السؤال والرد الحاليين لمطابقتهما مع الحالات المتعلَّمة سابقاً —
            // حساب أخف من [answerDetailed] لكنه يبقى تحليل نص، فيُفضَّل عدم
            // تنفيذه على خيط الواجهة كذلك.
            val updated = withContext(Dispatchers.Default) {
                HerbAssistant.recordFeedback(target.sourceQuestion, target.text, helpful)
            }
            onAutoLearnedExamplesChange(updated)
            onFeedbackRecorded(target.sourceQuestion, target.text, helpful)
            messages = messages.map { if (it.id == messageId) it.copy(feedback = helpful) else it }
        }
    }

    LaunchedEffect(messages.size, isThinking) {
        val lastIndex = messages.size - 1 + if (isThinking) 1 else 0
        if (lastIndex >= 0) chatListState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistantAvatar(size = 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("سيمو المساعد"))
                }
            }, navigationIcon = {
                GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) }
            }, actions = {
                if (messages.isNotEmpty()) {
                    GlassIconButton(onClick = {
                        val shareText = buildChatShareText(messages)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "محادثة مع سيمو المساعد")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "مشاركة المحادثة"))
                    }) { Icon(Icons.Filled.IosShare, tr("مشاركة المحادثة")) }
                }
                TextButton(onClick = { showAttachPicker = true }) {
                    Icon(Icons.Filled.Eco, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (attached.isEmpty()) tr("إرفاق") else "${attached.size}")
                }
            })
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            // شريط الأعشاب المرفقة: اختياري بالكامل، يظهر فقط عند وجود إرفاق
            AnimatedVisibility(visible = attached.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attached, key = { it.id }) { herb ->
                        InputChip(
                            selected = true,
                            onClick = { attachedIds = attachedIds - herb.id },
                            label = { Text(herb.name) },
                            trailingIcon = { Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // ── مؤشر "ذاكرة المحادثة": يظهر فقط حين لا يوجد إرفاق يدوي (ذاك
            // يظهر أصلاً في الشريط أعلاه) وتوجد فعلاً عشبة/أعشاب محفوظة من
            // سؤال سابق يستخدمها سيمو تلقائياً للمتابعة (انظر focusHerbs في
            // sendMessage) — حتى يفهم المستخدم لماذا أجاب سيمو عن عشبة لم
            // يذكرها في آخر سؤال، ويملك خياراً صريحاً لمسح هذا التركيز.
            AnimatedVisibility(visible = attached.isEmpty() && focusHerbs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { focusHerbs = emptyList() },
                        label = { Text(tr("🧠 يتابع الحديث عن: ${focusHerbs.joinToString(" و") { it.name }}")) },
                        trailingIcon = { Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !isThinking) {
                    WelcomeState(
                        attached = attached,
                        allHerbs = herbs,
                        onSuggestionClick = { sendMessage(it) }
                    )
                } else {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubble(
                                message,
                                modifier = Modifier.animateItem(),
                                onRate = { helpful -> rateMessage(message.id, helpful) },
                                onExpand = { expandMessage(message.id) },
                                onOpenHerb = onOpenHerb,
                                onSuggestion = { name -> sendMessage(followQuestionFor(message.sourceQuestion, name)) }
                            )
                        }
                        if (isThinking) {
                            item(key = "typing") { TypingBubble(modifier = Modifier.animateItem()) }
                        }
                    }

                    // زر "السكرول للأسفل" لم يكن موجوداً أصلاً: عند التمرير
                    // لأعلى لقراءة رسائل قديمة في محادثة طويلة، لا توجد أي
                    // وسيلة للعودة السريعة لآخر رسالة سوى السحب اليدوي حتى
                    // النهاية. يظهر الآن فقط عندما يكون آخر عنصر في القائمة
                    // غير ظاهر فعلياً على الشاشة (المستخدم مبتعد عن الأسفل)،
                    // ويختفي تلقائياً بمجرد الوصول لآخر رسالة — بدل زر ثابت
                    // دائم الظهور بلا فائدة حين تكون المحادثة قصيرة أصلاً.
                    val totalItems by remember { derivedStateOf { chatListState.layoutInfo.totalItemsCount } }
                    val lastVisibleIndex by remember {
                        derivedStateOf { chatListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    }
                    val showScrollToBottom = totalItems > 0 && lastVisibleIndex < totalItems - 1

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToBottom,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val target = messages.size - 1 + if (isThinking) 1 else 0
                                    if (target >= 0) chatListState.animateScrollToItem(target)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("النزول لآخر رسالة"))
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (attached.isEmpty()) tr("اسأل سيمو عن أي عشبة...") else tr("اسأل عن ${attached.joinToString(" أو ") { it.name }}...")) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) })
                )
                // زر إرسال بلون الهوية الصريح عند وجود نص، ورمادي هادئ حين يكون
                // معطّلاً — بدل زجاج باهت لا يُفرَّق فيه بين الحالتين.
                val canSend = inputText.isNotBlank() && !isThinking
                FilledIconButton(
                    onClick = { sendMessage(inputText) },
                    enabled = canSend,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, tr("إرسال"))
                }
            }
        }
    }

    if (showAttachPicker) {
        AttachHerbsDialog(
            herbs = herbs,
            initiallySelected = attachedIds.toSet(),
            onDismiss = { showAttachPicker = false },
            onConfirm = { ids ->
                attachedIds = ids.toList()
                showAttachPicker = false
            }
        )
    }
}

/**
 * حالة الترحيب الأولى قبل أي رسالة: تشرح لسيمو نفسه بإيجاز وتعرض اقتراحات
 * سريعة جاهزة للنقر، مبنية على ما هو مرفق فعلياً (أو عيّنة من الموسوعة إن
 * لم يُرفق شيء) دون إجبار المستخدم على أي اختيار مسبق.
 */
@Composable
private fun WelcomeState(attached: List<Herb>, allHerbs: List<Herb>, onSuggestionClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AssistantAvatar(size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(tr("أهلاً، أنا سيمو 👋"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("اسألني عن أي عشبة في الموسوعة، أو اطلب مقارنة بين أكثر من عشبة مباشرة داخل سؤالك — بلا اتصال إنترنت وبلا أي حظر على الإجابة."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        val suggestions = remember(attached, allHerbs) { HerbAssistant.quickSuggestions(attached, allHerbs) }
        // عرض موحّد كامل لكل اقتراح بدل بطاقات بعروض مختلفة حسب طول النص.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestions.forEach { suggestion ->
                SuggestionCard(suggestion) { onSuggestionClick(suggestion) }
            }
        }
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttachHerbsDialog(
    herbs: List<Herb>,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp)) {
                Text(tr("إرفاق أعشاب (اختياري)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    tr("يفيد إرفاق عشبة أو أكثر (حتى $MAX_ATTACHED) في تضييق إجابات سيمو، ويمكّنك من طلب مقارنة صريحة بينها. ليس شرطاً لبدء الدردشة."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(herbs, key = { it.id }) { herb ->
                        val isSelected = herb.id in selected
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selected = when {
                                    isSelected -> selected - herb.id
                                    selected.size < MAX_ATTACHED -> selected + herb.id
                                    else -> selected
                                }
                            },
                            label = { Text(herb.name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { selected = emptySet() }) { Text(tr("مسح الكل")) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onDismiss) { Text(tr("إلغاء")) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onConfirm(selected) }) { Text(tr("تم")) }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onRate: (Boolean) -> Unit = {},
    onExpand: () -> Unit = {},
    onOpenHerb: (String) -> Unit = {},
    onSuggestion: (String) -> Unit = {}
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                AssistantAvatar(size = 28.dp)
                Spacer(Modifier.width(6.dp))
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ),
                color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    // الأهم هنا: message.text هو رد سيمو الفعلي (نص ديناميكي
                    // يُبنى محلياً من بيانات العشبة، وليس من القاموس الثابت)،
                    // فيمر عبر ترجمة جوجل الحيّة تلقائياً (وليس القاموس) عند
                    // اختيار الإنجليزية — هذا هو ما يجعل ردود سيمو نفسها
                    // مترجمة أيضاً وليس فقط أزرار الشاشة.
                    tr(message.text),
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .alpha(if (message.isPlaceholder) 0.75f else 1f),
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = if (message.isPlaceholder) {
                        MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    }
                )
            }
        }
        // ═══ ميزة أُبلغ عن طلبها: زر نسخ على ردود سيمو، كي يقدر أي مستخدم
        // ينسخ نص الرد كاملاً (مثلاً لمشاركته خارج التطبيق) ═══
        // يظهر على كل رد فعلي من سيمو (لا رسائل المستخدم نفسه)، ولا يظهر
        // على فقاعة "أفكّر وأبحث..." المؤقتة (message.isPlaceholder) لأن
        // نصها ليس إجابة فعلية بعد وليس هناك ما يُنسخ فائدة منه.
        // ملاحظة عن التنبيه بعد النسخ: أندرويد 13 (API 33) فما فوق يعرض
        // تنبيهاً نظامياً تلقائياً عند أي نسخ للحافظة (والتطبيق مستهدَف هنا
        // لـ targetSdk 36، فوق هذا الحد)، فإضافة Toast خاص بنا فوقه يعني
        // تنبيهين مكرَّرين للمستخدم لنفس الحدث — لذا نعرض Toast خاصاً بنا
        // فقط على الأجهزة الأقدم من API 33 التي لا تملك هذا التنبيه التلقائي.
        if (!message.isUser && !message.isPlaceholder && !message.streaming) {
            val clipboardManager = LocalClipboardManager.current
            val copyContext = LocalContext.current
            // يُحسَب هنا (داخل نطاق @Composable، حيث tr() قابل للاستدعاء)
            // لا داخل onClick أدناه (نطاق عادي غير Composable لا يمكن
            // استدعاء tr() بداخله مباشرة).
            val copiedToastText = tr("تم نسخ الرد")
            Row(
                Modifier.padding(start = 34.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GlassIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            // راجع التعليق أعلى هذه الكتلة: تنبيهنا الخاص فقط
                            // لما قبل API 33، بنفس نمط tr() المستخدم بباقي
                            // نصوص هذه الشاشة (لا موارد strings.xml منفصلة).
                            android.widget.Toast.makeText(
                                copyContext,
                                copiedToastText,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    size = 26.dp
                ) {
                    Icon(Icons.Filled.ContentCopy, tr("نسخ الرد"), modifier = Modifier.size(14.dp))
                }
                if (message.learnable) {
                    when (message.feedback) {
                        null -> {
                            Text(
                                tr("هل كانت هذه الإجابة مفيدة؟"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            GlassIconButton(onClick = { onRate(true) }, size = 26.dp) {
                                Icon(Icons.Filled.ThumbUp, tr("مفيدة"), modifier = Modifier.size(14.dp))
                            }
                            GlassIconButton(onClick = { onRate(false) }, size = 26.dp) {
                                Icon(Icons.Filled.ThumbDown, tr("غير مفيدة"), modifier = Modifier.size(14.dp))
                            }
                        }
                        true -> Text(
                            tr("🌱 شكراً، سُجّل تقييمك. تُعتمد الإجابة بعد تأكيدها من ٣ أجهزة مختلفة."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        false -> Text(
                            tr("تم، لن يعتمد سيمو على هذه الإجابة تحديداً مرة أخرى."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (!message.isUser && !message.isPlaceholder && !message.streaming) {
            if (message.fullText != null && !message.expanded) {
                TextButton(onClick = onExpand, modifier = Modifier.padding(start = 30.dp)) {
                    Text(tr("اشرح أكثر"), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (message.sources.isNotEmpty()) {
                Text(
                    tr("المصدر من الموسوعة:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp, top = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(start = 34.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(message.sources) { source ->
                        val sectionLabel = tr(SemoReplyPolicy.sourceLabel(source.field))
                        AssistChip(
                            onClick = { onOpenHerb(source.herbId) },
                            label = { Text(source.herbName + " · " + sectionLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            if (message.suggestions.isNotEmpty()) {
                Text(
                    tr("هل تقصد:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp, top = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(start = 34.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(message.suggestions) { name ->
                        AssistChip(
                            onClick = { onSuggestion(name) },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * ═══ إصلاح: نبضة "سيمو يكتب…" كانت الحركة المستمرة الوحيدة بالتطبيق التي
 * تتجاهل وضع الأداء/معدل التحديث الفعلي (قارن بـ[rememberSmoothMotionAllowed]
 * وطريقة استخدامها في [com.salman.herbalencyclopedia.ui.theme.staggeredEntrance])
 * — كانت تدور بلا توقف حتى في الوضع الاقتصادي أو على شاشة منخفضة المعدل،
 * رغم أن كل حركة متصلة أخرى بالتطبيق تحترم هذا الشرط. كما كانت مدتها
 * ومنحناها رقمين محليين مستقلّين (tween(600) بمنحنى Compose الافتراضي)
 * بدل الاعتماد على [AppMotion] الموحّد كبقية حركات التطبيق. الآن: تستخدم
 * نفس منحنى ومدة [AppMotion.smooth]/[AppMotion.Slow] المستخدمة بأماكن
 * أخرى، وتتوقف كلياً (نص ثابت بلا نبض) حين لا يُسمح بالحركة المتصلة —
 * تماماً كسلوك [staggeredEntrance] في الحالة نفسها.
 */
@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    val smoothAllowed = rememberSmoothMotionAllowed()
    // ثلاث نقاط متتابعة النبض (مؤشر كتابة مألوف) بدل نص يومض؛ تتوقف عند
    // عدم السماح بالحركة المتصلة كما بقية حركات التطبيق.
    val transition = if (smoothAllowed) rememberInfiniteTransition(label = "typing") else null
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        AssistantAvatar(size = 28.dp)
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val dotAlpha = if (transition != null) {
                        val a by transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                androidx.compose.animation.core.tween(520, easing = AppMotion.Smooth),
                                RepeatMode.Reverse,
                                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 170)
                            ),
                            label = "typingDot$i"
                        )
                        a
                    } else 0.7f
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AssistantAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

private fun buildChatShareText(messages: List<ChatMessage>): String = buildString {
    appendLine("محادثة مع سيمو المساعد")
    appendLine()
    // نستبعد فقاعة "أفكّر وأبحث" المؤقتة إن كانت ما تزال معروضة لحظة
    // المشاركة (نادر) — نصّها انتقالي فقط ولا معنى لتضمينه في نسخة محفوظة
    // من المحادثة.
    messages.filterNot { it.isPlaceholder }.forEach { m ->
        appendLine(if (m.isUser) "أنت: ${m.text}" else "سيمو: ${m.text}")
        appendLine()
    }
}

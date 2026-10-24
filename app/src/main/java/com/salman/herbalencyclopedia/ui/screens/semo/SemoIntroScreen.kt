package com.salman.herbalencyclopedia.ui.screens.semo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.components.GlassButton
import com.salman.herbalencyclopedia.ui.util.tr

private data class SemoIntroSection(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String,
    val emphasized: Boolean = false
)

private val semoIntroSections = listOf(
    SemoIntroSection(
        icon = Icons.Filled.Construction,
        title = "سيمو لا يزال في مراحل التطوير",
        body = "سيمو مساعد ذكي محلي بالكامل ضمن الموسوعة، وما زال قيد التطوير المستمر، لذا قد يرتكب أخطاء أو يقدّم أحياناً إجابة غير دقيقة تماماً. تحقّق دائماً من أي معلومة مهمة، ولا تعتمد عليه وحده في أي قرار طبي.",
        emphasized = true
    ),
    SemoIntroSection(
        icon = Icons.Filled.SystemUpdate,
        title = "تحديثات مستمرة من أجل استقرار أفضل",
        body = "يتحسّن سيمو تدريجياً مع كل تحديث جديد. يُرجى مواصلة تحديث التطبيق كلما توفّر إصدار جديد، فهذا ما يمنحك أفضل استقرار وأدق إجابات ممكنة منه."
    ),
    SemoIntroSection(
        icon = Icons.Filled.CardGiftcard,
        title = "تجربة مجانية بالكامل",
        body = "سيمو متاح مجاناً بالكامل، بلا أي اشتراكات أو رسوم خفية — جزء أصيل من موسوعة الأعشاب تماماً كبقية محتواها."
    )
)

/**
 * شاشة ترحيب سيمو الأولى — تظهر مرة واحدة فقط عند أول فتح لسيمو (منفصلة
 * تماماً عن شاشة ترحيب التطبيق العامة [com.salman.herbalencyclopedia.ui.screens.onboarding.WelcomeScreen]
 * التي تظهر عند أول تشغيل للتطبيق نفسه)، وتوضّح بصراحة طبيعة سيمو كمساعد
 * قيد التطوير قبل أن يبدأ المستخدم أي محادثة معه. القرار يُخزَّن محلياً عبر
 * [com.salman.herbalencyclopedia.data.repository.PreferencesRepository.setSemoIntroSeen]
 * فلا تظهر هذه الشاشة مرة أخرى بعد أول موافقة.
 */
@Composable
fun SemoIntroScreen(onAccept: () -> Unit) {
    var isSaving by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AssistantAvatar(size = 84.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        tr("أهلاً بك مع سيمو 👋"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("قبل أن تبدأ أول محادثة معه، الرجاء قراءة النقاط التالية"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            items(semoIntroSections) { section ->
                // تنبيه كهرماني دافئ بدل الأحمر الصارخ (errorContainer) الذي كان
                // يخيف المستخدم من أول شاشة؛ يبقى لافتاً لكنه تحذير لا إنذار.
                val dark = androidx.compose.foundation.isSystemInDarkTheme()
                val amberBg = if (dark) Color(0xFF3A2F1A) else Color(0xFFFFF1D0)
                val amberFg = if (dark) Color(0xFFFFD98A) else Color(0xFF5C3D00)
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    border = if (section.emphasized) androidx.compose.foundation.BorderStroke(1.dp, amberFg.copy(alpha = 0.35f)) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (section.emphasized) {
                            amberBg
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    val contentColor = if (section.emphasized) {
                        amberFg
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(section.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(tr(section.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr(section.body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (section.emphasized) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // شريط سفلي ثابت يحمل زر الموافقة الوحيد، حتى يبقى ظاهراً دائماً
        // بغض النظر عن مقدار التمرير داخل النقاط أعلاه — بنفس نمط
        // WelcomeScreen العام تماماً.
        //
        // إصلاح: نفس مشكلة WelcomeScreen العامة تماماً - بلا navigationBarsPadding
        // كان الزر يظهر جزئياً خلف شريط التنقل التقليدي (الأزرار الثلاثة)
        // على الأجهزة التي تستخدمه. راجع تعليق الإصلاح الكامل في
        // WelcomeScreen.kt لنفس السطر بالضبط.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            GlassButton(
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        onAccept()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(tr("فهمت، لنبدأ مع سيمو 🌿"))
                }
            }
        }
    }
}

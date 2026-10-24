package com.salman.herbalencyclopedia.ui.screens.terms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import com.salman.herbalencyclopedia.ui.util.tr

private data class TermItem(val title: String, val body: String)

private val terms = listOf(
    TermItem(
        "قبول الشروط",
        "استخدامك لتطبيق موسوعة الأعشاب يعني موافقتك على هذه الشروط والأحكام كاملة. إن لم توافق عليها، يُرجى عدم متابعة استخدام التطبيق."
    ),
    TermItem(
        "طبيعة المحتوى",
        "محتوى الموسوعة (الفوائد، الاستخدامات، التحذيرات، الأضرار المحتملة) معلومات توعوية عامة من تدقيق المعالج الفيزيائي سلمان، وليست بديلاً عن استشارة طبية متخصصة، وقد لا تكون دقيقة بنسبة 100%."
    ),
    TermItem(
        "مسؤولية الاستخدام",
        "أنت المسؤول الوحيد عن أي قرار تتخذه بناءً على محتوى هذا التطبيق، بما في ذلك استخدام أي عشبة أو تركيبة. يُنصح دائماً باستشارة طبيب مختص قبل استخدام أي عشبة، خصوصاً مع وجود حالة صحية مزمنة أو أدوية أخرى."
    ),
    TermItem(
        "حقوق المحتوى",
        "محتوى الموسوعة مملوك لمطوّر التطبيق ولا يجوز نسخه أو إعادة نشره تجارياً دون إذن."
    ),
    TermItem(
        "التحديثات والأمان",
        "تُوزَّع تحديثات التطبيق الرسمية كملفات APK مباشرة عبر صفحة إصدارات المشروع الرسمية على GitHub. وأي رابط تحديث أو إصدار غير منشور عبر تلك القنوات الرسمية لا يُعد إصداراً موثوقاً من المشروع."
    ),
    TermItem(
        "التعديلات",
        "يجوز تحديث هذه الشروط بين حين وآخر لتعكس تحسينات على التطبيق. استمرارك باستخدام التطبيق بعد أي تعديل يُعد موافقة على الشروط المحدَّثة."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("الشروط والأحكام")) },
                navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }
            )
        }
    ) { padding ->
        ResponsiveScreenContent(windowInfo = rememberWindowSizeInfo(), modifier = Modifier.padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(terms) { term ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr(term.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(tr(term.body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text(
                    tr("© 2026 سلمان — موسوعة الأعشاب الطبية. جميع الحقوق محفوظة."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
        }
    }
}

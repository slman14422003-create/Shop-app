package com.salman.herbalencyclopedia.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WarningAmber
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
import com.salman.herbalencyclopedia.ui.components.GlassOutlinedButton
import com.salman.herbalencyclopedia.ui.components.LiquidGlassSurface
import com.salman.herbalencyclopedia.ui.util.AppLanguage
import com.salman.herbalencyclopedia.ui.util.tr
import com.salman.herbalencyclopedia.ui.theme.staggeredEntrance
import com.salman.herbalencyclopedia.ui.theme.entranceFade

private data class WelcomeSection(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String,
    val emphasized: Boolean = false
)

private val sections = listOf(
    WelcomeSection(
        icon = Icons.Filled.WarningAmber,
        title = "تحذير طبي مهم",
        body = "المعلومات المعروضة بهذا التطبيق للاستئناس العام فقط، وقد لا تكون دقيقة بنسبة 100%. " +
            "لا يُغني محتوى التطبيق عن استشارة طبيب مختص، ولا يجوز الاعتماد عليه وحده في أي قرار " +
            "علاجي أو تشخيصي. استشر طبيبك دائماً قبل استخدام أي عشبة، خصوصاً إن كنت تتناول أدوية " +
            "أخرى أو تعاني من حالة صحية مزمنة.",
        emphasized = true
    ),
    WelcomeSection(
        icon = Icons.Filled.Lock,
        title = "سياسة الخصوصية",
        body = "لا يطلب التطبيق أي بيانات شخصية منك (لا اسم، لا بريد إلكتروني، لا رقم هاتف). " +
            "قائمة المفضّلة وتفضيلات العرض تُحفظ محلياً على جهازك فقط. محتوى الأعشاب معروض " +
            "لأغراض توعوية عامة فقط ولا يُشارَك مع أي جهة خارجية."
    ),
    WelcomeSection(
        icon = Icons.Filled.Spa,
        title = "الشروط والأحكام",
        body = "باستخدامك هذا التطبيق فإنك توافق على استخدام محتواه ضمن الغرض التوعوي العام المذكور " +
            "أعلاه، وعلى تحمّل مسؤوليتك الكاملة عن أي قرار تتخذه بناءً على هذا المحتوى. يمكنك قراءة " +
            "النص الكامل للشروط والأحكام في أي وقت من الإعدادات."
    )
)

/**
 * شاشة الترحيب الأولى - تظهر مرة واحدة فقط بعد تثبيت التطبيق (قبل الوصول
 * للشاشة الرئيسية)، وتجمع: اختيار لغة الواجهة، التحذير الطبي، ملخص سياسة
 * الخصوصية، والشروط والأحكام، مع زر موافقة واحد. كلا القرارين (اللغة
 * المختارة والموافقة على الشروط) يُخزَّنان محلياً عبر
 * [PreferencesRepository.setAppLanguage] و[PreferencesRepository.setTermsAccepted]
 * فلا تظهر هذه الشاشة مرة أخرى إلا إذا حذف المستخدم بيانات التطبيق أو أعاد تثبيته.
 */
@Composable
fun WelcomeScreen(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onViewFullPrivacyPolicy: () -> Unit,
    onAgree: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().entranceFade()) {
                    LiquidGlassSurface(
                        shape = CircleShape,
                        modifier = Modifier.size(84.dp),
                        tint = MaterialTheme.colorScheme.primary,
                        glowColor = MaterialTheme.colorScheme.primary
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Spa,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        tr("أهلاً بك في موسوعة الأعشاب"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("قبل المتابعة، الرجاء قراءة النقاط التالية والموافقة عليها"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // ── اختيار لغة الواجهة: أول خطوة فعلية في معالج الإعداد، قبل أي
            // نص آخر، حتى تُعرض بقية الشاشة (والتطبيق كله بعدها) مباشرة
            // باللغة المختارة دون الحاجة للمرور لاحقاً بشاشة الإعدادات. ──
            item {
                Card(
                    modifier = Modifier.staggeredEntrance(1),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(tr("لغة التطبيق"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            AppLanguage.entries.forEach { language ->
                                val selected = language == currentLanguage
                                FilterChip(
                                    selected = selected,
                                    onClick = { onLanguageChange(language) },
                                    label = { Text(language.nativeName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(sections) { index, section ->
                Card(
                    modifier = Modifier.staggeredEntrance(index + 2),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (section.emphasized) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    val contentColor = if (section.emphasized) {
                        MaterialTheme.colorScheme.onErrorContainer
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

            item {
                GlassOutlinedButton(
                    onClick = onViewFullPrivacyPolicy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("قراءة سياسة الخصوصية كاملة")) }
            }
        }

        // شريط سفلي ثابت يحمل زر الموافقة الوحيد، حتى يبقى ظاهراً دائماً
        // بغض النظر عن مقدار التمرير داخل النقاط أعلاه.
        //
        // إصلاح: هذا العمود لم يكن يحسب أبداً ارتفاع شريط التنقل السفلي
        // للنظام (navigationBarsPadding) رغم أن الشاشة تُرسم edge-to-edge
        // (enableEdgeToEdge في MainActivity) - أي أن المحتوى يمتد فعلياً
        // خلف شريط النظام. على أزرار التنقل التقليدية الثلاثة (ارتفاع أثخن
        // بوضوح) كان هذا يعني زر "أوافق وأتابع" يظهر جزئياً خلف/تحت شريط
        // النظام المصمت بدل أن يعلوه بمسافة واضحة. .navigationBarsPadding()
        // هنا يضيف تماماً ارتفاع شريط النظام الفعلي أياً كان (رفيع جداً في
        // وضع الإيماءات، أثخن بوضوح مع الأزرار الثلاثة)، ثم الحشو الثابت
        // (20dp) يُضاف فوقه كمسافة أمان إضافية - فيبقى الزر ظاهراً بالكامل
        // وبمسافة مريحة عن حافة الشاشة في كلا الوضعين دون أي تفريق يدوي.
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
                        onAgree()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(tr("أوافق وأتابع"))
                }
            }
        }
    }
}

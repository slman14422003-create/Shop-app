package com.salman.herbalencyclopedia.ui.screens.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import com.salman.herbalencyclopedia.ui.util.tr

/** الرقم كما وصلنا بصيغته المحلية — هو ما يُعرَض للمستخدم دوماً بغض النظر عن أي افتراض أدناه. */
private const val SUPPORT_PHONE_LOCAL = "0932934273"

// افتراض رمز الدولة +963 (سوريا) بناءً على صيغة الرقم المحلي (09xx xxxxxx،
// وتحديداً بادئة 093 المستخدمة فعلياً في سوريا). إن كان الرقم من دولة أخرى،
// يكفي تعديل هذا الثابت وحده — الرقم المعروض في الواجهة (أعلاه) لا يتأثر
// إطلاقاً بهذا الافتراض.
private const val SUPPORT_WHATSAPP_COUNTRY_CODE = "963"

private const val SUPPORT_CONTACT_NAME = "المعالج الفيزيائي سلمان"

/**
 * شاشة الدعم داخل الإعدادات: تواصل مباشر مع مطوّر/مدقّق الموسوعة (المعالج
 * الفيزيائي سلمان) عبر واتساب، لأي استفسار أو ملاحظة أو مشكلة تقنية —
 * منفصلة عمداً عن شاشة "ملاحظات المستخدمين" الإدارية (AdminFeedbackScreen)
 * التي تُرسِل ملاحظة مرتبطة بعشبة/خلطة محدَّدة إلى Firestore لتُراجَع لاحقاً؛
 * هذه الشاشة قناة تواصل مباشرة وفورية بدل نموذج ينتظر مراجعة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("الدعم الفني")) },
                navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع")) } }
            )
        }
    ) { padding ->
        ResponsiveScreenContent(windowInfo = rememberWindowSizeInfo(), modifier = Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.SupportAgent,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("بحاجة لمساعدة؟"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("لأي استفسار، ملاحظة، أو مشكلة تقنية تواجهك في التطبيق، يمكنك التواصل المباشر عبر واتساب:"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(SUPPORT_CONTACT_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            SUPPORT_PHONE_LOCAL,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { openWhatsApp(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("تواصل عبر واتساب"), color = Color.White)
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { copySupportNumber(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("نسخ الرقم"))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    tr("غالباً يصلك الرد خلال يوم عمل واحد."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // بطاقة دعم المطوّر: نص تبرّعي بسيط يشير لنفس رقم الدعم
                // المعروض أعلى الشاشة (SUPPORT_PHONE_LOCAL) بدل تكرار رقم
                // منفصل، فلا يوجد سوى مصدر واحد للرقم بكل الشاشة.
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr("إذا وجدت هذا التطبيق مفيداً، بإمكانك دعم المطوّر عبر سيرياتيل كاش على الرقم الظاهر أعلى الشاشة:"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            SUPPORT_PHONE_LOCAL,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** يفتح محادثة واتساب مع رقم الدعم مباشرة عبر رابط wa.me، مع رسالة واضحة إن لم يكن واتساب مثبَّتاً. */
private fun openWhatsApp(context: Context) {
    val digitsOnly = SUPPORT_PHONE_LOCAL.trimStart('0')
    val fullNumber = "$SUPPORT_WHATSAPP_COUNTRY_CODE$digitsOnly"
    val message = Uri.encode("مرحباً، لدي استفسار حول تطبيق موسوعة الأعشاب.")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$fullNumber?text=$message"))
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "تعذّر فتح واتساب، تأكد من تثبيت التطبيق", Toast.LENGTH_LONG).show()
    }
}

/** ينسخ رقم الدعم بصيغته المحلية المعروضة (لا الصيغة الدولية المستخدَمة داخلياً لواتساب فقط) إلى الحافظة. */
private fun copySupportNumber(context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboardManager?.setPrimaryClip(ClipData.newPlainText("support_phone", SUPPORT_PHONE_LOCAL))
    Toast.makeText(context, "تم نسخ الرقم", Toast.LENGTH_SHORT).show()
}

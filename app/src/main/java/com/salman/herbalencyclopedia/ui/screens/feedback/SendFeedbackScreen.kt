package com.salman.herbalencyclopedia.ui.screens.feedback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.components.GlassButton
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import kotlinx.coroutines.launch
import com.salman.herbalencyclopedia.ui.util.tr

/**
 * شاشة "الإبلاغ عن خطأ" — متاحة للجميع بلا أي تسجيل دخول (انظر
 * firestore.rules: allow create على مجموعة "feedback" مفتوح للجميع)،
 * تصل الملاحظة للأدمن فقط عبر AdminFeedbackScreen ضمن الإعدادات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFeedbackScreen(
    targetName: String,
    onBack: () -> Unit,
    onSubmit: (senderName: String?, message: String, onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var senderName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("الإبلاغ عن خطأ")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(tr("بخصوص"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(targetName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                tr("إن وجدت معلومة خاطئة أو ناقصة، اكتب ملاحظتك هنا وستصل مباشرة للمسؤول عن الموسوعة."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (sent) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        tr("تم إرسال ملاحظتك، شكراً لمساعدتك في تحسين الموسوعة."),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text(tr("اسمك (اختياري)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(tr("وصف الخطأ أو الملاحظة")) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassButton(
                    onClick = {
                        isSending = true
                        onSubmit(senderName.ifBlank { null }, message) { success, errorMessage ->
                            isSending = false
                            if (success) {
                                sent = true
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(errorMessage ?: "تعذّر إرسال الملاحظة") }
                            }
                        }
                    },
                    enabled = message.isNotBlank() && !isSending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(tr("إرسال"))
                    }
                }
            }
        }
    }
}

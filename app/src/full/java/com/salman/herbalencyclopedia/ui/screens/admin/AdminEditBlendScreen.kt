package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.salman.herbalencyclopedia.data.image.GithubImageUploader
import com.salman.herbalencyclopedia.data.image.ImageCompressor
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.components.GlassButton
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassOutlinedButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.salman.herbalencyclopedia.ui.util.tr

/**
 * نموذج إضافة/تعديل خلطة — للأدمن فقط. يشبه [AdminEditHerbScreen] تماماً
 * لكن بدل تصنيف واحد، يختار الأدمن مجموعة أعشاب موجودة (من نفس مجموعة
 * "herbs" في Firestore) لتكوّن مكوّنات الخلطة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEditBlendScreen(
    existingBlend: Blend?,
    herbs: List<Herb>,
    onBack: () -> Unit,
    onSave: (Blend, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(existingBlend?.name ?: "") }
    var selectedHerbIds by remember { mutableStateOf(existingBlend?.herbIds?.toSet() ?: emptySet()) }
    var benefits by remember { mutableStateOf(existingBlend?.benefits ?: "") }
    var usage by remember { mutableStateOf(existingBlend?.usage ?: "") }
    var warnings by remember { mutableStateOf(existingBlend?.warnings ?: "") }
    var notes by remember { mutableStateOf(existingBlend?.notes ?: "") }
    var imageUrl by remember { mutableStateOf(existingBlend?.imageUrl ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCompressingImage by remember { mutableStateOf(false) }
    var herbPickerExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // يُحسَب هنا (سياق @Composable سليم) بدل استدعاء tr() مباشرة داخل
    // رد نداء rememberLauncherForActivityResult/coroutineScope.launch أدناه —
    // ذاك السياق ليس @Composable إطلاقاً، فاستدعاء tr() هناك مباشرة يمنع
    // بناء المشروع بالكامل (compile error: "@Composable invocations can
    // only happen from the context of a @Composable function").
    val imageCompressionErrorMessage = tr("تعذّر معالجة هذه الصورة (قد تكون كبيرة جداً)، جرّب صورة أخرى")
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            errorMessage = null
            isCompressingImage = true
            // نفس منطق AdminEditHerbScreen: نحاول الرفع الخارجي (GitHub عبر
            // Cloudflare Worker) أولاً لتفريغ Firestore من الصورة كلياً، مع
            // تراجع تلقائي للتضمين المحلي عند أي فشل بالرفع الخارجي.
            coroutineScope.launch {
                val idToken = runCatching {
                    FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                }.getOrNull()

                val uploadedUrl = idToken?.let { token ->
                    ImageCompressor.compressToWebpBytes(context, it)?.let { bytes ->
                        GithubImageUploader.upload(bytes, token)
                    }
                }

                if (uploadedUrl != null) {
                    imageUrl = uploadedUrl
                    isCompressingImage = false
                } else {
                    val fallback = ImageCompressor.compressToDataUrl(context, it)
                    isCompressingImage = false
                    if (fallback != null) imageUrl = fallback else errorMessage = imageCompressionErrorMessage
                }
            }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(if (existingBlend == null) tr("إضافة خلطة") else tr("تعديل خلطة")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        }
    ) { padding ->
        // نفس إصلاح لوحة المفاتيح المطبَّق في AdminEditHerbScreen (راجع
        // التوثيق الكامل هناك): imePadding() ضروري هنا أيضاً لنفس السبب
        // بالضبط — نموذج طويل قابل للتمرير بحقول نص متعددة، بلا حساب
        // لارتفاع لوحة المفاتيح في وضع edge-to-edge.
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(tr("اسم الخلطة")) }, modifier = Modifier.fillMaxWidth()
            )

            Text(tr("مكوّنات الخلطة"), style = MaterialTheme.typography.titleMedium)
            Surface(
                onClick = { herbPickerExpanded = !herbPickerExpanded },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedHerbIds.isEmpty()) tr("اختر الأعشاب (${herbs.size} متاحة)")
                        else herbs.filter { it.id in selectedHerbIds }.joinToString(", ") { it.name },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (herbPickerExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (herbPickerExpanded) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        if (herbs.isEmpty()) {
                            Text(
                                tr("لا توجد أعشاب في الموسوعة بعد"),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        herbs.forEach { herb ->
                            val checked = herb.id in selectedHerbIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedHerbIds = if (checked) selectedHerbIds - herb.id else selectedHerbIds + herb.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedHerbIds = if (isChecked) selectedHerbIds + herb.id else selectedHerbIds - herb.id
                                    }
                                )
                                Text(herb.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = benefits, onValueChange = { benefits = it },
                label = { Text(tr("الفوائد")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = usage, onValueChange = { usage = it },
                label = { Text(tr("طريقة التحضير والاستخدام")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = warnings, onValueChange = { warnings = it },
                label = { Text(tr("التحذيرات")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(tr("ملاحظات إضافية")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )

            Text(tr("صورة الخلطة"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(onClick = { imagePicker.launch("image/*") }, enabled = !isCompressingImage) { Text(tr("اختيار صورة")) }
                if (imageUrl.isNotBlank() && !isCompressingImage) GlassOutlinedButton(onClick = { imageUrl = "" }) { Text(tr("مسح")) }
            }
            if (isCompressingImage) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(tr("جاري ضغط الصورة..."), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (imageUrl.isNotBlank()) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp))

            errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            GlassButton(
                onClick = {
                    isSaving = true
                    errorMessage = null
                    val blend = Blend(
                        id = existingBlend?.id ?: "",
                        name = name,
                        herbIds = selectedHerbIds.toList(),
                        benefits = benefits,
                        usage = usage,
                        warnings = warnings,
                        notes = notes,
                        imageUrl = imageUrl.ifBlank { null }
                    )
                    onSave(blend) { success, message ->
                        isSaving = false
                        if (success) onBack() else errorMessage = message
                    }
                },
                enabled = name.isNotBlank() && !isSaving && !isCompressingImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(tr("حفظ"))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

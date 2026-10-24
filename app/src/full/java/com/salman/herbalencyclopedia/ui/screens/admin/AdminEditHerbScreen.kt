package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassButton
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassOutlinedButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.ImagePreviewDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.salman.herbalencyclopedia.data.image.GithubImageUploader
import com.salman.herbalencyclopedia.data.image.ImageCompressor
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEditHerbScreen(
    existingHerb: Herb?,
    categories: List<Category>,
    onBack: () -> Unit,
    onSave: (Herb, (Boolean, String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(existingHerb?.name ?: "") }
    var categoryId by remember { mutableStateOf(existingHerb?.categoryId) }
    var benefits by remember { mutableStateOf(existingHerb?.benefits ?: "") }
    var usage by remember { mutableStateOf(existingHerb?.usage ?: "") }
    var warnings by remember { mutableStateOf(existingHerb?.warnings ?: "") }
    var harms by remember { mutableStateOf(existingHerb?.harms ?: "") }
    var notes by remember { mutableStateOf(existingHerb?.notes ?: "") }
    var imageUrl by remember { mutableStateOf(existingHerb?.imageUrl ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var isCompressingImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // انظر نفس التعليق في AdminEditBlendScreen: يُحسَب هنا (سياق @Composable
    // سليم) بدل استدعاء tr() داخل رد نداء الالتقاط/coroutine أدناه، الذي
    // ليس سياق @Composable ويمنع بناء المشروع (نفس الخطأ الذي ظهر فعلياً
    // عند البناء: "@Composable invocations can only happen from the
    // context of a @Composable function").
    val imageCompressionErrorMessage = tr("تعذّر معالجة هذه الصورة (قد تكون كبيرة جداً)، جرّب صورة أخرى")
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            errorMessage = null
            isCompressingImage = true
            // الضغط ينفَّذ الآن على خيط خلفي (راجع ImageCompressor) بدل تجميد
            // الواجهة أثناء معالجة الصور الكبيرة.
            //
            // نحاول أولاً الرفع الخارجي (GitHub عبر Cloudflare Worker، راجع
            // GithubImageUploader) لتفريغ مساحة Firestore المجانية من الصور
            // كلياً؛ أي فشل هنا (بلا إنترنت، الـ Worker متوقف أو غير مُعدّ
            // بعد، فشل التحقق من الهوية) يرجع null بهدوء، فنعود للتضمين
            // المحلي القديم (data URL داخل المستند نفسه) بدل تعطيل حفظ
            // العشبة بالكامل بسبب مشكلة بخدمة رفع خارجية.
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

    val selectedCategoryName = categories.firstOrNull { it.id == categoryId }?.name ?: tr("بدون تصنيف")

    // ═══ ميزة إدمن جديدة: "معاينة قبل النشر" ═══
    // يفتح شاشة تفاصيل العشبة الحقيقية (HerbDetailScreen — نفس ما يراه
    // المستخدم لاحقاً بالضبط) لكن ببيانات المسوّدة الحالية غير المحفوظة
    // بعد، بدل الاضطرار للحفظ أولاً لمعرفة شكلها النهائي. draftHerb يُبنى
    // من كل الحقول الحيّة فتتحدّث المعاينة تلقائياً مع كل تعديل.
    var showHerbPreview by remember { mutableStateOf(false) }
    val draftHerb = Herb(
        id = existingHerb?.id ?: "",
        name = name.ifBlank { tr("(بلا اسم بعد)") },
        categoryId = categoryId,
        benefits = benefits,
        warnings = warnings,
        harms = harms,
        usage = usage,
        notes = notes,
        imageUrl = imageUrl.ifBlank { null }
    )

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(if (existingHerb == null) tr("إضافة عشبة") else tr("تعديل عشبة")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        }
    ) { padding ->
        // ═══ إصلاح خلل حقيقي أُبلغ عنه: لوحة المفاتيح تغطي حقل "ملاحظات
        // إضافية" (وأي حقل أسفله) بلا أي طريقة للتمرير لرؤيته أثناء الكتابة
        // فيه ═══
        // السبب: التطبيق edge-to-edge (enableEdgeToEdge + setDecorFitsSystemWindows
        // = false في MainActivity)، فإعداد android:windowSoftInputMode="adjustResize"
        // بالمانيفست لم يعد يُغيّر حجم الشاشة تلقائياً عند ظهور لوحة المفاتيح
        // كما في الوضع القديم غير edge-to-edge — أصبحت مسؤولية حجز المساحة
        // للوحة المفاتيح على Compose نفسه عبر imePadding()، وكانت غائبة هنا.
        // فكان التمرير (verticalScroll) يحسب طول المحتوى بلا أي حساب لارتفاع
        // لوحة المفاتيح، فتبقى الحقول السفلية عملياً خلف اللوحة بلا أي مساحة
        // تمرير إضافية للوصول إليها. imePadding() هنا يضيف مساحة سفلية تساوي
        // ارتفاع لوحة المفاتيح الظاهرة فتصبح كل الحقول قابلة للتمرير فوقها.
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
                label = { Text(tr("اسم العشبة")) }, modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(tr("التصنيف")) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = benefits, onValueChange = { benefits = it },
                label = { Text(tr("الفوائد")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = usage, onValueChange = { usage = it },
                label = { Text(tr("طريقة الاستخدام")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = warnings, onValueChange = { warnings = it },
                label = { Text(tr("التحذيرات")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = harms, onValueChange = { harms = it },
                label = { Text(tr("الأضرار المحتملة")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(tr("ملاحظات إضافية")) }, minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            Text(tr("صورة العشبة"), style = MaterialTheme.typography.titleMedium)
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
            if (imageUrl.isNotBlank()) {
                // معاينة دائرية (بدل المستطيل السابق fillMaxWidth/height(180dp))
                // لتطابق شكل الصورة الفعلي كما يظهر لاحقاً في كل مكان بالتطبيق
                // (HerbThumbnail دائري الشكل دوماً)، فما يراه المطوّر هنا عند
                // الرفع هو نفسه ما سيظهر للمستخدم لاحقاً. الضغط عليها يفتح
                // معاينة بحجم الشاشة الكاملة للتأكد من جودة الصورة قبل الحفظ.
                var showPreview by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { showPreview = true }
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (showPreview) {
                    ImagePreviewDialog(imageUrl = imageUrl, onDismiss = { showPreview = false })
                }
            }

            errorMessage?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassOutlinedButton(
                    onClick = { showHerbPreview = true },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(tr("معاينة")) }

                GlassButton(
                    onClick = {
                        isSaving = true
                        errorMessage = null
                        onSave(draftHerb) { success, message ->
                            isSaving = false
                            if (success) onBack() else errorMessage = message
                        }
                    },
                    enabled = name.isNotBlank() && !isSaving && !isCompressingImage,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(tr("حفظ"))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showHerbPreview) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showHerbPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                com.salman.herbalencyclopedia.ui.screens.herbdetail.HerbDetailScreen(
                    herb = draftHerb,
                    isFavorite = false,
                    onBack = { showHerbPreview = false },
                    onToggleFavorite = {},
                    onReportIssue = {}
                )
            }
        }
    }
}

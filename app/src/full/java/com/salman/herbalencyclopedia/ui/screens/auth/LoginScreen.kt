package com.salman.herbalencyclopedia.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassButton
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LiquidGlassSurface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.theme.entranceFade
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLogin: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }

    fun submit() {
        keyboardController?.hide()
        isLoading = true
        errorMessage = null
        onLogin(email, password) { success, message ->
            isLoading = false
            if (success) onSuccess()
            else errorMessage = message ?: "تعذّر تسجيل الدخول."
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("دخول المسؤول")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        }
    ) { padding ->
        // ثغرة كانت هنا: الشاشة كانت ثابتة الحجم بلا أي تمرير ولا معالجة
        // لمساحة لوحة المفاتيح (IME) — فبمجرد فتح الكيبورد للكتابة بحقل
        // البريد أو كلمة المرور على شاشة صغيرة، كان يغطي الحقول نفسها (لا
        // طريقة لرؤية ما تكتبه). الآن: .imePadding() يرفع المحتوى تلقائياً
        // بقدر ارتفاع الكيبورد الفعلي، و.verticalScroll() يسمح بالتمرير
        // اليدوي لو بقيت البطاقة أطول من المساحة المتبقية فوق الكيبورد.
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // بطاقة الدخول أصبحت زجاجية (LiquidGlassSurface) بدل تدرّج
            // مسطّح ثابت: نفس المكوّن المستخدم بكل عناصر التطبيق الفريدة
            // (الشريط العلوي، الزر الأساسي...)، فيتّسق مظهرها مع باقي
            // الواجهة تلقائياً، وتحترم وضع الأداء المختار من الإعدادات
            // بلا أي كود إضافي هنا: تمويه/توهّج حقيقي على الأجهزة الحديثة
            // القوية، وتدرّج مصمت خفيف بلا أي تكلفة رسم إضافية على الوضع
            // الاقتصادي للأجهزة الضعيفة.
            LiquidGlassSurface(
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .entranceFade(),
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                sheen = true
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(tr("منطقة محمية"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        tr("هذه الواجهة مخصصة للحساب المسؤول المسجّل مسبقاً فقط."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(tr("البريد الإلكتروني")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Email, contentDescription = null)
                        },
                        // نوع لوحة مفاتيح مخصص للبريد الإلكتروني (يظهر @
                        // مباشرة، ويرشّح اقتراحات النظام لعناوين بريد فعلية
                        // بدل لوحة نصية عامة)، وزر "التالي" بدل "تم" كي
                        // ينتقل المستخدم مباشرة لحقل كلمة المرور بالكيبورد
                        // نفسه دون لمس الشاشة.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr("كلمة المرور")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        // إصلاح أمني كان مفقوداً: هذا الحقل كان بلا أي
                        // KeyboardType محدَّد (أي "Text" عادي رغم أنه كلمة
                        // مرور)، فبعض لوحات المفاتيح (Gboard وغيرها) تتعامل
                        // مع حقل نصي عادي بالاقتراح التلقائي والتعلّم
                        // الشخصي كأي نص طبيعي — ما قد يُسرّب كلمة المرور
                        // لقائمة الاقتراحات أو لقاموس الكيبورد الشخصي.
                        // KeyboardType.Password يخبر النظام صراحة أن هذا
                        // حقل حسّاس، فتُعطَّل الاقتراحات والتصحيح التلقائي
                        // والتعلّم الشخصي لهذا الإدخال تحديداً.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (email.isNotBlank() && password.length >= 6 && !isLoading) submit() }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) tr("إخفاء كلمة المرور") else tr("إظهار كلمة المرور")
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocusRequester),
                        shape = RoundedCornerShape(18.dp)
                    )

                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    GlassButton(
                        onClick = { submit() },
                        enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(tr("تسجيل الدخول"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

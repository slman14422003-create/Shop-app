package com.shopmanager.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.chat.NAME_REGEX
import com.shopmanager.app.data.chat.cleanName
import com.shopmanager.app.ui.common.AppPillButton
import com.shopmanager.app.ui.common.AppScreenPadding
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.groupedRowShape
import com.shopmanager.app.ui.theme.LocalSemanticColors

/**
 * شاشة الدخول/إنشاء الحساب (التصميم المعتمد): شعار دائري بحلقة وشارة درع، تبديل علوي بين
 * "إنشاء حساب" و"تسجيل الدخول"، حقول بأيقونات وعلامة صح خضراء عند صحة الإدخال، زر حبّة واحد،
 * وسطر أمان أسفل الشاشة. الحساب يُنشأ مرة واحدة فقط، ثم تبقى الجلسة محفوظة.
 */
@Composable
fun ChatAuthScreen(
    state: AuthUiState,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onForgot: (email: String) -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var signUp by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val nameOk = NAME_REGEX.matches(cleanName(name))
    val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passOk = password.length >= 6

    fun submit() {
        localError = null
        keyboard?.hide()
        when {
            signUp && !nameOk -> localError = "اسم المستخدم من 2 إلى 20 حرفاً (حروف وأرقام ومسافات)"
            !emailOk -> localError = "اكتب بريداً إلكترونياً صحيحاً"
            !passOk -> localError = if (signUp) "كلمة المرور 6 أحرف على الأقل" else "اكتب كلمة المرور"
            signUp -> onSignUp(name, email, password)
            else -> onSignIn(email, password)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppScreenPadding + 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // الشعار: حلقة خارجية + دائرة داخلية + شارة درع
        Box(Modifier.padding(top = 8.dp).size(116.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .border(1.dp, cs.outlineVariant, CircleShape)
            )
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Forum, null, tint = cs.onSurface, modifier = Modifier.size(38.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = cs.surface, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("الدردشة المحلية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            if (signUp) "أنشئ حسابك مرة واحدة وابدأ المحادثة" else "أهلاً بعودتك، سجّل دخولك للمتابعة",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        AuthModeToggle(signUp = signUp, onChange = {
            if (it != signUp) {
                signUp = it
                localError = null
                onClearMessages()
            }
        })
        Spacer(Modifier.height(14.dp))

        val fieldCount = if (signUp) 3 else 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (signUp) {
                FieldWithAccessory(accessory = {
                    if (nameOk) Icon(Icons.Default.CheckCircle, null, tint = LocalSemanticColors.current.success, modifier = Modifier.size(22.dp))
                }) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it.take(20); localError = null },
                        label = "اسم المستخدم",
                        leadingIcon = Icons.Default.Person,
                        shape = groupedRowShape(0, fieldCount - 1),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
                    )
                }
            }
            FieldWithAccessory(accessory = {
                if (emailOk) Icon(Icons.Default.CheckCircle, null, tint = LocalSemanticColors.current.success, modifier = Modifier.size(22.dp))
            }) {
                AppTextField(
                    value = email,
                    onValueChange = { email = it.replace(" ", ""); localError = null },
                    label = "البريد الإلكتروني",
                    leadingIcon = Icons.Default.Email,
                    shape = groupedRowShape(if (signUp) 1 else 0, fieldCount - 1),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
                )
            }
            FieldWithAccessory(accessory = {
                Icon(
                    if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { showPassword = !showPassword }
                        .padding(9.dp)
                )
            }) {
                AppTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = "كلمة المرور",
                    leadingIcon = Icons.Default.Lock,
                    shape = groupedRowShape(fieldCount - 1, fieldCount - 1),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
            }
        }

        if (signUp) {
            Text(
                "6 أحرف على الأقل",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (password.isNotEmpty() && !passOk) cs.error else cs.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }

        val shownError = localError ?: state.error
        if (shownError != null) {
            Text(
                shownError,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.error
            )
        }
        if (state.info != null) {
            Text(
                state.info,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalSemanticColors.current.success
            )
        }

        Spacer(Modifier.height(8.dp))
        AppPillButton(
            label = when {
                state.busy -> "جارٍ المعالجة..."
                signUp -> "إنشاء الحساب"
                else -> "دخول"
            },
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            enabled = !state.busy,
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth()
        )

        if (!signUp) {
            TextButton(
                onClick = {
                    if (emailOk) onForgot(email) else localError = "اكتب بريدك أولاً ثم اضغط \"نسيت كلمة المرور\""
                },
                enabled = !state.busy
            ) {
                Text("نسيت كلمة المرور؟", color = cs.onSurface)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Security, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                "محادثاتك خاصة بينك وبين المستخدمين فقط",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** يضع أيقونة (صح/عين) في نهاية الحقل فوق [AppTextField] بدون تغيير تواقيعه. */
@Composable
private fun FieldWithAccessory(accessory: @Composable () -> Unit, field: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        field()
        Box(Modifier.padding(end = 12.dp)) { accessory() }
    }
}

/** تبديل علوي بحبّتين: المحددة بلون onSurface (نفس لغة AppChip). */
@Composable
private fun AuthModeToggle(signUp: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        @Composable
        fun Seg(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
            Box(
                modifier
                    .clip(CircleShape)
                    .background(if (selected) cs.onSurface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab, onClick = onClick)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) cs.surface else cs.onSurfaceVariant
                )
            }
        }
        Seg("إنشاء حساب", signUp, { onChange(true) }, Modifier.weight(1f))
        Seg("تسجيل الدخول", !signUp, { onChange(false) }, Modifier.weight(1f))
    }
}


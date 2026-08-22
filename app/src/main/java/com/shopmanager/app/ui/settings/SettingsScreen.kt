package com.shopmanager.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (AppThemeMode) -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var hasPin by remember { mutableStateOf(settings.hasPin) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("المظهر", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            AppThemeMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = {
                            themeMode = mode
                            settings.themeMode = mode
                            onThemeChanged(mode)
                        }
                    )
                    Text(
                        when (mode) {
                            AppThemeMode.SYSTEM -> "حسب النظام"
                            AppThemeMode.LIGHT -> "فاتح"
                            AppThemeMode.DARK -> "داكن"
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("الحماية", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (hasPin) "قفل برمز PIN مفعّل" else "قفل برمز PIN غير مفعّل")
                    Text("يحمي فتح التطبيق برمز محلي على هذا الجهاز فقط", style = MaterialTheme.typography.labelSmall)
                }
                if (hasPin) {
                    TextButton(onClick = { settings.clearPin(); hasPin = false }) { Text("إلغاء") }
                } else {
                    TextButton(onClick = { showSetPinDialog = true }) { Text("تفعيل") }
                }
            }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirm = { pin ->
                settings.setPin(pin)
                hasPin = true
                showSetPinDialog = false
            }
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعيين رمز PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("رمز من 4 إلى 6 أرقام") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("تأكيد الرمز") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "الرمز لازم يكون 4 أرقام على الأقل"
                    pin != confirm -> error = "الرمزان غير متطابقين"
                    else -> onConfirm(pin)
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

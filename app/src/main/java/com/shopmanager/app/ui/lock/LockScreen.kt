package com.shopmanager.app.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.settings.SettingsRepository
import kotlinx.coroutines.delay

/**
 * SECURITY FIX: a 4-6 digit PIN used to have no limit on wrong guesses —
 * anyone with the phone in hand could just keep tapping "دخول" until they
 * landed on the right one. `settings.verifyPin` now enforces a real
 * escalating lockout (see PinAttemptThrottle/SettingsRepository); this
 * screen surfaces that lockout instead of only reflecting a right/wrong
 * result — the field and button disable and a countdown shows while
 * locked, so it's visibly a real cooldown rather than the app just
 * silently rejecting the correct PIN.
 */
@Composable
fun LockScreen(settings: SettingsRepository, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var lockRemaining by remember { mutableLongStateOf(settings.pinLockRemainingSeconds()) }
    val isLocked = lockRemaining > 0

    // Ticks the visible countdown once a second while locked, and clears
    // itself the moment the lockout actually expires — no manual "try
    // again" tap needed just to find out it's over.
    LaunchedEffect(isLocked) {
        while (lockRemaining > 0) {
            delay(1000)
            lockRemaining = settings.pinLockRemainingSeconds()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("إدارة المحل مقفلة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); error = false },
                label = { Text("أدخل رمز PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                enabled = !isLocked,
                singleLine = true
            )
            if (isLocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "محاولات كثيرة خاطئة — حاول بعد $lockRemaining ثانية",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            } else if (error) {
                Spacer(Modifier.height(4.dp))
                Text("رمز خاطئ، حاول مجدداً", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !isLocked,
                onClick = {
                    if (settings.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = true
                        lockRemaining = settings.pinLockRemainingSeconds()
                    }
                }
            ) { Text("دخول") }
        }
    }
}

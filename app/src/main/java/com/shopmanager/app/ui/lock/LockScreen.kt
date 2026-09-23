package com.shopmanager.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.settings.SettingsRepository
import com.shopmanager.app.ui.common.AppTextField
import com.shopmanager.app.ui.common.BrandGradient
import com.shopmanager.app.ui.common.BrandOnGradient
import com.shopmanager.app.ui.common.LiquidGlassGlow
import com.shopmanager.app.ui.common.liquidGlassSurface
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
 *
 * REDESIGN (طلب "اعادة تلوين كل الواجهة حسب الوضع الموجود... حرفيا كلشيء"):
 * this was the one screen in the whole app that never picked up any of the
 * app's own design system — a bare centered [Column] on the plain Material
 * background, a stock [Icon]/[Button], no brand gradient, no glass. Every
 * other screen (the splash, every header, every dialog) already reads as
 * this app's own product; the PIN screen alone looked like unstyled
 * scaffolding, and it's the very first thing a returning person sees.
 * Rebuilt on the exact same language [AppSplashScreen] already
 * established — [BrandGradient] behind a [LiquidGlassGlow]-lit glass badge
 * — so unlocking the app now visually continues straight out of the same
 * screen the splash just handed off from, instead of cutting to a
 * completely different, unbranded look. The actual PIN entry sits inside
 * its own [liquidGlassSurface] card, tinted toward this mode's own
 * primary/tertiary the same way [GlassAlertDialog] blends its panel — so
 * the field and error text keep reading from the ordinary
 * onSurface/onSurfaceVariant roles that already look correct in every
 * color mode, no one-off color logic of their own needed.
 *
 * Status bar/nav bar icon color for this screen is handled in
 * MainActivity, not here — see its own note on why this full-bleed
 * gradient background means this screen now follows the exact same
 * "always white icons" rule the splash and every unlocked screen already
 * use, rather than the light/dark-background rule a plain screen would.
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

    // Claude-app style ("والهيكل"): a plain flat background instead of a
    // full-screen colored gradient wash — Claude's own screens never paint
    // the whole page in the accent color, only small elements (like the
    // icon badge below) carry it. The card no longer blends toward
    // primary/tertiary either; same flat surfaceContainerHigh panel every
    // other dialog/card in the app now uses.
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardRimColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Same fixed, unblurred light pool [AppSplashScreen] uses — one
        // light source instead of a flat wash, drawn once and never
        // animated. Tinted toward the accent color now instead of plain
        // white, since it's sitting on a neutral background instead of an
        // already-colored one.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-90).dp)
                .size(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Same glow + glass-circle language as the splash logo (see
            // AppSplashScreen's LiquidGlassLogo), just holding a lock glyph
            // instead of the storefront mark — reads as a continuation of
            // the same screen the splash just faded from, not a hand-off
            // to a different design.
            Box(contentAlignment = Alignment.Center) {
                LiquidGlassGlow(modifier = Modifier.size(104.dp), color = MaterialTheme.colorScheme.primary)
                Box(
                    Modifier
                        .size(92.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
                )
                Box(
                    Modifier
                        .size(84.dp)
                        .liquidGlassSurface(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BrandOnGradient, modifier = Modifier.size(38.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "إدارة المحل مقفلة",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .liquidGlassSurface(
                        shape = MaterialTheme.shapes.large,
                        baseBrush = Brush.verticalGradient(listOf(cardColor, cardColor)),
                        elevation = 6.dp,
                        highlight = false,
                        rimColor = cardRimColor
                    )
                    .padding(24.dp)
            ) {
                AppTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6); error = false },
                    label = "أدخل رمز PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    enabled = !isLocked,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLocked) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "محاولات كثيرة خاطئة — حاول بعد $lockRemaining ثانية",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                } else if (error) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "رمز خاطئ، حاول مجدداً",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    enabled = !isLocked,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onClick = {
                        if (settings.verifyPin(pin)) {
                            onUnlocked()
                        } else {
                            error = true
                            lockRemaining = settings.pinLockRemainingSeconds()
                        }
                    }
                ) {
                    Text("دخول", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

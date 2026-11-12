package com.shopmanager.app.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.R
import com.shopmanager.app.ui.common.AppScreenPadding

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val vm: ChatSessionViewModel = viewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val auth by vm.auth.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    when (session) {
        ChatSessionState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        ChatSessionState.SignedOut -> GoogleLoginScreen(
            state = auth,
            onContinue = { activity?.let(vm::signInWithGoogle) },
            onCancel = vm::cancelAuth
        )
        is ChatSessionState.SignedIn -> content()
    }
}

@Composable
private fun GoogleLoginScreen(
    state: AuthUiState,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AppScreenPadding + 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).clip(CircleShape).border(1.dp, cs.outlineVariant, CircleShape))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(cs.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storefront, null, tint = cs.onSurface, modifier = Modifier.size(44.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(cs.onSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = cs.surface, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "سجّل الدخول بحساب Google للمتابعة",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))

        if (state.error != null) {
            Text(
                state.error,
                color = cs.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            )
        }

        Surface(
            onClick = onContinue,
            enabled = !state.busy,
            shape = CircleShape,
            color = cs.onSurface,
            contentColor = cs.surface,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        color = cs.surface,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("المتابعة مع Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.busy) {
            TextButton(onClick = onCancel) { Text("إلغاء", color = cs.onSurfaceVariant) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Text(
            "كل جهاز يرتبط بحساب واحد فقط",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

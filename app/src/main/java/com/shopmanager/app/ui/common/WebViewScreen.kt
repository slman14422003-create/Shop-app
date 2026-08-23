package com.shopmanager.app.ui.common

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * A small, self-contained in-app browser used for the "دليل الاستخدام"
 * (help) page and any future in-app link, so the person never has to leave
 * the app to a separate Chrome window.
 *
 * Android-11-and-up notes (why this isn't just `WebView(context)`):
 *  - Force-dark: there is no single API that works the same from Android 11
 *    through today. `WebSettingsCompat` (androidx.webkit) is the only way to
 *    ask for dark WebView content that degrades gracefully across API
 *    levels instead of crashing or silently no-op'ing on some of them.
 *  - Mixed content / cleartext: Android is stricter about this release over
 *    release. Loading the bundled local asset (`file:///android_asset/...`)
 *    sidesteps it entirely — no network permission dance, works the same on
 *    every Android version, and never breaks if the user is offline.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(url: String, title: String, onBack: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webViewRef
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BrandOnGradient,
                    navigationIconContentColor = BrandOnGradient
                ),
                modifier = Modifier.background(BrandGradient.brush())
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false // static help content only — keeps the surface area minimal
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false

                        // Force-dark support, resolved via the compat shim so it
                        // behaves correctly from Android 11 up without per-version
                        // branching in this file.
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                isLoading = false
                            }

                            // Keep our own bundled pages inside the WebView, but
                            // hand off any real external link (http/https) to the
                            // system browser instead of rendering it in-app.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url
                                return if (target.scheme == "http" || target.scheme == "https") {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        loadUrl(url)
                        webViewRef = this
                    }
                }
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                }
            }
        }
    }
}

package com.shopmanager.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.withContext

/**
 * يعرض الرقم النهائي فقط: لا عدّ تصاعدي ولا أرقام وسيطة.
 * التنسيق يتم على الخيط الخفي ([AppCompute]) ولا يظهر النص إلا حين يجهز الرقم الصحيح
 * ([animate] = false تعني أن البيانات لم تُحمَّل بعد، فلا يظهر شيء).
 * التوقيع كما هو، فلا تغيير مطلوب في أي شاشة تستدعيه.
 */
@Composable
fun AnimatedCounterText(
    targetValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold,
    animate: Boolean = true
) {
    val currentFormat by rememberUpdatedState(format)
    val text by produceState<String?>(null, targetValue, animate) {
        if (animate) value = withContext(AppCompute.dispatcher) { currentFormat(targetValue) }
    }
    Text(text ?: "", modifier = modifier, style = style, fontWeight = fontWeight, maxLines = 1)
}

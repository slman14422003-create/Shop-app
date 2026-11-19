package com.shopmanager.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * خيط خلفي واحد مخفي لكل التطبيق: كل العمليات الحسابية الخاصة بالعرض (مجاميع، تصنيف، تجميع، تنسيق أرقام)
 * تجري عليه بالتسلسل، فلا تزاحم خيط الرسم ولا تتشابك مع بعضها.
 */
object AppCompute {
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "app-compute").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }.asCoroutineDispatcher()
}

/**
 * يحسب [compute] على الخيط الخفي ويُعيد null حتى تجهز النتيجة، ثم النتيجة النهائية فقط.
 * عند تغيّر [keys] تبقى النتيجة القديمة معروضة حتى تجهز الجديدة (بلا وميض ولا قيم وسيطة).
 */
@Composable
fun <T : Any> rememberComputed(vararg keys: Any?, compute: () -> T): State<T?> =
    produceState<T?>(null, *keys) {
        value = withContext(AppCompute.dispatcher) { compute() }
    }

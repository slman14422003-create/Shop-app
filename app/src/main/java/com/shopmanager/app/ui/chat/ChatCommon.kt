package com.shopmanager.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.shopmanager.app.ui.common.avatarColorFor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** أول حرفين من الاسم لصورة رمزية بالأحرف (حرف واحد لكل كلمة، أو أول حرفين من الكلمة الوحيدة). */
fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "؟"
        parts.size == 1 -> parts[0].take(2)
        else -> parts[0].take(1) + parts[1].take(1)
    }
}

/** صورة رمزية دائرية بالأحرف الأولى، بلون ثابت لكل اسم (نفس ألوان عملاء الديون). */
@Composable
fun ChatAvatar(name: String, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.34f).sp
        )
    }
}

/**
 * نبضة "متصل الآن": تُرسل كل 45 ثانية ما دامت الشاشة ظاهرة (STARTED) فقط،
 * فلا يظهر المستخدم متصلاً وهو خارج التطبيق.
 */
@Composable
fun ChatPresenceEffect(heartbeat: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val beat by rememberUpdatedState(heartbeat)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                beat()
                delay(45_000L)
            }
        }
    }
}

private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.US)
private val dateFmt get() = SimpleDateFormat("d/M/yyyy", Locale.US)
private val dayNames = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

fun formatClock(ms: Long): String = if (ms <= 0) "" else timeFmt.format(Date(ms))

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun sameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

/** وقت صف قائمة المحادثات: اليوم → الساعة، أمس، اسم اليوم خلال الأسبوع، وإلا التاريخ. */
fun formatListTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    val days = ((startOfDay(now) - startOfDay(ms)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> formatClock(ms)
        days == 1 -> "أمس"
        days < 7 -> dayNames[Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK) - 1]
        else -> dateFmt.format(Date(ms))
    }
}

/** فاصل تاريخ داخل المحادثة. */
fun formatDaySeparator(ms: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((startOfDay(now) - startOfDay(ms)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "اليوم"
        days == 1 -> "أمس"
        days < 7 -> dayNames[Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK) - 1]
        else -> dateFmt.format(Date(ms))
    }
}

fun formatLastSeen(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    return when {
        sameDay(ms, now) -> "آخر ظهور ${formatClock(ms)}"
        else -> "آخر ظهور ${formatListTime(ms, now)}"
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun chatMutedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

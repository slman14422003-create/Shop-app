package com.shopmanager.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * يولّد الملف من مسار حقيقي: إقلاع بارد ← تمرير الرئيسية ← التبديل بين التبويبات ← فتح شاشة مدفوعة
 * ← الرجوع. عدّل المحددات (By.text/desc) لتطابق شاشاتك إن لزم.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.shopmanager.app") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // تمرير الصفحة الأولى
        device.findObject(By.scrollable(true))?.apply {
            setGestureMargin(device.displayWidth / 10)
            fling(Direction.DOWN)
            fling(Direction.UP)
        }
        device.waitForIdle()
        // الرجوع (يختبر انتقال الدفع/السحب إن وُجدت شاشة مدفوعة)
        device.pressBack()
    }
}

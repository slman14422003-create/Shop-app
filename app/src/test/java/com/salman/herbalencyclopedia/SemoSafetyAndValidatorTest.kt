package com.salman.herbalencyclopedia

import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.ai.SemoReplyPolicy
import com.salman.herbalencyclopedia.data.ai.local.RephraseValidator
import com.salman.herbalencyclopedia.data.search.GroundedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemoSafetyAndValidatorTest {

    private val points = listOf(
        GroundedPoint("h0", "الزنجبيل", "الفوائد", "يفيد في الغثيان ودوار السفر", 1.0),
        GroundedPoint("h0", "الزنجبيل", "التحذيرات", "يُحذر منه لمن يتناولون مميعات الدم", 0.9)
    )

    @Test
    fun noticeRequiredForSensitiveQuestions() {
        assertTrue(HerbAssistant.requiresDoctorNotice("هل الزنجبيل آمن للحامل؟"))
        assertTrue(HerbAssistant.requiresDoctorNotice("هل يناسب طفلي"))
        assertFalse(HerbAssistant.requiresDoctorNotice("ما فوائد الزنجبيل؟"))
    }

    @Test
    fun noticeAppendedOnce() {
        val once = SemoReplyPolicy.appendNotice("نص")
        assertEquals(once, SemoReplyPolicy.appendNotice(once))
    }

    @Test
    fun shapeKeepsNoticeInShortAndFull() {
        val body = (1..8).joinToString("\n") { "السطر رقم $it فيه معلومة طويلة نوعاً ما للاختبار" }
        val shaped = SemoReplyPolicy.shape(SemoReplyPolicy.appendNotice(body))
        assertTrue(shaped.shortText.contains(SemoReplyPolicy.DOCTOR_NOTICE))
        assertNotNull(shaped.fullText)
        assertTrue(shaped.fullText!!.contains(SemoReplyPolicy.DOCTOR_NOTICE))
    }

    @Test
    fun validatorAcceptsFaithfulRephrase() {
        val out = "الزنجبيل يفيد في الغثيان ودوار السفر، لكن يُحذر منه لمن يتناولون مميعات الدم."
        assertTrue(RephraseValidator.validate(out, points).ok)
    }

    @Test
    fun validatorRejectsMissingHerbWarningAndInventedNumbers() {
        assertFalse(RephraseValidator.validate("يفيد في الغثيان ودوار السفر ويساعد على الراحة العامة.", points).ok)
        assertFalse(RephraseValidator.validate("الزنجبيل يفيد في الغثيان ودوار السفر ويساعد على الراحة العامة.", points).ok)
        assertFalse(RephraseValidator.validate("الزنجبيل يفيد في الغثيان، ويُحذر منه لمن يتناولون مميعات الدم، والجرعة 500 مغ.", points).ok)
    }

    @Test
    fun validatorStripsThinkingBlocks() {
        assertEquals("نص", RephraseValidator.stripThinking("<think>تفكير</think>نص"))
    }
}

package org.aust.dialer

import org.aust.dialer.core.PhoneUtils
import org.aust.dialer.data.CallGrouping
import org.aust.dialer.data.CallLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {
    @Test fun arabicIndicDigitsAreNormalised() {
        assertEquals("0933123", PhoneUtils.digitsOnly("٠٩٣٣ ١٢٣"))
    }

    @Test fun sanitizeKeepsDialableCharactersOnly() {
        assertEquals("+963933123#", PhoneUtils.sanitizeDialable("+963 (933) 12-3#"))
        assertEquals("123", PhoneUtils.sanitizeDialable("12+3"))
    }

    @Test fun sameSubscriberMatchesAcrossFormats() {
        assertEquals(PhoneUtils.matchKey("+963933123456"), PhoneUtils.matchKey("0933123456"))
    }

    @Test fun t9() {
        assertEquals("5646", PhoneUtils.t9Word("John"))
        assertEquals(listOf("254", "24623"), PhoneUtils.t9Words("Ali Ahmad"))
        assertTrue(PhoneUtils.t9Matches(PhoneUtils.t9Words("Ali Ahmad"), "246"))
        assertFalse(PhoneUtils.t9Matches(PhoneUtils.t9Words("Ali Ahmad"), "999"))
        assertTrue(PhoneUtils.t9Words("علي").isEmpty())
    }

    @Test fun unknownNumbers() {
        assertTrue(PhoneUtils.isUnknown(""))
        assertTrue(PhoneUtils.isUnknown("-1"))
        assertFalse(PhoneUtils.isUnknown("123"))
    }

    @Test fun callGroupingMergesAllCallsForSameNumber() {
        fun e(id: Long, n: String, t: Int) = CallLogEntry(id, n, t, id, 0, null, null)
        val groups = CallGrouping.group(
            listOf(e(5, "0933123456", 1), e(4, "+963933123456", 1), e(3, "0933123456", 3), e(2, "", 1), e(1, "", 1)),
        )
        assertEquals(listOf(3, 1, 1), groups.map { it.count })
    }
}

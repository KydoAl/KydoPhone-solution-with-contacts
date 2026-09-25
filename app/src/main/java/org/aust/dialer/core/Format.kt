package org.aust.dialer.core

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import java.util.Locale

object Format {
    /** Locale-aware display formatting; falls back to the raw number for USSD codes, short codes, etc. */
    fun number(raw: String): String {
        if (raw.isBlank()) return raw
        if (raw.startsWith("*") || raw.startsWith("#")) return raw
        return try {
            val country = Locale.getDefault().country
            PhoneNumberUtils.formatNumber(raw, if (country.isNullOrEmpty()) "US" else country) ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /**
     * Wraps text in Unicode LRI/PDI so digits keep their left-to-right order inside RTL (Arabic) layouts
     * while the paragraph itself still aligns with the layout direction.
     */
    fun ltr(text: String): String = "\u2066$text\u2069"

    fun callTime(context: Context, millis: Long): String {
        val flags = if (DateUtils.isToday(millis)) {
            DateUtils.FORMAT_SHOW_TIME
        } else {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        }
        return DateUtils.formatDateTime(context, millis, flags)
    }

    fun duration(seconds: Long): String = DateUtils.formatElapsedTime(seconds.coerceAtLeast(0))
}

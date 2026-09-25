package org.aust.dialer.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Application-level language selection. The choice is stored in [Prefs]; an empty value means
 * "follow the system language". Layout direction follows the selected locale, so Arabic is real RTL.
 */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val lang = Prefs.readLanguage(base)
        if (lang.isEmpty()) return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

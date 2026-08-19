package com.sonar.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.sonar.app.data.AppLanguage
import java.util.Locale

object LocaleHelper {
    private val systemDefaultLocale: Locale = Locale.getDefault()

    fun getLocale(language: AppLanguage): Locale = when (language) {
        AppLanguage.SYSTEM -> systemDefaultLocale
        AppLanguage.RU -> Locale.forLanguageTag("ru")
        AppLanguage.EN -> Locale.forLanguageTag("en")
    }

    fun applyLanguage(language: AppLanguage) {
        val locale = getLocale(language)
        runCatching { Locale.setDefault(locale) }

        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.RU -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
        }
        runCatching { AppCompatDelegate.setApplicationLocales(locales) }
    }
}

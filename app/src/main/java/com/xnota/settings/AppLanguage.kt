package com.xnota.settings

import android.content.Context
import android.content.res.Configuration
import com.xnota.platform.JsonStore
import java.util.Locale

/**
 * xnota: applies the persisted app language ([Settings.appLanguage]) to the
 * activity's base context, so every resource — including the layout direction the
 * whole Compose tree reads — matches it from the first frame.
 *
 * "system" (the default) leaves the context untouched and the OS locale wins, which
 * is exactly upstream behaviour. The stored key is ignored by the original build.
 */
object AppLanguage {

    /** The language tag persisted in settings ("system" follows the OS). */
    fun persistedTag(base: Context): String = try {
        JsonStore.settings(base).read().optString("app_language", "system")
    } catch (_: Exception) {
        "system"
    }

    /** Wraps [base] with the persisted locale and its matching layout direction. */
    fun wrap(base: Context): Context {
        val tag = persistedTag(base)
        if (tag == "system") return base
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isBlank()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

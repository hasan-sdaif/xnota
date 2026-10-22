package com.xnota.settings

import java.util.Locale

/**
 * xnota: digit shaping for display — ASCII 0-9 become the Arabic-Indic digits ٠١٢٣٤٥٦٧٨٩
 * (U+0660..U+0669) when the [Preferences.digitSystem] pref says so.
 *
 * "auto" follows the app language exactly the way [AppLanguage] resolves it: the persisted tag
 * "ar" is Arabic, "system" defers to the OS locale, anything else (e.g. "en") is Latin.
 *
 * Display-only by contract: nothing written to disk — file names, JSON payloads, exports —
 * ever passes through [apply]; only text rendered on screen does.
 */
object DigitSystem {

    /** Valid [Preferences.digitSystem] values. */
    const val AUTO = "auto"
    const val LATIN = "latin"
    const val ARABIC = "arabic"

    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    /**
     * Whether [system] resolves to Arabic-Indic digits, given the persisted [appLanguage] tag
     * ("system" | "ar" | "en", as [Settings.appLanguage] stores it). Unknown values behave
     * like "auto", matching the forgiving read in [Preferences.fromJson].
     */
    fun active(system: String, appLanguage: String): Boolean = when (system) {
        ARABIC -> true
        LATIN -> false
        else -> when (appLanguage) {
            // The OS locale decides, the same default AppLanguage falls back to.
            "system" -> Locale.getDefault().language == "ar"
            else -> appLanguage.startsWith("ar")
        }
    }

    /** Maps the ASCII digits in [s] to Arabic-Indic ones; every other character passes through. */
    fun apply(s: String): String {
        if (!s.any { it in '0'..'9' }) return s
        val out = StringBuilder(s.length)
        for (c in s) out.append(if (c in '0'..'9') ARABIC_DIGITS[c - '0'] else c)
        return out.toString()
    }
}

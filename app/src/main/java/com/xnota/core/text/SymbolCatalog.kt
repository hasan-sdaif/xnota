package com.xnota.core.text

/**
 * The flow editor's symbol & emoji catalog (the fork): the Arabic punctuation and marks an Arabic
 * keyboard leaves out, plus the symbols everyone keeps handy. Pure — the popup grid asks for the
 * categories and inserts whatever tile was tapped, so the catalog the tests pin is the one the
 * editor shows.
 *
 * [Category.symbols] are single characters; [Category.label] is a stable token the UI maps to its
 * localized name (see the `symbols_*` strings).
 */
object SymbolCatalog {

    /** A group of insertable characters. */
    class Category(val label: String, val symbols: List<String>)

    /** Arabic punctuation: the comma, semicolon, question mark, guillemets and friends. */
    val ARABIC_PUNCTUATION = listOf(
        "،", "؛", "؟", "«", "»", "„", "‟", "؞", "۩", "۝", "۞", "ـ", "٭", "؋",
    )

    /** Marks: the Arabic footnote/verse end markers and the reference braces. */
    val ARABIC_MARKS = listOf(
        "﴿", "﴾", "ؑ", "ؐ", "ؒ", "ؓ", "ؔ", "ՙ", "ƞ",
    )

    /** The symbols everyone reaches for: ticks, stars, hearts, arrows. */
    val COMMON = listOf(
        "✓", "✔", "✗", "✘", "★", "☆", "♥", "♡", "→", "←", "↑", "↓", "↔", "⇒", "✦", "✧",
        "•", "‣", "·", "…", "—", "–", "†", "‡", "§", "¶", "№", "℗", "©", "®", "™",
    )

    /** Quick emoji: faces and the everyday objects a note names. */
    val EMOJI = listOf(
        "😀", "🙂", "😎", "🤔", "😅", "😍", "😭", "😡", "👍", "👎", "👏", "🙏",
        "❤", "🔥", "⭐", "✨", "💡", "📌", "📎", "📝", "📅", "⏰", "✅", "❌",
        "🌙", "🕌", "📖", "☕",
    )

    /** Every category, in the order the picker shows them. */
    val CATEGORIES = listOf(
        Category("symbols_arabic_punct", ARABIC_PUNCTUATION),
        Category("symbols_arabic_marks", ARABIC_MARKS),
        Category("symbols_common", COMMON),
        Category("symbols_emoji", EMOJI),
    )

    /** Every insertable symbol, in category order. */
    val ALL: List<String> = CATEGORIES.flatMap { it.symbols }
}

package com.xnota.core.util

/**
 * The fork's own release ledger: what each wave of the fork brought, newest first, as stable ids
 * the About pane maps onto its string resources. Pure data with no Android dependency, so the
 * shape of the list (every version unique, every release carrying its headlines) is testable
 * like any other pure layer — the wording itself lives in the two locales' string tables.
 */
object ForkChangelog {

    /** One headline of a release, as the id its string resource hangs from. */
    data class Entry(val id: String)

    /** One release of the fork: its version header and its headline entries in reading order. */
    data class Release(val version: String, val entries: List<Entry>)

    /** The releases the About pane lists, newest first. */
    val releases: List<Release> = listOf(
        Release(
            "1.9.0",
            listOf(
                Entry("native_brand"),
                Entry("right_edge_navigation"),
                Entry("brand_green_seed"),
            ),
        ),
        Release(
            "1.8.0",
            listOf(
                Entry("brand_relaunch"),
                Entry("reference_home"),
                Entry("room_chrome"),
                Entry("secondary_cards"),
            ),
        ),
        Release(
            "1.7.0",
            listOf(
                Entry("emerald_identity"),
                Entry("home_dashboard"),
                Entry("floating_toolbar"),
            ),
        ),
        Release(
            "1.6.1",
            listOf(
                Entry("joined_stroke_fix"),
                Entry("settings_hub"),
                Entry("menu_icons"),
            ),
        ),
        Release(
            "1.6",
            listOf(
                Entry("rounded_image_corners"),
                Entry("prefs_search"),
                Entry("reader_brightness"),
                Entry("page_number_stamps"),
                Entry("sticky_preset"),
                Entry("search_history"),
            ),
        ),
        Release(
            "1.5",
            listOf(
                Entry("pin_lock"),
                Entry("encrypted_backups"),
                Entry("app_imports"),
                Entry("text_exports"),
                Entry("version_history"),
                Entry("duplicate_arrays"),
                Entry("measure_tool"),
            ),
        ),
        Release(
            "1.4",
            listOf(
                Entry("ink_engine"),
                Entry("brush_studio"),
                Entry("layers_panel"),
                Entry("align_mirror"),
                Entry("arrowheads_patterns"),
            ),
        ),
        Release(
            "1.3",
            listOf(
                Entry("reading_mode"),
                Entry("focus_timer"),
                Entry("find_replace"),
                Entry("organization"),
                Entry("smart_folders"),
            ),
        ),
        Release(
            "1.2",
            listOf(
                Entry("calligraphy_rulings"),
                Entry("hijri_dates"),
                Entry("arabic_digits"),
                Entry("loose_arabic_search"),
            ),
        ),
        Release(
            "1.0",
            listOf(
                Entry("arabic_first"),
                Entry("any_folder_backups"),
                Entry("no_accounts"),
            ),
        ),
    )

    /** The version headers in listing order (newest first). */
    fun versions(): List<String> = releases.map { it.version }

    /** A version's headline ids, or null when the ledger holds no such version. */
    fun entriesFor(version: String): List<String>? =
        releases.firstOrNull { it.version == version }?.entries?.map { it.id }
}

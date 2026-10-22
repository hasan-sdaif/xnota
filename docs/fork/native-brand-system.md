# XNota — Native Brand System (merged)

This brand system is implemented in the Android app itself with Kotlin and Jetpack Compose. It is not a web mock-up. It merges two independent brand passes — the reference-studio chrome (1.8.0's identity foundation and presentation) and the native-brand mark/navigation pass — into one system.

## Identity

- **Primary mark:** the X + fountain-pen nib cropped from `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`, stored as `app/src/main/res/drawable-nodpi/xnota_brand_mark.png`.
- **Wordmark:** the mark paired with "Nota", so the icon supplies the X without duplicating it.
- **Arabic tagline:** `اكتب أفكارك… في أي وقت`.
- **English tagline:** `Write your ideas — anytime`.

## Colour tokens

The chrome's colour source is `com.xnota.ui.theme.XNotaIdentity` (the reference design's tokens: the ink surface ladder, the emerald accent `#00E676`, sage hairlines) applied unconditionally by `Editor.buildPalette` — a user's re-picked accent and the appearance mode (dark/light/OLED) are the only colour choices that reach the chrome. The brand mark's tile gradient (forest → ink) is artwork, held in `com.xnota.ui.brand.XNotaBrand` alongside the reusable composables.

## Layout and interaction principles

- Navigation defaults to the **physical right edge** on tablets and the phone drawer, independent of the interface reading direction; an old stored "auto" value migrates once to the right edge (left and bottom remain available through Preferences).
- Arabic content stays RTL and English content stays LTR inside navigation and content panes.
- Active destinations get the emerald selected surface, mint icon/text and a slim accent edge marker; inactive destinations use quiet palette text.
- Navigation rows target a comfortable 44 dp height; the compact rail keeps a recognizable brand mark while conserving width.
- About, the sidebar, the rail and the folder-picker header share the same reusable brand composables (`XNotaBrandMark`, `XNotaBrandLockup`, `XNotaBrandDivider`), not separate hand-drawn copies of the logo.

## Native implementation

- `app/src/main/java/com/xnota/ui/brand/XNotaBrand.kt` — reusable brand mark, wordmark lockup, divider and edge marker.
- `app/src/main/java/com/xnota/ui/theme/XNotaIdentity.kt` — the reference chrome tokens and `xnotaChrome()`, the unconditional palette builder.
- `app/src/main/java/com/xnota/ui/Backstage.kt` — sidebar, compact right-side drawer, rail, and the navigation row recipe.
- `app/src/main/java/com/xnota/settings/Settings.kt` — right-edge navigation default and one-time migration of the old automatic position.
- `app/src/main/java/com/xnota/ui/PreferencesPane.kt` — immediate left/right/bottom navigation choices.
- `app/src/main/java/com/xnota/ui/AboutPane.kt` — the shared logo lockup crowning the About hero.

## Verification

`SettingsTest.navigationMigratesToTheStableRightEdge` locks the edge migration; `XNotaIdentityTest` locks the chrome tokens and the identity-survives-any-seed property; `SettingsTest`'s v4 cases lock the one-time material identity reset. Visual verification runs in CI (`screenshots` workflow): an Arabic-RTL emulator walks the app and uploads screen captures for review against the reference before each release.

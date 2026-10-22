<div dir="rtl">

# ✕ إكس نوتا — XNota

**اكتب أفكارك… في مكان واحد**

تطبيق تدوين ملاحظات ورسم بخط اليد لأجهزة أندرويد والأجهزة اللوحية، مصمَّم للعربية أولًا.

</div>

---

**XNota** (إكس نوتا) is a handwriting notes, sketching and PDF-annotation app for
Android, built Arabic-first and privacy-first:

- **عربية أولًا** — full Arabic translation, RTL layouts, 19 bundled Arabic font
  families (Naskh, Kufi, Ruqaa, Nastaliq…), Arabic-Indic digits and Hijri calendar
  options, Arabic page templates (Quran study, Hijri planner, Cornell عربي…).
- **Stylus power** — pressure with per-tool response curves, tilt shading,
  two-button pen mapping (Xiaomi Smart Pen, S-Pen, USI…), hover tools.
- **Gestures** — two-finger tap = undo, three-finger tap = redo (configurable),
  shape snapping, lasso, split view.
- **Adaptive ink** — stored ink colours re-tone themselves to the paper, so white
  ink on a dark page reads black on a light page; the saved colours never change.
- **Notebook engine** — paged notebooks + infinite canvas, a custom native ink
  engine (PDFium, tree-sitter code blocks, LaTeX), PDF import/annotation/export,
  templates, covers, themes.
- **Zero network** — no permissions, no accounts, no ads, no telemetry. Your notes
  live in files you own, via SAF folders (works with any Drive).

## Build

Requirements: JDK 17, Android SDK 36, NDK 27.0.12077973, CMake 3.22.1.

```
./gradlew assembleRelease
```

Signing reads a gitignored `keystore.properties` (see
`keystore.properties.example`). CI builds signed releases from GitHub Secrets on
every `v*` tag.

## License

MIT — see [LICENSE](LICENSE). Bundled fonts are SIL OFL (in-app license screen
lists every family).

# Downly UI Redesign - Progress File

Hand this file + the latest project zip + Downly_Design_AI.zip to any Claude. Read this file first, then continue from the next pending step.
(Ei file ar project zip ar design zip onno Claude account e dile ekdom ei jaiga theke kaj shuru kora jabe.)

## Task (from the user)
- Repo: https://github.com/darkrider13/Downly (branch main). User uploads files to GitHub manually from a phone.
- Apply the design in `Downly_Design_AI.zip` to the Kotlin / Jetpack Compose app, matching the PNG screenshots exactly,
  in BOTH Day and AMOLED Dark modes. Read PROMPT.md first, view every PNG, use the HTML only for exact colours / sizes.
- Change ONLY the UI. Do not change download logic. Do not remove any existing feature.
- Work in small steps. After each step give ONLY the changed files (with folder paths) + update this file.
- No Android SDK in the sandbox: code is NOT compiled. Write code that compiles without testing and re-read it carefully.

## User rules (every reply)
- Reply in Bengali (code, file names and links stay as they are).
- Repo link in a code block. Upload table: left = tappable `REPO/upload/main/<folder>` link, right = files; one row per folder.
- ONE zip: top folder `Downly_UI_StepN_Upload`, numbered subfolders (01_root, 02_...) in the same order as the table, original file names.
  Dot files get an underscore prefix (`_gitignore`). A single changed file is given directly, no zip.
- Never include secrets (keystores, keystore.properties, .env, API keys, signing-private/).
- When behaviour or look changes, explain the change with a concrete example in Bengali.

## Steps
| # | Step | Status |
|---|------|--------|
| 1 | Theme foundation (Day / AMOLED Dark tokens, background glows, icons, shared buttons, theme setting) + Home screen (header, chips, cards, progress, white + button) | DONE |
| 2 | Plus button animated menu (From clipboard / Add link, overlay, rotate to x) + "Add link" bottom sheet | PENDING |
| 3 | Delete bottom sheet ("Delete download?" + checkbox + Cancel / Delete) + toast pill ("File deleted from phone" / "Removed from list") + cancel-download and details dialogs restyled | PENDING |
| 4 | Settings as a full screen (glass top bar with back arrow, white section pills, glass groups, segmented theme switch, custom 38x22 toggles, white Done button) | PENDING |
| 5 | Final polish: update banner / update popup / offline banner / search field / empty states, last check of every screen against the PNGs, README + PROGRESS.md notes | PENDING |

Progress: 1 of 5 steps done | 4 remaining | 20% done, 80% remaining.

## Step 1 notes (done)
New file `DownlyTheme.kt` (package com.downly.app) holds everything shared:
- `enum ThemeMode { AUTO, DAY, DARK }` (+ `isDark(systemDark)`, `fromKey`). Saved as `theme_mode` in SettingsRepository, part of AppSettings, SettingNames.THEME,
  included in settings export / import (SettingsBackup key `theme_mode`). Default = AUTO.
- `DownlyColors` with `DownlyDark` / `DownlyDay` (all colours from PROMPT.md). Use inside any composable: `val c = Downly.colors`
  tokens: text, textSecondary, glassFill, glassBorder, green (text / Download button), greenBar, onGreen, red, onRed, track, paused,
  divider, sheetFill, scrim, plusOverlay, whiteBorder, toggleOffTrack, toggleOffBorder, background, isDark.
- `DownlyTheme(mode) { }` (wraps MaterialTheme too), `DownlyBackground { }` (soft elliptical glows, 5 in dark, 3 in day).
- Modifiers: `Modifier.glass(shape, strong)` (theme aware; strong = sheetFill), `Modifier.whiteButton(shape)` (white fill, theme border, small shadow),
  `Modifier.pressable(enabled, pressedScale, onClick)`.
- Buttons: `GlassIconButton` (= WHITE round icon button, default 34, tap area >= 40), `PlainIconButton` (no background, 40 tap, 18 icon),
  `PillButton(text, onClick, ..., green, danger, outlineRed, small)`: default WHITE, green = only Download, danger = red filled (Delete),
  outlineRed = red outline (Cancel), small = 32 high. `primary` is ignored (kept so old calls compile).
  `ThemeSegmented` (Auto / Day / Dark), `GlassDialog` (still a centred dialog, glass strong fill).
- `DownlyIcons`: 1.8 stroke icons from the design paths: Search, Pause, ClearAll, Settings, Play, Close, OpenFile, Trash, Plus, Minus, Check, Back,
  Link, Clipboard, Retry, FileVideo, FileZip, FileDoc, FileImage, FileAudio, FileApp, FileOther. Built with PathParser, tint with Icon(tint = ...).
- The old `Palette` object is REMOVED. Old dialogs / settings now use the theme tokens (mapped: Ice->text, Mist->textSecondary, Sky->text,
  Amber->paused, Coral->red, Mint/Lime->green) so they are readable in both modes, but they are NOT pixel matched yet (steps 2-5).

MainActivity.kt (Step 1):
- onCreate: reads themeMode, `DownlyTheme(...)`, status / navigation bar icons follow Day / Dark (`SystemBarStyle.auto`).
- Header (glass, radius 18): "Downloads" 18sp bold + "1 active · 7.5 MB/s" 11sp, icons search (plain), pause all (white 32), clear finished (red plain), settings (plain).
  Search toggle moved from the chip row to the header.
- DownloadCard: 34 tile, name 13sp one line, status 12sp (green bold / gray / red), bar 6dp (green, gray when paused / waiting, hidden when done),
  size line 11sp ("612.4 / 916.3 MB · 40s left", "4.2 MB · Downloads"), white 34 circle + plain red icon. List: side margin 12, gap 10.
- Plus button: only a 56 white circle with a black "+" at bottom right (right 16, bottom 18). It still opens the Add dialog directly (menu = Step 2).
- Settings dialog (old centred popup) got a temporary-but-working Theme row (Auto / Day / Dark) so both modes can be tested now.
  Settings toggles (`GlassSwitch`) use white track + black knob; Step 4 replaces them with the exact 38x22 custom toggle.
- Add dialog: "Download" button is green (green = true).
DownloadTools.kt: new `FilterBar(items, filter, onFilter)` (chips "All 3", height 32, radius 16, gap 6; selected = white black bold), `glassFieldColors()` theme aware.
UpdateUi.kt: colours mapped, `GlassProgress(progress, indeterminate, color)` new signature (flat colour).

## Next: Step 2 (Plus menu + Add link sheet) - what to do
- Plus tap: "+" rotates 45 degrees to "x" (250 ms), overlay `c.plusOverlay` fades in (200 ms) with light blur, two options above, right aligned, gap 14:
  bottom "Add link" (DownlyIcons.Link), above it "From clipboard" (DownlyIcons.Clipboard). Each = glass label chip (radius 12, 13sp semibold, min height 36)
  + 46 white circle icon. Animation: up 18 + scale from 0.8 with small bounce, 260 ms, second one delayed 60 ms. Tap overlay or "x" closes.
  Options container: right 16, bottom 88. Close button stays at the same place as the + (56, right 16, bottom 18).
- "From clipboard" = same as the existing clipboard-prefill logic in AddLinkDialog (do not change download logic).
- Add link = glass bottom sheet (margin 8, radius 24, handle 36x4, slide up 300 ms, title 15sp bold, input labelled "File link", input height 44 glass radius 12,
  buttons Cancel (outlineRed) + Download (green)). No title text is given in the design for this sheet: use "Add link". Keep the existing features: Connect / link check, optional file name, duplicate warning, "Download again".

## Things to remember
- Code never compiled here. First real build happens on GitHub (release.yml). If the user reports compile errors, fix those first.
- Download engine, database, service, update manager, backup logic were NOT touched (only SettingsBackup got the theme key).
- `window background` in themes.xml is black: a very short black flash can show on cold start in Day mode (can be fixed in a later step).

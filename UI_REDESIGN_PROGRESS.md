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
| 2 | Plus button animated menu (From clipboard / Add link, overlay, rotate to x) + "Add link" bottom sheet | DONE |
| 3 | Delete bottom sheet ("Delete download?" + checkbox + Cancel / Delete) + toast pill ("File deleted from phone" / "Removed from list") + cancel-download and details dialogs restyled | DONE |
| 4 | Settings as a full screen (glass top bar with back arrow, white section pills, glass groups, segmented theme switch, custom 38x22 toggles, white Done button) | DONE |
| 5 | Final polish: update banner / update popup / offline banner / search field / empty states, last check of every screen against the PNGs, README + PROGRESS.md notes | PENDING |

Progress: 4 of 5 steps done | 1 remaining | 80% done, 20% remaining.

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

## Step 2 notes (done)
DownlyTheme.kt (new shared pieces):
- `GlassBottomSheet(onDismiss, title) { }`: Dialog, own scrim (`c.scrim`, window dim switched off), glass strong sheet, margin 8, radius 24, handle 36x4,
  title 15sp bold, slides up 300 ms, above the keyboard (imePadding + navigationBarsPadding). Tap on scrim / Back closes it. USE THIS for the Delete sheet in Step 3.
- `GlassField(value, onValueChange, placeholder, ..., focusRequester, trailing)`: single-line input, height 44, radius 12, glass fill + border (red border when isError).
MainActivity.kt:
- `AddButton` is replaced by `PlusMenu` (BoxScope) + `PlusMenuOption`. State `menuOpen` (rememberSaveable) lives in MainScreen.
  "+" rotates 45 deg (250 ms) into "x"; overlay `c.plusOverlay` fades 200 ms; the list column gets `Modifier.blur(0..10dp)` (works on Android 12+, simply no blur below).
  Options: container right 21 (centres the 46 circle on the 56 button), bottom 88, gap 14; "Add link" bottom, "From clipboard" above (60 ms later);
  chip = glass strong radius 12, 13sp semibold, min height 36; circle 46 white. Bounce = cubic-bezier(.34,1.56,.64,1), 260 ms, up 18, scale from 0.8. Back button closes the menu.
- "From clipboard": reads the clipboard, `extractLink()` (existing function); link found -> Add link sheet opens pre-filled; none -> Toast "No link found on the clipboard".
- "Add link": opens the sheet empty (the old "suggest the clipboard link if the field is empty" still runs inside the sheet, feature kept).
- `AddLinkDialog` now uses `GlassBottomSheet` (title "Add link"): "File link" field (+ clipboard paste icon), "File name (optional)" field, duplicate warning,
  small white "Connect" pill + status text (link check), buttons side by side: Cancel (red outline) + Download (green; "Download again" when duplicate).
  All logic (connect / probe / submit / duplicate check) is unchanged.
- Not pixel-checked: no screenshot exists for the Add link sheet, it follows the text in PROMPT.md.

## Step 3 notes (done)
DownlyTheme.kt: `DownlyCheckbox(checked)` (22 dp, radius 7; unchecked 2 dp gray border, checked white box + black tick), `DownlyToast(text)` (glass strong pill, min height 48, 28 white circle + tick, 13sp).
DownloadTools.kt:
- `ConfirmRemoveDialog(item, onCancel, onListOnly, onDeleteFile)` is now the "Delete download?" bottom sheet (name kept so MainActivity calls are unchanged):
  subtitle, glass row (radius 14) with checkbox "Also delete the file from phone" / "This can't be undone" (unchecked by default), Cancel (red outline) + Delete (red filled).
  Delete with checkbox off -> `onListOnly` (vm.remove), on -> `onDeleteFile` (vm.deleteWithFile). Same ViewModel calls as before.
- `ConfirmDeleteFileDialog` (from the details popup) and `DetailsDialog` are bottom sheets now (title "Delete file from phone?" / "Details"), text 13 / 11sp, Close = full-width white pill.
MainActivity.kt: `ConfirmCancelDialog` = sheet "Cancel this download?" with Keep (white) + Cancel download (red filled).
Toast: state `toastText` + `showToast()` in MainScreen, pill at bottom (left 16, right 84, bottom 22), slides up, hides after 2.6 s. Messages: "File deleted from phone"
(ViewModel text changed from "File deleted"), "Removed from list" (list-only delete), plus the existing "already gone" / "could not delete" texts. The old Android Toast is no longer used for deletes.
Not done on purpose: the "Cancel download" X on running cards still opens the cancel sheet (no toast).

## Step 4 notes (done)
MainActivity.kt: `SettingsDialog` (popup) is replaced by `SettingsScreen`, a full-screen overlay drawn INSIDE MainScreen's `DownlyBackground` box
(after PlusMenu / toast, so it covers them): `AnimatedVisibility(showSettings)`, own `DownlyBackground`, Back button / system Back / "Done" close it.
Same parameters and callbacks as the old dialog (nothing removed): theme, download folder (+ new "Default folder / Reset" row shown only when a custom folder is set =
the old "Use Downloads folder" button), simultaneous downloads, Wi-Fi only, notifications, auto update check, update row, export / import, file pickers.
- Layout: glass top bar (back arrow + "Settings" 16sp bold), white section pills (GENERAL, NETWORK AND ALERTS, UPDATES, BACKUP), `SettingGroup` (glass radius 16, margin 12),
  `SettingRow` min 52 / title 13sp / subtitle 11sp, `SettingDivider` = `c.divider`, full-width white "Done" (min height 48).
- `GlassSwitch` is now the custom 38x22 toggle (ON white track + black knob, OFF gray track + gray knob, 40 dp tap area, animated knob).
- `Stepper`: white round - / + (32) with `DownlyIcons.Minus / Plus`. Download folder subtitle = folder name only (e.g. "Movies").
UpdateUi.kt: row title "Check now", default subtitle "Downly <version>", small white pills.
Removed from the old popup: the "Downly <version>" line at the bottom (the version is now the subtitle of "Check now").

## Next: Step 5 (final polish) - what to do
- Restyle what is still old: `UpdateBanner` (under the header), `UpdateDialog` (UpdateUi.kt: use `GlassBottomSheet`, white / green / red button rules), `OfflineBanner`,
  `SearchField` (DownloadTools.kt: glass, radius 12, height 44 like `GlassField`), `EmptyState` / `EmptyListMessage`.
- Check every screen against the PNGs in Day and Dark (home, plus menu, delete sheet, toast, settings), fix small size / colour differences.
- `themes.xml` window background is black: use a day-aware window background to avoid a black flash on cold start in Day mode.
- Update README.md and PROGRESS.md notes (UI redesign finished, design tokens in DownlyTheme.kt). Then the first GitHub build: fix compile errors if any.

## Things to remember
- Code never compiled here. First real build happens on GitHub (release.yml). If the user reports compile errors, fix those first.
- Download engine, database, service, update manager, backup logic were NOT touched (only SettingsBackup got the theme key).
- `window background` in themes.xml is black: a very short black flash can show on cold start in Day mode (can be fixed in a later step).

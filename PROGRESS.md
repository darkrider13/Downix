# Downly - Progress File

Hand this zip to any Claude. Read this file first, then continue from the next pending step.
Build on the files already in this zip, update them, and return the full updated zip.

## Project
Android download manager (native Kotlin + Jetpack Compose). Paste a link, download inside the app.
- App name: **Downly** (renamed from "Glass Downloader" in Step 5) | package: com.downly.app
- UI language: English only
- UI design: see UI_REDESIGN_PROGRESS.md (Day + AMOLED Dark, glass surfaces, bottom sheets, full-screen Settings; tokens in DownlyTheme.kt)
- Features: progress bar, speed, size, pause / resume / cancel, background download with notification,
  saves to Downloads folder, download list persists after app is closed
- Icon: grey rounded square, white down arrow, long soft shadow. Source of truth: branding/downly-icon.svg
  (Android files in res/ were generated from it: adaptive icon, legacy PNGs mipmap-*/ic_launcher.png, drawable/ic_stat_download.xml)

## Rules from the user (Banglish speaker, reply in Banglish)
- One step at a time, one step = one deliverable zip with PROGRESS.md updated
- After each step show: done steps, remaining steps, "% remaining"
- Do NOT tell the user to build/test on GitHub after each step (only at the very end). Step 8 is the exception: the release pipeline needs the 4 GitHub secrets once
- Write code that compiles without testing (the user tests at the end); no invented logic when porting
- Zip must unzip into ONE folder (Downly/)

## Steps
| # | Step | Status |
|---|------|--------|
| 1 | Project setup (build.gradle.kts, settings, AndroidManifest, theme) | DONE |
| 2 | Download engine (OkHttp, pause/resume with Range header) | DONE |
| 3 | Database (Room, download list) | DONE |
| 4 | Main screen UI (glass + blue gradient, Compose) | DONE |
| 5 | Rebrand to Downly + new icon + foreground DownloadService + notifications | DONE |
| 6 | Smart engine: network monitor, auto-resume, auto-retry, Wi-Fi only, wake/wifi lock | DONE |
| 7 | Settings: preferences store + Settings screen | DONE |
| 8 | Settings backup (export / import), signed release key, in-app update checker, GitHub Actions build + publish release, R8, README | DONE |
| 9 | List tools and file actions: filters, search, details, copy / share / delete file, file name, duplicates, clipboard suggestion | DONE |

Progress: 9 of 9 done | 0 remaining | 0% remaining (all planned steps finished; next = first GitHub build and fixing compile errors if any)

(The user redefined Step 8 in the Step 8 chat: settings backup + release + updater came first. The old Step 8 "list tools" became Step 9;
the old Step 9 "GitHub Actions + R8 + README" was merged into the new Step 8.)

## Step 8 notes (done): settings backup, signing, updater, release pipeline
- SettingsRepository.kt: AppSettings got `autoUpdateCheck = true`; `setAutoUpdateCheck`, `replaceAll(AppSettings)` (import); new `object SettingNames`
  (user-visible names: "Simultaneous downloads", "Wi-Fi only", "Notifications", "Check for updates automatically") used by the Settings UI AND the backup file.
- SettingsBackup.kt: `export(settings, appVersion): String` (JSON: type "downly-settings-backup", formatVersion 1, appVersion, exportedAt,
  settings[] of {key, name, value}), `import(text, current): Pair<ImportResult, AppSettings>` (tolerant: unknown keys / wrong types skipped,
  ints range-checked, missing keys keep current value, file over 64 KB refused by the ViewModel). Only settings, never the download list.
  Adding a new setting = add it to AppSettings, SettingNames, SettingsRepository (key + read + replaceAll) and SettingsBackup (export + import).
- UpdateManager.kt: `UpdateManager.get(context)`, `state: StateFlow<UpdateState>` (Idle, Checking, UpToDate, Available, Downloading, ReadyToInstall, Error),
  `check(manual)`, `startDownload()`, `install(context): InstallStart`, `shouldAnnounce/markAnnounced`, `shouldNotify/markNotified`.
  Source = GitHub `releases/latest` of `BuildConfig.UPDATE_REPO` ("owner/repo", set by CI via -PupdateRepo, or `downly.updateRepo` in gradle.properties).
  Release tag `v<versionName>`, first `.apk` asset is downloaded to cache/updates, verified (same package, higher versionCode, same signing certificate),
  then installed via FileProvider + ACTION_VIEW (asks "install unknown apps" permission first). Auto check max every 6 h, last release is saved so the banner survives restarts.
- UpdateCheckWorker.kt: WorkManager periodic (12 h, network required), one "Downly update available" notification per version (channel "updates"),
  `UpdateCheckWorker.sync(context, enabled)` is driven by the autoUpdateCheck setting from MainViewModel.init.
- UpdateUi.kt: `UpdateBanner` (under header, tap opens popup), `UpdateDialog` (notes, Update now, progress, Install, errors), `UpdateSettingsRow`.
  MainActivity: popup opens by itself once per new version; Settings popup has sections Downloads / Updates / Backup; GlassDialog now scrolls;
  shared UI pieces (Palette, glass, pressable, GlassDialog, PillButton, GlassProgress, SettingRow, SettingDivider, GlassSwitch) are `internal`.
- app/build.gradle.kts: versionName = `<downly.versionBase>.<buildNumber>`, versionCode = buildNumber (min 1), BuildConfig.UPDATE_REPO, release signing from env
  (DOWNLY_KEYSTORE_PATH / _PASSWORD, DOWNLY_KEY_ALIAS / _PASSWORD) or signing-private/keystore.properties; assembleRelease FAILS without a key; R8 + shrinkResources on
  (app/proguard-rules.pro). Dependency added: androidx.work:work-runtime-ktx 2.9.0. Manifest: REQUEST_INSTALL_PACKAGES + FileProvider (res/xml/file_paths.xml).
- signing-private/ (NOT for GitHub, in .gitignore): downly-release.jks (alias downly, valid until 2054), keystore.properties, keystore.base64.txt, GITHUB_SECRETS.txt.
  GitHub secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Losing this key = users must uninstall once.
- .github/workflows/release.yml: on push to main/master (md and branding changes ignored) + manual run: checks secrets, Gradle 8.7, signed assembleRelease,
  apksigner print, `gh release create v<base>.<run number>` with Downly-<version>.apk. Repo must be public for the updater.
- README.md explains the flow and the secret setup.

## Step 9 notes (done): list tools and file actions
- DownloadTools.kt (new): `enum DownloadFilter` (ALL / ACTIVE = isActive or PAUSED / COMPLETED / FAILED, with empty texts), `applyListTools(items, filter, query)`
  (case-insensitive search on file name + url, newest-first order kept), `FilterBar` (chips with counts + search toggle at the END of the chip row,
  not in the header: the header has no room for a 4th button on 360dp phones), `SearchField`, `glassFieldColors()`, `EmptyListMessage`,
  `DetailsDialog` (name, link, size, added date, status, saved to, error; Copy link / Share file / Open / Delete file, file actions only for COMPLETED),
  `ConfirmDeleteFileDialog` ("Delete file from your phone?"), `copyLink`, `shareDownloaded` (ACTION_SEND with the MediaStore uri + clipData grant;
  Android 9- file paths go through FileProvider, res/xml/file_paths.xml got `external-path Download/`), `stateLabel`.
- DownloadDatabase.kt: `DownloadDao.findByUrl`, `DownloadRepository.findByUrl(url)` (normalizes the url first), `DownloadRepository.deleteWithFile(id): FileDeleteResult`
  (DELETED / ALREADY_GONE remove the row, FAILED keeps it; content uri via contentResolver.delete, file uri via File.delete; not finished items are just cancelled).
  No Room schema change.
- MainActivity.kt: tap on a card opens the details popup (the trash button on completed cards still only removes from the list);
  Add dialog has an optional "File name" field (passed to repo.add), duplicate check ("Already in your list" + "Download again"),
  clipboard link is pre-filled when the field is empty; MainViewModel.add(url, fileName), findDuplicate, deleteWithFile.
  GlassIconButton, FileTile, StatusLine, sizeLabel are now `internal`. Filter / search / query survive rotation (rememberSaveable).
- Renaming an already finished file is NOT implemented (only the name before downloading).

## Step 7 notes (done): SettingsRepository.kt + settings UI
- SettingsRepository.kt: `data class AppSettings(maxParallel = 3, wifiOnly = false, notifications = true)` (limits 1..5),
  `SettingsRepository.get(context)` singleton, SharedPreferences file "downly_settings", `settings: StateFlow<AppSettings>`,
  `current`, setMaxParallel / setWifiOnly / setNotifications
- DownloadRepository applies maxParallel + wifiOnly to the engine at start and on every change
- DownloadNotifier.notifyCompleted / notifyFailed return early when notifications are off (the ongoing foreground notification stays)
- MainActivity.kt: gear button in Header -> SettingsDialog (stepper 1..5, two glass switches, app version), OfflineBanner under the header
  when network is NONE; MainViewModel exposes settings, network and the three setters

## Step 6 notes (done): NetworkMonitor.kt + engine / repository changes
- NetworkMonitor.kt: `enum NetworkState { NONE, UNMETERED, METERED }`, `class NetworkMonitor(context).state: StateFlow<NetworkState>`
  (registerDefaultNetworkCallback, Wi-Fi to mobile switch does not show offline)
- DownloadState has a new value WAITING (alive but cannot run: no network, Wi-Fi only on mobile data, or waiting before a retry).
  `DownloadUpdate.error` / `DownloadItem.error` carries the reason text for WAITING ("Waiting for network", "Waiting for Wi-Fi",
  "Connection problem, retrying (n of 3)"). No Room schema change (state is stored as text). WAITING counts as active:
  activeCount, getActiveIds (pause all) and `DownloadItem.isActive` include it; resetInterrupted turns it into PAUSED after a process kill.
- DownloadEngine(context, network: StateFlow<NetworkState>, maxParallel = 3):
  - before every attempt `awaitNetwork`: not allowed -> WAITING until allowed, then QUEUED
  - network lost while downloading: running connections are cancelled, the download waits and continues by itself (resumes with Range)
  - retry: connection problems, HTTP 5xx / 408 / 429 -> up to 3 retries after 2s / 5s / 10s. A retry is not used up while the network is down.
    Never retried: HTTP 4xx, "Not enough storage space", errors while saving to Downloads (FatalDownloadException), invalid link
  - `setWifiOnly(Boolean)`, `setMaxParallel(Int)` work at runtime (private class Gate replaced the Semaphore)
- DownloadRepository: owns the NetworkMonitor, new `networkState: StateFlow<NetworkState>`, `setWifiOnly()`, `setMaxParallel()`
- DownloadService: `DownloadLocks` (PARTIAL_WAKE_LOCK + WifiLock) held only while an item is DOWNLOADING, wake lock timeout 10 min renewed
  by every notification refresh; notification says "Waiting to download" when everything is WAITING. Manifest got WAKE_LOCK.
- MainActivity.kt: StatusLine / progress colour handle WAITING (amber). Pause and cancel buttons work on WAITING items.

## Step 5 notes (done): rebrand + icon + DownloadService.kt
- Everything renamed: package com.downly.app, theme Theme.Downly, db file downly.db, user agent Downly/1.0, rootProject Downly
- branding/downly-icon.svg (+ downly-icon-512.png): 512x512 SVG, simple shapes only (rounded square, gradient, arrow, shadow)
- Android icon: adaptive (drawable/ic_launcher_background.xml + ic_launcher_foreground.xml, vector with a gradient shadow),
  legacy PNGs in mipmap-mdpi..xxxhdpi for Android 7.x, notification icon drawable/ic_stat_download.xml
- DownloadService.kt contains two things:
  - `object DownloadNotifier`: channels ("downloads" LOW, "downloads_done" DEFAULT), ongoing notification builder
    (title "Downloading N file(s)", percent + total speed, progress bar, inbox lines for up to 4 files, "Pause all" action),
    notifyCompleted (tap opens the file with ACTION_VIEW on the MediaStore uri, or the app on Android 9-), notifyFailed
    (with "Retry" action), cancelFinished
  - `class DownloadService`: foreground (dataSync) while activeCount > 0, stops itself 3 s after the last active download
    (grace time because resume writes its state to the DB slightly later), notification refresh max once per second,
    handles ACTION_PAUSE_ALL and ACTION_RETRY (extra download_id). `DownloadService.start(context)` is the helper the UI calls.
- DownloadRepository now posts the finished / failed notification itself in onUpdate (works even if the service is not running)
- MainViewModel.add / resume call DownloadService.start(app) after the repository call
- Targeting Android 15 later: dataSync foreground services have a 6 hour limit (Service.onTimeout, API 35). Not needed at compileSdk 34

## Step 4 notes: MainActivity.kt (done, single file) + icon + manifest edits
Contains: MainActivity, MainViewModel, GlassTheme, Palette, glass() modifier, GlassBackground (blue gradient + drifting orbs),
GlassIconButton, PillButton, GlassDialog, MainScreen, Header, EmptyState, AddButton, DownloadCard, StatusLine, FileTile,
GlassProgress, AddLinkDialog (paste button + validation), ConfirmCancelDialog, openDownloaded().
- MainViewModel uses DownloadRepository.get(app): add(url), pause(id), resume(id), remove(id), pauseAll(), clearFinished()
- UI features: header with active count + total speed, pause all, clear finished, add-link popup, per-file card
  (icon by file type, status line with speed + ETA, animated progress bar, pause / resume / retry / cancel / open / remove),
  cancel asks for confirmation, empty state, spring press animations, list reorder animation
- Link shared from another app (Share > text) opens the add popup with the link filled in
- Runtime permissions asked at start: POST_NOTIFICATIONS (Android 13+), WRITE_EXTERNAL_STORAGE (Android 9 and below)
- Manifest has: android:icon="@mipmap/ic_launcher", MainActivity launchMode singleTop, ACTION_SEND text/plain intent-filter
- Compose BOM is 2024.06.00 (foundation 1.6.x): that is why Modifier.animateItemPlacement() is used (not animateItem)

## Step 3 notes: DownloadDatabase.kt (done, single file)
Contains: DownloadEntity, DownloadDao, AppDatabase (Room, db name downly.db),
DownloadItem (UI model), DownloadRepository.
DownloadRepository.get(context) is the singleton used by UI AND service. It owns the one DownloadEngine.
API:
- val downloads: Flow<List<DownloadItem>>   // newest first, live speed / bytes merged in
- val activeCount: Flow<Int>                // used by DownloadService to start / stop itself
- suspend add(url, fileName = null): Long?  // null = invalid link; inserts row + starts download
- pause(id), suspend resume(id) (also retry for FAILED), cancel(id) (deletes row + part file),
  suspend remove(id), suspend clearFinished(), suspend pauseAll()
Behaviour:
- DB is written only on state changes; progress ticks live in memory (StateFlow merged into `downloads`)
- On app start rows left DOWNLOADING / QUEUED become PAUSED (process was killed); user taps resume
- CANCELED deletes the row. COMPLETED keeps the row with savedUri

## Step 2 notes: DownloadEngine.kt (done, single file)
Public API (class DownloadEngine(context, maxParallel = 3)):
- start(id: Long, url: String, fileName: String? = null, knownTotal: Long = -1L)   // also used for resume
- pause(id), cancel(id), isRunning(id), partialBytes(id), deletePartial(id), shutdown()
- val updates: SharedFlow<DownloadUpdate>   // one event per state change, progress every ~400ms
- DownloadUpdate(id, state, fileName, downloadedBytes, totalBytes, speedBytesPerSec, savedUri, error)
  + helpers: progress (0..1), percent, hasKnownSize, etaSeconds
- DownloadState: QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED
- Companion: DownloadEngine.normalizeUrl(input), DownloadEngine.isValidUrl(input)
- Top-level formatters: formatBytes(Long), formatSpeed(Long), formatEta(Long)
How it works:
- Partial data is stored in <externalFilesDir>/partial/<id>.part; resume sends Range: bytes=N-
- If server ignores Range (HTTP 200) it restarts from 0; HTTP 416 deletes the bad part and retries once
- On finish the file is copied to public Downloads (MediaStore on Android 10+, direct file on 9 and below)
- Max 3 parallel downloads, extra ones stay QUEUED
- The engine does NOT touch the database; the repository collects `updates` and persists them
- The engine is created only inside DownloadRepository (singleton), no Application class needed

## Step 1 notes
- Manifest declares: MainActivity, DownloadService (foreground, dataSync), INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS,
  FOREGROUND_SERVICE(+DATA_SYNC), WRITE_EXTERNAL_STORAGE (maxSdk 28), cleartext HTTP allowed
- app/build.gradle.kts already has ALL dependencies (Compose, Material3, icons, OkHttp, coroutines, Room + KSP)
- No gradle wrapper (Step 9 workflow uses gradle/actions/setup-gradle)

## Round 2 (UI + updater fixes) - ALL DONE
Request from the user (screenshots): fix blur on Add link, centered popups, remove header cards, fix update check, remove Backup.
| # | Step | Status |
|---|------|--------|
| R1 | Backup removed completely (SettingsBackup.kt deleted, Settings section, ViewModel code, replaceAll, README) | DONE |
| R2 | Updates: "Check automatically" switch + setting removed; UpdateCheckWorker.kt + work-runtime dependency removed (no more background checks); app checks 2 s after opening (MainScreen LaunchedEffect delay(2000)); popup opens when an update is found (once per app launch); 6-hour skip removed from UpdateManager; GitHub API 403/429 now falls back to github.com release pages (fetchLatestWeb) | DONE |
| R3 | Header card removed (Downloads title row) and Settings top bar card removed; gear icon stays top right | DONE |
| R4 | Add link / From clipboard popup is now a centered dialog (GlassCenterDialog in DownlyTheme.kt); menu blur removed (only the dim overlay stays) | DONE |
Notes: not compiled here (no SDK). If the update check still says "up to date", the GitHub repo may be private (releases/latest gives 404 to the app) or the release has no .apk asset.
`UpdateSettingsRow` ("Check now") is unchanged. `SettingNames.AUTO_UPDATE_CHECK` and `AppSettings.autoUpdateCheck` no longer exist.

## Round 3 (home buttons + progress bar + Add link) - ALL DONE
| # | Step | Status |
|---|------|--------|
| R5 | "Add link" (from + menu) no longer auto-fills the clipboard link; "From clipboard" still fills it (MainScreen onFromClipboard sets addInitial). AddLinkDialog has no clipboard auto-fill any more (the paste button inside still works) | DONE |
| R6 | Home buttons are icon only (no white circle): header pause-all, card Pause / Resume / Retry / Open use new `AnimatedIconButton` (DownlyTheme.kt): press squeeze + pause<->play icon swap animation (shrink out, bounce in). `GlassIconButton` is still used by the Settings stepper | DONE |
| R7 | `GlassProgress` (MainActivity.kt) redone: 7 dp, glass fill (bar colour at 55 to 90 % opacity), light border, glossy top highlight, soft sheen gliding while downloading (param `active`), smoother 700 ms ease-out; unknown size = glass segment sliding | DONE |
Not compiled here (no SDK). If `AnimatedContent` / `togetherWith` fail to compile, the Compose BOM is older than expected: replace `togetherWith` with `with` and add @OptIn(ExperimentalAnimationApi::class).

## Round 4 (Settings redesign, credit, update text) - ALL DONE
| # | Step | Status |
|---|------|--------|
| R8 | Update popup no longer shows GitHub release text (it had commit text / file paths); it shows a fixed "What's new: Bug fixes, performance improvements and a smoother experience." (UpdateUi.kt) | DONE |
| R9 | Settings: section labels are bold text (no white pill); Stepper is plain - / + (AnimatedIconButton); "Default folder / Reset" row removed (no folder picked = Downloads) | DONE |
| R10 | Settings uses a draft (`draft` in SettingsScreen); an "Apply" pill appears at the right of the Settings header when anything changed; `MainViewModel.applySettings()` saves all changed values; Back / Done without Apply discards | DONE |
| R11 | Home gear icon = classic cog (`DownlyIcons.Settings`) | DONE |
| R12 | New file DeveloperCredit.kt: "Developed by NAME", WhatsApp / Instagram / Telegram icons, copyright line, at the bottom of Settings. Links are empty strings in `DeveloperInfo` (user adds them later; empty = tap does nothing) | DONE |
Not compiled here (no SDK).

## Things to remember
- Code has not been compiled yet (no network / Android SDK in the build sandbox); first real build happens when the user pushes to GitHub (Step 8 workflow)
- Never regenerate or replace the signing key after users installed a build: every build must use the same key
- Do not put signing-private/ into the repo
- Icon changes: edit branding/downly-icon.svg first, then regenerate the Android files from it

## UI redesign (done, see UI_REDESIGN_PROGRESS.md)
Steps 1 to 5 finished: theme + home, plus menu + Add link sheet, delete sheet + toast, full-screen Settings, final polish.
Palette is gone, use `Downly.colors`. Popups are bottom sheets (`GlassBottomSheet`); `GlassDialog` is no longer used by any screen.

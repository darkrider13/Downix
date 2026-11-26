# Glass Downloader - Progress File

Hand this zip to any Claude. Read this file first, then continue from the next pending step.
Build on the files already in this zip, update them, and return the full updated zip.

## Project
Android download manager (native Kotlin + Jetpack Compose). Paste a link, download inside the app.
- App name: Glass Downloader | package: com.glass.downloader
- UI language: English only
- UI design: glass-effect cards, popup dialogs, blue gradient background, professional, smooth animations
- Features: progress bar, speed, size, pause / resume / cancel, background download with notification,
  saves to Downloads folder, download list persists after app is closed

## Rules from the user (Banglish speaker, reply in Banglish)
- One step at a time, one step = one deliverable zip with PROGRESS.md updated
- After each step show: done steps, remaining steps, "% remaining"
- Do NOT tell the user to build/test on GitHub after each step (only at the very end)

## Steps
| # | Step | Status |
|---|------|--------|
| 1 | Project setup (build.gradle.kts, settings, AndroidManifest, theme) | DONE |
| 2 | Download engine (OkHttp, pause/resume with Range header) | DONE |
| 3 | Database (Room, download list) | DONE |
| 4 | Main screen UI (glass + blue gradient, Compose) | DONE |
| 5 | Notification + foreground DownloadService | PENDING |
| 6 | GitHub Actions workflow (APK build) | PENDING |

Progress: 4 of 6 done | 2 remaining | 33% remaining

## Next step to do: Step 5 - Notification + foreground DownloadService
Create app/src/main/java/com/glass/downloader/DownloadService.kt (the manifest already declares it:
`.DownloadService`, foregroundServiceType dataSync, exported=false).
- Service gets DownloadRepository.get(this); observes `repository.downloads` (list) and `repository.activeCount`
- Runs as a foreground service while activeCount > 0, stops itself (stopSelf) when it reaches 0
- Notification channel (e.g. "downloads", IMPORTANCE_LOW, no sound) created in the service
- Ongoing notification: title "Downloading N file(s)", text = overall percent + total speed (formatSpeed), progress bar,
  tap opens MainActivity (PendingIntent, FLAG_IMMUTABLE), action "Pause all" (PendingIntent to the service with an action string
  -> repository.pauseAll())
- Separate normal (non-ongoing) notification when a file finishes: "Download complete" + file name, tap opens the file or the app;
  and one when a download fails
- Throttle notification updates (about once per second) to avoid flooding
- Starting the service: the UI (MainViewModel.add / resume) must call
  ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) after add / resume.
  Easiest: add a small helper object DownloadService.start(context) and call it from MainViewModel.add and MainViewModel.resume
  (edit MainActivity.kt for that - it is the previous step's file, update it and return it)
- startForeground must be called within 5 seconds of startForegroundService; use ServiceCompat.startForeground with
  ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC on API 29+
- Use a notification small icon: create res/drawable/ic_stat_download.xml (simple vector arrow, white)

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
- Manifest now has: android:icon="@mipmap/ic_launcher", MainActivity launchMode singleTop, ACTION_SEND text/plain intent-filter
- Launcher icon: res/mipmap-anydpi-v26/ic_launcher.xml (adaptive) + res/mipmap-mdpi/ic_launcher.xml (layer-list fallback),
  drawables ic_launcher_background.xml / ic_launcher_foreground.xml
- Without Step 5 a download keeps running only while the app process is alive; Step 5 adds the foreground service
- Compose BOM is 2024.06.00 (foundation 1.6.x): that is why Modifier.animateItemPlacement() is used (not animateItem)

## Step 3 notes: DownloadDatabase.kt (done, single file)
Contains: DownloadEntity, DownloadDao, AppDatabase (Room, db name glass_downloader.db),
DownloadItem (UI model), DownloadRepository.
DownloadRepository.get(context) is the singleton used by UI AND service. It owns the one DownloadEngine.
API:
- val downloads: Flow<List<DownloadItem>>   // newest first, live speed / bytes merged in
- val activeCount: Flow<Int>                // for starting / stopping the foreground service (Step 5)
- suspend add(url, fileName = null): Long?  // null = invalid link; inserts row + starts download
- pause(id), suspend resume(id) (also retry for FAILED), cancel(id) (deletes row + part file),
  suspend remove(id), suspend clearFinished(), suspend pauseAll()
Behaviour:
- DB is written only on state changes; progress ticks live in memory (StateFlow merged into `downloads`)
- On app start rows left DOWNLOADING / QUEUED become PAUSED (process was killed); user taps resume
- CANCELED deletes the row. COMPLETED keeps the row with savedUri

## Step 1 notes
- Manifest declares: MainActivity, DownloadService (foreground, dataSync), INTERNET, POST_NOTIFICATIONS,
  FOREGROUND_SERVICE(+DATA_SYNC), WRITE_EXTERNAL_STORAGE (maxSdk 28), cleartext HTTP allowed
- app/build.gradle.kts already has ALL dependencies (Compose, Material3, icons, OkHttp, coroutines, Room + KSP)
- DownloadService.kt does NOT exist yet (Step 5) - the manifest references it
- No gradle wrapper (Step 6 workflow uses gradle/actions/setup-gradle)

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
- The engine does NOT touch the database; Step 3 / 5 must collect `updates` and persist them
- The engine is created only inside DownloadRepository (singleton), no Application class needed

## Things to remember for later steps
- Step 5: DownloadService should own the single DownloadEngine, run as foreground service while any download is active
- Code has not been compiled yet (no network in the build sandbox); first real build happens in Step 6

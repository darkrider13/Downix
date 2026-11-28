# Downix Torrent Support: Progress File

> এই ফাইলটা নতুন চ্যাট/অ্যাকাউন্টে আপলোড করে বলবে: "PROGRESS.md দেখে পরের ধাপ করো"।
> (To the next Claude session: read this file first, then continue from the first unchecked step.)

Repo: https://github.com/darkrider13/Downix (branch main) · Package: com.downix.app

## Goal
Add torrent (magnet link) downloads next to normal file downloads, professionally.
The torrent button is the TOP option of the "+" menu (above "From clipboard"), magnet (U shape) icon.

## Design decisions (do not change without a reason)
- Library: libtorrent4j 2.1.0-35 (arm64 native part only; x86_64 removed in step 5, 32-bit arm removed in step 7) in app/build.gradle.kts.
- A torrent is a normal row in the `downloads` table: `kind = TORRENT`, `url` = the magnet link, `infoHash` = hash.
  The rest of the app (list, notifications, service, selection, delete) works unchanged.
- `TorrentEngine` mirrors `DownloadEngine`: emits `DownloadUpdate` (now with `peers`, `seeds`), polls libtorrent once a second.
- `DownloadRepository` merges both engines into ONE collector (the bookkeeping maps need a single coroutine).
- Data goes to a private work folder `torrents/<id>/`; when complete it is copied to the download folder
  (picked folder = SAF tree, else public Downloads via MediaStore / legacy file). Multi-file torrents keep their folder.
  `savedUri` = the biggest file of the torrent. Work folder is deleted after the copy (unless the torrent keeps seeding, see below).
- DB version 3 (`MIGRATION_1_2` adds `kind`, `infoHash`; `MIGRATION_2_3` adds `folderRef`). Old rows stay HTTP.
- Queue: torrents have their OWN limit "Active torrents" (Settings > Torrents, default 3). It replaces the idea of
  sharing "Simultaneous downloads" with HTTP. Oldest torrent first; the rest are paused by the engine (`Entry.queued`) and start by themselves.
- Seeding (off by default): a finished torrent is NOT removed from libtorrent; its files stay in `torrents/<id>/` and are shared
  until ratio / hours limit, "Stop seeding", delete, or Settings switch off. Live numbers: `TorrentEngine.seeding` -> `DownloadItem.seeding`.
  Seeding is NOT restored after an app restart (leftover work folders of finished torrents are deleted at start).
- Multi-file torrent: `DownloadEntity.folderRef` = folder of the torrent (picked folder = content uri, `ms:<name>` = inside public Downloads,
  `file:<path>` = Android 9-). Open = folder view, Delete = whole folder. The card passes `FOLDER_TAG + folderRef` to Open.

## Steps
- [x] Step 1: Foundation + first working slice
  - [x] libtorrent4j dependency, proguard keep rule
  - [x] DB migration, `DownloadKind`, repository routing (add / resume / pause / cancel / remove / pauseAll)
  - [x] `TorrentEngine.kt`: session, magnet add, metadata, progress, pause/resume, network aware (Wi-Fi only),
        finish = copy to folder, duplicate detection by info hash
  - [x] "Torrent" button in "+" menu, `MagnetDialog` (`TorrentUi.kt`), clipboard magnet auto fill
  - [x] magnet:// links open Downix (manifest), Share of a magnet works, card shows "Finding peers" / "· N peers"
- [x] Step 2: Better torrent experience
  - [x] .torrent file support: "Choose .torrent file" button in the Torrent popup (file picker) + opening a .torrent from
        any file manager / browser (manifest filters, content:/file: links). Stored in filesDir/torrent-files/<hash>.torrent,
        row url = "torrent-file:<hash>"
  - [x] File selection for multi-file torrents: after the info arrives the torrent is paused (error = "Choose files") and
        `TorrentFilesDialog` opens (Back = download all, Cancel = remove torrent). Choice saved in torrents/<id>/selection.txt
        (skipped file indexes) so it survives an app restart. Only selected files are copied to the folder.
  - [x] Card shows "Choose files · tap resume" if the popup was missed (tap resume to open it again)
  - [ ] Moved to step 3: details popup rows (seeds, peers, ratio, hash, file list), auto-resume at app start
- [x] Step 3: Control and settings (this zip)
  - [x] Details popup (DownloadTools.kt DetailsDialog): type, info hash, peers / seeds, seeding numbers, file list (selected / skipped),
        "Copy link" gives a magnet link also for .torrent torrents; auto-resume at app start (torrents that were running continue by themselves)
  - [x] Settings section "Torrents": active torrents, download / upload speed limit, DHT on/off, seed after download (+ ratio / hours)
  - [x] Queue for torrents ("Active torrents" limit, see design decisions)
  - [x] Multi-file torrent: Open = folder view, Delete = delete whole folder (details popup: "Open folder", "Delete folder")
  - [x] Seeding with notification ("Seeding ..." + "Stop seeding" action), "Stop seeding" button in details popup, "Seeding" status on the card
- [x] Step 4: Polish (this zip)
  - [x] Seeding restored after an app restart: `.torrent` file is kept (magnets: saved from `TorrentInfo.bencode()` at finish), `TorrentEngine.restoreOrCleanup`
        re-adds it from `torrents/<id>/`; skipped files re-applied (`Seeder.fresh`). Ratio / time limits start again from 0. Seeding off = leftovers deleted.
  - [x] "Delete folder" (public Downloads) deletes only this torrent's files: list saved in filesDir/torrent-saved/<id>.txt (`TorrentEngine.savedList`)
  - [x] Low storage: when the chosen files are known and do not fit, the torrent stops with "Not enough storage space" (FAILED, can be retried)
  - [x] No peers: after 90 s without peers or speed the card says "No peers yet · still searching" (`TorrentEngine.NO_PEERS`)
  - [x] README: Torrents section, settings table, project structure
  - [ ] Not done on purpose: R8 (needs a release test on the phone first)
- [x] Step 5: APK size (this zip)
  - [x] Removed `libtorrent4j-android-x86_64` from app/build.gradle.kts (emulator-only CPU type). Files touched: app/build.gradle.kts, PROGRESS.md
- [x] Step 6: ABI split (this zip)
  - [x] app/build.gradle.kts: `splits { abi }` -> arm64-v8a + armeabi-v7a APKs, no universal APK (same versionCode for both, fine for sideload)
  - [x] .github/workflows/release.yml: all APKs copied to release-apks/ as `Downix-<ver>-<abi>.apk`, each signature-checked, all uploaded to the release
  - [x] UpdateManager.kt: `parseRelease` picks the asset matching `Build.SUPPORTED_ABIS` (fallback: old universal APK, then first APK);
        `fetchLatestWeb` now returns ALL apk links, not only the first
  - [x] README: download line. Files touched: build.gradle.kts, release.yml, UpdateManager.kt, README.md, PROGRESS.md
- [x] Step 7: arm64 only (this zip)
  - [x] app/build.gradle.kts: removed `libtorrent4j-android-arm`; `splits { abi }` now includes only "arm64-v8a" -> ONE APK `Downix-<ver>-arm64-v8a.apk`
  - [x] README download line. Workflow and UpdateManager unchanged (they already handle one or many APKs). Files touched: build.gradle.kts, README.md, PROGRESS.md
  - Note: old 32-bit phones cannot install the app any more (no matching CPU type). To undo: re-add the arm dependency and "armeabi-v7a" in `splits`.
  - (Step 6 risk, still valid) Risk: written WITHOUT compiling. If the build is red check `UpdateManager.parseRelease` (firstNotNullOfOrNull) and the `splits {}` block. x86 phones get the fallback APK (no matching ABI).


## Known risks / notes
- Step 4 was also written WITHOUT compiling. Extra libtorrent4j call to check if the build is red: `TorrentInfo.bencode()` (TorrentEngine.beginFinish).
- Files touched in step 4: TorrentEngine.kt, DownloadDatabase.kt, MainActivity.kt (1 status text), README.md, PROGRESS.md (no new files)
- ALL STEPS ARE DONE. Next work = only bug fixes from real tests (send the red build lines or the crash / behaviour).
- Step 3 was also written WITHOUT compiling. libtorrent4j calls to check first if the build is red:
  `SettingsPack.downloadRateLimit / uploadRateLimit / enableDht` and `SessionManager.applySettings` (TorrentEngine.applySessionSettings),
  `TorrentStatus.allTimeUpload()` (TorrentEngine.tickSeeders).
- Files touched in step 3: SettingsRepository.kt, TorrentEngine.kt, DownloadDatabase.kt, DownloadService.kt, DownloadTools.kt,
  DownloadEngine.kt (1 field in DownloadUpdate), MainActivity.kt, PROGRESS.md (no new files)
- This step was written WITHOUT compiling (no Gradle/internet in the authoring environment).
  If GitHub Actions fails, send the red error lines: most likely a libtorrent4j method name that must be adjusted in `TorrentEngine.kt`.
- Files touched in step 2: AndroidManifest.xml, DownloadDatabase.kt, DownixTheme.kt (Square icon), MainActivity.kt, TorrentEngine.kt, TorrentUi.kt, PROGRESS.md
- Files touched in step 1: app/build.gradle.kts, app/proguard-rules.pro, AndroidManifest.xml, DownloadDatabase.kt,
  DownloadEngine.kt (only 2 fields in DownloadUpdate), DownixTheme.kt (Magnet icon), MainActivity.kt, new: TorrentEngine.kt, TorrentUi.kt, PROGRESS.md

# Downix Torrent Support: Progress File

> এই ফাইলটা নতুন চ্যাট/অ্যাকাউন্টে আপলোড করে বলবে: "PROGRESS.md দেখে পরের ধাপ করো"।
> (To the next Claude session: read this file first, then continue from the first unchecked step.)

Repo: https://github.com/darkrider13/Downix (branch main) · Package: com.downix.app

## Goal
Add torrent (magnet link) downloads next to normal file downloads, professionally.
The torrent button is the TOP option of the "+" menu (above "From clipboard"), magnet (U shape) icon.

## Design decisions (do not change without a reason)
- Library: libtorrent4j 2.1.0-35 (arm64, arm, x86_64 native parts) in app/build.gradle.kts.
- A torrent is a normal row in the `downloads` table: `kind = TORRENT`, `url` = the magnet link, `infoHash` = hash.
  The rest of the app (list, notifications, service, selection, delete) works unchanged.
- `TorrentEngine` mirrors `DownloadEngine`: emits `DownloadUpdate` (now with `peers`, `seeds`), polls libtorrent once a second.
- `DownloadRepository` merges both engines into ONE collector (the bookkeeping maps need a single coroutine).
- Data goes to a private work folder `torrents/<id>/`; when complete it is copied to the download folder
  (picked folder = SAF tree, else public Downloads via MediaStore / legacy file). Multi-file torrents keep their folder.
  `savedUri` = the biggest file of the torrent. Work folder is deleted after the copy. No seeding yet.
- DB version 2 (`MIGRATION_1_2` adds `kind`, `infoHash`). Old rows stay HTTP.

## Steps
- [x] Step 1: Foundation + first working slice (this zip)
  - [x] libtorrent4j dependency, proguard keep rule
  - [x] DB migration, `DownloadKind`, repository routing (add / resume / pause / cancel / remove / pauseAll)
  - [x] `TorrentEngine.kt`: session, magnet add, metadata, progress, pause/resume, network aware (Wi-Fi only),
        finish = copy to folder, duplicate detection by info hash
  - [x] "Torrent" button in "+" menu, `MagnetDialog` (`TorrentUi.kt`), clipboard magnet auto fill
  - [x] magnet:// links open Downix (manifest), Share of a magnet works, card shows "Finding peers" / "· N peers"
- [x] Step 2: Better torrent experience (this zip)
  - [x] .torrent file support: "Choose .torrent file" button in the Torrent popup (file picker) + opening a .torrent from
        any file manager / browser (manifest filters, content:/file: links). Stored in filesDir/torrent-files/<hash>.torrent,
        row url = "torrent-file:<hash>"
  - [x] File selection for multi-file torrents: after the info arrives the torrent is paused (error = "Choose files") and
        `TorrentFilesDialog` opens (Back = download all, Cancel = remove torrent). Choice saved in torrents/<id>/selection.txt
        (skipped file indexes) so it survives an app restart. Only selected files are copied to the folder.
  - [x] Card shows "Choose files · tap resume" if the popup was missed (tap resume to open it again)
  - [ ] Moved to step 3: details popup rows (seeds, peers, ratio, hash, file list), auto-resume at app start
- [ ] Step 3: Control and settings
  - [ ] Details popup (DownloadTools.kt DetailsDialog): torrent rows, auto-resume at app start
  - [ ] Settings section "Torrent": download/upload speed limit, DHT on/off, seeding after completion (+ ratio/time), max active torrents
  - [ ] Respect "Simultaneous downloads" for torrents (queue)
  - [ ] Multi-file torrent: Open = folder view, Delete = delete whole folder
  - [ ] Seeding with notification, stop seeding button
- [ ] Step 4: Polish
  - [ ] README section for torrents, edge cases (low storage, no peers timeout message), save resume data
  - [ ] Optional: ABI split / size check, enable R8 only after a release test

## Known risks / notes
- This step was written WITHOUT compiling (no Gradle/internet in the authoring environment).
  If GitHub Actions fails, send the red error lines: most likely a libtorrent4j method name that must be adjusted in `TorrentEngine.kt`.
- Files touched in step 2: AndroidManifest.xml, DownloadDatabase.kt, DownixTheme.kt (Square icon), MainActivity.kt, TorrentEngine.kt, TorrentUi.kt, PROGRESS.md
- Files touched in step 1: app/build.gradle.kts, app/proguard-rules.pro, AndroidManifest.xml, DownloadDatabase.kt,
  DownloadEngine.kt (only 2 fields in DownloadUpdate), DownixTheme.kt (Magnet icon), MainActivity.kt, new: TorrentEngine.kt, TorrentUi.kt, PROGRESS.md

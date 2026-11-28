package com.downix.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ------------------------------------------------------------------ */
/*  Room: entity, dao, database                                        */
/* ------------------------------------------------------------------ */

/** HTTP = normal file link. TORRENT = magnet link (the link is stored in [DownloadEntity.url]). */
enum class DownloadKind { HTTP, TORRENT }

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String = "",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val state: String = DownloadState.QUEUED.name,
    val savedUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: String = DownloadKind.HTTP.name,
    val infoHash: String? = null, // torrents only: lets us spot the same torrent added twice
    val folderRef: String? = null // multi-file torrents: where their own folder is (see TorrentEngine.FOLDER_*)
)

@Dao
interface DownloadDao {

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    /** Newest row with exactly this (normalized) link, used for the "Already in your list" check. */
    @Query("SELECT * FROM downloads WHERE url = :url ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    /** Same torrent (by info hash) that is already in the list. */
    @Query("SELECT * FROM downloads WHERE kind = 'TORRENT' AND infoHash = :hash ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByInfoHash(hash: String): DownloadEntity?

    /** Keeps the old file name when the new one is blank. */
    @Query(
        "UPDATE downloads SET " +
            "fileName = CASE WHEN :fileName != '' THEN :fileName ELSE fileName END, " +
            "totalBytes = :totalBytes, downloadedBytes = :downloadedBytes, " +
            "state = :state, savedUri = :savedUri, error = :error, " +
            "folderRef = CASE WHEN :folderRef IS NOT NULL THEN :folderRef ELSE folderRef END WHERE id = :id"
    )
    suspend fun updateState(
        id: Long,
        fileName: String,
        totalBytes: Long,
        downloadedBytes: Long,
        state: String,
        savedUri: String?,
        error: String?,
        folderRef: String?
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE state IN ('COMPLETED', 'FAILED', 'CANCELED')")
    suspend fun clearFinished()

    /** After the app / process was killed, nothing is really downloading anymore. */
    @Query("UPDATE downloads SET state = 'PAUSED' WHERE state IN ('DOWNLOADING', 'QUEUED', 'WAITING')")
    suspend fun resetInterrupted()

    @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('DOWNLOADING', 'QUEUED', 'WAITING')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT id FROM downloads WHERE state IN ('DOWNLOADING', 'QUEUED', 'WAITING')")
    suspend fun getActiveIds(): List<Long>

    /** Torrents that were running when the app was closed: they start again by themselves. */
    @Query("SELECT id FROM downloads WHERE kind = 'TORRENT' AND state IN ('DOWNLOADING', 'QUEUED', 'WAITING')")
    suspend fun getActiveTorrentIds(): List<Long>

    @Query("SELECT * FROM downloads WHERE kind = 'TORRENT' AND state = 'COMPLETED'")
    suspend fun getCompletedTorrents(): List<DownloadEntity>
}

/** Version 2: torrent support. Old rows become normal HTTP downloads, nothing is lost. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN kind TEXT NOT NULL DEFAULT 'HTTP'")
        db.execSQL("ALTER TABLE downloads ADD COLUMN infoHash TEXT")
    }
}

/** Version 3: multi-file torrents remember their folder (Open = folder, Delete = whole folder). */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN folderRef TEXT")
    }
}

@Database(entities = [DownloadEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "downix.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

/* ------------------------------------------------------------------ */
/*  UI model                                                           */
/* ------------------------------------------------------------------ */

/** What the UI shows: database row merged with live speed / bytes from the engine. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val state: DownloadState,
    val speedBytesPerSec: Long,
    val savedUri: String?,
    val error: String?,
    val createdAt: Long,
    val savingPercent: Int = -1, // 0..100 while the finished file is being saved, else -1
    val kind: DownloadKind = DownloadKind.HTTP,
    val peers: Int = 0,          // torrents: connected peers (live only)
    val seeds: Int = 0,          // torrents: connected seeds (live only)
    val peerTotal: Int = 0,      // torrents: peers known in the swarm (live only)
    val infoHash: String? = null,   // torrents: the hash that identifies the torrent
    val folderRef: String? = null,  // multi-file torrents: their folder
    val seeding: SeedStats? = null  // finished torrent that is still being shared (live only)
) {
    val isTorrent: Boolean get() = kind == DownloadKind.TORRENT
    /** A finished torrent with several files: Open shows its folder, Delete removes the whole folder. */
    val isFolderTorrent: Boolean get() = isTorrent && !folderRef.isNullOrBlank()
    val hasKnownSize: Boolean get() = totalBytes > 0
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (progress * 100).toInt()
    val etaSeconds: Long
        get() = if (speedBytesPerSec > 0 && totalBytes > downloadedBytes)
            (totalBytes - downloadedBytes) / speedBytesPerSec else -1L

    val isActive: Boolean
        get() = state == DownloadState.DOWNLOADING || state == DownloadState.QUEUED || state == DownloadState.WAITING
    val canPause: Boolean get() = isActive
    val canResume: Boolean get() = state == DownloadState.PAUSED || state == DownloadState.FAILED
    val isCompleted: Boolean get() = state == DownloadState.COMPLETED
}

private fun DownloadEntity.toItem(live: DownloadUpdate?, seed: SeedStats? = null): DownloadItem {
    val parsed = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.PAUSED)
    val itemKind = runCatching { DownloadKind.valueOf(kind) }.getOrDefault(DownloadKind.HTTP)
    return if (live != null && live.state == DownloadState.DOWNLOADING) {
        DownloadItem(
            id = id,
            url = url,
            fileName = live.fileName.ifBlank { fileName },
            totalBytes = live.totalBytes,
            downloadedBytes = live.downloadedBytes,
            state = DownloadState.DOWNLOADING,
            speedBytesPerSec = live.speedBytesPerSec,
            savedUri = savedUri,
            error = if (itemKind == DownloadKind.TORRENT) live.error else null, // "No peers found" hint
            createdAt = createdAt,
            savingPercent = live.savingPercent,
            kind = itemKind,
            peers = live.peers,
            seeds = live.seeds,
            peerTotal = live.peerTotal,
            infoHash = infoHash,
            folderRef = folderRef,
            seeding = seed
        )
    } else {
        DownloadItem(
            id = id,
            url = url,
            fileName = fileName,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            state = parsed,
            speedBytesPerSec = 0L,
            savedUri = savedUri,
            error = error,
            createdAt = createdAt,
            kind = itemKind,
            infoHash = infoHash,
            folderRef = folderRef,
            seeding = seed
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Repository: the one place UI and service talk to                   */
/* ------------------------------------------------------------------ */

/** Result of [DownloadRepository.deleteWithFile]. */
enum class FileDeleteResult {
    /** File removed from the phone and row removed from the list. */
    DELETED,
    /** The file did not exist any more (moved / deleted elsewhere); the row was removed from the list. */
    ALREADY_GONE,
    /** Android refused to delete the file; the row stays in the list. */
    FAILED
}

/**
 * Owns the single DownloadEngine + Room database and keeps them in sync.
 * Get it anywhere with DownloadRepository.get(context) (UI and Service share the same instance).
 *
 * - Database is written only on state changes (not on every progress tick)
 * - Live speed / bytes are kept in memory and merged into [downloads]
 * - Posts the "complete" / "failed" notification when a download ends (DownloadNotifier)
 * - Applies SettingsRepository values (max parallel downloads, Wi-Fi only) to the engine
 */
class DownloadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val settingsRepo = SettingsRepository.get(context)
    private val network = NetworkMonitor(context)
    private val engine = DownloadEngine(context, network.state)
    private val torrents = TorrentEngine(context, network.state)
    private val dao = AppDatabase.get(context).downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val live = MutableStateFlow<Map<Long, DownloadUpdate>>(emptyMap())
    private val lastState = HashMap<Long, DownloadState>() // touched only by the collector coroutine
    // Most bytes seen while downloading, and when they were last saved. A pause / retry that reports less
    // (e.g. 0) must never make the list show 0%: it keeps the progress that was really reached.
    private val bestBytes = HashMap<Long, Long>() // touched only by the collector coroutine
    private val lastSavedAt = HashMap<Long, Long>() // touched only by the collector coroutine

    /** Full list for the UI, newest first, with live progress. */
    val downloads: Flow<List<DownloadItem>> =
        combine(dao.observeAll(), live, torrents.seeding) { rows, liveMap, seedMap ->
            rows.map { it.toItem(liveMap[it.id], seedMap[it.id]) }
        }

    /** Current network (none / Wi-Fi / mobile data). */
    val networkState: StateFlow<NetworkState> = network.state

    /** Number of active downloads (use it to start / stop the foreground service). */
    val activeCount: Flow<Int> = dao.observeActiveCount()

    /** Active downloads + torrents being shared: the service stays alive while this is above 0. */
    val keepAliveCount: Flow<Int> =
        combine(dao.observeActiveCount(), torrents.seedingCount) { active, seeding -> active + seeding }

    /** Finishes after interrupted rows were reset; add / resume wait for it. */
    private val ready: Job

    init {
        // Apply the saved settings right away, then follow every change.
        engine.setMaxParallel(settingsRepo.current.maxParallel)
        engine.setWifiOnly(settingsRepo.current.wifiOnly)
        engine.setSaveFolder(settingsRepo.current.downloadFolder)
        torrents.setWifiOnly(settingsRepo.current.wifiOnly)
        torrents.setSaveFolder(settingsRepo.current.downloadFolder)
        torrents.configure(settingsRepo.current.toTorrentSettings())
        scope.launch {
            settingsRepo.settings.collect {
                engine.setMaxParallel(it.maxParallel)
                engine.setWifiOnly(it.wifiOnly)
                engine.setSaveFolder(it.downloadFolder)
                torrents.setWifiOnly(it.wifiOnly)
                torrents.setSaveFolder(it.downloadFolder)
                torrents.configure(it.toTorrentSettings())
            }
        }
        // Subscribe first so no engine update is missed. ONE collector for both engines (HTTP + torrent):
        // the bookkeeping maps below are touched only from this single coroutine.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            merge(engine.updates, torrents.updates).collect { onUpdate(it) }
        }
        ready = scope.launch {
            val torrentsToResume = dao.getActiveTorrentIds() // read BEFORE they are marked as paused
            dao.resetInterrupted()
            // Torrents that finished while seeding and were cut off by an app close: free their work folder.
            // With seeding on, they are shared again; otherwise the work folder is deleted.
            var restored = false
            dao.getCompletedTorrents().forEach {
                if (torrents.restoreOrCleanup(it.id, it.infoHash, it.fileName, maxOf(it.totalBytes, it.downloadedBytes))) restored = true
            }
            // Auto-resume: torrents that were running when the app closed continue by themselves.
            torrentsToResume.forEach { id ->
                dao.getById(id)?.let { torrents.start(it.id, it.url, it.fileName.ifBlank { null }) }
            }
            if (torrentsToResume.isNotEmpty() || restored) DownloadService.start(appContext)
        }
    }

    /* ----------------------------- actions ----------------------------- */

    /**
     * Adds a new download and starts it. Returns the new id, or null if the link is invalid.
     */
    suspend fun add(url: String, fileName: String? = null): Long? {
        if (!DownloadEngine.isValidUrl(url)) return null
        ready.join()
        val clean = DownloadEngine.normalizeUrl(url)
        val id = dao.insert(DownloadEntity(url = clean, fileName = fileName.orEmpty()))
        engine.start(id, clean, fileName)
        return id
    }

    /**
     * Adds a torrent from a magnet link and starts it. Returns the new id, or null if the magnet link is invalid.
     * The name shown first comes from the link (dn=); the real name arrives with the torrent info.
     */
    suspend fun addMagnet(magnet: String): Long? {
        val clean = TorrentEngine.normalizeMagnet(magnet) ?: return null
        ready.join()
        val name = TorrentEngine.nameOf(clean)
        val id = dao.insert(
            DownloadEntity(
                url = clean,
                fileName = name,
                kind = DownloadKind.TORRENT.name,
                infoHash = TorrentEngine.infoHashOf(clean)
            )
        )
        torrents.start(id, clean, name)
        return id
    }

    /**
     * Adds a torrent from the bytes of a .torrent file. Returns (id, null) on success or (null, message) when the
     * file is not a torrent or the torrent is already in the list.
     */
    suspend fun addTorrentFile(bytes: ByteArray): Pair<Long?, String?> {
        val meta = torrents.inspect(bytes) ?: return null to "This is not a valid .torrent file"
        if (dao.findByInfoHash(meta.hash) != null) return null to "Already in your list"
        ready.join()
        torrents.storeSource(meta.hash, bytes)
        val url = TorrentEngine.FILE_PREFIX + meta.hash
        val id = dao.insert(
            DownloadEntity(url = url, fileName = meta.name, kind = DownloadKind.TORRENT.name, infoHash = meta.hash)
        )
        torrents.start(id, url, meta.name)
        return id to null
    }

    /** Download a torrent again from the start. Returns the new id, or null when it can not be restarted. */
    suspend fun restartTorrent(item: DownloadItem): Long? {
        if (item.url.startsWith(TorrentEngine.FILE_PREFIX)) {
            // the stored .torrent file is needed again: read it first, so nothing is removed if it is gone
            val bytes = torrents.readSource(item.url.removePrefix(TorrentEngine.FILE_PREFIX)) ?: return null
            remove(item.id)
            delay(TORRENT_RELEASE_MS)
            return addTorrentFile(bytes).first
        }
        remove(item.id)
        delay(TORRENT_RELEASE_MS) // the old torrent must be fully gone from the engine before it is added again
        return addMagnet(item.url)
    }

    /** Ids of multi-file torrents that wait for the user to choose their files. */
    val torrentSelectionRequests = torrents.selectionRequests

    /** Files inside a torrent (null while its info has not arrived). */
    suspend fun torrentFiles(id: Long): List<TorrentFileInfo>? = withContext(Dispatchers.IO) { torrents.files(id) }

    /** The user's file choice: these file indexes are downloaded, the rest are skipped. */
    fun applyTorrentSelection(id: Long, selected: Set<Int>) = torrents.applySelection(id, selected)

    /** Stops sharing a finished torrent (the saved files stay on the phone). */
    fun stopSeeding(id: Long) = torrents.stopSeeding(id)

    fun stopAllSeeding() = torrents.stopAllSeeding()

    /** The same torrent (same info hash) that is already in the list, or null. */
    suspend fun findMagnetDuplicate(magnet: String): DownloadItem? {
        val clean = TorrentEngine.normalizeMagnet(magnet) ?: return null
        val hash = TorrentEngine.infoHashOf(clean) ?: return null
        return dao.findByInfoHash(hash)?.toItem(null)
    }

    /** true = download only on Wi-Fi (unmetered networks). Takes effect immediately. */
    fun setWifiOnly(enabled: Boolean) = engine.setWifiOnly(enabled)

    /** How many files may download at the same time (1 or more). Takes effect immediately. */
    fun setMaxParallel(count: Int) = engine.setMaxParallel(count)

    // Each engine ignores ids it does not know, so both can be told (ids are shared, never clash).
    fun pause(id: Long) {
        engine.pause(id)
        torrents.pause(id)
    }

    /** Resume a paused download, or retry a failed one (continues from the saved part). */
    suspend fun resume(id: Long) {
        ready.join()
        val row = dao.getById(id) ?: return
        if (row.kind == DownloadKind.TORRENT.name) {
            torrents.start(id, row.url, row.fileName.ifBlank { null })
        } else {
            engine.start(id, row.url, row.fileName.ifBlank { null }, row.totalBytes)
        }
    }

    /** Stops the download and deletes the half-downloaded file. The row disappears from the list. */
    fun cancel(id: Long) {
        scope.launch { stopAndClean(id) }
    }

    /** Stops the download (HTTP or torrent) and deletes its half-done files. */
    private suspend fun stopAndClean(id: Long) {
        val row = dao.getById(id)
        if (row?.kind == DownloadKind.TORRENT.name) torrents.cancel(id, row.infoHash) else engine.cancel(id)
    }

    /** Removes an item from the list (stops it first if needed). A finished file stays on the phone. */
    suspend fun remove(id: Long) {
        stopAndClean(id)
        runCatching { TorrentEngine.savedList(appContext, id).delete() }
        dao.deleteById(id)
    }

    suspend fun clearFinished() {
        torrents.stopAllSeeding() // only finished torrents are shared, and they are all leaving the list
        dao.clearFinished()
    }

    /** The existing list item with the same link, or null. */
    suspend fun findByUrl(url: String): DownloadItem? =
        dao.findByUrl(DownloadEngine.normalizeUrl(url))?.toItem(null)

    /**
     * Deletes the downloaded file from the phone (MediaStore row on Android 10+, plain file on 9 and below)
     * and then the list row. Not finished items are simply cancelled. If Android refuses to delete the file
     * (for example after reinstalling the app, the file is no longer owned by Downix) the row is kept.
     */
    suspend fun deleteWithFile(id: Long): FileDeleteResult {
        val row = dao.getById(id) ?: return FileDeleteResult.ALREADY_GONE
        val completed = row.state == DownloadState.COMPLETED.name
        val saved = row.savedUri
        if (!completed || saved.isNullOrBlank()) {
            remove(id)
            return FileDeleteResult.DELETED
        }
        val folder = row.folderRef
        val result = withContext(Dispatchers.IO) {
            if (row.kind == DownloadKind.TORRENT.name && !folder.isNullOrBlank()) deleteSavedFolder(folder, id)
            else deleteSavedFile(saved)
        }
        if (result != FileDeleteResult.FAILED) {
            if (row.kind == DownloadKind.TORRENT.name) torrents.stopSeeding(id) // the files are gone: stop sharing them
            dao.deleteById(id)
            DownloadNotifier.cancelFinished(appContext, id)
        }
        return result
    }

    /** Deletes the whole folder of a multi-file torrent (see TorrentEngine.FOLDER_*). */
    private fun deleteSavedFolder(ref: String, id: Long): FileDeleteResult = try {
        when {
            ref.startsWith(TorrentEngine.FOLDER_MEDIASTORE) -> {
                val name = ref.removePrefix(TorrentEngine.FOLDER_MEDIASTORE)
                val path = android.os.Environment.DIRECTORY_DOWNLOADS + "/" + name
                // Best: delete exactly the files this torrent saved (another torrent may share the folder name)
                val listFile = TorrentEngine.savedList(appContext, id)
                val uris = listFile.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }
                val removed = if (uris != null) {
                    uris.count { runCatching { appContext.contentResolver.delete(Uri.parse(it), null, null) > 0 }.getOrDefault(false) }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val column = android.provider.MediaStore.MediaColumns.RELATIVE_PATH
                    appContext.contentResolver.delete(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "$column = ? OR $column LIKE ?",
                        arrayOf("$path/", "$path/%")
                    )
                } else 0
                runCatching { listFile.delete() }
                // MediaStore removes the files; the folder is removed only if it is empty and Android allows it
                runCatching {
                    File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), name)
                        .delete()
                }
                if (removed > 0) FileDeleteResult.DELETED else FileDeleteResult.ALREADY_GONE
            }
            ref.startsWith(TorrentEngine.FOLDER_FILE) -> {
                val dir = File(ref.removePrefix(TorrentEngine.FOLDER_FILE))
                when {
                    !dir.exists() -> FileDeleteResult.ALREADY_GONE
                    dir.deleteRecursively() -> FileDeleteResult.DELETED
                    else -> FileDeleteResult.FAILED
                }
            }
            else -> deleteDocumentFile(Uri.parse(ref)) // folder inside the picked download folder
        }
    } catch (e: Exception) {
        FileDeleteResult.FAILED
    }

    private fun deleteSavedFile(saved: String): FileDeleteResult = try {
        val uri = Uri.parse(saved)
        if (DocumentsContract.isDocumentUri(appContext, uri)) {
            deleteDocumentFile(uri) // saved in a picked folder
        } else if (uri.scheme == "content") {
            if (appContext.contentResolver.delete(uri, null, null) > 0) FileDeleteResult.DELETED
            else FileDeleteResult.ALREADY_GONE
        } else {
            val file = uri.path?.let { File(it) }
            when {
                file == null || !file.exists() -> FileDeleteResult.ALREADY_GONE
                file.delete() -> FileDeleteResult.DELETED
                else -> FileDeleteResult.FAILED
            }
        }
    } catch (e: Exception) {
        FileDeleteResult.FAILED
    }

    private fun deleteDocumentFile(uri: Uri): FileDeleteResult {
        val resolver = appContext.contentResolver
        fun exists(target: Uri): Boolean = runCatching {
            resolver.query(target, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)

        if (exists(uri)) {
            return if (DocumentsContract.deleteDocument(resolver, uri)) FileDeleteResult.DELETED
            else FileDeleteResult.FAILED
        }
        // Folder reachable but no file: it is gone. Folder not reachable (SD card out, no access): keep the row.
        val folder = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        return if (exists(folder)) FileDeleteResult.ALREADY_GONE else FileDeleteResult.FAILED
    }

    suspend fun pauseAll() {
        dao.getActiveIds().forEach { pause(it) }
    }

    /* ------------------------- engine -> database ------------------------- */

    private suspend fun onUpdate(u: DownloadUpdate) {
        when (u.state) {
            DownloadState.DOWNLOADING -> {
                // savingPercent 0: keep the final size in the database, resume needs it if the app is closed now
                bestBytes[u.id] = maxOf(bestBytes[u.id] ?: 0L, u.downloadedBytes)
                val now = android.os.SystemClock.elapsedRealtime()
                // also save the progress every 2 s, so a pause, crash or app restart still shows the real percent
                val periodic = now - (lastSavedAt[u.id] ?: 0L) >= 2000L && u.savingPercent < 0
                if (lastState[u.id] != DownloadState.DOWNLOADING || u.savingPercent == 0 || periodic) {
                    lastState[u.id] = DownloadState.DOWNLOADING
                    lastSavedAt[u.id] = now
                    write(u)
                }
                live.update { it + (u.id to u) }
            }

            DownloadState.CANCELED -> {
                lastState.remove(u.id)
                bestBytes.remove(u.id)
                lastSavedAt.remove(u.id)
                dao.deleteById(u.id)
                live.update { it - u.id }
            }

            else -> {
                if (u.state == DownloadState.COMPLETED || u.state == DownloadState.FAILED) {
                    lastState.remove(u.id)
                } else {
                    lastState[u.id] = u.state
                }
                val keep = when (u.state) {
                    DownloadState.COMPLETED -> {
                        bestBytes.remove(u.id); lastSavedAt.remove(u.id)
                        u
                    }
                    else -> {
                        // paused / queued / waiting / failed: never show less than what was already downloaded
                        val best = bestBytes[u.id] ?: 0L
                        if (u.downloadedBytes < best) u.copy(downloadedBytes = best) else u
                    }
                }
                lastSavedAt.remove(u.id)
                write(keep) // write first, then drop live data, so the UI never flashes an old state
                live.update { it - u.id }
                when (u.state) {
                    DownloadState.COMPLETED ->
                        DownloadNotifier.notifyCompleted(appContext, u.id, u.fileName, u.savedUri, u.downloadedBytes)
                    DownloadState.FAILED ->
                        DownloadNotifier.notifyFailed(appContext, u.id, u.fileName, u.error)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun write(u: DownloadUpdate) {
        dao.updateState(
            id = u.id,
            fileName = u.fileName,
            totalBytes = u.totalBytes,
            downloadedBytes = u.downloadedBytes,
            state = u.state.name,
            savedUri = u.savedUri,
            error = u.error,
            folderRef = u.folderRef
        )
    }

    companion object {
        private const val TORRENT_RELEASE_MS = 800L

        @Volatile private var instance: DownloadRepository? = null

        fun get(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** The torrent part of [AppSettings], in the form the torrent engine wants. */
internal fun AppSettings.toTorrentSettings() = TorrentSettings(
    maxActive = maxTorrents,
    downKb = torrentDownKb,
    upKb = torrentUpKb,
    dht = torrentDht,
    seeding = torrentSeeding,
    seedRatioX10 = seedRatioX10,
    seedHours = seedHours
)

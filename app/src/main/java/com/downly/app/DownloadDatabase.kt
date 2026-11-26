package com.downly.app

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ------------------------------------------------------------------ */
/*  Room: entity, dao, database                                        */
/* ------------------------------------------------------------------ */

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
    val createdAt: Long = System.currentTimeMillis()
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

    /** Keeps the old file name when the new one is blank. */
    @Query(
        "UPDATE downloads SET " +
            "fileName = CASE WHEN :fileName != '' THEN :fileName ELSE fileName END, " +
            "totalBytes = :totalBytes, downloadedBytes = :downloadedBytes, " +
            "state = :state, savedUri = :savedUri, error = :error WHERE id = :id"
    )
    suspend fun updateState(
        id: Long,
        fileName: String,
        totalBytes: Long,
        downloadedBytes: Long,
        state: String,
        savedUri: String?,
        error: String?
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
}

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "downly.db"
                ).build().also { instance = it }
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
    val savingPercent: Int = -1 // 0..100 while the file is copied to Downloads, else -1
) {
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

private fun DownloadEntity.toItem(live: DownloadUpdate?): DownloadItem {
    val parsed = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.PAUSED)
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
            error = null,
            createdAt = createdAt,
            savingPercent = live.savingPercent
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
            createdAt = createdAt
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
    private val dao = AppDatabase.get(context).downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val live = MutableStateFlow<Map<Long, DownloadUpdate>>(emptyMap())
    private val lastState = HashMap<Long, DownloadState>() // touched only by the collector coroutine

    /** Full list for the UI, newest first, with live progress. */
    val downloads: Flow<List<DownloadItem>> =
        combine(dao.observeAll(), live) { rows, liveMap ->
            rows.map { it.toItem(liveMap[it.id]) }
        }

    /** Current network (none / Wi-Fi / mobile data). */
    val networkState: StateFlow<NetworkState> = network.state

    /** Number of active downloads (use it to start / stop the foreground service). */
    val activeCount: Flow<Int> = dao.observeActiveCount()

    /** Finishes after interrupted rows were reset; add / resume wait for it. */
    private val ready: Job

    init {
        // Apply the saved settings right away, then follow every change.
        engine.setMaxParallel(settingsRepo.current.maxParallel)
        engine.setWifiOnly(settingsRepo.current.wifiOnly)
        scope.launch {
            settingsRepo.settings.collect {
                engine.setMaxParallel(it.maxParallel)
                engine.setWifiOnly(it.wifiOnly)
            }
        }
        // Subscribe first so no engine update is missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.updates.collect { onUpdate(it) }
        }
        ready = scope.launch { dao.resetInterrupted() }
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

    /** true = download only on Wi-Fi (unmetered networks). Takes effect immediately. */
    fun setWifiOnly(enabled: Boolean) = engine.setWifiOnly(enabled)

    /** How many files may download at the same time (1 or more). Takes effect immediately. */
    fun setMaxParallel(count: Int) = engine.setMaxParallel(count)

    fun pause(id: Long) = engine.pause(id)

    /** Resume a paused download, or retry a failed one (continues from the saved part). */
    suspend fun resume(id: Long) {
        ready.join()
        val row = dao.getById(id) ?: return
        engine.start(id, row.url, row.fileName.ifBlank { null }, row.totalBytes)
    }

    /** Stops the download and deletes the half-downloaded file. The row disappears from the list. */
    fun cancel(id: Long) = engine.cancel(id)

    /** Removes an item from the list (stops it first if needed). A finished file stays in Downloads. */
    suspend fun remove(id: Long) {
        engine.cancel(id)
        dao.deleteById(id)
    }

    suspend fun clearFinished() = dao.clearFinished()

    /** The existing list item with the same link, or null. */
    suspend fun findByUrl(url: String): DownloadItem? =
        dao.findByUrl(DownloadEngine.normalizeUrl(url))?.toItem(null)

    /**
     * Deletes the downloaded file from the phone (MediaStore row on Android 10+, plain file on 9 and below)
     * and then the list row. Not finished items are simply cancelled. If Android refuses to delete the file
     * (for example after reinstalling the app, the file is no longer owned by Downly) the row is kept.
     */
    suspend fun deleteWithFile(id: Long): FileDeleteResult {
        val row = dao.getById(id) ?: return FileDeleteResult.ALREADY_GONE
        val completed = row.state == DownloadState.COMPLETED.name
        val saved = row.savedUri
        if (!completed || saved.isNullOrBlank()) {
            remove(id)
            return FileDeleteResult.DELETED
        }
        val result = withContext(Dispatchers.IO) { deleteSavedFile(saved) }
        if (result != FileDeleteResult.FAILED) {
            dao.deleteById(id)
            DownloadNotifier.cancelFinished(appContext, id)
        }
        return result
    }

    private fun deleteSavedFile(saved: String): FileDeleteResult = try {
        val uri = Uri.parse(saved)
        if (uri.scheme == "content") {
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

    suspend fun pauseAll() {
        dao.getActiveIds().forEach { engine.pause(it) }
    }

    /* ------------------------- engine -> database ------------------------- */

    private suspend fun onUpdate(u: DownloadUpdate) {
        when (u.state) {
            DownloadState.DOWNLOADING -> {
                // savingPercent 0: keep the final size in the database, resume needs it if the app is closed now
                if (lastState[u.id] != DownloadState.DOWNLOADING || u.savingPercent == 0) {
                    lastState[u.id] = DownloadState.DOWNLOADING
                    write(u)
                }
                live.update { it + (u.id to u) }
            }

            DownloadState.CANCELED -> {
                lastState.remove(u.id)
                dao.deleteById(u.id)
                live.update { it - u.id }
            }

            else -> {
                if (u.state == DownloadState.COMPLETED || u.state == DownloadState.FAILED) {
                    lastState.remove(u.id)
                } else {
                    lastState[u.id] = u.state
                }
                write(u) // write first, then drop live data, so the UI never flashes an old state
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
            error = u.error
        )
    }

    companion object {
        @Volatile private var instance: DownloadRepository? = null

        fun get(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}

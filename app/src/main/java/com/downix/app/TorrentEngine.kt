package com.downix.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** One file inside a torrent (for the "Choose files" popup). [index] is the position inside the torrent. */
data class TorrentFileInfo(val index: Int, val path: String, val size: Long, val selected: Boolean = true)

/** Torrent options from Settings (speed limits in KB/s, 0 = no limit). */
data class TorrentSettings(
    val maxActive: Int = AppSettings.DEFAULT_TORRENTS,
    val downKb: Int = 0,
    val upKb: Int = 0,
    val dht: Boolean = true,
    val seeding: Boolean = false,
    val seedRatioX10: Int = 20,
    val seedHours: Int = 4
)

/** Live numbers of a finished torrent that is still being shared. */
data class SeedStats(val uploadSpeed: Long, val uploaded: Long, val ratio: Float, val peers: Int)

/** What a .torrent file contains, read without starting anything. */
data class TorrentMeta(val hash: String, val name: String)

/**
 * Torrent downloads (magnet links and .torrent files), built on libtorrent4j.
 *
 * It follows the same rules as [DownloadEngine], so the rest of the app does not care which engine runs a row:
 *  - it reports progress as [DownloadUpdate] on [updates] (QUEUED / DOWNLOADING / PAUSED / WAITING / COMPLETED / FAILED)
 *  - torrent data is first downloaded into a private work folder (torrents/<id>/)
 *  - when every file is complete, the files are copied to the chosen download folder (same place as normal
 *    downloads; a multi-file torrent keeps its own folder), then the work folder is deleted
 *  - network aware: no network (or Wi-Fi only on mobile data) = WAITING, continues by itself
 *
 * The libtorrent session (and its native code) starts only when the first torrent is added.
 * Work is polled once a second; that is cheap and keeps this class free of alert-listener plumbing.
 */
class TorrentEngine(
    context: Context,
    network: StateFlow<NetworkState>
) {

    /** One torrent that the engine knows about (running, paused or being saved). */
    private class Entry(val id: Long, val source: String, val hash: Sha1Hash, @Volatile var name: String) {
        @Volatile var paused = false
        @Volatile var finishing = false
        @Volatile var configured = false   // true after the one-time "run now, not queued" setup of its handle
        @Volatile var netPaused = false    // paused by us because the network is not allowed
        @Volatile var selectionDone = false // false = a multi-file torrent still waits for "Choose files"
        @Volatile var skipped: Set<Int> = emptySet() // file indexes the user did not select
        @Volatile var done = 0L
        @Volatile var total = -1L
        @Volatile var finishJob: Job? = null
        @Volatile var queued = false       // paused by us because the "Active torrents" limit is reached
        @Volatile var spaceChecked = false // free space was checked once the size of the chosen files is known
        @Volatile var idleSince = 0L       // since when there is no speed and no peer (0 = not idle)
    }

    /** A finished torrent that keeps sharing (its files stay in the work folder until seeding stops). */
    private class Seeder(val id: Long, val hash: Sha1Hash, val name: String, val total: Long) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        @Volatile var netPaused = false
        @Volatile var fresh = false                 // restored after an app restart: handle setup still to do
        @Volatile var skipped: Set<Int> = emptySet() // files the user had not selected
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var sessionRef: SessionManager? = null

    private val entries = ConcurrentHashMap<Long, Entry>()
    private val seeders = ConcurrentHashMap<Long, Seeder>()
    @Volatile private var config = TorrentSettings()
    private val wifiOnly = MutableStateFlow(false)
    @Volatile private var saveFolder = "" // picked folder (tree URI), blank = Downloads

    /** true = torrents may use the network right now. */
    private val allowed: StateFlow<Boolean> = combine(network, wifiOnly) { n, w -> isAllowed(n, w) }
        .stateIn(scope, SharingStarted.Eagerly, isAllowed(network.value, false))

    private val _updates = MutableSharedFlow<DownloadUpdate>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<DownloadUpdate> = _updates.asSharedFlow()

    private val _selection = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Emits the id of a multi-file torrent that is waiting for the user to choose its files. */
    val selectionRequests: SharedFlow<Long> = _selection.asSharedFlow()

    private val _seeding = MutableStateFlow<Map<Long, SeedStats>>(emptyMap())

    /** Finished torrents that are being shared right now (id -> live numbers). */
    val seeding: StateFlow<Map<Long, SeedStats>> = _seeding

    /** How many torrents are being shared (keeps the background service alive). */
    val seedingCount: Flow<Int> = _seeding.map { it.size }.distinctUntilChanged()

    init {
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MS)
            }
        }
    }

    /* -------------------------- public API -------------------------- */

    fun setWifiOnly(enabled: Boolean) {
        wifiOnly.value = enabled
    }

    fun setSaveFolder(folderUri: String) {
        saveFolder = folderUri
    }

    /** Applies the torrent options of Settings. Speed limit and DHT are applied to the session at once. */
    fun configure(settings: TorrentSettings) {
        config = settings
        scope.launch { runCatching { sessionRef?.let { applySessionSettings(it, settings) } } }
    }

    private fun applySessionSettings(sm: SessionManager, c: TorrentSettings) {
        val pack = SettingsPack()
        pack.downloadRateLimit(c.downKb * 1024)
        pack.uploadRateLimit(c.upKb * 1024)
        pack.setEnableDht(c.dht)
        sm.applySettings(pack)
    }

    /** Stops sharing one finished torrent and frees its work folder. */
    fun stopSeeding(id: Long) {
        val s = seeders[id] ?: return
        endSeeding(s)
    }

    fun stopAllSeeding() {
        seeders.values.forEach { endSeeding(it) }
    }

    /** Removes what is left of a finished torrent from an earlier app run (the app was closed while it was seeding). */
    fun cleanupLeftovers(id: Long, infoHash: String?) {
        if (seeders.containsKey(id) || entries.containsKey(id)) return
        runCatching { workDir(id).deleteRecursively() }
        infoHash?.let { runCatching { sourceFile(it).delete() } }
    }

    /**
     * Called once at app start for every finished torrent of an earlier run. When seeding is on and its files
     * and .torrent file are still there, it is shared again (true). Otherwise the leftovers are removed (false).
     */
    fun restoreOrCleanup(id: Long, infoHash: String?, name: String, total: Long): Boolean {
        if (seeders.containsKey(id) || entries.containsKey(id)) return true
        val src = infoHash?.let { sourceFile(it) }
        if (config.seeding && src != null && src.exists() && workDir(id).exists()) {
            try {
                val sm = session()
                val info = TorrentInfo(src)
                sm.download(info, workDir(id))
                seeders[id] = Seeder(id, info.infoHash(), name, total).also {
                    it.skipped = loadSkipped(id).orEmpty()
                    it.fresh = true
                }
                _seeding.update { it + (id to SeedStats(0L, 0L, 0f, 0)) }
                return true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // could not restore: fall through and clean up
            }
        }
        cleanupLeftovers(id, infoHash)
        return false
    }

    private fun endSeeding(s: Seeder) {
        if (seeders.remove(s.id, s)) {
            _seeding.update { it - s.id }
            scope.launch {
                runCatching {
                    val handle = sessionRef?.find(s.hash)
                    if (handle != null && handle.isValid()) sessionRef?.remove(handle)
                }
                delay(500) // let libtorrent let go of the files
                runCatching { workDir(s.id).deleteRecursively() }
                runCatching { sourceFile(s.hash.toHex()).delete() }
            }
        }
    }

    /**
     * Start or resume a torrent. If its files are already (partly) in the work folder, libtorrent checks them
     * and continues from there, also after the app was closed.
     */
    fun start(id: Long, source: String, knownName: String? = null) {
        val known = entries[id]
        if (known != null && !known.paused && !known.finishing) return // already running

        scope.launch {
            try {
                val sm = session()
                val clean = source.trim()
                // A magnet link, or "torrent-file:<hash>" = a .torrent file that was stored when it was added.
                val fileInfo: TorrentInfo? =
                    if (clean.startsWith(FILE_PREFIX)) TorrentInfo(sourceFile(clean.removePrefix(FILE_PREFIX))) else null
                val hash = fileInfo?.infoHash() ?: magnetHash(clean)
                val entry = entries.getOrPut(id) {
                    Entry(
                        id, clean, hash,
                        knownName?.takeIf { it.isNotBlank() } ?: fileInfo?.name() ?: nameOf(clean)
                    )
                }
                if (entry.finishing) return@launch
                entry.paused = false
                entry.netPaused = false
                entry.queued = false
                entry.spaceChecked = false
                entry.idleSince = 0L
                publish(entry, DownloadState.QUEUED)

                val existing = sm.find(hash)
                if (existing != null && existing.isValid()) {
                    existing.resume()
                    entry.configured = true
                } else {
                    entry.configured = false
                    val dir = workDir(id).also { it.mkdirs() }
                    if (fileInfo != null) sm.download(fileInfo, dir)
                    else sm.download(withTrackers(clean), dir, TorrentFlags.AUTO_MANAGED)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                fail(id, t)
            }
        }
    }

    /** Pause: the torrent stops, the downloaded data stays in the work folder. */
    fun pause(id: Long) {
        val e = entries[id] ?: return
        if (e.finishing) return
        e.paused = true
        scope.launch {
            runCatching {
                sessionRef?.find(e.hash)?.takeIf { it.isValid() }?.let {
                    it.unsetFlags(TorrentFlags.AUTO_MANAGED)
                    it.pause()
                }
            }
            publish(e, DownloadState.PAUSED)
        }
    }

    /** Cancel: stops the torrent and deletes everything downloaded so far. */
    fun cancel(id: Long, infoHash: String? = null) {
        scope.launch {
            val e = entries.remove(id)
            val seeder = seeders.remove(id)
            if (seeder != null) _seeding.update { it - id }
            e?.finishJob?.cancel()
            runCatching {
                val sm = sessionRef
                val hash = e?.hash ?: seeder?.hash
                val handle = hash?.let { sm?.find(it) }
                if (sm != null && handle != null && handle.isValid()) sm.remove(handle, SessionHandle.DELETE_FILES)
            }
            delay(300) // let libtorrent let go of the files
            workDir(id).deleteRecursively()
            (infoHash ?: e?.hash?.toHex() ?: seeder?.hash?.toHex())?.let { sourceFile(it).delete() }
            _updates.tryEmit(DownloadUpdate(id, DownloadState.CANCELED, e?.name ?: seeder?.name.orEmpty(), 0L, -1L))
        }
    }

    /* ----------------------- .torrent files, file choice ----------------------- */

    /** Reads a .torrent file. null = it is not a valid torrent (or the torrent engine can not start). */
    fun inspect(bytes: ByteArray): TorrentMeta? = runCatching {
        val info = TorrentInfo.bdecode(bytes)
        TorrentMeta(info.infoHash().toHex().lowercase(), info.name().orEmpty().ifBlank { "Torrent" })
    }.getOrNull()

    /** Keeps the .torrent file, so the torrent can be paused, resumed and restarted later. */
    fun storeSource(hash: String, bytes: ByteArray) {
        val file = sourceFile(hash)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    /** The stored .torrent file of [hash], or null when it is gone (for example after the torrent finished). */
    fun readSource(hash: String): ByteArray? = sourceFile(hash).takeIf { it.exists() }?.readBytes()

    /** The files of a torrent that is known to the engine (null while its info has not arrived). */
    fun files(id: Long): List<TorrentFileInfo>? {
        val e = entries[id]
        val hash = e?.hash ?: seeders[id]?.hash ?: return null
        val skipped = e?.skipped ?: loadSkipped(id).orEmpty()
        val handle = sessionRef?.find(hash)?.takeIf { it.isValid() } ?: return null
        val info = handle.torrentFile() ?: return null
        val fs = info.files()
        return (0 until info.numFiles()).map { TorrentFileInfo(it, fs.filePath(it), fs.fileSize(it), it !in skipped) }
    }

    /** Applies the user's choice ([selected] = indexes to download) and starts the torrent. */
    fun applySelection(id: Long, selected: Set<Int>) {
        val e = entries[id] ?: return
        scope.launch {
            runCatching {
                val handle = sessionRef?.find(e.hash)?.takeIf { it.isValid() } ?: return@runCatching
                val count = handle.torrentFile()?.numFiles() ?: return@runCatching
                val skipped = (0 until count).filter { it !in selected }.toSet()
                saveSkipped(id, skipped)
                applyPriorities(handle, count, skipped)
                e.skipped = skipped
                e.selectionDone = true
                e.paused = false
                e.netPaused = false
                handle.resume()
                publish(e, DownloadState.QUEUED)
            }
        }
    }

    private fun applyPriorities(handle: TorrentHandle, count: Int, skipped: Set<Int>) {
        handle.prioritizeFiles(Array(count) { if (it in skipped) Priority.IGNORE else Priority.DEFAULT })
    }

    private fun selectionFile(id: Long) = File(workDir(id), "selection.txt")

    private fun saveSkipped(id: Long, skipped: Set<Int>) {
        selectionFile(id).apply { parentFile?.mkdirs() }.writeText(skipped.sorted().joinToString(","))
    }

    private fun loadSkipped(id: Long): Set<Int>? {
        val file = selectionFile(id)
        if (!file.exists()) return null
        return file.readText().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    private fun sourceFile(hash: String) = File(appContext.filesDir, "torrent-files/${hash.lowercase()}.torrent")

    /* ----------------------------- polling ----------------------------- */

    private fun tick() {
        val sm = sessionRef ?: return
        val ok = allowed.value
        tickSeeders(sm, ok)
        if (entries.isEmpty()) return
        val maxActive = config.maxActive.coerceAtLeast(1)
        var slots = 0 // torrents that may run now; the oldest ones come first, the rest wait in the queue
        for (e in entries.values.sortedBy { it.id }) {
            if (e.finishing || e.paused) continue
            try {
                val handle = sm.find(e.hash)
                if (handle == null || !handle.isValid()) continue // still being added

                if (!e.configured) {
                    // Added as auto-managed (so libtorrent never keeps it in its own hidden queue): from now on
                    // we decide ourselves when it runs.
                    handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
                    handle.resume()
                    e.configured = true
                }
                if (!ok) {
                    if (!e.netPaused) {
                        handle.pause()
                        e.netPaused = true
                    }
                    publish(
                        e, DownloadState.WAITING,
                        error = if (wifiOnly.value) "Waiting for Wi-Fi" else "Waiting for network"
                    )
                    continue
                }
                if (e.netPaused) {
                    handle.resume()
                    e.netPaused = false
                }

                // "Active torrents" limit: only this many run, the others wait (and start by themselves later)
                if (slots >= maxActive) {
                    if (!e.queued) {
                        handle.pause()
                        e.queued = true
                        publish(e, DownloadState.QUEUED)
                    }
                    continue
                }
                slots++
                if (e.queued) {
                    handle.resume()
                    e.queued = false
                }

                val info = handle.torrentFile() // null until the torrent info (metadata) has arrived
                val st = handle.status()
                if (info != null && info.numFiles() > 0) {
                    info.name()?.takeIf { it.isNotBlank() }?.let { e.name = it }
                    e.total = st.totalWanted()
                    e.done = st.totalWantedDone()
                    if (!e.selectionDone) {
                        val saved = loadSkipped(e.id)
                        if (saved != null) {
                            // the user already chose earlier (before an app restart): apply it again
                            applyPriorities(handle, info.numFiles(), saved)
                            e.skipped = saved
                            e.selectionDone = true
                        } else if (info.numFiles() > 1) {
                            // several files: do not download anything until the user has chosen
                            handle.pause()
                            e.paused = true
                            publish(e, DownloadState.PAUSED, error = CHOOSE_FILES)
                            _selection.tryEmit(e.id)
                            continue
                        } else {
                            e.selectionDone = true
                        }
                    }
                    if (!e.spaceChecked && e.selectionDone) {
                        // The chosen files must fit on the phone: stop with a clear message instead of failing half way.
                        e.spaceChecked = true
                        val need = (e.total - e.done).coerceAtLeast(0L)
                        val free = workDir(e.id).also { it.mkdirs() }.usableSpace
                        if (need > 0 && free < need + MIN_FREE_SPACE) {
                            handle.pause()
                            e.paused = true
                            publish(e, DownloadState.FAILED, error = "Not enough storage space")
                            continue
                        }
                    }
                    if (st.isFinished() || st.isSeeding()) {
                        beginFinish(sm, handle, e, info)
                        continue
                    }
                }
                // No speed and no peer for a while: say so, instead of "Finding peers" for ever
                val speed = st.downloadRate().toLong()
                val now = android.os.SystemClock.elapsedRealtime()
                if (speed > 0 || st.numPeers() > 0) e.idleSince = 0L
                else if (e.idleSince == 0L) e.idleSince = now
                val noPeers = e.idleSince != 0L && now - e.idleSince >= NO_PEERS_MS
                publish(
                    e, DownloadState.DOWNLOADING,
                    speed = speed,
                    error = if (noPeers) NO_PEERS else null,
                    peers = st.numPeers(),
                    seeds = st.numSeeds(),
                    peerTotal = swarmSize(st)
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // one bad torrent must never stop the others: try again on the next tick
            }
        }
    }

    /** Seeding: stop when the ratio or time limit is reached (or seeding was switched off), pause without network. */
    private fun tickSeeders(sm: SessionManager, networkOk: Boolean) {
        if (seeders.isEmpty()) return
        val c = config
        val now = android.os.SystemClock.elapsedRealtime()
        val stats = HashMap<Long, SeedStats>()
        for (s in seeders.values) {
            try {
                val handle = sm.find(s.hash)
                if (handle == null || !handle.isValid()) {
                    endSeeding(s)
                    continue
                }
                if (s.fresh) {
                    // restored after an app restart: set it up like the other torrents (own control, skipped files)
                    val files = handle.torrentFile()?.numFiles() ?: 0
                    if (files > 0) {
                        handle.unsetFlags(TorrentFlags.AUTO_MANAGED)
                        if (s.skipped.isNotEmpty()) applyPriorities(handle, files, s.skipped)
                        handle.resume()
                        s.fresh = false
                    }
                    stats[s.id] = _seeding.value[s.id] ?: SeedStats(0L, 0L, 0f, 0)
                    continue
                }
                val st = handle.status()
                val uploaded = st.allTimeUpload()
                val ratio = if (s.total > 0) uploaded.toFloat() / s.total else 0f
                val timeUp = now - s.startedAt >= c.seedHours * 3_600_000L
                if (!c.seeding || ratio >= c.seedRatioX10 / 10f || timeUp) {
                    endSeeding(s)
                    continue
                }
                if (!networkOk) {
                    if (!s.netPaused) {
                        handle.pause()
                        s.netPaused = true
                    }
                    stats[s.id] = SeedStats(0L, uploaded, ratio, 0)
                    continue
                }
                if (s.netPaused) {
                    handle.resume()
                    s.netPaused = false
                }
                stats[s.id] = SeedStats(st.uploadRate().toLong(), uploaded, ratio, st.numPeers())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // keep sharing, try again on the next tick
                stats[s.id] = _seeding.value[s.id] ?: SeedStats(0L, 0L, 0f, 0)
            }
        }
        _seeding.value = stats.filterKeys { seeders.containsKey(it) }
    }

    /* ------------------------ finish: copy to folder ------------------------ */

    private fun beginFinish(
        sm: SessionManager,
        handle: TorrentHandle,
        e: Entry,
        info: org.libtorrent4j.TorrentInfo
    ) {
        if (e.finishing) return
        e.finishing = true
        val fs = info.files()
        val files = (0 until info.numFiles()).filter { it !in e.skipped }.map { fs.filePath(it) to fs.fileSize(it) }
        e.total = files.sumOf { it.second }
        e.done = e.total

        val seed = config.seeding
        e.finishJob = scope.launch {
            try {
                if (!seed) {
                    // No seeding: stop the torrent; the files stay on disk for the copy below.
                    runCatching { sm.remove(handle) }
                    delay(500)
                }
                publishSaving(e, 0)
                val saved = saveAll(e, files)
                entries.remove(e.id)
                if (saved.uris.isNotEmpty() && saved.folderRef != null) {
                    // exact list of saved files: "Delete folder" must only remove THIS torrent's files
                    runCatching {
                        savedList(appContext, e.id).apply { parentFile?.mkdirs() }.writeText(saved.uris.joinToString("\n"))
                    }
                }
                if (seed) {
                    // a magnet link has no .torrent file yet: keep one, so seeding can continue after an app restart
                    runCatching {
                        val file = sourceFile(e.hash.toHex())
                        if (!file.exists()) {
                            file.parentFile?.mkdirs()
                            file.writeBytes(torrentFileBytes(info))
                        }
                    }
                    // Keep sharing from the work folder; it is deleted when seeding stops.
                    seeders[e.id] = Seeder(e.id, e.hash, e.name, e.total)
                    _seeding.update { it + (e.id to SeedStats(0L, 0L, 0f, 0)) }
                } else {
                    workDir(e.id).deleteRecursively()
                    sourceFile(e.hash.toHex()).delete()
                }
                _updates.tryEmit(
                    DownloadUpdate(
                        id = e.id,
                        state = DownloadState.COMPLETED,
                        fileName = e.name,
                        downloadedBytes = e.total,
                        totalBytes = e.total,
                        savedUri = saved.mainUri,
                        folderRef = saved.folderRef
                    )
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // Forget the entry: Resume starts it again, libtorrent sees all files complete and we retry saving.
                entries.remove(e.id)
                fail(e.id, t, e.name, e.total)
            }
        }
    }

    /** Result of saving: the biggest file, and the torrent's own folder when it has several files. */
    private class Saved(val mainUri: String, val folderRef: String?, val uris: List<String> = emptyList())

    /** Name of the folder that holds a multi-file torrent (all its paths start with it), null for a single file. */
    private fun topFolder(files: List<Pair<String, Long>>): String? =
        files.map { it.first }.firstOrNull { it.contains('/') }?.substringBefore('/')?.takeIf { it.isNotBlank() }

    /** Copies every file of the torrent to the chosen folder. Returns the main file and the folder (if any). */
    private suspend fun saveAll(e: Entry, files: List<Pair<String, Long>>): Saved {
        val total = files.sumOf { it.second }
        val top = topFolder(files)
        val folder = saveFolder
        val work = workDir(e.id)
        if (SaveLocation.isOnPhoneStorage(folder) && work.usableSpace < total + MIN_FREE_SPACE) {
            throw IOException("Not enough storage space to save the files")
        }
        val progress = Progress(total)

        if (folder.isNotBlank()) {
            val writer = TreeWriter(folder)
            try {
                val main = copyFiles(e, files, work, progress) { rel -> writer.open(rel) }
                return Saved(main, top?.let { writer.folderRef(it) })
            } catch (c: CancellationException) {
                writer.rollback()
                throw c
            } catch (t: Exception) {
                // folder gone / no access: remove the partial copy and save to Downloads instead
                writer.rollback()
                progress.done = 0L
                publishSaving(e, 0)
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val writer = MediaStoreWriter()
            try {
                val main = copyFiles(e, files, work, progress) { rel -> writer.open(rel) }
                Saved(main, top?.let { writer.folderRef(it) }, writer.createdUris())
            } catch (t: Throwable) {
                writer.rollback()
                throw t
            }
        } else {
            val writer = LegacyFileWriter()
            try {
                val main = copyFiles(e, files, work, progress) { rel -> writer.open(rel) }
                writer.scan()
                Saved(main, top?.let { writer.folderRef(it) })
            } catch (t: Throwable) {
                writer.rollback()
                throw t
            }
        }
    }

    private class Progress(val total: Long) {
        var done = 0L
        var lastTick = 0L
    }

    private suspend fun copyFiles(
        e: Entry,
        files: List<Pair<String, Long>>,
        work: File,
        progress: Progress,
        open: (String) -> Pair<OutputStream, String>
    ): String {
        var mainUri = ""
        var mainSize = -1L
        val buffer = ByteArray(COPY_BUFFER)
        for ((rel, size) in files) {
            currentCoroutineContext().ensureActive()
            val source = File(work, rel)
            if (!source.exists()) {
                // padding files of some torrents are never written to disk: nothing to copy
                if (isPadding(rel) || size == 0L) continue
                throw IOException("A file is missing: ${rel.substringAfterLast('/')}")
            }
            val (out, uri) = open(rel)
            source.inputStream().use { input ->
                out.use { o ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        o.write(buffer, 0, n)
                        progress.done += n
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - progress.lastTick >= 500L && progress.total > 0) {
                            progress.lastTick = now
                            publishSaving(e, (progress.done * 100 / progress.total).toInt().coerceIn(0, 100))
                        }
                    }
                    o.flush()
                }
            }
            if (size > mainSize) {
                mainSize = size
                mainUri = uri
            }
        }
        if (mainUri.isEmpty()) throw IOException("The torrent has no files to save")
        return mainUri
    }

    private fun isPadding(rel: String): Boolean =
        rel.contains("/.pad/") || rel.startsWith(".pad/") || rel.substringAfterLast('/').startsWith(".____padding")

    /* ---- writer 1: a folder picked in Settings (Storage Access Framework) ---- */

    private inner class TreeWriter(folder: String) {
        private val resolver = appContext.contentResolver
        private val tree = Uri.parse(folder)
        private val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        private val dirs = HashMap<String, Uri>()
        private val created = ArrayList<Uri>()

        private fun dir(path: String): Uri {
            if (path.isEmpty()) return root
            dirs[path]?.let { return it }
            val parent = dir(path.substringBeforeLast('/', ""))
            val made = DocumentsContract.createDocument(
                resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, path.substringAfterLast('/')
            ) ?: throw IOException("Could not create a folder in the chosen location")
            dirs[path] = made
            created.add(made)
            return made
        }

        fun open(rel: String): Pair<OutputStream, String> {
            val parent = dir(rel.substringBeforeLast('/', ""))
            val name = rel.substringAfterLast('/')
            val doc = DocumentsContract.createDocument(resolver, parent, mimeOf(name), name)
                ?: throw IOException("Could not create the file in the chosen folder")
            created.add(doc)
            val out = resolver.openOutputStream(doc) ?: throw IOException("Could not open the file for writing")
            return out to doc.toString()
        }

        /** Document uri of the torrent's folder inside the picked folder. */
        fun folderRef(top: String): String? = dirs[top]?.toString()

        fun rollback() {
            created.asReversed().forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        }
    }

    /* ---- writer 2: public Downloads, Android 10+ (MediaStore, no permission needed) ---- */

    private inner class MediaStoreWriter {
        private val resolver = appContext.contentResolver
        private val created = ArrayList<Uri>()

        fun open(rel: String): Pair<OutputStream, String> {
            val sub = rel.substringBeforeLast('/', "")
            val name = rel.substringAfterLast('/')
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    if (sub.isEmpty()) Environment.DIRECTORY_DOWNLOADS else "${Environment.DIRECTORY_DOWNLOADS}/$sub"
                )
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the file in Downloads")
            created.add(uri)
            val out = resolver.openOutputStream(uri) ?: throw IOException("Could not open the file for writing")
            return out to uri.toString()
        }

        /** "ms:<folder>" = a folder inside the public Downloads (found again through MediaStore). */
        fun folderRef(top: String): String = FOLDER_MEDIASTORE + top

        fun createdUris(): List<String> = created.map { it.toString() }

        fun rollback() {
            created.forEach { runCatching { resolver.delete(it, null, null) } }
        }
    }

    /* ---- writer 3: public Downloads, Android 9 and below (plain files) ---- */

    private inner class LegacyFileWriter {
        private val created = ArrayList<File>()
        private val base: File = run {
            var dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if ((!dir.exists() && !dir.mkdirs()) || !dir.canWrite()) {
                dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                if (!dir.exists()) dir.mkdirs()
            }
            dir
        }

        fun open(rel: String): Pair<OutputStream, String> {
            var target = File(base, rel)
            target.parentFile?.mkdirs()
            var i = 1
            while (target.exists()) {
                val name = rel.substringAfterLast('/')
                val stem = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
                target = File(target.parentFile, "$stem ($i)$ext")
                i++
            }
            created.add(target)
            return target.outputStream() to Uri.fromFile(target).toString()
        }

        /** "file:<path>" = a plain folder (Android 9 and below). */
        fun folderRef(top: String): String = FOLDER_FILE + File(base, top).absolutePath

        fun scan() {
            MediaScannerConnection.scanFile(appContext, created.map { it.absolutePath }.toTypedArray(), null, null)
        }

        fun rollback() {
            created.forEach { runCatching { it.delete() } }
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    private fun session(): SessionManager = synchronized(lock) {
        sessionRef ?: SessionManager().also {
            it.start()
            runCatching { applySessionSettings(it, config) }
            sessionRef = it
        }
    }

    private fun workDir(id: Long): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, "torrents/$id")
    }

    /** Peers known in the swarm: connected + waiting in the list, or the tracker's numbers, whichever is bigger. */
    private fun swarmSize(st: Any): Int {
        fun num(name: String): Int = runCatching { (st.javaClass.getMethod(name).invoke(st) as Number).toInt() }
            .getOrDefault(0).coerceAtLeast(0)
        val connected = num("numPeers")
        val listed = num("listPeers") + num("listSeeds")
        val tracker = num("numComplete") + num("numIncomplete")
        return maxOf(connected, listed, tracker)
    }

    private fun publish(
        e: Entry,
        state: DownloadState,
        speed: Long = 0L,
        error: String? = null,
        peers: Int = 0,
        seeds: Int = 0,
        peerTotal: Int = 0
    ) {
        _updates.tryEmit(
            DownloadUpdate(
                id = e.id,
                state = state,
                fileName = e.name,
                downloadedBytes = e.done,
                totalBytes = e.total,
                speedBytesPerSec = speed,
                error = error,
                peers = peers,
                seeds = seeds,
                peerTotal = peerTotal
            )
        )
    }

    private fun publishSaving(e: Entry, percent: Int) {
        _updates.tryEmit(
            DownloadUpdate(
                id = e.id,
                state = DownloadState.DOWNLOADING,
                fileName = e.name,
                downloadedBytes = e.total,
                totalBytes = e.total,
                savingPercent = percent
            )
        )
    }

    private fun fail(id: Long, t: Throwable, name: String = "", total: Long = -1L) {
        val message = when (t) {
            is UnsatisfiedLinkError, is NoClassDefFoundError, is ExceptionInInitializerError ->
                "Torrent engine could not start on this phone"
            is IllegalArgumentException -> "Invalid magnet link"
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Torrent failed"
        }
        _updates.tryEmit(
            DownloadUpdate(
                id = id, state = DownloadState.FAILED, fileName = name,
                downloadedBytes = 0L, totalBytes = total, error = message
            )
        )
    }

    companion object {
        private const val TICK_MS = 1000L

        /** Prefix of the stored link of a torrent that was added from a .torrent file. */
        const val FILE_PREFIX = "torrent-file:"

        /** Shown on a paused multi-file torrent until the user has chosen its files. */
        const val CHOOSE_FILES = "Choose files"

        /** Shown on a running torrent that has had no peer and no speed for a while. */
        const val NO_PEERS = "No peers found"
        private const val NO_PEERS_MS = 90_000L

        /** List of the files a finished multi-file torrent saved in the public Downloads (one uri per line). */
        fun savedList(context: Context, id: Long) = File(context.applicationContext.filesDir, "torrent-saved/$id.txt")

        /** Prefixes of the folder reference stored for a multi-file torrent (a picked folder is a plain content uri). */
        const val FOLDER_MEDIASTORE = "ms:"
        const val FOLDER_FILE = "file:"
        private const val COPY_BUFFER = 1024 * 1024
        private const val MIN_FREE_SPACE = 5L * 1024 * 1024

        /** Public trackers added to magnet links that carry none, so peers are found faster than with DHT alone. */
        private val DEFAULT_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce"
        )

        private val BTIH = Regex("xt=urn:btih:([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
        private val DN = Regex("[?&]dn=([^&]*)", RegexOption.IGNORE_CASE)
        private val MAGNET = Regex("magnet:\\?\\S+", RegexOption.IGNORE_CASE)

        private fun isAllowed(state: NetworkState, wifiOnly: Boolean): Boolean =
            state != NetworkState.NONE && !(wifiOnly && state == NetworkState.METERED)

        /** The magnet link if it is valid (has an info hash: 40 hex or 32 base32 characters), otherwise null. */
        fun normalizeMagnet(input: String): String? {
            val text = input.trim()
            if (!text.startsWith("magnet:?", ignoreCase = true)) return null
            val hash = BTIH.find(text)?.groupValues?.get(1) ?: return null
            val hex = hash.length == 40 && hash.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            val base32 = hash.length == 32 && hash.all { it.uppercaseChar() in 'A'..'Z' || it in '2'..'7' }
            return if (hex || base32) text else null
        }

        /** Finds a magnet link inside any copied / shared text. */
        fun extractMagnet(text: String?): String? =
            text?.let { MAGNET.find(it)?.value }?.let { normalizeMagnet(it) }

        /** Bytes of a .torrent file for [info]; works across libtorrent4j versions (method names differ). */
        fun torrentFileBytes(info: TorrentInfo): ByteArray {
            val cls = info.javaClass
            runCatching { (cls.getMethod("bencode").invoke(info) as? ByteArray)?.let { return it } }
            runCatching {
                val entry = cls.getMethod("toEntry").invoke(info)
                (entry.javaClass.getMethod("bencode").invoke(entry) as? ByteArray)?.let { return it }
            }
            val section = cls.getMethod("metadata").invoke(info) as ByteArray
            // a minimal .torrent: {"info": <info dictionary>}
            return "d4:info".toByteArray(Charsets.US_ASCII) + section + "e".toByteArray(Charsets.US_ASCII)
        }

        /** Info hash of a magnet link as a Sha1Hash (accepts 40-char hex and 32-char base32 forms). */
        fun magnetHash(magnet: String): Sha1Hash {
            val raw = BTIH.find(magnet)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("No info hash in magnet link")
            if (raw.length == 40) return Sha1Hash.parseHex(raw.lowercase())
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0
            var value = 0
            val out = java.io.ByteArrayOutputStream()
            for (ch in raw.uppercase()) {
                val idx = alphabet.indexOf(ch)
                require(idx >= 0) { "Bad base32 info hash" }
                value = (value shl 5) or idx
                bits += 5
                if (bits >= 8) {
                    out.write((value shr (bits - 8)) and 0xFF)
                    bits -= 8
                }
            }
            return Sha1Hash.parseHex(out.toByteArray().joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        }

        /** Info hash as written in the link (hex in lower case), used to spot duplicates. */
        fun infoHashOf(magnet: String): String? {
            val hash = BTIH.find(magnet)?.groupValues?.get(1) ?: return null
            return if (hash.length == 40) hash.lowercase() else hash.uppercase()
        }

        /** Name from the link's dn= part, or a short placeholder until the torrent info arrives. */
        fun nameOf(magnet: String): String {
            val raw = DN.find(magnet)?.groupValues?.get(1)
            val decoded = raw?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                ?.trim()
            if (!decoded.isNullOrBlank()) return decoded
            return "Torrent " + (infoHashOf(magnet)?.take(8) ?: "")
        }

        private fun withTrackers(magnet: String): String {
            val lower = magnet.lowercase()
            if (lower.contains("&tr=") || lower.contains("?tr=")) return magnet
            return magnet + DEFAULT_TRACKERS.joinToString("") { "&tr=" + URLEncoder.encode(it, "UTF-8") }
        }

        private fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}

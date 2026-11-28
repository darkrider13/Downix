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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Torrent downloads (magnet links), built on libtorrent4j.
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
    private class Entry(val id: Long, val magnet: String, val hash: Sha1Hash, @Volatile var name: String) {
        @Volatile var paused = false
        @Volatile var finishing = false
        @Volatile var configured = false   // true after the one-time "run now, not queued" setup of its handle
        @Volatile var netPaused = false    // paused by us because the network is not allowed
        @Volatile var done = 0L
        @Volatile var total = -1L
        @Volatile var finishJob: Job? = null
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var sessionRef: SessionManager? = null

    private val entries = ConcurrentHashMap<Long, Entry>()
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

    /**
     * Start or resume a torrent. If its files are already (partly) in the work folder, libtorrent checks them
     * and continues from there, also after the app was closed.
     */
    fun start(id: Long, magnet: String, knownName: String? = null) {
        val known = entries[id]
        if (known != null && !known.paused && !known.finishing) return // already running

        scope.launch {
            try {
                val sm = session()
                val clean = magnet.trim()
                val hash = AddTorrentParams.parseMagnetUri(clean).infoHash()
                val entry = entries.getOrPut(id) {
                    Entry(id, clean, hash, knownName?.takeIf { it.isNotBlank() } ?: nameOf(clean))
                }
                if (entry.finishing) return@launch
                entry.paused = false
                entry.netPaused = false
                publish(entry, DownloadState.QUEUED)

                val existing = sm.find(hash)
                if (existing != null && existing.isValid()) {
                    existing.resume()
                    entry.configured = true
                } else {
                    entry.configured = false
                    val dir = workDir(id).also { it.mkdirs() }
                    sm.download(withTrackers(clean), dir, TorrentFlags.AUTO_MANAGED)
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
    fun cancel(id: Long) {
        scope.launch {
            val e = entries.remove(id)
            e?.finishJob?.cancel()
            runCatching {
                val sm = sessionRef
                val handle = e?.let { sm?.find(it.hash) }
                if (sm != null && handle != null && handle.isValid()) sm.remove(handle, SessionHandle.DELETE_FILES)
            }
            delay(300) // let libtorrent let go of the files
            workDir(id).deleteRecursively()
            _updates.tryEmit(DownloadUpdate(id, DownloadState.CANCELED, e?.name.orEmpty(), 0L, -1L))
        }
    }

    /* ----------------------------- polling ----------------------------- */

    private fun tick() {
        val sm = sessionRef ?: return
        if (entries.isEmpty()) return
        val ok = allowed.value
        for (e in entries.values) {
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

                val info = handle.torrentFile() // null until the torrent info (metadata) has arrived
                val st = handle.status()
                if (info != null && info.numFiles() > 0) {
                    info.name()?.takeIf { it.isNotBlank() }?.let { e.name = it }
                    e.total = st.totalWanted()
                    e.done = st.totalWantedDone()
                    if (st.isFinished() || st.isSeeding()) {
                        beginFinish(sm, handle, e, info)
                        continue
                    }
                }
                publish(
                    e, DownloadState.DOWNLOADING,
                    speed = st.downloadRate().toLong(),
                    peers = st.numPeers(),
                    seeds = st.numSeeds()
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // one bad torrent must never stop the others: try again on the next tick
            }
        }
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
        val files = (0 until info.numFiles()).map { fs.filePath(it) to fs.fileSize(it) }
        e.total = files.sumOf { it.second }
        e.done = e.total

        e.finishJob = scope.launch {
            try {
                // Stop the torrent (no seeding for now); the files stay on disk for the copy below.
                runCatching { sm.remove(handle) }
                delay(500)
                publishSaving(e, 0)
                val mainUri = saveAll(e, files)
                workDir(e.id).deleteRecursively()
                entries.remove(e.id)
                _updates.tryEmit(
                    DownloadUpdate(
                        id = e.id,
                        state = DownloadState.COMPLETED,
                        fileName = e.name,
                        downloadedBytes = e.total,
                        totalBytes = e.total,
                        savedUri = mainUri
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

    /** Copies every file of the torrent to the chosen folder. @return uri of the biggest file (the main one). */
    private suspend fun saveAll(e: Entry, files: List<Pair<String, Long>>): String {
        val total = files.sumOf { it.second }
        val folder = saveFolder
        val work = workDir(e.id)
        if (SaveLocation.isOnPhoneStorage(folder) && work.usableSpace < total + MIN_FREE_SPACE) {
            throw IOException("Not enough storage space to save the files")
        }
        val progress = Progress(total)

        if (folder.isNotBlank()) {
            val writer = TreeWriter(folder)
            try {
                return copyFiles(e, files, work, progress) { rel -> writer.open(rel) }
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
                copyFiles(e, files, work, progress) { rel -> writer.open(rel) }
            } catch (t: Throwable) {
                writer.rollback()
                throw t
            }
        } else {
            val writer = LegacyFileWriter()
            try {
                copyFiles(e, files, work, progress) { rel -> writer.open(rel) }.also { writer.scan() }
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
            sessionRef = it
        }
    }

    private fun workDir(id: Long): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, "torrents/$id")
    }

    private fun publish(
        e: Entry,
        state: DownloadState,
        speed: Long = 0L,
        error: String? = null,
        peers: Int = 0,
        seeds: Int = 0
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
                seeds = seeds
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

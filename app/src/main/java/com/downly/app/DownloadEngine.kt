package com.downly.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

/* ------------------------------------------------------------------ */
/*  Public models                                                      */
/* ------------------------------------------------------------------ */

/**
 * WAITING = the download is alive but cannot run right now: no network, Wi-Fi only on mobile data,
 * or a short pause before an automatic retry. It continues by itself. [DownloadUpdate.error] holds the reason text.
 */
enum class DownloadState { QUEUED, WAITING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED }

/** One progress / state event for a download. Collect from [DownloadEngine.updates]. */
data class DownloadUpdate(
    val id: Long,
    val state: DownloadState,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long,              // -1 when the server does not tell the size
    val speedBytesPerSec: Long = 0L,
    val savedUri: String? = null,      // set when COMPLETED
    val error: String? = null          // set when FAILED
) {
    val hasKnownSize: Boolean get() = totalBytes > 0
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val percent: Int get() = (progress * 100).toInt()
    val etaSeconds: Long
        get() = if (speedBytesPerSec > 0 && totalBytes > downloadedBytes)
            (totalBytes - downloadedBytes) / speedBytesPerSec else -1L
}

/* ------------------------------------------------------------------ */
/*  Helpers: resizable parallel gate, error types                      */
/* ------------------------------------------------------------------ */

/** Like a Semaphore, but the number of permits can be changed while downloads are running. */
private class Gate(initial: Int) {
    private val limit = MutableStateFlow(initial.coerceAtLeast(1))
    private val running = MutableStateFlow(0)

    fun setLimit(value: Int) {
        limit.value = value.coerceAtLeast(1)
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    private suspend fun acquire() {
        while (true) {
            val current = running.value
            if (current < limit.value) {
                if (running.compareAndSet(current, current + 1)) return
            } else {
                combine(running, limit) { r, l -> r < l }.first { it }
            }
        }
    }
}

/** Problem that retrying cannot fix (no space, cannot write the file). */
private class FatalDownloadException(message: String) : IOException(message)

/** HTTP error from the server. 5xx / 408 / 429 are retried, other 4xx are not. */
private class HttpStatusException(val code: Int) : IOException("Server returned HTTP $code")

/* ------------------------------------------------------------------ */
/*  Engine                                                             */
/* ------------------------------------------------------------------ */

/**
 * Download engine: HTTP download with pause / resume (Range header), speed tracking,
 * parallel limit, and saving the finished file to the public Downloads folder.
 *
 * Usage:
 *   val engine = DownloadEngine(context)
 *   engine.start(id = 1, url = "https://...")
 *   engine.pause(1); engine.start(1, url)   // resume continues from the .part file
 *   engine.cancel(1)
 *   engine.updates.collect { update -> ... }
 *
 * The engine does not touch the database. The repository listens to [updates] and persists them.
 *
 * Network aware (Step 6): with no network (or Wi-Fi only on mobile data) a download goes to WAITING and continues
 * by itself when the network is back. Connection problems are retried up to 3 times (2s, 5s, 10s).
 */
class DownloadEngine(
    context: Context,
    private val network: StateFlow<NetworkState> = MutableStateFlow(NetworkState.UNMETERED),
    maxParallel: Int = 3
) {
    private enum class Stop { PAUSE, CANCEL }

    /** State of a single start() call. A new one is created on every start / resume. */
    private class Session {
        @Volatile var stop: Stop? = null
        @Volatile var call: Call? = null
    }

    private class Meta(
        @Volatile var fileName: String,
        @Volatile var total: Long,
        @Volatile var mime: String? = null
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Gate(maxParallel)
    private val wifiOnly = MutableStateFlow(false)

    /** true = a download may use the network right now. */
    private val allowed: StateFlow<Boolean> = combine(network, wifiOnly) { n, w -> isAllowed(n, w) }
        .stateIn(scope, SharingStarted.Eagerly, isAllowed(network.value, false))

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val _updates = MutableSharedFlow<DownloadUpdate>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<DownloadUpdate> = _updates.asSharedFlow()

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val sessions = ConcurrentHashMap<Long, Session>()
    private val metas = ConcurrentHashMap<Long, Meta>()

    init {
        // Network lost (or Wi-Fi only switched on while on mobile data): cut the running connections.
        // The retry loop in start() sees that the network is not allowed and waits.
        scope.launch {
            allowed.collect { ok ->
                if (!ok) sessions.values.forEach { it.call?.cancel() }
            }
        }
    }

    /* -------------------------- public API -------------------------- */

    /**
     * Start or resume a download. If a .part file for [id] exists, it continues from there.
     * [fileName] null/blank = take the name from the server / URL.
     */
    fun start(id: Long, url: String, fileName: String? = null, knownTotal: Long = -1L) {
        val prev = jobs[id]
        val prevSession = sessions[id]
        if (prev != null && prev.isActive && prevSession?.stop == null) return // already running

        val cleanUrl = normalizeUrl(url)
        val nameFromServer = fileName.isNullOrBlank()
        metas[id] = Meta(
            fileName = if (nameFromServer) guessFileName(cleanUrl) else sanitize(fileName!!),
            total = knownTotal
        )
        val session = Session()
        sessions[id] = session
        publish(id, DownloadState.QUEUED, partialBytes(id))

        val job = scope.launch {
            try {
                prev?.join() // wait until the previous run (if it was just paused) fully stops
                var attempt = 0
                while (true) {
                    awaitNetwork(id)
                    try {
                        gate.withPermit { download(id, cleanUrl, session, nameFromServer) }
                        break
                    } catch (e: Throwable) {
                        if (e is CancellationException || session.stop != null || !isRetryable(e)) throw e
                        if (!allowed.value) continue // network problem: wait for the network, no retry is used up
                        if (attempt >= RETRY_DELAYS_SEC.size) throw e
                        val wait = RETRY_DELAYS_SEC[attempt]
                        attempt++
                        publish(
                            id, DownloadState.WAITING, partialBytes(id),
                            error = "Connection problem, retrying ($attempt of ${RETRY_DELAYS_SEC.size})"
                        )
                        delay(wait * 1000L)
                    }
                }
            } catch (t: Throwable) {
                val stop = session.stop
                when {
                    stop != null -> onStopped(id, stop)
                    t is CancellationException -> onStopped(id, Stop.PAUSE)
                    else -> onFailed(id, t)
                }
                if (t is CancellationException) throw t
            } finally {
                jobs.remove(id, coroutineContext[Job])
                if (sessions[id] === session) sessions.remove(id)
            }
        }
        jobs[id] = job
    }

    /** Pause: connection is closed, the .part file is kept so [start] can resume. */
    fun pause(id: Long) {
        val session = sessions[id] ?: return
        session.stop = Stop.PAUSE
        session.call?.cancel()
        jobs[id]?.cancel()
    }

    /** Cancel: stops the download and deletes the .part file. */
    fun cancel(id: Long) {
        val job = jobs[id]
        val session = sessions[id]
        if (session != null && job != null && job.isActive) {
            session.stop = Stop.CANCEL
            session.call?.cancel()
            job.cancel()
        } else {
            partFile(id).delete()
            publish(id, DownloadState.CANCELED, 0L)
        }
    }

    fun isRunning(id: Long): Boolean = jobs[id]?.isActive == true && sessions[id]?.stop == null

    /** Bytes already stored in the .part file (what resume will continue from). */
    fun partialBytes(id: Long): Long = partFile(id).let { if (it.exists()) it.length() else 0L }

    /** Remove leftover .part file (use when a download item is deleted from the list). */
    fun deletePartial(id: Long) {
        partFile(id).delete()
    }

    /** true = downloads run only on Wi-Fi / unmetered networks. */
    fun setWifiOnly(enabled: Boolean) {
        wifiOnly.value = enabled
    }

    /** Max downloads running at the same time (at least 1). Can be changed any time. */
    fun setMaxParallel(count: Int) {
        gate.setLimit(count)
    }

    fun shutdown() {
        scope.cancel()
    }

    /* ----------------------- network / retry ----------------------- */

    private suspend fun awaitNetwork(id: Long) {
        if (allowed.value) return
        val reason = if (network.value == NetworkState.METERED && wifiOnly.value) "Waiting for Wi-Fi" else "Waiting for network"
        publish(id, DownloadState.WAITING, partialBytes(id), error = reason)
        allowed.first { it }
        publish(id, DownloadState.QUEUED, partialBytes(id))
    }

    private fun isRetryable(t: Throwable): Boolean = when (t) {
        is FatalDownloadException -> false
        is HttpStatusException -> t.code >= 500 || t.code == 429 || t.code == 408
        is IllegalArgumentException -> false
        is IOException -> true
        else -> false
    }

    /* -------------------------- core work -------------------------- */

    private suspend fun download(id: Long, url: String, session: Session, nameFromServer: Boolean) {
        val part = partFile(id)
        publish(id, DownloadState.DOWNLOADING, partialBytes(id))

        var restarted = false
        while (true) {
            val existing = if (part.exists()) part.length() else 0L
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "identity") // keep Content-Length / ranges reliable
            if (existing > 0) builder.header("Range", "bytes=$existing-")

            val call = client.newCall(builder.build())
            session.call = call
            if (session.stop != null) throw CancellationException("stopped")

            val done = call.execute().use { resp ->
                transfer(id, session, resp, part, existing, nameFromServer)
            }
            if (done) break
            if (restarted) throw IOException("Server rejected the resume request")
            restarted = true // part file was invalid, loop again from 0
        }

        val meta = metas[id]!!
        val size = part.length()
        val uri = try {
            saveToPublicDownloads(part, meta.fileName, meta.mime)
        } catch (e: Exception) {
            throw FatalDownloadException(e.message ?: "Could not save the file to Downloads")
        }
        part.delete()
        publish(id, DownloadState.COMPLETED, size, 0L, savedUri = uri)
    }

    /** @return true = finished, false = part file was invalid and has been deleted (restart). */
    private suspend fun transfer(
        id: Long,
        session: Session,
        resp: Response,
        part: File,
        existing: Long,
        nameFromServer: Boolean
    ): Boolean {
        if (resp.code == 416 && existing > 0) {
            part.delete()
            return false
        }
        if (!resp.isSuccessful) throw HttpStatusException(resp.code)
        val body = resp.body ?: throw IOException("Empty response from server")

        val resuming = resp.code == 206 && existing > 0
        val startAt = if (resuming) existing else 0L
        val length = body.contentLength()
        val total = when {
            resp.code == 206 ->
                parseContentRangeTotal(resp.header("Content-Range"))
                    ?: if (length > 0) startAt + length else -1L
            length > 0 -> length
            else -> -1L
        }

        val meta = metas.getOrPut(id) { Meta(guessFileName(resp.request.url.toString()), total) }
        meta.total = total
        meta.mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
        if (nameFromServer) meta.fileName = fileNameFromHeaders(resp, meta.mime)

        if (total > 0) {
            val free = part.parentFile?.usableSpace ?: Long.MAX_VALUE
            if (free < (total - startAt) + MIN_FREE_SPACE) throw FatalDownloadException("Not enough storage space")
        }

        var downloaded = startAt
        FileOutputStream(part, resuming).use { out ->
            val input = body.byteStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var lastTick = SystemClock.elapsedRealtime()
            var lastBytes = downloaded
            var speed = 0L

            publish(id, DownloadState.DOWNLOADING, downloaded, 0L)

            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n == -1) break
                out.write(buffer, 0, n)
                downloaded += n

                val now = SystemClock.elapsedRealtime()
                val dt = now - lastTick
                if (dt >= TICK_MS) {
                    val instant = (downloaded - lastBytes) * 1000 / dt
                    speed = if (speed == 0L) instant else (speed * 6 + instant * 4) / 10
                    lastTick = now
                    lastBytes = downloaded
                    publish(id, DownloadState.DOWNLOADING, downloaded, speed)
                }
            }
        }

        if (total > 0 && downloaded < total) {
            throw IOException("Connection lost")
        }
        return true
    }

    private fun onStopped(id: Long, stop: Stop) {
        if (stop == Stop.CANCEL) {
            partFile(id).delete()
            publish(id, DownloadState.CANCELED, 0L)
        } else {
            publish(id, DownloadState.PAUSED, partialBytes(id))
        }
    }

    private fun onFailed(id: Long, t: Throwable) {
        val message = when (t) {
            is UnknownHostException -> "No internet connection or server not found"
            is SocketTimeoutException -> "Connection timed out"
            is SSLException -> "Secure connection failed"
            is IllegalArgumentException -> "Invalid link"
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Download failed"
        }
        publish(id, DownloadState.FAILED, partialBytes(id), 0L, error = message)
    }

    private fun publish(
        id: Long,
        state: DownloadState,
        downloaded: Long,
        speed: Long = 0L,
        savedUri: String? = null,
        error: String? = null
    ) {
        val meta = metas[id]
        _updates.tryEmit(
            DownloadUpdate(
                id = id,
                state = state,
                fileName = meta?.fileName.orEmpty(),
                downloadedBytes = downloaded,
                totalBytes = meta?.total ?: -1L,
                speedBytesPerSec = speed,
                savedUri = savedUri,
                error = error
            )
        )
    }

    /* -------------------------- files -------------------------- */

    private fun partFile(id: Long): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val dir = File(base, "partial")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.part")
    }

    /**
     * Copies the finished file to the public Downloads folder.
     * Android 10+: MediaStore (no permission needed).
     * Android 9 and below: direct write (needs WRITE_EXTERNAL_STORAGE, falls back to the app folder).
     */
    private fun saveToPublicDownloads(part: File, name: String, mime: String?): String {
        val type = mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: mimeFromName(name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, type)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the file in Downloads")
            try {
                val os = resolver.openOutputStream(uri) ?: throw IOException("Could not open the file for writing")
                os.use { out -> part.inputStream().use { it.copyTo(out, BUFFER_SIZE) } }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri.toString()
        }

        var dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if ((!dir.exists() && !dir.mkdirs()) || !dir.canWrite()) {
            dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
            if (!dir.exists()) dir.mkdirs()
        }
        val target = uniqueFile(dir, name)
        part.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(type), null)
        return Uri.fromFile(target).toString()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (file.exists()) {
            file = File(dir, "$base ($i)$ext")
            i++
        }
        return file
    }

    /* -------------------------- helpers -------------------------- */

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Downly/1.0"
        private const val BUFFER_SIZE = 64 * 1024
        private const val TICK_MS = 400L
        private const val MIN_FREE_SPACE = 5L * 1024 * 1024
        private val RETRY_DELAYS_SEC = longArrayOf(2, 5, 10)

        private fun isAllowed(state: NetworkState, wifiOnly: Boolean): Boolean =
            state != NetworkState.NONE && (!wifiOnly || state == NetworkState.UNMETERED)

        /** Trim and add https:// when the user pasted a link without a scheme. */
        fun normalizeUrl(input: String): String {
            val t = input.trim()
            return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
        }

        fun isValidUrl(input: String): Boolean {
            if (input.isBlank()) return false
            val url = normalizeUrl(input).toHttpUrlOrNull() ?: return false
            return url.host.contains('.') || url.host == "localhost"
        }

        private fun parseContentRangeTotal(header: String?): Long? =
            header?.substringAfterLast('/')?.trim()?.toLongOrNull()

        private fun sanitize(name: String): String {
            val cleaned = name
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
                .trim()
                .trimStart('.')
            return cleaned.ifBlank { "download_${System.currentTimeMillis()}" }
        }

        private fun guessFileName(url: String): String {
            val segment = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
            return sanitize(segment?.let { Uri.decode(it) } ?: "")
        }

        private fun fileNameFromHeaders(resp: Response, mime: String?): String {
            val cd = resp.header("Content-Disposition")
            val fromStar = cd?.let {
                Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.get(1)
            }
            val fromPlain = cd?.let {
                Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                    .find(it)?.groupValues?.get(1)
            }
            val raw = fromStar?.let { Uri.decode(it.trim().trim('"')) }
                ?: fromPlain?.trim()
                ?: Uri.decode(resp.request.url.pathSegments.lastOrNull().orEmpty())
            var name = sanitize(raw)

            if (!name.contains('.') && !mime.isNullOrBlank()) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                if (!ext.isNullOrBlank()) name = "$name.$ext"
            }
            return name
        }

        private fun mimeFromName(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Formatting helpers (used by UI and notifications)                  */
/* ------------------------------------------------------------------ */

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = -1
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

fun formatSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0) "-- /s" else "${formatBytes(bytesPerSec)}/s"

fun formatEta(seconds: Long): String = when {
    seconds < 0 -> "--"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

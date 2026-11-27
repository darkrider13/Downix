package com.downly.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One published release: what the update dialog shows and what gets downloaded. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long
)

sealed interface UpdateState {
    /** Nothing checked yet in this session. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val downloadedBytes: Long, val totalBytes: Long) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val hasKnownSize: Boolean get() = totalBytes > 0
    }
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    /** [info] is set when the failure happened after an update was found (download / verify), so "Try again" can retry it. */
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

enum class InstallStart { STARTED, NEEDS_PERMISSION, FAILED }

/** An error whose text is safe to show to the user as it is. */
private class UpdateException(message: String) : IOException(message)

/**
 * In-app updater. New builds are published as GitHub Releases (see .github/workflows/release.yml):
 * the tag is v<versionName> and the signed APK is attached to the release.
 *
 *  - check(): asks GitHub for the latest release and compares versions
 *  - startDownload(): downloads the APK into cache/updates and verifies it
 *    (same package, higher version, same signing key) before it is offered for install
 *  - install(): hands the file to the system installer. Because every build is signed with the
 *    same key, Android installs it as an update and keeps all data and settings.
 *
 * Singleton: UpdateManager.get(context). The app checks once, 2 seconds after it opens.
 */
class UpdateManager private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkLock = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val updatesDir: File get() = File(app.cacheDir, "updates")

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** False when the build has no GitHub repository configured (local builds). */
    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /* ------------------------------ check ------------------------------ */

    /**
     * [manual] = the user tapped "Check for updates": show progress and report every error.
     * The automatic check (2 s after the app opens) always asks the server and stays silent when it fails.
     */
    suspend fun check(manual: Boolean) {
        if (!isConfigured) {
            if (manual) _state.value = UpdateState.Error("Updates are not set up in this build")
            return
        }

        checkLock.withLock {
            if (_state.value is UpdateState.Downloading) return
            if (manual) _state.value = UpdateState.Checking
            try {
                val json = withContext(Dispatchers.IO) { fetchLatest() }

                val info = json?.let { parseRelease(it) }
                if (json != null && info != null && isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) {
                    prefs.edit().putString(KEY_RELEASE, json).apply()
                    _state.value = readyOrAvailable(info)
                } else {
                    prefs.edit().remove(KEY_RELEASE).apply()
                    cleanupFiles()
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (manual) _state.value = UpdateState.Error(friendly(e))
            }
        }
    }

    /**
     * Latest release as JSON, or null when the repository has no release yet.
     * The GitHub API allows only 60 requests an hour per network, so when it refuses (403 / 429)
     * the check falls back to the normal github.com release pages, which have no such limit.
     */
    private fun fetchLatest(): String? {
        return try {
            fetchLatestApi()
        } catch (e: RateLimited) {
            fetchLatestWeb()
        }
    }

    private class RateLimited : IOException("rate limited")

    private fun fetchLatestWeb(): String? {
        val repo = BuildConfig.UPDATE_REPO
        val noRedirect = client.newBuilder().followRedirects(false).build()

        // 1) /releases/latest redirects to /releases/tag/<tag>
        val tag = noRedirect.newCall(
            Request.Builder()
                .url("https://github.com/$repo/releases/latest")
                .header("User-Agent", "Downly/${BuildConfig.VERSION_NAME}")
                .build()
        ).execute().use { response ->
            when {
                response.code == 404 -> return null
                response.isRedirect -> {
                    val location = response.header("Location").orEmpty()
                    if (!location.contains("/tag/")) return null
                    java.net.URLDecoder.decode(location.substringAfterLast("/tag/").substringBefore('?'), "UTF-8")
                }
                else -> throw UpdateException("Update server problem (${response.code})")
            }
        }

        // 2) the release's file list page holds the link to the APK
        val html = client.newCall(
            Request.Builder()
                .url("https://github.com/$repo/releases/expanded_assets/$tag")
                .header("User-Agent", "Downly/${BuildConfig.VERSION_NAME}")
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Update server problem (${response.code})")
            response.body?.string().orEmpty()
        }
        val path = Regex("href=\"(/[^\"]+/releases/download/[^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null

        return JSONObject()
            .put("tag_name", tag)
            .put("body", "")
            .put("html_url", "https://github.com/$repo/releases/tag/$tag")
            .put(
                "assets",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("name", path.substringAfterLast('/'))
                        .put("browser_download_url", "https://github.com$path")
                        .put("size", 0)
                )
            )
            .toString()
    }

    private fun fetchLatestApi(): String? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Downly/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.code == 403 || response.code == 429 -> throw RateLimited()
                !response.isSuccessful -> throw UpdateException("Update server problem (${response.code})")
                else -> response.body?.string() ?: throw UpdateException("Update server sent an empty answer")
            }
        }
    }

    private fun parseRelease(json: String): UpdateInfo? = try {
        val release = JSONObject(json)
        val assets = release.optJSONArray("assets")
        var apkUrl = ""
        var apkSize = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }
        val version = release.optString("tag_name").trim().trimStart('v', 'V')
        if (version.isEmpty() || apkUrl.isEmpty()) null
        else UpdateInfo(
            versionName = version,
            notes = cleanNotes(release.optString("body")),
            pageUrl = release.optString("html_url"),
            apkUrl = apkUrl,
            apkSize = apkSize
        )
    } catch (e: Exception) {
        null
    }

    /** Release notes are Markdown: keep plain readable lines, drop headings marks and the auto "Full Changelog" line. */
    private fun cleanNotes(raw: String): String =
        raw.lines()
            .map { it.trim().trimStart('#', '*', '-', ' ') }
            .filter { it.isNotEmpty() && !it.startsWith("Full Changelog", ignoreCase = true) }
            .joinToString("\n")
            .take(600)

    /* ----------------------------- download ----------------------------- */

    /** Downloads the update found by [check]. Runs in the manager's own scope, so it survives screen rotation. */
    fun startDownload() {
        val current = _state.value
        val info = when (current) {
            is UpdateState.Available -> current.info
            is UpdateState.Error -> current.info ?: return
            else -> return
        }
        if (!_state.compareAndSet(current, UpdateState.Downloading(info, 0L, info.apkSize))) return
        scope.launch { downloadApk(info) }
    }

    private fun downloadApk(info: UpdateInfo) {
        val target = targetFile(info)
        val part = File(target.parentFile, target.name + ".part")
        try {
            updatesDir.mkdirs()
            updatesDir.listFiles()?.forEach { it.delete() }

            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "Downly/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw UpdateException("Download failed (${response.code})")
                val body = response.body ?: throw UpdateException("Download failed")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                var done = 0L
                var lastEmit = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmit >= 250) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(info, done, total)
                            }
                        }
                    }
                }
                if (info.apkSize > 0 && done != info.apkSize) {
                    throw UpdateException("The download was incomplete. Please try again.")
                }
            }

            if (!part.renameTo(target)) throw UpdateException("Could not save the update file")
            val problem = verify(target)
            if (problem != null) {
                target.delete()
                throw UpdateException(problem)
            }
            _state.value = UpdateState.ReadyToInstall(info, target)
        } catch (e: Exception) {
            part.delete()
            _state.value = UpdateState.Error(friendly(e), info)
        }
    }

    /** Returns null when the APK is a genuine, newer Downly build, otherwise the reason it must not be installed. */
    private fun verify(file: File): String? {
        val pm = app.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, signatureFlags)
            ?: return "The downloaded file is not a valid app"
        if (archive.packageName != app.packageName) return "The downloaded file is not Downly"

        val installed = try {
            pm.getPackageInfo(app.packageName, signatureFlags)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return "This version is already installed"
        }
        // If a signer list is unavailable we do not block: Android's installer checks it again anyway.
        val newSigners = signersOf(archive)
        val oldSigners = signersOf(installed)
        if (newSigners.isNotEmpty() && oldSigners.isNotEmpty() && newSigners != oldSigners) {
            return "This update is signed with a different key, so Android cannot install it over your current Downly"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun signersOf(info: PackageInfo): Set<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toSet() ?: emptySet()
        } else {
            info.signatures?.toSet() ?: emptySet()
        }

    @Suppress("DEPRECATION")
    private val signatureFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    /* ------------------------------ install ------------------------------ */

    /**
     * Opens the system installer for the downloaded update. If Downly is not yet allowed to install apps,
     * opens that permission screen instead (NEEDS_PERMISSION): after allowing it the user taps Install again.
     */
    fun install(context: Context): InstallStart {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return InstallStart.FAILED
        if (!ready.file.isFile) {
            _state.value = UpdateState.Available(ready.info)
            return InstallStart.FAILED
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                InstallStart.NEEDS_PERMISSION
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                InstallStart.STARTED
            }
        } catch (e: Exception) {
            InstallStart.FAILED
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    /** The last release found (saved on disk) is shown again after the app restarts, without a network call. */
    private fun restore(): UpdateState {
        val json = prefs.getString(KEY_RELEASE, null) ?: return UpdateState.Idle
        val info = parseRelease(json) ?: return UpdateState.Idle
        return if (isNewerVersion(info.versionName, BuildConfig.VERSION_NAME)) readyOrAvailable(info) else UpdateState.Idle
    }

    private fun readyOrAvailable(info: UpdateInfo): UpdateState {
        val file = targetFile(info)
        return if (file.isFile && (info.apkSize <= 0 || file.length() == info.apkSize)) {
            UpdateState.ReadyToInstall(info, file)
        } else {
            UpdateState.Available(info)
        }
    }

    private fun targetFile(info: UpdateInfo) = File(updatesDir, "Downly-${info.versionName}.apk")

    private fun cleanupFiles() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UpdateException -> e.message ?: "Update failed"
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "No internet connection"
        is IOException -> "Could not reach the update server"
        else -> "Update failed"
    }

    companion object {
        private const val FILE = "downly_update"
        private const val KEY_RELEASE = "latest_release"

        @Volatile private var instance: UpdateManager? = null

        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }

        /** "1.0.12" > "1.0.9" > "1.0". Leading "v" and suffixes like "-beta" are ignored. */
        fun isNewerVersion(candidate: String, installed: String): Boolean {
            val a = versionParts(candidate)
            val b = versionParts(installed)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun versionParts(version: String): List<Int> =
            version.trim().trimStart('v', 'V')
                .split('.', '-', '+')
                .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    }
}

package com.downix.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** User options. Defaults are used on first start. */
data class AppSettings(
    val maxParallel: Int = DEFAULT_PARALLEL,
    val wifiOnly: Boolean = false,
    val notifications: Boolean = true,
    val downloadFolder: String = "", // picked folder (tree URI), blank = Downloads
    // ---- torrents ----
    val maxTorrents: Int = DEFAULT_TORRENTS,      // torrents that download at the same time, the rest wait in a queue
    val torrentDownKb: Int = 0,                   // download speed limit in KB/s, 0 = no limit
    val torrentUpKb: Int = 0,                     // upload speed limit in KB/s, 0 = no limit
    val torrentDht: Boolean = true,               // DHT: finds peers without a tracker
    val torrentSeeding: Boolean = false,          // keep sharing a torrent after it finished
    val seedRatioX10: Int = 20,                   // stop seeding at this share ratio (x10, so 20 = 2.0)
    val seedHours: Int = 4                        // ... or after this many hours
) {
    companion object {
        const val MIN_PARALLEL = 1
        const val MAX_PARALLEL = 5
        const val DEFAULT_PARALLEL = 3
        const val MIN_TORRENTS = 1
        const val MAX_TORRENTS = 5
        const val DEFAULT_TORRENTS = 3
    }
}

/** The fixed choices of the torrent settings (the Settings screen steps through these lists). */
object TorrentLimits {
    val SPEED_KB = listOf(0, 50, 100, 250, 500, 1000, 2000, 5000)
    val RATIOS_X10 = listOf(5, 10, 15, 20, 30, 50)
    val HOURS = listOf(1, 2, 4, 8, 12, 24)

    fun speedLabel(kb: Int): String = when {
        kb <= 0 -> "Off"
        kb >= 1000 -> "${kb / 1000} MB"
        else -> "$kb KB"
    }

    fun ratioLabel(x10: Int): String = "${x10 / 10}.${x10 % 10}"

    fun hoursLabel(hours: Int): String = "$hours h"

    /** Position of [value] in [list], or the closest smaller entry when it is not in the list. */
    fun indexOf(list: List<Int>, value: Int): Int {
        val exact = list.indexOf(value)
        if (exact >= 0) return exact
        return list.indexOfLast { it <= value }.coerceAtLeast(0)
    }
}

/**
 * The names the user sees in the Settings screen.
 */
object SettingNames {
    const val MAX_PARALLEL = "Simultaneous downloads"
    const val WIFI_ONLY = "Wi-Fi only"
    const val NOTIFICATIONS = "Notifications"
    const val DOWNLOAD_FOLDER = "Download folder"
    const val MAX_TORRENTS = "Active torrents"
    const val TORRENT_DOWN = "Download speed limit"
    const val TORRENT_UP = "Upload speed limit"
    const val TORRENT_DHT = "DHT"
    const val TORRENT_SEEDING = "Seed after download"
    const val SEED_RATIO = "Stop seeding at ratio"
    const val SEED_HOURS = "Stop seeding after"
}

/**
 * Saves [AppSettings] in SharedPreferences and exposes them as a [StateFlow].
 * Singleton: SettingsRepository.get(context). DownloadRepository listens to it and applies
 * maxParallel / wifiOnly / torrent options to the engines; DownloadNotifier checks `notifications`.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Current value, for code that is not a coroutine. */
    val current: AppSettings get() = _settings.value

    fun setMaxParallel(count: Int) {
        val value = count.coerceIn(AppSettings.MIN_PARALLEL, AppSettings.MAX_PARALLEL)
        prefs.edit().putInt(KEY_PARALLEL, value).apply()
        _settings.update { it.copy(maxParallel = value) }
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _settings.update { it.copy(wifiOnly = enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _settings.update { it.copy(notifications = enabled) }
    }

    /** [folderUri] blank = back to the Downloads folder. */
    fun setDownloadFolder(folderUri: String) {
        prefs.edit().putString(KEY_FOLDER, folderUri).apply()
        _settings.update { it.copy(downloadFolder = folderUri) }
    }

    /* ------------------------------ torrents ------------------------------ */

    fun setMaxTorrents(count: Int) {
        val value = count.coerceIn(AppSettings.MIN_TORRENTS, AppSettings.MAX_TORRENTS)
        prefs.edit().putInt(KEY_TORRENTS, value).apply()
        _settings.update { it.copy(maxTorrents = value) }
    }

    fun setTorrentDownKb(kb: Int) {
        val value = kb.coerceAtLeast(0)
        prefs.edit().putInt(KEY_TORRENT_DOWN, value).apply()
        _settings.update { it.copy(torrentDownKb = value) }
    }

    fun setTorrentUpKb(kb: Int) {
        val value = kb.coerceAtLeast(0)
        prefs.edit().putInt(KEY_TORRENT_UP, value).apply()
        _settings.update { it.copy(torrentUpKb = value) }
    }

    fun setTorrentDht(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TORRENT_DHT, enabled).apply()
        _settings.update { it.copy(torrentDht = enabled) }
    }

    fun setTorrentSeeding(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TORRENT_SEEDING, enabled).apply()
        _settings.update { it.copy(torrentSeeding = enabled) }
    }

    fun setSeedRatioX10(value: Int) {
        val v = value.coerceAtLeast(1)
        prefs.edit().putInt(KEY_SEED_RATIO, v).apply()
        _settings.update { it.copy(seedRatioX10 = v) }
    }

    fun setSeedHours(hours: Int) {
        val v = hours.coerceAtLeast(1)
        prefs.edit().putInt(KEY_SEED_HOURS, v).apply()
        _settings.update { it.copy(seedHours = v) }
    }

    private fun read() = AppSettings(
        maxParallel = prefs.getInt(KEY_PARALLEL, AppSettings.DEFAULT_PARALLEL)
            .coerceIn(AppSettings.MIN_PARALLEL, AppSettings.MAX_PARALLEL),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        downloadFolder = prefs.getString(KEY_FOLDER, "").orEmpty(),
        maxTorrents = prefs.getInt(KEY_TORRENTS, AppSettings.DEFAULT_TORRENTS)
            .coerceIn(AppSettings.MIN_TORRENTS, AppSettings.MAX_TORRENTS),
        torrentDownKb = prefs.getInt(KEY_TORRENT_DOWN, 0).coerceAtLeast(0),
        torrentUpKb = prefs.getInt(KEY_TORRENT_UP, 0).coerceAtLeast(0),
        torrentDht = prefs.getBoolean(KEY_TORRENT_DHT, true),
        torrentSeeding = prefs.getBoolean(KEY_TORRENT_SEEDING, false),
        seedRatioX10 = prefs.getInt(KEY_SEED_RATIO, 20).coerceAtLeast(1),
        seedHours = prefs.getInt(KEY_SEED_HOURS, 4).coerceAtLeast(1)
    )

    companion object {
        private const val FILE = "downix_settings"
        private const val KEY_PARALLEL = "max_parallel"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_FOLDER = "download_folder"
        private const val KEY_TORRENTS = "max_torrents"
        private const val KEY_TORRENT_DOWN = "torrent_down_kb"
        private const val KEY_TORRENT_UP = "torrent_up_kb"
        private const val KEY_TORRENT_DHT = "torrent_dht"
        private const val KEY_TORRENT_SEEDING = "torrent_seeding"
        private const val KEY_SEED_RATIO = "seed_ratio_x10"
        private const val KEY_SEED_HOURS = "seed_hours"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

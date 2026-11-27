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
    val downloadFolder: String = "" // picked folder (tree URI), blank = Downloads
) {
    companion object {
        const val MIN_PARALLEL = 1
        const val MAX_PARALLEL = 5
        const val DEFAULT_PARALLEL = 3
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
}

/**
 * Saves [AppSettings] in SharedPreferences and exposes them as a [StateFlow].
 * Singleton: SettingsRepository.get(context). DownloadRepository listens to it and applies
 * maxParallel / wifiOnly to the engine; DownloadNotifier checks `notifications`.
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

    private fun read() = AppSettings(
        maxParallel = prefs.getInt(KEY_PARALLEL, AppSettings.DEFAULT_PARALLEL)
            .coerceIn(AppSettings.MIN_PARALLEL, AppSettings.MAX_PARALLEL),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
        notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        downloadFolder = prefs.getString(KEY_FOLDER, "").orEmpty()
    )

    companion object {
        private const val FILE = "downix_settings"
        private const val KEY_PARALLEL = "max_parallel"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_FOLDER = "download_folder"

        @Volatile private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

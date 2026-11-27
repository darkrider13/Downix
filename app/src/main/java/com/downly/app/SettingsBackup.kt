package com.downly.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Settings backup: export to / import from a small JSON file.
 *
 * The file holds only settings (never the download list). Each setting is written with
 *  - "key":   stable id used by the importer (never changes between versions)
 *  - "name":  the name the user sees in Settings, so the file is readable
 *  - "value": the value the user chose
 *
 * Import is tolerant: unknown keys and wrong value types are skipped, numbers are range-checked,
 * settings that are missing from the file keep their current value.
 */
object SettingsBackup {

    private const val TYPE = "downly-settings-backup"
    private const val FORMAT_VERSION = 1

    private const val KEY_PARALLEL = "max_parallel"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_THEME = "theme_mode"

    sealed interface ImportResult {
        /** [names] are the user-visible names of the settings that were restored. */
        data class Success(val names: List<String>, val skipped: Int) : ImportResult
        data class Failure(val message: String) : ImportResult
    }

    fun suggestedFileName(): String =
        "downly-settings-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    fun export(settings: AppSettings, appVersion: String): String {
        val items = JSONArray()
            .put(item(KEY_PARALLEL, SettingNames.MAX_PARALLEL, settings.maxParallel))
            .put(item(KEY_WIFI_ONLY, SettingNames.WIFI_ONLY, settings.wifiOnly))
            .put(item(KEY_NOTIFICATIONS, SettingNames.NOTIFICATIONS, settings.notifications))
            .put(item(KEY_AUTO_UPDATE, SettingNames.AUTO_UPDATE_CHECK, settings.autoUpdateCheck))
            .put(item(KEY_THEME, SettingNames.THEME, settings.themeMode.key))

        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        return JSONObject()
            .put("type", TYPE)
            .put("formatVersion", FORMAT_VERSION)
            .put("app", "Downly")
            .put("appVersion", appVersion)
            .put("exportedAt", stamp)
            .put("settings", items)
            .toString(2)
    }

    fun import(text: String, current: AppSettings): Pair<ImportResult, AppSettings> {
        val notDownly = ImportResult.Failure("This is not a Downly settings file")
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return notDownly to current
        }
        if (root.optString("type") != TYPE) return notDownly to current
        if (root.optInt("formatVersion", 0) !in 1..FORMAT_VERSION) {
            return ImportResult.Failure("This file was made by a newer Downly. Update the app first.") to current
        }
        val items = root.optJSONArray("settings")
            ?: return ImportResult.Failure("No settings found in this file") to current

        var result = current
        val restored = mutableListOf<String>()
        var skipped = 0

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i)
            if (item == null) {
                skipped++
                continue
            }
            val value = item.opt("value")
            when (item.optString("key")) {
                KEY_PARALLEL -> if (value is Number) {
                    result = result.copy(
                        maxParallel = value.toInt().coerceIn(AppSettings.MIN_PARALLEL, AppSettings.MAX_PARALLEL)
                    )
                    restored += SettingNames.MAX_PARALLEL
                } else skipped++

                KEY_WIFI_ONLY -> if (value is Boolean) {
                    result = result.copy(wifiOnly = value)
                    restored += SettingNames.WIFI_ONLY
                } else skipped++

                KEY_NOTIFICATIONS -> if (value is Boolean) {
                    result = result.copy(notifications = value)
                    restored += SettingNames.NOTIFICATIONS
                } else skipped++

                KEY_AUTO_UPDATE -> if (value is Boolean) {
                    result = result.copy(autoUpdateCheck = value)
                    restored += SettingNames.AUTO_UPDATE_CHECK
                } else skipped++

                KEY_THEME -> if (value is String && ThemeMode.values().any { it.key == value }) {
                    result = result.copy(themeMode = ThemeMode.fromKey(value))
                    restored += SettingNames.THEME
                } else skipped++

                else -> skipped++
            }
        }

        if (restored.isEmpty()) {
            return ImportResult.Failure("No usable settings found in this file") to current
        }
        return ImportResult.Success(restored, skipped) to result
    }

    private fun item(key: String, name: String, value: Any): JSONObject =
        JSONObject().put("key", key).put("name", name).put("value", value)
}

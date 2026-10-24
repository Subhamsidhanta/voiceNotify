package com.example

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.TileService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable entry in the Voice Announcement Activity Log.
 * Strictly privacy-safe: stores only timestamp, appName, senderName, and spokenPhrase.
 */
data class VoiceLogEntry(
    val id: String,
    val timestamp: Long,
    val formattedTime: String,
    val appName: String,
    val senderName: String,
    val spokenPhrase: String
)

/**
 * SharedPreferences helper managing VoiceNotify's persistent state:
 * - Master On/Off Switch
 * - Headphone-Only Mode Toggle
 * - Speech Rate & Pitch
 * - Selected App Package Name Set
 * - Voice Announcement Activity Log (date-wise, searchable by app, individual & bulk deletable)
 */
class PrefManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Master On/Off Switch state (Default: true).
     */
    var isMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()
            requestQuickTileUpdate()
        }

    /**
     * Headphone-Only Mode state (Default: false).
     * When true, announcements only play if wired or Bluetooth audio devices are connected.
     */
    var isHeadphoneOnly: Boolean
        get() = prefs.getBoolean(KEY_HEADPHONE_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HEADPHONE_ONLY, value).apply()
        }

    /**
     * Text-to-Speech speech rate multiplier (Default: 1.0f, range 0.5f..2.0f).
     */
    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEECH_RATE, value.coerceIn(0.5f, 2.0f)).apply()
        }

    /**
     * Text-to-Speech voice pitch multiplier (Default: 1.0f, range 0.5f..2.0f).
     */
    var speechPitch: Float
        get() = prefs.getFloat(KEY_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 2.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEECH_PITCH, value.coerceIn(0.5f, 2.0f)).apply()
        }

    /**
     * Returns a defensive immutable copy of the user's selected package names.
     */
    fun getSelectedApps(): Set<String> {
        val storedSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        return HashSet(storedSet)
    }

    /**
     * Saves a new set of selected package names.
     * Always creates a defensive HashSet copy so SharedPreferences detects mutations reliably.
     */
    fun setSelectedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_APPS, HashSet(packages)).apply()
    }

    /**
     * Adds or removes a single package name from the selected set.
     */
    fun toggleAppSelection(packageName: String, isSelected: Boolean): Set<String> {
        val updated = getSelectedApps().toMutableSet()
        if (isSelected) {
            updated.add(packageName)
        } else {
            updated.remove(packageName)
        }
        setSelectedApps(updated)
        return updated
    }

    /**
     * Checks whether a given package is currently enabled for voice announcements.
     */
    fun isAppSelected(packageName: String): Boolean {
        return getSelectedApps().contains(packageName)
    }

    /**
     * Stores the last announced phrase (App Name + Sender Name only) for quick status visibility.
     */
    var lastAnnouncementSummary: String
        get() = prefs.getString(KEY_LAST_ANNOUNCEMENT, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_ANNOUNCEMENT, value).apply()
        }

    /**
     * Appends a privacy-safe voice announcement entry to the persistent Voice Log (newest first, max 200).
     */
    fun appendVoiceLog(
        appName: String,
        senderName: String,
        spokenPhrase: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val formattedTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            .format(Date(timestamp))
        val newEntry = VoiceLogEntry(
            id = "${timestamp}_${spokenPhrase.hashCode()}",
            timestamp = timestamp,
            formattedTime = formattedTime,
            appName = appName,
            senderName = senderName,
            spokenPhrase = spokenPhrase
        )

        val currentLogs = getVoiceLogs().toMutableList()
        currentLogs.add(0, newEntry)
        val trimmed = currentLogs.take(MAX_VOICE_LOG_ENTRIES)

        saveVoiceLogList(trimmed)

        val shortTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        prefs.edit()
            .putString(KEY_LAST_ANNOUNCEMENT, "[$shortTime] \"$spokenPhrase\"")
            .apply()
    }

    /**
     * Deletes a single voice log entry by its unique ID.
     */
    fun deleteVoiceLog(logId: String): List<VoiceLogEntry> {
        val updated = getVoiceLogs().filterNot { it.id == logId }
        saveVoiceLogList(updated)
        if (updated.isEmpty()) {
            prefs.edit().putString(KEY_LAST_ANNOUNCEMENT, "").apply()
        }
        return updated
    }

    /**
     * Deletes all voice log entries belonging to a specific app name.
     */
    fun deleteVoiceLogsByApp(appName: String): List<VoiceLogEntry> {
        val updated = getVoiceLogs().filterNot { it.appName.equals(appName, ignoreCase = true) }
        saveVoiceLogList(updated)
        if (updated.isEmpty()) {
            prefs.edit().putString(KEY_LAST_ANNOUNCEMENT, "").apply()
        }
        return updated
    }

    private fun saveVoiceLogList(entries: List<VoiceLogEntry>) {
        val jsonArray = JSONArray()
        for (item in entries) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("formattedTime", item.formattedTime)
                put("appName", item.appName)
                put("senderName", item.senderName)
                put("spokenPhrase", item.spokenPhrase)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_VOICE_LOGS, jsonArray.toString()).apply()
    }

    /**
     * Retrieves the persisted list of Voice Log entries (newest first).
     */
    fun getVoiceLogs(): List<VoiceLogEntry> {
        val rawJson = prefs.getString(KEY_VOICE_LOGS, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = ArrayList<VoiceLogEntry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VoiceLogEntry(
                        id = obj.optString("id", "$i"),
                        timestamp = obj.optLong("timestamp", 0L),
                        formattedTime = obj.optString("formattedTime", ""),
                        appName = obj.optString("appName", ""),
                        senderName = obj.optString("senderName", ""),
                        spokenPhrase = obj.optString("spokenPhrase", "")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Clears all stored Voice Log entries and resets the last announcement summary.
     */
    fun clearVoiceLogs() {
        prefs.edit()
            .remove(KEY_VOICE_LOGS)
            .putString(KEY_LAST_ANNOUNCEMENT, "")
            .apply()
    }

    /**
     * Registers a SharedPreferences listener to keep UI and services in sync when the
     * Quick Settings Tile, NotificationReaderService, or MainActivity mutates preferences.
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Unregisters a previously registered SharedPreferences listener.
     */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Requests Android's Quick Settings shade to refresh QuickToggleTileService.
     */
    private fun requestQuickTileUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    appContext,
                    ComponentName(appContext, QuickToggleTileService::class.java)
                )
            } catch (_: Exception) {
                // Ignore if tile is not added or in a unit test environment
            }
        }
    }

    companion object {
        const val PREFS_NAME = "voice_notify_prefs"
        const val KEY_MASTER_ENABLED = "key_master_enabled"
        const val KEY_HEADPHONE_ONLY = "key_headphone_only"
        const val KEY_SELECTED_APPS = "key_selected_apps"
        const val KEY_SPEECH_RATE = "key_speech_rate"
        const val KEY_SPEECH_PITCH = "key_speech_pitch"
        const val KEY_LAST_ANNOUNCEMENT = "key_last_announcement"
        const val KEY_VOICE_LOGS = "key_voice_logs"
        private const val MAX_VOICE_LOG_ENTRIES = 200
    }
}

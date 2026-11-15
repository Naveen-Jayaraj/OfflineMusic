package com.example.offlinemusic.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.offlinemusic.data.model.AppTheme
import com.example.offlinemusic.data.model.SortBy
import com.example.offlinemusic.data.model.SortOption
import com.example.offlinemusic.data.model.SortOrder

class MusicPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("offline_music_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOLDER_URI = "key_folder_uri"
        private const val KEY_FOLDER_NAME = "key_folder_name"
        private const val KEY_SORT_BY = "key_sort_by"
        private const val KEY_SORT_ORDER = "key_sort_order"
        private const val KEY_LAST_PLAYED_ID = "key_last_played_id"
        private const val KEY_RECENT_SEARCHES = "key_recent_searches"
        private const val KEY_DOWNLOAD_SOURCE = "key_download_source"
        private const val KEY_APP_THEME = "key_app_theme"
        private const val KEY_EQUALIZER_ENABLED = "key_equalizer_enabled"
        private const val KEY_EQUALIZER_GAINS = "key_equalizer_gains"
        private const val KEY_LAST_DYNAMIC_MIX_TIME = "key_last_dynamic_mix_time"
    }

    var lastDynamicMixTimestamp: Long
        get() = prefs.getLong(KEY_LAST_DYNAMIC_MIX_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DYNAMIC_MIX_TIME, value).apply()

    var downloadSourcePreference: com.example.offlinemusic.downloader.models.DownloadSourceFilter
        get() {
            val str = prefs.getString(KEY_DOWNLOAD_SOURCE, com.example.offlinemusic.downloader.models.DownloadSourceFilter.BOTH.name)
            return try {
                com.example.offlinemusic.downloader.models.DownloadSourceFilter.valueOf(str ?: com.example.offlinemusic.downloader.models.DownloadSourceFilter.BOTH.name)
            } catch (_: Exception) {
                com.example.offlinemusic.downloader.models.DownloadSourceFilter.BOTH
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_SOURCE, value.name).apply()
        }

    var appTheme: AppTheme
        get() {
            val savedTheme = prefs.getString(KEY_APP_THEME, null)
            return AppTheme.entries.firstOrNull { it.name == savedTheme } ?: AppTheme.DEFAULT
        }
        set(value) = prefs.edit().putString(KEY_APP_THEME, value.name).apply()

    val equalizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQUALIZER_ENABLED, false)

    val equalizerGains: Map<Int, Int>
        get() = prefs.getStringSet(KEY_EQUALIZER_GAINS, emptySet()).orEmpty()
            .mapNotNull { entry ->
                val frequency = entry.substringBefore(':').toIntOrNull()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val gain = entry.substringAfter(':', "").toIntOrNull() ?: return@mapNotNull null
                frequency to gain
            }
            .toMap()

    fun saveEqualizer(enabled: Boolean, gains: Map<Int, Int>) {
        prefs.edit()
            .putBoolean(KEY_EQUALIZER_ENABLED, enabled)
            .putStringSet(KEY_EQUALIZER_GAINS, gains.map { (frequency, gain) -> "$frequency:$gain" }.toSet())
            .apply()
    }

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return raw.split("\u001F").filter { it.isNotBlank() }
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getRecentSearches().filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableList()
        current.add(0, trimmed)
        val limited = current.take(20)
        prefs.edit().putString(KEY_RECENT_SEARCHES, limited.joinToString("\u001F")).apply()
    }

    fun removeRecentSearch(query: String) {
        val current = getRecentSearches().filterNot { it.equals(query.trim(), ignoreCase = true) }
        prefs.edit().putString(KEY_RECENT_SEARCHES, current.joinToString("\u001F")).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    var selectedFolderUri: Uri?
        get() = prefs.getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }
        set(value) = prefs.edit().putString(KEY_FOLDER_URI, value?.toString()).apply()

    var selectedFolderName: String?
        get() = prefs.getString(KEY_FOLDER_NAME, null)
        set(value) = prefs.edit().putString(KEY_FOLDER_NAME, value).apply()

    var sortOption: SortOption
        get() {
            val sortByStr = prefs.getString(KEY_SORT_BY, SortBy.TITLE.name) ?: SortBy.TITLE.name
            val sortOrderStr = prefs.getString(KEY_SORT_ORDER, SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name
            val sortBy = try { SortBy.valueOf(sortByStr) } catch (e: Exception) { SortBy.TITLE }
            val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (e: Exception) { SortOrder.ASCENDING }
            return SortOption(sortBy, sortOrder)
        }
        set(value) {
            prefs.edit()
                .putString(KEY_SORT_BY, value.sortBy.name)
                .putString(KEY_SORT_ORDER, value.sortOrder.name)
                .apply()
        }

    var lastPlayedId: String?
        get() = prefs.getString(KEY_LAST_PLAYED_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_PLAYED_ID, value).apply()

    fun clearFolder() {
        prefs.edit()
            .remove(KEY_FOLDER_URI)
            .remove(KEY_FOLDER_NAME)
            .apply()
    }
}

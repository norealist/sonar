package com.sonar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    val current: AppSettings
    fun update(transform: (AppSettings) -> AppSettings)
}

class PersistentSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = mutable.asStateFlow()
    override val current: AppSettings
        get() = mutable.value

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(mutable.value)
        mutable.value = next
        preferences.edit()
            .putBoolean("highResolutionOutput", next.highResolutionOutput)
            .putBoolean("resumeAfterFocusLoss", next.resumeAfterFocusLoss)
            .putFloat("volume", next.volume)
            .putBoolean("libraryGrid", next.libraryGrid)
            .putInt("gridColumns", next.gridColumns)
            .putBoolean("artistGrid", next.artistGrid)
            .putString("subControlMode", next.subControlMode.name)
            .putString("repeatMode", next.repeatMode.name)
            .putBoolean("shuffle", next.shuffle)
            .putString("selectedTrackId", next.selectedTrackId)
            .putInt("selectedIndex", next.selectedIndex)
            .putString("language", next.language.name)
            .apply()
    }

    private fun load() = AppSettings(
        highResolutionOutput = preferences.getBoolean("highResolutionOutput", false),
        resumeAfterFocusLoss = preferences.getBoolean("resumeAfterFocusLoss", true),
        volume = preferences.getFloat("volume", 1f),
        libraryGrid = preferences.getBoolean("libraryGrid", false),
        gridColumns = preferences.getInt("gridColumns", 4).coerceIn(2, 12),
        artistGrid = preferences.getBoolean("artistGrid", true),
        subControlMode = runCatching {
            SubControlMode.valueOf(preferences.getString("subControlMode", SubControlMode.SHUFFLE.name)!!)
        }.getOrDefault(SubControlMode.SHUFFLE),
        repeatMode = runCatching {
            RepeatMode.valueOf(preferences.getString("repeatMode", RepeatMode.OFF.name)!!)
        }.getOrDefault(RepeatMode.OFF),
        shuffle = preferences.getBoolean("shuffle", false),
        selectedTrackId = preferences.getString("selectedTrackId", null),
        selectedIndex = preferences.getInt("selectedIndex", 0),
        language = runCatching {
            AppLanguage.valueOf(preferences.getString("language", AppLanguage.SYSTEM.name)!!)
        }.getOrDefault(AppLanguage.SYSTEM),
    )
}

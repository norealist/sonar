package com.sonar.app.player

import android.content.Context
import com.sonar.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerControllerHolder {
    private val mutableController = MutableStateFlow<AppPlayerController?>(null)
    val controllerFlow: StateFlow<AppPlayerController?> = mutableController.asStateFlow()

    val controller: AppPlayerController?
        get() = mutableController.value?.takeIf { !it.isReleased }

    fun getOrCreate(context: Context, settings: SettingsRepository): AppPlayerController {
        val current = mutableController.value
        if (current != null && !current.isReleased) {
            return current
        }
        return synchronized(this) {
            val existing = mutableController.value
            if (existing != null && !existing.isReleased) {
                existing
            } else {
                AppPlayerController(context.applicationContext, settings).also {
                    mutableController.value = it
                }
            }
        }
    }

    fun setInstance(controller: AppPlayerController) {
        synchronized(this) {
            mutableController.value = controller
        }
    }

    fun clear(controller: AppPlayerController) {
        synchronized(this) {
            if (mutableController.value === controller) {
                mutableController.value = null
            }
        }
    }
}

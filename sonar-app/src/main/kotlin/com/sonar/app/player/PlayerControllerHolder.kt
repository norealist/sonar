package com.sonar.app.player

import android.content.Context
import com.sonar.app.data.SettingsRepository

object PlayerControllerHolder {
    @Volatile
    private var instance: AppPlayerController? = null

    val controller: AppPlayerController?
        get() = instance

    fun getOrCreate(context: Context, settings: SettingsRepository): AppPlayerController {
        return instance ?: synchronized(this) {
            instance ?: AppPlayerController(context.applicationContext, settings).also {
                instance = it
            }
        }
    }

    fun setInstance(controller: AppPlayerController) {
        synchronized(this) {
            instance = controller
        }
    }
}

package com.sonar.core

import android.content.Context
import android.media.AudioManager

class SessionManager(context: Context) {
    val audioSessionId: Int =
        context.applicationContext
            .getSystemService(AudioManager::class.java)
            ?.generateAudioSessionId()
            ?: 0
}

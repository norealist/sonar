package com.sonar.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {
    @Suppress("DEPRECATION")
    fun performTick(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createOneShot(20L, 120)
            } else {
                null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && effect != null) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null) {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                vibrator.vibrate(effect, audioAttrs)
            } else {
                vibrator.vibrate(20L)
            }
        }
    }
}

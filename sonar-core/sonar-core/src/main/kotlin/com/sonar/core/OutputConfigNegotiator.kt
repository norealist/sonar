package com.sonar.core

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import java.util.Locale

data class OutputConfig(
    val encoding: Int,
) {
    val integerBitDepth: Int?
        get() = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> null
        }

    val displayName: String
        get() = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit PCM"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit packed PCM"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit PCM"
            AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float PCM"
            else -> "Unknown PCM ($encoding)"
        }
}

fun interface EncodingProbe {
    fun isSupported(encoding: Int, sampleRate: Int, channels: Int): Boolean
}

class OutputConfigNegotiator(
    private val apiLevel: Int = Build.VERSION.SDK_INT,
    private val probe: EncodingProbe = AndroidAudioTrackProbe(),
) {
    private data class ProbeKey(val encoding: Int, val sampleRate: Int, val channels: Int)

    private val cache = mutableMapOf<ProbeKey, Boolean>()

    fun beginOpen() {
        cache.clear()
    }

    fun negotiate(
        enabled: Boolean,
        sourceBitDepth: Int,
        codec: String,
        sampleRate: Int,
        channels: Int,
    ): OutputConfig {
        val candidates = candidateEncodings(enabled, sourceBitDepth, codec)
        val probeCandidates = if (candidates.size > 1) candidates.dropLast(1) else emptyList()
        val selected = probeCandidates.firstOrNull { encoding ->
            val key = ProbeKey(encoding, sampleRate, channels)
            cache.getOrPut(key) {
                try {
                    probe.isSupported(encoding, sampleRate, channels)
                } catch (_: Throwable) {
                    false
                }
            }
        } ?: AudioFormat.ENCODING_PCM_16BIT
        return OutputConfig(selected)
    }

    /**
     * Returns the same ordered fallback chain used by negotiation, including
     * PCM16 as the final runtime fallback after a real AudioTrack build fails.
     */
    fun candidateEncodings(
        enabled: Boolean,
        sourceBitDepth: Int,
        codec: String,
    ): List<Int> {
        if (!enabled || sourceBitDepth < 24 || codec.isLossyCodec()) {
            return listOf(AudioFormat.ENCODING_PCM_16BIT)
        }

        val highResolution = if (apiLevel >= 31) {
            listOf(
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
        } else {
            listOf(AudioFormat.ENCODING_PCM_FLOAT)
        }
        return highResolution + AudioFormat.ENCODING_PCM_16BIT
    }

    private fun String.isLossyCodec(): Boolean {
        val normalized = lowercase(Locale.US)
        return normalized == "mp3" || normalized == "opus" || normalized == "ogg-opus"
    }
}

class AndroidAudioTrackProbe : EncodingProbe {
    override fun isSupported(encoding: Int, sampleRate: Int, channels: Int): Boolean {
        if (sampleRate <= 0 || channels !in 1..2) return false
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        return try {
            val minimum = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
            if (minimum <= 0 || minimum == AudioTrack.ERROR_BAD_VALUE) return false
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(minimum)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val supported = track.state == AudioTrack.STATE_INITIALIZED
            track.release()
            supported
        } catch (_: Throwable) {
            false
        }
    }
}

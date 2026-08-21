package com.sonar.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import kotlin.math.max

fun interface PcmReader {
    fun read(handle: Long, buffer: ByteBuffer, maxFrames: Int): Int
}

class AudioTrackWrapper(
    private val handle: Long,
    private val streamInfo: StreamInfo,
    private val output: OutputConfig,
    private val sessionId: Int,
    private val config: PlayerConfig,
    private val reader: PcmReader = PcmReader { nativeHandle, buffer, maxFrames ->
        NativeBridge.nativeReadPcm(nativeHandle, buffer, maxFrames)
    },
    private val onReadError: (SonarError) -> Unit = {},
    private val onCompleted: () -> Unit = {},
) {
    private val frameBytes = outputBytesPerSample(output.encoding) * streamInfo.channels
    private val maxFrames = config.writeChunkFrames
    private val pcmBuffer = ByteBuffer.allocateDirect(maxFrames * frameBytes)
    private val audioTrack: AudioTrack = createAudioTrack()
    private val lock = Any()
    private var active = false
    private var writeThread: Thread? = null

    private fun createAudioTrack(): AudioTrack {
        val channelMask = when (streamInfo.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException("Only mono and stereo are supported")
        }
        val minimum = AudioTrack.getMinBufferSize(streamInfo.sampleRate, channelMask, output.encoding)
        if (minimum <= 0 || minimum == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalStateException("AudioTrack rejected the output format")
        }
        val desired = streamInfo.sampleRate * config.desiredLatencyMs / 1000 * frameBytes
        val bufferSize = max(minimum * 2, desired)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(output.encoding)
                    .setSampleRate(streamInfo.sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(sessionId)
            .build()
            .also { track ->
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    throw IllegalStateException("AudioTrack failed to initialize")
                }
            }
    }

    fun start() {
        synchronized(lock) {
            if (active) return
            audioTrack.play()
            active = true
            writeThread = Thread(::writeLoop, "sonar-audio-write").also { it.start() }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (active) audioTrack.pause()
        }
    }

    fun resume() {
        synchronized(lock) {
            if (active) audioTrack.play()
        }
    }

    fun setVolume(volume: Float) {
        audioTrack.setVolume(volume.coerceIn(0f, 1f))
    }

    fun release() {
        val thread: Thread?
        synchronized(lock) {
            active = false
            thread = writeThread
            writeThread = null
            try {
                audioTrack.pause()
                audioTrack.flush()
                audioTrack.stop()
            } catch (_: IllegalStateException) {
                // AudioTrack can already be stopped after an output error.
            }
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(lock) {
            audioTrack.release()
        }
    }

    private fun writeLoop() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}

        while (true) {
            synchronized(lock) {
                if (!active) return
            }
            if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack.play()
                } catch (_: Exception) {}
            }
            pcmBuffer.clear()
            val frames = try {
                reader.read(handle, pcmBuffer, maxFrames)
            } catch (_: Throwable) {
                reportError(SonarError.ERR_INTERNAL)
                return
            }
            when {
                frames > 0 -> {
                    val safeFrames = frames.coerceAtMost(maxFrames)
                    val bytes = safeFrames * frameBytes
                    pcmBuffer.position(0)
                    pcmBuffer.limit(bytes)
                    val written = try {
                        audioTrack.write(pcmBuffer, bytes, AudioTrack.WRITE_BLOCKING)
                    } catch (_: Throwable) {
                        reportError(SonarError.ERR_OUTPUT_FORMAT)
                        return
                    }
                    if (written < 0) {
                        reportError(SonarError.ERR_OUTPUT_FORMAT)
                        return
                    }
                }
                frames == 0 -> {
                    when (NativeBridge.nativeGetState(handle)) {
                        PlayerState.COMPLETED.nativeValue -> {
                            if (deactivateIfActive()) onCompleted()
                            return
                        }
                        PlayerState.ERROR.nativeValue -> {
                            reportError(SonarError.ERR_DECODER_DECODE)
                            return
                        }
                    }
                    try {
                        Thread.sleep(5)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
                else -> {
                    reportError(SonarError.fromCode(frames))
                    return
                }
            }
        }
    }

    private fun deactivateIfActive(): Boolean = synchronized(lock) {
        if (!active) return@synchronized false
        active = false
        true
    }

    private fun reportError(error: SonarError) {
        if (deactivateIfActive()) onReadError(error)
    }

    private fun outputBytesPerSample(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> throw IllegalArgumentException("Unsupported PCM encoding: $encoding")
    }
}

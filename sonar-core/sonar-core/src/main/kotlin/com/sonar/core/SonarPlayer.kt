package com.sonar.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.AudioFormat
import java.io.File
import java.io.IOException

class SonarPlayer(
    context: Context,
    private val config: PlayerConfig = PlayerConfig(),
    private val negotiator: OutputConfigNegotiator = OutputConfigNegotiator(),
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val sessionManager = SessionManager(appContext)
    private var nativeHandle: Long = NativeBridge.nativeCreateEngine(
        48_000,
        AudioFormat.ENCODING_PCM_16BIT,
        2,
    )
    private var track: AudioTrackWrapper? = null
    private var currentStream: StreamInfo? = null
    private var currentOutput = OutputConfig(AudioFormat.ENCODING_PCM_16BIT)
    private var highResolutionEnabled = false

    @Volatile
    private var lastError: SonarError? = null

    val audioSessionId: Int
        get() = sessionManager.audioSessionId

    val state: PlayerState
        get() = synchronized(lock) {
            if (nativeHandle == 0L) PlayerState.IDLE
            else PlayerState.fromNative(NativeBridge.nativeGetState(nativeHandle))
        }

    val streamInfo: StreamInfo?
        get() = synchronized(lock) { currentStream }

    val positionMs: Long
        get() = synchronized(lock) {
            if (nativeHandle == 0L) 0L else NativeBridge.nativeGetPosition(nativeHandle)
        }

    val outputEncoding: Int
        get() = synchronized(lock) { currentOutput.encoding }

    val outputBitDepth: Int?
        get() = synchronized(lock) { currentOutput.integerBitDepth }

    val outputDescription: String
        get() = synchronized(lock) { currentOutput.displayName }

    val highResolutionOutputEnabled: Boolean
        get() = highResolutionEnabled

    val error: SonarError?
        get() {
            val local = lastError
            if (local != null) return local
            synchronized(lock) {
                if (nativeHandle == 0L) return null
                val detail = NativeBridge.nativeGetError(nativeHandle)
                return if (detail.isNullOrBlank()) null else SonarError.ERR_INTERNAL.copy(message = detail)
            }
        }

    init {
        check(nativeHandle != 0L) { "Unable to create the native Sonar engine" }
    }

    fun open(filePath: String): SonarError = synchronized(lock) {
        openPathLocked(filePath)
    }

    fun open(uri: Uri): SonarError {
        val path = try {
            materializeUri(uri)
        } catch (_: IOException) {
            return SonarError.ERR_FILE_READ.also { lastError = it }
        }
        return synchronized(lock) { openPathLocked(path) }
    }

    fun play(): SonarError = synchronized(lock) {
        val code = NativeBridge.nativePlay(nativeHandle)
        if (code != SonarError.OK.code) return@synchronized recordErrorLocked(code)
        try {
            track?.start()
            clearErrorLocked()
            SonarError.OK
        } catch (_: Throwable) {
            NativeBridge.nativeStop(nativeHandle)
            recordErrorLocked(SonarError.ERR_OUTPUT_FORMAT.code)
        }
    }

    fun pause(): SonarError = synchronized(lock) {
        val code = NativeBridge.nativePause(nativeHandle)
        if (code == SonarError.OK.code) track?.pause()
        recordResultLocked(code)
    }

    fun resume(): SonarError = synchronized(lock) {
        val code = NativeBridge.nativeResume(nativeHandle)
        if (code == SonarError.OK.code) track?.resume()
        recordResultLocked(code)
    }

    fun stop(): SonarError = synchronized(lock) {
        track?.release()
        track = null
        val code = NativeBridge.nativeStop(nativeHandle)
        currentStream = null
        recordResultLocked(code)
    }

    fun seekTo(positionMs: Long): SonarError = synchronized(lock) {
        val code = NativeBridge.nativeSeek(nativeHandle, positionMs.coerceAtLeast(0L))
        recordResultLocked(code)
    }

    fun next(uri: Uri): SonarError {
        val opened = open(uri)
        return if (opened.isOk) play() else opened
    }

    fun previous(uri: Uri): SonarError {
        val opened = open(uri)
        return if (opened.isOk) play() else opened
    }

    fun setVolume(volume: Float) {
        synchronized(lock) {
            track?.setVolume(volume)
        }
    }

    fun setHighResolutionOutputEnabled(enabled: Boolean): SonarError = synchronized(lock) {
        if (highResolutionEnabled == enabled) return@synchronized SonarError.OK
        highResolutionEnabled = enabled
        val info = currentStream ?: return@synchronized SonarError.OK
        val previousState = PlayerState.fromNative(NativeBridge.nativeGetState(nativeHandle))
        val negotiated = negotiateLocked(info)
        track?.release()
        track = null
        try {
            track = createTrackWithFallbackLocked(info, negotiated)
            if (previousState == PlayerState.PLAYING || previousState == PlayerState.BUFFERING) {
                track?.start()
            }
            clearErrorLocked()
            SonarError.OK
        } catch (_: Throwable) {
            currentOutput = OutputConfig(AudioFormat.ENCODING_PCM_16BIT)
            NativeBridge.nativeSetOutputFormat(nativeHandle, currentOutput.encoding)
            recordErrorLocked(SonarError.ERR_OUTPUT_FORMAT.code)
        }
    }

    fun release() {
        synchronized(lock) {
            if (nativeHandle == 0L) return
            track?.release()
            track = null
            NativeBridge.nativeDestroyEngine(nativeHandle)
            nativeHandle = 0L
            currentStream = null
        }
    }

    private fun openPathLocked(filePath: String): SonarError {
        if (nativeHandle == 0L || filePath.isBlank()) {
            return recordErrorLocked(SonarError.ERR_FILE_NOT_FOUND.code)
        }
        track?.release()
        track = null
        currentStream = null
        NativeBridge.nativeStop(nativeHandle)
        val openCode = NativeBridge.nativeOpen(nativeHandle, filePath)
        if (openCode != SonarError.OK.code) return recordErrorLocked(openCode)
        val info = NativeBridge.nativeGetStreamInfo(nativeHandle)
            ?: return recordErrorLocked(SonarError.ERR_INTERNAL.code)
        negotiator.beginOpen()
        val negotiated = negotiateLocked(info)
        currentStream = info
        try {
            track = createTrackWithFallbackLocked(info, negotiated)
        } catch (_: Throwable) {
            NativeBridge.nativeStop(nativeHandle)
            return recordErrorLocked(SonarError.ERR_OUTPUT_FORMAT.code)
        }
        clearErrorLocked()
        return SonarError.OK
    }

    private fun negotiateLocked(info: StreamInfo): OutputConfig = negotiator.negotiate(
        enabled = highResolutionEnabled,
        sourceBitDepth = info.sourceBitDepth,
        codec = info.codec,
        sampleRate = info.sampleRate,
        channels = info.channels,
    )

    private fun createTrackLocked(info: StreamInfo): AudioTrackWrapper = AudioTrackWrapper(
        handle = nativeHandle,
        streamInfo = info,
        output = currentOutput,
        sessionId = sessionManager.audioSessionId,
        config = config,
        onReadError = { error -> lastError = error },
    )

    private fun createTrackWithFallbackLocked(
        info: StreamInfo,
        preferred: OutputConfig,
    ): AudioTrackWrapper {
        val candidates = negotiator.candidateEncodings(
            enabled = highResolutionEnabled,
            sourceBitDepth = info.sourceBitDepth,
            codec = info.codec,
        )
        val preferredIndex = candidates.indexOf(preferred.encoding).let { index ->
            if (index >= 0) index else 0
        }
        var lastFailure: Throwable? = null
        for (index in preferredIndex until candidates.size) {
            val candidate = OutputConfig(candidates[index])
            val setCode = NativeBridge.nativeSetOutputFormat(nativeHandle, candidate.encoding)
            if (setCode != SonarError.OK.code) continue
            currentOutput = candidate
            try {
                return createTrackLocked(info)
            } catch (failure: Throwable) {
                lastFailure = failure
            }
        }
        throw IllegalStateException("No usable AudioTrack output format", lastFailure)
    }

    private fun recordResultLocked(code: Int): SonarError {
        return if (code == SonarError.OK.code) {
            clearErrorLocked()
            SonarError.OK
        } else {
            recordErrorLocked(code)
        }
    }

    private fun recordErrorLocked(code: Int): SonarError {
        val detail = if (nativeHandle == 0L) null else NativeBridge.nativeGetError(nativeHandle)
        return SonarError.fromCode(code, detail).also { lastError = it }
    }

    private fun clearErrorLocked() {
        lastError = null
    }

    private fun materializeUri(uri: Uri): String {
        if (uri.scheme == "file") return requireNotNull(uri.path)
        val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "audio"
        val destination = File(appContext.cacheDir, "sonar-$name")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Unable to read $uri")
        return destination.absolutePath
    }
}

package com.sonar.app.player

import android.content.Context
import android.net.Uri
import com.sonar.app.data.AppError
import com.sonar.app.data.AppSettings
import com.sonar.app.data.RepeatMode
import com.sonar.app.data.SleepTimerState
import com.sonar.app.data.SubControlMode
import com.sonar.app.data.Track
import com.sonar.app.data.SettingsRepository
import com.sonar.core.PlayerState
import com.sonar.core.SonarError
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext

data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val selectedTrack: Track? = null,
    val coreState: PlayerState = PlayerState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val outputDescription: String = "16-bit PCM",
    val outputBitDepth: Int? = 16,
    val audioSessionId: Int = 0,
    val error: AppError? = null,
    val isFavorite: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val subControlMode: SubControlMode = SubControlMode.SHUFFLE,
    val isSeeking: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val activeSheet: com.sonar.app.data.Sheet? = null,
) {
    val isPlaying: Boolean
        get() = coreState == PlayerState.PLAYING || coreState == PlayerState.BUFFERING
}

class AppPlayerController(
    context: Context,
    private val settings: SettingsRepository,
    gatewayFactory: (Context) -> PlayerGateway = ::CorePlayerGateway,
) {
    private val appContext = context.applicationContext
    private val gateway = gatewayFactory(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerUiState())
    private var pollingJob: Job
    private var sleepJob: Job? = null
    private var wasPlayingBeforeSeek = false
    private var lastObservedState = PlayerState.IDLE
    private var shuffleOrder: List<Int> = emptyList()
    private val released = AtomicBoolean(false)

    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    init {
        PlayerControllerHolder.controller = this
        pollingJob = scope.launch { pollCore() }
        applySettings(settings.current)
    }

    fun setQueue(tracks: List<Track>, selectedId: String? = state.value.selectedTrack?.id) {
        val index = tracks.indexOfFirst { it.id == selectedId }
        mutableState.value = mutableState.value.copy(
            queue = tracks,
            currentIndex = index,
            selectedTrack = tracks.getOrNull(index),
        )
        if (state.value.shuffle) resetShuffleOrder(index) else shuffleOrder = emptyList()
    }

    fun selectTrack(track: Track, autoplay: Boolean = true) {
        val index = state.value.queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        mutableState.value = state.value.copy(
            selectedTrack = track,
            currentIndex = index,
            error = null,
        )
        if (state.value.shuffle) resetShuffleOrder(index)
        settings.update { it.copy(selectedTrackId = track.id, selectedIndex = index) }
        if (autoplay) MediaPlaybackService.start(appContext)
        scope.launch { openSelected(autoplay) }
    }

    fun togglePlayPause() {
        scope.launch {
            val current = state.value
            val result = when (current.coreState) {
                PlayerState.PLAYING, PlayerState.BUFFERING -> withContext(Dispatchers.IO) { gateway.pause() }
                PlayerState.PAUSED -> {
                    MediaPlaybackService.start(appContext)
                    withContext(Dispatchers.IO) { gateway.resume() }
                }
                else -> {
                    MediaPlaybackService.start(appContext)
                    if (gateway.streamInfo == null) openSelected(false) else SonarError.OK
                    withContext(Dispatchers.IO) { gateway.play() }
                }
            }
            publishResult(result)
        }
    }

    fun stop() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { gateway.stop() }
            publishResult(result)
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { gateway.seekTo(positionMs) }
            publishResult(result)
            if (wasPlayingBeforeSeek) {
                val resumed = withContext(Dispatchers.IO) { gateway.resume() }
                publishResult(resumed)
                wasPlayingBeforeSeek = false
            }
            mutableState.value = state.value.copy(isSeeking = false)
        }
    }

    fun setSeeking(active: Boolean) {
        val alreadySeeking = state.value.isSeeking
        if (active) {
            if (alreadySeeking) return
            wasPlayingBeforeSeek = state.value.isPlaying
            mutableState.value = state.value.copy(isSeeking = true)
            if (wasPlayingBeforeSeek) {
                scope.launch(Dispatchers.IO) { gateway.pause() }
            }
        } else {
            mutableState.value = state.value.copy(isSeeking = false)
        }
    }

    fun next() {
        scope.launch { advance(forward = true, automatic = false) }
    }

    fun previous() {
        scope.launch {
            if (state.value.positionMs > 3_000L) {
                seekTo(0L)
            } else {
                advance(forward = false, automatic = false)
            }
        }
    }

    fun cycleRepeat() {
        val nextMode = when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        mutableState.value = state.value.copy(repeatMode = nextMode)
        settings.update { it.copy(repeatMode = nextMode) }
    }

    fun tapSubControl() {
        if (state.value.subControlMode == SubControlMode.SHUFFLE) toggleShuffle() else cycleRepeat()
    }

    fun toggleSubControlMode() {
        val mode = if (state.value.subControlMode == SubControlMode.SHUFFLE) {
            SubControlMode.REPEAT
        } else {
            SubControlMode.SHUFFLE
        }
        settings.update { it.copy(subControlMode = mode) }
        mutableState.value = state.value.copy(subControlMode = mode)
    }

    fun toggleShuffle() {
        val enabled = !state.value.shuffle
        mutableState.value = state.value.copy(shuffle = enabled)
        if (enabled) resetShuffleOrder(state.value.currentIndex) else shuffleOrder = emptyList()
        settings.update { it.copy(shuffle = enabled) }
    }

    fun setVolume(volume: Float) {
        val safe = volume.coerceIn(0f, 1f)
        settings.update { it.copy(volume = safe) }
        scope.launch(Dispatchers.IO) { gateway.setVolume(safe) }
    }

    fun setHighResolution(enabled: Boolean) {
        val previous = settings.current.highResolutionOutput
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                gateway.setHighResolutionOutputEnabled(enabled)
            }
            settings.update { it.copy(highResolutionOutput = if (result.isOk) enabled else previous) }
            publishResult(result)
        }
    }

    fun setSheet(sheet: com.sonar.app.data.Sheet?) {
        mutableState.value = state.value.copy(activeSheet = sheet)
    }

    fun setFavorite(value: Boolean) {
        mutableState.value = state.value.copy(isFavorite = value)
    }

    fun setSleepTimer(minutes: Int) {
        val expires = System.currentTimeMillis() + minutes.coerceIn(1, 600) * 60_000L
        mutableState.value = state.value.copy(sleepTimer = SleepTimerState(expires))
        sleepJob?.cancel()
        sleepJob = scope.launch {
            while (isActive && state.value.sleepTimer.remainingMs() > 0L) delay(1_000L)
            if (isActive) {
                withContext(Dispatchers.IO) { gateway.pause() }
                mutableState.value = state.value.copy(sleepTimer = SleepTimerState())
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        mutableState.value = state.value.copy(sleepTimer = SleepTimerState())
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        if (PlayerControllerHolder.controller === this) {
            PlayerControllerHolder.controller = null
        }
        pollingJob.cancel()
        sleepJob?.cancel()
        gateway.release()
        scope.cancel()
    }

    private suspend fun openSelected(autoplay: Boolean): SonarError {
        val track = state.value.selectedTrack ?: return publishResult(SonarError.ERR_FILE_NOT_FOUND)
        val opened = withContext(Dispatchers.IO) { gateway.open(Uri.parse(track.uri)) }
        publishResult(opened)
        if (!opened.isOk || !autoplay) return opened
        withContext(Dispatchers.Main.immediate) {
            val info = gateway.streamInfo
            if (info != null && state.value.selectedTrack?.id == track.id) {
                mutableState.value = state.value.copy(
                    selectedTrack = state.value.selectedTrack?.copy(
                        durationMs = info.durationMs,
                        codec = info.codec,
                        sampleRate = info.sampleRate,
                        sourceBitDepth = info.sourceBitDepth,
                    ),
                    durationMs = info.durationMs,
                )
            }
        }
        val played = withContext(Dispatchers.IO) { gateway.play() }
        publishResult(played)
        return played
    }

    private suspend fun advance(forward: Boolean, automatic: Boolean) {
        val current = state.value
        if (current.queue.isEmpty()) return
        if (automatic && current.repeatMode == RepeatMode.ONE) {
            withContext(Dispatchers.IO) {
                gateway.seekTo(0L)
                gateway.play()
            }
            return
        }
        val resolved = if (current.shuffle && current.queue.size > 1) {
            resolveShuffleIndex(current, forward, automatic)
        } else {
            val nextIndex = if (forward) current.currentIndex + 1 else current.currentIndex - 1
            when {
                nextIndex in current.queue.indices -> nextIndex
                automatic && current.repeatMode == RepeatMode.ALL -> if (forward) 0 else current.queue.lastIndex
                !automatic -> if (forward) 0 else current.queue.lastIndex
                else -> null
            }
        }
        if (resolved == null) {
            if (automatic) mutableState.value = state.value.copy(coreState = PlayerState.COMPLETED)
            return
        }
        val track = current.queue[resolved]
        mutableState.value = state.value.copy(currentIndex = resolved, selectedTrack = track)
        settings.update { it.copy(selectedTrackId = track.id, selectedIndex = resolved) }
        openSelected(autoplay = true)
    }

    private suspend fun pollCore() {
        while (currentCoroutineContext().isActive && !released.get()) {
            val coreState = withContext(Dispatchers.IO) { gateway.state }
            val position = withContext(Dispatchers.IO) { gateway.positionMs }
            val info = withContext(Dispatchers.IO) { gateway.streamInfo }
            val coreError = withContext(Dispatchers.IO) { gateway.error }
            mutableState.value = state.value.copy(
                coreState = coreState,
                positionMs = if (state.value.isSeeking) state.value.positionMs else position,
                durationMs = info?.durationMs ?: state.value.selectedTrack?.durationMs ?: 0L,
                outputDescription = gateway.outputDescription,
                outputBitDepth = gateway.outputBitDepth,
                audioSessionId = gateway.audioSessionId,
                error = coreError?.let { AppError(it.code, it.message) },
            )
            if (coreState == PlayerState.COMPLETED && lastObservedState != PlayerState.COMPLETED) {
                advance(forward = true, automatic = true)
            }
            lastObservedState = coreState
            delay(175L)
        }
    }

    private fun publishResult(error: SonarError): SonarError {
        if (!error.isOk) mutableState.value = state.value.copy(error = AppError(error.code, error.message))
        else if (state.value.error != null) mutableState.value = state.value.copy(error = null)
        return error
    }

    private fun applySettings(settings: AppSettings) {
        mutableState.value = state.value.copy(
            selectedTrack = null,
            currentIndex = settings.selectedIndex,
            subControlMode = settings.subControlMode,
            repeatMode = settings.repeatMode,
            shuffle = settings.shuffle,
        )
        scope.launch(Dispatchers.IO) {
            gateway.setVolume(settings.volume)
            gateway.setHighResolutionOutputEnabled(settings.highResolutionOutput)
        }
    }

    private fun resetShuffleOrder(currentIndex: Int) {
        val queue = state.value.queue
        if (queue.isEmpty()) {
            shuffleOrder = emptyList()
            return
        }
        val rest = queue.indices.filterNot { it == currentIndex }.shuffled()
        shuffleOrder = if (currentIndex in queue.indices) listOf(currentIndex) + rest else rest
    }

    private fun resolveShuffleIndex(current: PlayerUiState, forward: Boolean, automatic: Boolean): Int? {
        if (shuffleOrder.size != current.queue.size || current.currentIndex !in shuffleOrder) {
            resetShuffleOrder(current.currentIndex)
        }
        val currentPosition = shuffleOrder.indexOf(current.currentIndex)
        if (currentPosition < 0) return null
        val nextPosition = if (forward) currentPosition + 1 else currentPosition - 1
        if (nextPosition in shuffleOrder.indices) return shuffleOrder[nextPosition]

        if (automatic && current.repeatMode == RepeatMode.ALL) {
            resetShuffleOrder(current.currentIndex)
            return shuffleOrder.getOrNull(1) ?: current.currentIndex
        }
        if (!automatic) {
            resetShuffleOrder(current.currentIndex)
            return if (forward) shuffleOrder.getOrNull(1) else shuffleOrder.lastOrNull()
        }
        return null
    }
}

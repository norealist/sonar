package com.sonar.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.sonar.app.MainActivity
import com.sonar.app.R
import com.sonar.app.data.Track
import com.sonar.core.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var isForegroundService = false

    private val artworkCache = LruCache<String, Bitmap>(20)

    private var lastTrackId: String? = null
    private var lastIsPlaying: Boolean? = null
    private var lastCoreState: PlayerState? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                PlayerControllerHolder.controller?.let { controller ->
                    if (controller.state.value.isPlaying) {
                        controller.togglePlayPause()
                    }
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val controller = PlayerControllerHolder.controller ?: return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                if (controller.state.value.isPlaying) {
                    controller.togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                if (controller.state.value.isPlaying) {
                    resumeOnFocusGain = true
                    controller.togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck volume if supported
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    if (!controller.state.value.isPlaying) {
                        controller.togglePlayPause()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sonar:MediaPlaybackWakeLock").apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()
        setupMediaSession()
        registerNoisyReceiver()

        // Immediate startForeground with initial notification to satisfy Android 5-second startup requirement
        val initialNotification = buildNotification(null, isPlaying = false, artwork = null)
        startForegroundServiceInternal(initialNotification)

        observePlayerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = PlayerControllerHolder.controller
        when (intent?.action) {
            ACTION_PLAY -> controller?.let {
                if (!it.state.value.isPlaying) {
                    requestAudioFocus()
                    it.togglePlayPause()
                }
            }
            ACTION_PAUSE -> controller?.let {
                if (it.state.value.isPlaying) {
                    it.togglePlayPause()
                }
            }
            ACTION_TOGGLE_PLAY_PAUSE -> controller?.let {
                if (!it.state.value.isPlaying) requestAudioFocus()
                it.togglePlayPause()
            }
            ACTION_PREVIOUS -> controller?.previous()
            ACTION_NEXT -> controller?.next()
            ACTION_STOP -> {
                controller?.stop()
                abandonAudioFocus()
                stopForegroundInternal()
                stopSelf()
            }
            ACTION_START_SERVICE -> {
                observePlayerState()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        unregisterNoisyReceiver()
        abandonAudioFocus()
        releaseWakeLock()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        stopForegroundInternal()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val controller = PlayerControllerHolder.controller
        if (controller == null || !controller.state.value.isPlaying) {
            stopForegroundInternal()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SonarMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    requestAudioFocus()
                    PlayerControllerHolder.controller?.let {
                        if (!it.state.value.isPlaying) it.togglePlayPause()
                    }
                }

                override fun onPause() {
                    PlayerControllerHolder.controller?.let {
                        if (it.state.value.isPlaying) it.togglePlayPause()
                    }
                }

                override fun onSkipToNext() {
                    PlayerControllerHolder.controller?.next()
                }

                override fun onSkipToPrevious() {
                    PlayerControllerHolder.controller?.previous()
                }

                override fun onSeekTo(pos: Long) {
                    PlayerControllerHolder.controller?.seekTo(pos)
                }

                override fun onStop() {
                    PlayerControllerHolder.controller?.stop()
                    abandonAudioFocus()
                    stopForegroundInternal()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    private fun observePlayerState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            PlayerControllerHolder.controllerFlow.collectLatest { controller ->
                if (controller != null && !controller.isReleased) {
                    controller.state.collectLatest { state ->
                        handleStateUpdate(state)
                    }
                }
            }
        }
    }

    private fun handleStateUpdate(state: PlayerUiState) {
        val currentTrack = state.selectedTrack
        val isPlaying = state.isPlaying
        val coreState = state.coreState

        val trackChanged = currentTrack?.id != lastTrackId
        val playStateChanged = isPlaying != lastIsPlaying || coreState != lastCoreState

        // Manage WakeLock to prevent CPU sleeping during background playback
        if (isPlaying) {
            acquireWakeLock()
            if (!hasAudioFocus) requestAudioFocus()
        } else {
            releaseWakeLock()
        }

        if (trackChanged || playStateChanged) {
            lastTrackId = currentTrack?.id
            lastIsPlaying = isPlaying
            lastCoreState = coreState

            updateMediaSessionState(state)

            if (currentTrack != null) {
                serviceScope.launch(Dispatchers.IO) {
                    val cachedArt = artworkCache.get(currentTrack.id) ?: loadArtwork(currentTrack)?.also {
                        artworkCache.put(currentTrack.id, it)
                    }
                    withContext(Dispatchers.Main.immediate) {
                        updateMediaSessionMetadata(currentTrack, cachedArt)
                        updateNotificationUi(currentTrack, isPlaying, cachedArt)
                    }
                }
            } else {
                updateMediaSessionMetadata(null, null)
                updateNotificationUi(null, isPlaying, null)
            }
        }
    }

    private fun updateMediaSessionState(state: PlayerUiState) {
        if (!::mediaSession.isInitialized) return

        val playbackState = when (state.coreState) {
            PlayerState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PlayerState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PlayerState.BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            PlayerState.IDLE, PlayerState.COMPLETED -> PlaybackStateCompat.STATE_STOPPED
            PlayerState.OPENED -> PlaybackStateCompat.STATE_PAUSED
            PlayerState.ERROR -> PlaybackStateCompat.STATE_ERROR
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    playbackState,
                    state.positionMs,
                    if (state.isPlaying) 1.0f else 0.0f,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )
    }

    private fun updateMediaSessionMetadata(track: Track?, artwork: Bitmap?) {
        if (!::mediaSession.isInitialized) return
        if (track == null) {
            mediaSession.setMetadata(null)
            return
        }

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)

        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
        }
        mediaSession.setMetadata(builder.build())
    }

    private fun updateNotificationUi(track: Track?, isPlaying: Boolean, artwork: Bitmap?) {
        if (track == null && !isPlaying) {
            stopForegroundInternal()
            return
        }

        val notification = buildNotification(track, isPlaying, artwork)
        if (isPlaying) {
            startForegroundServiceInternal(notification)
        } else {
            if (isForegroundService) {
                stopForegroundCompat(removeNotification = false)
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        track: Track?,
        isPlaying: Boolean,
        artwork: Bitmap?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags()
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
            pendingIntentFlags()
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            pendingIntentFlags()
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            pendingIntentFlags()
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_STOP },
            pendingIntentFlags()
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sonar)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist ?: "")
            .setSubText(track?.album)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopIntent)
            )

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        return builder.build()
    }

    private fun loadArtwork(track: Track): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, track.contentUri)
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(3600 * 1000L) // 1 hour max safeguard timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun startForegroundServiceInternal(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundService = true
        } catch (e: Exception) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundInternal() {
        if (isForegroundService) {
            stopForegroundCompat(removeNotification = true)
            isForegroundService = false
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
        if (removeNotification) {
            isForegroundService = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sonar audio playback controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
    }

    private fun unregisterNoisyReceiver() {
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        resumeOnFocusGain = false
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    companion object {
        const val CHANNEL_ID = "sonar_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.sonar.app.ACTION_START_SERVICE"
        const val ACTION_PLAY = "com.sonar.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.sonar.app.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.sonar.app.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.sonar.app.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.sonar.app.ACTION_NEXT"
        const val ACTION_STOP = "com.sonar.app.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

package com.sonar.app.player

import android.content.Context
import android.net.Uri
import com.sonar.core.PlayerState
import com.sonar.core.SonarError
import com.sonar.core.StreamInfo
import com.sonar.core.SonarPlayer

interface PlayerGateway {
    val state: PlayerState
    val streamInfo: StreamInfo?
    val positionMs: Long
    val outputDescription: String
    val outputBitDepth: Int?
    val audioSessionId: Int
    val error: SonarError?

    fun open(uri: Uri): SonarError
    fun play(): SonarError
    fun pause(): SonarError
    fun resume(): SonarError
    fun stop(): SonarError
    fun seekTo(positionMs: Long): SonarError
    fun setVolume(volume: Float)
    fun setHighResolutionOutputEnabled(enabled: Boolean): SonarError
    fun release()
}

class CorePlayerGateway(context: Context) : PlayerGateway {
    private val player = SonarPlayer(context.applicationContext)

    override val state get() = player.state
    override val streamInfo get() = player.streamInfo
    override val positionMs get() = player.positionMs
    override val outputDescription get() = player.outputDescription
    override val outputBitDepth get() = player.outputBitDepth
    override val audioSessionId get() = player.audioSessionId
    override val error get() = player.error

    override fun open(uri: Uri) = player.open(uri)
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun resume() = player.resume()
    override fun stop() = player.stop()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun setVolume(volume: Float) = player.setVolume(volume)
    override fun setHighResolutionOutputEnabled(enabled: Boolean) =
        player.setHighResolutionOutputEnabled(enabled)

    override fun release() = player.release()
}

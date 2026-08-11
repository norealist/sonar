package com.sonar.testharness

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.sonar.core.PlayerState
import com.sonar.core.SonarPlayer
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var player: SonarPlayer
    private lateinit var trackText: TextView
    private lateinit var metadataText: TextView
    private lateinit var outputText: TextView
    private lateinit var stateText: TextView
    private lateinit var errorText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseButton: Button
    private var selectedUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 150L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = SonarPlayer(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            val padding = dp(16)
            view.setPadding(padding, padding + top, padding, padding + bottom)
            insets
        }
        val choose = Button(this).apply {
            text = "Choose audio file"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("audio/*"),
                    REQUEST_OPEN,
                )
            }
        }
        root.addView(choose, match())
        trackText = label("No track selected")
        root.addView(trackText, match())

        val standardControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(controlButton("Prev") {
                withSelectedFile { player.previous(it) }
            })
            playPauseButton = controlButton("Play") { togglePlayPause() }
            addView(playPauseButton)
            addView(controlButton("Next") {
                withSelectedFile { player.next(it) }
            })
        }
        root.addView(standardControls, match())

        val debugControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(controlButton("Play") { show(player.play()) })
            addView(controlButton("Pause") { show(player.pause()) })
            addView(controlButton("Resume") { show(player.resume()) })
            addView(controlButton("Stop") { show(player.stop()) })
        }
        root.addView(debugControls, match())

        val debugToggle = CheckBox(this).apply {
            text = "Debug player"
            setOnCheckedChangeListener { _, enabled ->
                standardControls.visibility = if (enabled) View.GONE else View.VISIBLE
                debugControls.visibility = if (enabled) View.VISIBLE else View.GONE
            }
        }
        root.addView(debugToggle, match())

        seekBar = SeekBar(this)
        root.addView(seekBar, match())
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                show(player.seekTo(seekBar?.progress?.toLong() ?: 0L))
            }
        })

        val outputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val highResolution = Switch(this).apply {
            text = "Output audio up to 32-bit"
            setOnCheckedChangeListener { _, enabled -> show(player.setHighResolutionOutputEnabled(enabled)) }
        }
        outputRow.addView(highResolution, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outputText = label("")
        outputRow.addView(outputText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(outputRow, match())
        metadataText = label("")
        stateText = label("")
        errorText = label("")
        root.addView(metadataText, match())
        root.addView(stateText, match())
        root.addView(errorText, match())

        setContentView(root)
        handler.post(poll)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val result = player.open(uri)
            if (result.isOk) {
                selectedUri = uri
                trackText.text = formatTrackMetadata(uri)
            }
            show(result)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        player.release()
        super.onDestroy()
    }

    private fun render() {
        val info = player.streamInfo
        val duration = info?.durationMs ?: -1L
        val position = player.positionMs
        if (duration > 0) {
            seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (!seekBar.isPressed) seekBar.progress = position.coerceIn(0L, duration).toInt()
        } else {
            seekBar.max = 0
            seekBar.progress = 0
        }
        metadataText.text = if (info == null) {
            "No stream metadata"
        } else {
            "Format: ${info.codec}, ${info.sampleRate} Hz, ${info.channels} ch, " +
                "source ${info.sourceBitDepth}-bit\n" +
                "Position: ${formatMs(position)} / ${formatMs(duration)}"
        }
        outputText.text = "Output: ${player.outputDescription}"
        val currentState = player.state
        playPauseButton.text = if (currentState == PlayerState.PLAYING ||
            currentState == PlayerState.BUFFERING
        ) {
            "Pause"
        } else {
            "Play"
        }
        stateText.text = "State: $currentState, session: ${player.audioSessionId}"
        errorText.text = player.error?.let { "Error ${it.code}: ${it.message}" } ?: "Error: none"
    }

    private fun show(error: com.sonar.core.SonarError) {
        if (!error.isOk) errorText.text = "Error ${error.code}: ${error.message}"
        render()
    }

    private fun label(value: String) = TextView(this).apply { text = value }

    private fun controlButton(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun withSelectedFile(action: (Uri) -> com.sonar.core.SonarError) {
        val uri = selectedUri
        if (uri == null) {
            errorText.text = "Select an audio file first"
        } else {
            show(action(uri))
        }
    }

    private fun togglePlayPause() {
        show(
            when (player.state) {
                PlayerState.PLAYING, PlayerState.BUFFERING -> player.pause()
                PlayerState.PAUSED -> player.resume()
                else -> player.play()
            },
        )
    }

    private fun formatTrackMetadata(uri: Uri): String {
        val fallback = uri.lastPathSegment ?: uri.toString()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown artist"
            val title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: fallback
            val album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (album.isNullOrBlank()) {
                "Track: $artist - $title\nFile: $fallback"
            } else {
                "Track: $artist - $title\nAlbum: $album\nFile: $fallback"
            }
        } catch (_: Throwable) {
            "Track: Unknown artist - $fallback\nFile: $fallback"
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // Metadata display must not affect playback.
            }
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String? =
        extractMetadata(key)?.takeIf { it.isNotBlank() }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun formatMs(value: Long): String {
        if (value < 0) return "--:--"
        val seconds = value / 1000
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    companion object {
        private const val REQUEST_OPEN = 1001
    }
}

package com.sonar.core

data class StreamInfo(
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val sourceBitDepth: Int,
    val codec: String,
)

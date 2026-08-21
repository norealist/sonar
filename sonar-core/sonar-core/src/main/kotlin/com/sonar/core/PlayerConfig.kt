package com.sonar.core

data class PlayerConfig(
    val desiredLatencyMs: Int = 250,
    val writeChunkFrames: Int = 2048,
    val ringDurationMs: Int = 1000,
    val prebufferPercent: Int = 25,
    val maxConsecutiveDecodeErrors: Int = 50,
) {
    init {
        require(desiredLatencyMs > 0)
        require(writeChunkFrames > 0)
        require(ringDurationMs > 0)
        require(prebufferPercent in 1..100)
        require(maxConsecutiveDecodeErrors > 0)
    }
}

package com.sonar.core

data class PlayerConfig(
    val desiredLatencyMs: Int = 40,
    val writeChunkFrames: Int = 2048,
    val ringDurationMs: Int = 200,
    val prebufferPercent: Int = 50,
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

package com.sonar.core

enum class PlayerState(val nativeValue: Int) {
    IDLE(0),
    OPENED(1),
    BUFFERING(2),
    PLAYING(3),
    PAUSED(4),
    COMPLETED(5),
    ERROR(6),
    ;

    companion object {
        fun fromNative(value: Int): PlayerState =
            entries.firstOrNull { it.nativeValue == value } ?: ERROR
    }
}

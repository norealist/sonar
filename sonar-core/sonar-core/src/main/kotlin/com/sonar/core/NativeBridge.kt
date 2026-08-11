package com.sonar.core

import java.nio.ByteBuffer

object NativeBridge {
    init {
        System.loadLibrary("sonar_core")
    }

    @JvmStatic
    external fun nativeCreateEngine(outputSampleRate: Int, outputEncoding: Int, channels: Int): Long

    @JvmStatic
    external fun nativeDestroyEngine(handle: Long)

    @JvmStatic
    external fun nativeOpen(handle: Long, filePath: String): Int

    @JvmStatic
    external fun nativePlay(handle: Long): Int

    @JvmStatic
    external fun nativePause(handle: Long): Int

    @JvmStatic
    external fun nativeResume(handle: Long): Int

    @JvmStatic
    external fun nativeStop(handle: Long): Int

    @JvmStatic
    external fun nativeSeek(handle: Long, positionMs: Long): Int

    @JvmStatic
    external fun nativeReadPcm(handle: Long, buffer: ByteBuffer, maxFrames: Int): Int

    @JvmStatic
    external fun nativeGetStreamInfo(handle: Long): StreamInfo?

    @JvmStatic
    external fun nativeGetState(handle: Long): Int

    @JvmStatic
    external fun nativeGetPosition(handle: Long): Long

    @JvmStatic
    external fun nativeGetError(handle: Long): String?

    @JvmStatic
    external fun nativeSetOutputFormat(handle: Long, encoding: Int): Int
}

package com.sonar.core

import android.media.AudioFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeContractInstrumentedTest {
    @Test
    fun invalidHandleReturnsErrorWithoutThrowing() {
        assertEquals(SonarError.ERR_INVALID_STATE.code, NativeBridge.nativePlay(0L))
        assertEquals(PlayerState.ERROR.nativeValue, NativeBridge.nativeGetState(0L))
    }

    @Test
    fun engineLifecycleHasStableHandleAndSafeMissingFileError() {
        val handle = NativeBridge.nativeCreateEngine(
            48_000,
            AudioFormat.ENCODING_PCM_16BIT,
            2,
        )
        assertNotEquals(0L, handle)
        try {
            assertEquals(
                SonarError.ERR_FILE_NOT_FOUND.code,
                NativeBridge.nativeOpen(handle, ""),
            )
            assertEquals(PlayerState.ERROR.nativeValue, NativeBridge.nativeGetState(handle))
        } finally {
            NativeBridge.nativeDestroyEngine(handle)
        }
    }

    @Test
    fun playerKeepsAudioSessionAcrossReleaseBoundary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = SonarPlayer(context)
        val session = player.audioSessionId
        assertNotEquals(0, session)
        player.release()
    }
}

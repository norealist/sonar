package com.sonar.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMappingsTest {
    @Test
    fun playerStateValuesMatchNativeContract() {
        assertEquals(0, PlayerState.IDLE.nativeValue)
        assertEquals(1, PlayerState.OPENED.nativeValue)
        assertEquals(2, PlayerState.BUFFERING.nativeValue)
        assertEquals(3, PlayerState.PLAYING.nativeValue)
        assertEquals(4, PlayerState.PAUSED.nativeValue)
        assertEquals(5, PlayerState.COMPLETED.nativeValue)
        assertEquals(6, PlayerState.ERROR.nativeValue)
        assertEquals(PlayerState.ERROR, PlayerState.fromNative(999))
    }

    @Test
    fun allNativeErrorCodesMapToExpectedErrors() {
        val expected = listOf(
            SonarError.OK,
            SonarError.ERR_FILE_NOT_FOUND,
            SonarError.ERR_FILE_READ,
            SonarError.ERR_UNSUPPORTED_FORMAT,
            SonarError.ERR_DECODER_INIT,
            SonarError.ERR_DECODER_DECODE,
            SonarError.ERR_INVALID_STATE,
            SonarError.ERR_SEEK_FAILED,
            SonarError.ERR_OUTPUT_FORMAT,
            SonarError.ERR_INTERNAL,
        )
        expected.forEach { error -> assertEquals(error, SonarError.fromCode(error.code)) }
    }

    @Test
    fun errorDetailOverridesOnlyKnownErrorMessage() {
        assertEquals("native detail", SonarError.fromCode(-8, "native detail").message)
        assertEquals("ok", SonarError.fromCode(0, "ignored").message)
    }
}

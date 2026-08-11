package com.sonar.core

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputConfigNegotiatorTest {
    @Test
    fun disabledAlwaysUsesPcm16WithoutProbing() {
        val encodings = mutableListOf<Int>()
        val negotiator = negotiator(31) { encoding, _, _ -> encodings += encoding; true }

        val result = negotiator.negotiate(false, 32, "flac", 96_000, 2)

        assertEquals(AudioFormat.ENCODING_PCM_16BIT, result.encoding)
        assertTrue(encodings.isEmpty())
    }

    @Test
    fun sixteenBitSourceAlwaysUsesPcm16() {
        val encodings = mutableListOf<Int>()
        val negotiator = negotiator(35) { encoding, _, _ -> encodings += encoding; true }

        val result = negotiator.negotiate(true, 16, "wav", 48_000, 2)

        assertEquals(AudioFormat.ENCODING_PCM_16BIT, result.encoding)
        assertTrue(encodings.isEmpty())
    }

    @Test
    fun lossySourceAlwaysUsesPcm16EvenWithHighSourceDepth() {
        val negotiator = negotiator(35) { _, _, _ -> error("lossy input must not probe") }

        assertEquals(
            AudioFormat.ENCODING_PCM_16BIT,
            negotiator.negotiate(true, 24, "mp3", 44_100, 2).encoding,
        )
        assertEquals(
            AudioFormat.ENCODING_PCM_16BIT,
            negotiator.negotiate(true, 32, "opus", 48_000, 2).encoding,
        )
    }

    @Test
    fun api31Uses32Then24ThenFloat() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(31) { encoding, _, _ ->
            attempts += encoding
            encoding == AudioFormat.ENCODING_PCM_FLOAT
        }

        val result = negotiator.negotiate(true, 24, "flac", 96_000, 2)

        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, result.encoding)
        assertEquals(
            listOf(
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_FLOAT,
            ),
            attempts,
        )
    }

    @Test
    fun api31SelectsFirstSupported32BitEncoding() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(31) { encoding, _, _ -> attempts += encoding; true }

        assertEquals(
            AudioFormat.ENCODING_PCM_32BIT,
            negotiator.negotiate(true, 24, "flac", 48_000, 2).encoding,
        )
        assertEquals(listOf(AudioFormat.ENCODING_PCM_32BIT), attempts)
    }

    @Test
    fun api31SelectsPacked24After32BitProbeFails() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(31) { encoding, _, _ ->
            attempts += encoding
            encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED
        }

        assertEquals(
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            negotiator.negotiate(true, 24, "flac", 48_000, 2).encoding,
        )
        assertEquals(
            listOf(AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            attempts,
        )
    }

    @Test
    fun api31FallsBackToPcm16AfterAllHiResProbesFail() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(31) { encoding, _, _ -> attempts += encoding; false }

        val result = negotiator.negotiate(true, 32, "wav", 48_000, 1)

        assertEquals(AudioFormat.ENCODING_PCM_16BIT, result.encoding)
        assertEquals(
            listOf(
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_FLOAT,
            ),
            attempts,
        )
    }

    @Test
    fun api24To30UsesFloatThenPcm16() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(30) { encoding, _, _ -> attempts += encoding; false }

        val result = negotiator.negotiate(true, 24, "flac", 44_100, 2)

        assertEquals(AudioFormat.ENCODING_PCM_16BIT, result.encoding)
        assertEquals(listOf(AudioFormat.ENCODING_PCM_FLOAT), attempts)
    }

    @Test
    fun api24To30SelectsFloatWhenSupported() {
        val attempts = mutableListOf<Int>()
        val negotiator = negotiator(24) { encoding, _, _ -> attempts += encoding; true }

        assertEquals(
            AudioFormat.ENCODING_PCM_FLOAT,
            negotiator.negotiate(true, 24, "flac", 44_100, 1).encoding,
        )
        assertEquals(listOf(AudioFormat.ENCODING_PCM_FLOAT), attempts)
    }

    @Test
    fun runtimeFallbackChainEndsWithPcm16() {
        val negotiator = negotiator(35) { _, _, _ -> false }

        assertEquals(
            listOf(
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_24BIT_PACKED,
                AudioFormat.ENCODING_PCM_FLOAT,
                AudioFormat.ENCODING_PCM_16BIT,
            ),
            negotiator.candidateEncodings(true, 24, "flac"),
        )
    }

    @Test
    fun probeResultsAreCachedUntilNextOpen() {
        var calls = 0
        val negotiator = negotiator(31) { _, _, _ -> calls++; true }

        negotiator.negotiate(true, 24, "flac", 48_000, 2)
        negotiator.negotiate(true, 24, "flac", 48_000, 2)
        assertEquals(1, calls)

        negotiator.beginOpen()
        negotiator.negotiate(true, 24, "flac", 48_000, 2)
        assertEquals(2, calls)
    }

    @Test
    fun outputMappingSeparatesFloatFromInteger32() {
        assertEquals(32, OutputConfig(AudioFormat.ENCODING_PCM_32BIT).integerBitDepth)
        assertEquals(null, OutputConfig(AudioFormat.ENCODING_PCM_FLOAT).integerBitDepth)
        assertEquals("32-bit float PCM", OutputConfig(AudioFormat.ENCODING_PCM_FLOAT).displayName)
    }

    private fun negotiator(api: Int, probe: EncodingProbe): OutputConfigNegotiator =
        OutputConfigNegotiator(apiLevel = api, probe = probe)
}

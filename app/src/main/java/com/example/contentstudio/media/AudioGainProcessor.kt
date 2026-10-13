package com.example.contentstudio.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Explicit per-track linear gain; no normalization, pitch change or full-file PCM buffering. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AudioGainProcessor(private val gain: Float) : BaseAudioProcessor() {
    init { require(gain.isFinite() && gain in 0f..1f) }
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        require(inputAudioFormat.encoding == C.ENCODING_PCM_16BIT)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        require(inputBuffer.remaining() % 2 == 0)
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        while (inputBuffer.hasRemaining()) output.putShort((inputBuffer.short * gain).roundToInt().coerceIn(-32768, 32767).toShort())
        output.flip()
    }
}

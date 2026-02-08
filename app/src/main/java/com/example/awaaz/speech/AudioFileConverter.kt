package com.example.awaaz.speech

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder

/**
 * Converts M4A (AAC) to 16 kHz mono PCM WAV for Vosk recognition.
 * Uses MediaExtractor + MediaCodec to decode, then resamples to 16 kHz and writes WAV.
 */
object AudioFileConverter {

    private const val TARGET_SAMPLE_RATE = 16000
    private const val WAV_HEADER_SIZE = 44

    /**
     * Converts an M4A file to a 16 kHz mono WAV file. The output file is created in the same
     * directory as the input with extension replaced by .wav, or in [outputDir] if provided.
     *
     * @param m4aPath Path to the .m4a file (e.g. from [VoiceRecorder]).
     * @param outputDir Optional directory for the WAV file; if null, same dir as input.
     * @return The path to the created WAV file, or null on failure.
     */
    @JvmStatic
    fun m4aTo16kWav(m4aPath: String, outputDir: File? = null): String? {
        val inputFile = File(m4aPath)
        if (!inputFile.exists()) return null
        val outDir = outputDir ?: inputFile.parentFile ?: return null
        val wavPath = File(outDir, inputFile.nameWithoutExtension + "_16k.wav").absolutePath
        return try {
            m4aTo16kWav(File(m4aPath), File(wavPath))
            wavPath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts [m4aFile] to a 16 kHz mono WAV file at [wavFile].
     * @throws Exception if decoding or writing fails.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun m4aTo16kWav(m4aFile: File, wavFile: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(m4aFile.absolutePath)
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                break
            }
        }
        if (trackIndex < 0) throw IllegalArgumentException("No audio track in $m4aFile")
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("No MIME")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val bufferInfo = MediaCodec.BufferInfo()
        val pcmChunks = mutableListOf<ByteArray>()
        var inputDone = false
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (sampleRate <= 0) sampleRate = 44100
        while (true) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: break
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* continue */ }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: break
                    if (bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()
                        pcmChunks.add(chunk)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val pcm44 = pcmChunks.flatMap { it.toList() }.toByteArray()
        val pcm16k = resampleTo16k(pcm44, sampleRate, channelCount)
        writeWavFile(wavFile, pcm16k, TARGET_SAMPLE_RATE, 1)
    }

    private fun resampleTo16k(pcmBytes: ByteArray, sourceRate: Int, channels: Int): ByteArray {
        val samples = pcmBytes.size / 2
        val frames = samples / channels
        if (frames == 0) return byteArrayOf()
        val outFrames = (frames * TARGET_SAMPLE_RATE.toLong() / sourceRate).toInt().coerceAtLeast(1)
        val out = ByteArray(outFrames * 2)
        val order = ByteOrder.nativeOrder()
        for (i in 0 until outFrames) {
            val srcFrame = i * sourceRate.toDouble() / TARGET_SAMPLE_RATE
            val frameIdx = srcFrame.toInt().coerceIn(0, frames - 1)
            val byteOffset = frameIdx * channels * 2
            val sample = getSample16(pcmBytes, byteOffset, order)
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = (sample shr 8 and 0xFF).toByte()
        }
        return out
    }

    private fun getSample16(b: ByteArray, offset: Int, order: ByteOrder): Int {
        if (offset + 2 > b.size) return 0
        return if (order == ByteOrder.LITTLE_ENDIAN) {
            (b[offset].toInt() and 0xFF) or (b[offset + 1].toInt() shl 8)
        } else {
            (b[offset].toInt() shl 8) or (b[offset + 1].toInt() and 0xFF)
        }
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val dataSize = pcmData.size
        val byteRate = sampleRate * channels * 2
        val blockAlign = (channels * 2).toShort()
        val totalSize = 36 + dataSize
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray())
            out.write(intToBytes(totalSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToBytes(16))
            out.write(shortToBytes(1))
            out.write(shortToBytes(channels.toShort()))
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(blockAlign))
            out.write(shortToBytes(16))
            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            out.write(pcmData)
        }
    }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(),
        (v shr 24 and 0xFF).toByte()
    )

    private fun shortToBytes(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        (v.toInt() shr 8 and 0xFF).toByte()
    )
}

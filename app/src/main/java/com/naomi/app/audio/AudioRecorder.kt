package com.naomi.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

class AudioRecorder(
    private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private var startTimeMs = 0L

    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

    fun startRecording(coroutineScope: CoroutineScope): File {
        val tempFile = File(context.cacheDir, "naomi_capture_${System.currentTimeMillis()}.wav")
        this.outputFile = tempFile
        this.startTimeMs = System.currentTimeMillis()

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val rawBuffer = ByteArray(bufferSize)
                val fos = FileOutputStream(tempFile)
                // Write placeholder 44-byte WAV header
                fos.write(ByteArray(44))

                var totalAudioLen = 0L

                while (isRecording && isActive) {
                    val read = audioRecord?.read(rawBuffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        fos.write(rawBuffer, 0, read)
                        totalAudioLen += read

                        // Calculate RMS Amplitude
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            if (i + 1 < read) {
                                val sample = (rawBuffer[i + 1].toInt() shl 8) or (rawBuffer[i].toInt() and 0xFF)
                                sum += (sample * sample).toDouble()
                            }
                        }
                        val rms = sqrt(sum / (read / 2.0))
                        val normalized = (rms / 32767.0).toFloat().coerceIn(0.05f, 1.0f)
                        _amplitudeFlow.value = normalized
                    }
                }

                fos.flush()
                fos.close()

                // Fix WAV header with actual lengths
                if (tempFile.exists() && totalAudioLen > 0) {
                    writeWavHeader(tempFile, totalAudioLen, sampleRate, 1, 16)
                }
            }
        } catch (_: SecurityException) {
            // Audio permission missing or interrupted
        }

        return tempFile
    }

    fun stopRecording(): Pair<File?, Long> {
        isRecording = false
        val duration = System.currentTimeMillis() - startTimeMs
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        recordingJob?.cancel()
        _amplitudeFlow.value = 0f

        return Pair(outputFile, duration)
    }

    private fun writeWavHeader(file: File, totalAudioLen: Long, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * (bitsPerSample / 8)).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.write(header)
        raf.close()
    }
}

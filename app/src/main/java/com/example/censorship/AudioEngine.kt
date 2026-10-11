package com.example.censorship

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

class AudioEngine(
    private val context: Context, 
    private val mediaProjection: MediaProjection, 
    private val badWords: List<String>
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var voskModel: Model? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        
        scope.launch {
            val modelPath = downloadAndUnpackModel()
            if (modelPath != null) {
                try {
                    voskModel = Model(modelPath)
                    startRecording()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun downloadAndUnpackModel(): String? {
        val modelDir = File(context.filesDir, "vosk-model")
        
        // If it's already downloaded, just return the path!
        if (modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true) {
            return modelDir.listFiles()!![0].absolutePath
        }
        
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Downloading Audio AI Model (40MB)... This only happens once!", Toast.LENGTH_LONG).show()
        }
        
        try {
            val url = URL("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
            val connection = url.openConnection()
            connection.connect()
            
            val input = BufferedInputStream(connection.getInputStream())
            val zis = ZipInputStream(input)
            var entry = zis.nextEntry
            
            while (entry != null) {
                val file = File(modelDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    val out = FileOutputStream(file)
                    val buffer = ByteArray(4096)
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        out.write(buffer, 0, count)
                    }
                    out.close()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
            
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Audio AI Engine is now active!", Toast.LENGTH_SHORT).show()
            }
            return modelDir.listFiles()!![0].absolutePath
            
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun startRecording() {
        if (voskModel == null) return
        
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
                
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
                
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .build()
                
            audioRecord?.startRecording()
            isRecording = true
            
            val recognizer = Recognizer(voskModel, 16000f)
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            val buffer = ShortArray(4096)
            
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val result = recognizer.result.lowercase()
                        if (badWords.any { result.contains(it) }) {
                            toneGen.startTone(ToneGenerator.TONE_SUP_DIAL, 1000)
                        }
                    } else {
                        // Check partial results so we can bleep it instantly before they finish speaking!
                        val partial = recognizer.partialResult.lowercase()
                        if (badWords.any { partial.contains(it) }) {
                            toneGen.startTone(ToneGenerator.TONE_SUP_DIAL, 1000)
                            recognizer.reset() 
                        }
                    }
                }
            }
            recognizer.close()
            toneGen.release()
        } catch (e: SecurityException) {
            // Missing RECORD_AUDIO permission
        }
    }
    
    fun stop() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        scope.cancel()
    }
}

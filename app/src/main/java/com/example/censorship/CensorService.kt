package com.example.censorship

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ImageReader
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CensorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // Audio Capture Variables
    private var audioRecord: AudioRecord? = null
    private var isAudioRecording = false
    private lateinit var toneGenerator: ToneGenerator
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    
    private var badWords = emptyList<String>()
    private val nsfwLabels = listOf("weapon", "gun", "knife", "blood", "violence", "underwear", "swimwear", "flesh")
    
    private var isProcessing = false
    private var isBatterySaverMode = true
    private var lastCaptureTime = 0L
    private lateinit var settings: SettingsManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupOverlayWindow()
        
        settings = SettingsManager(this)
        badWords = settings.getBadWords().map { normalizeText(it) }
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100) // 100% Volume Bleep
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "CENSOR_CHANNEL")
            .setContentTitle("CensorAI is Active")
            .setContentText("Listening and scanning for bad content...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData: Intent? = intent?.getParcelableExtra("RESULT_DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, resultData)
            
            // Start Visual and Audio Engines
            setupVirtualDisplay()
            startAudioCapture()
        }

        return START_NOT_STICKY
    }

    // THE AUDIO BLEEPER ENGINE
    private fun startAudioCapture() {
        // AudioPlaybackCapture is only available on Android 10 (Q) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!settings.isAudioBleeperOn) return
        
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA) // Only capture Videos/Games, not phone calls
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
            isAudioRecording = true
            
            Thread {
                val buffer = ShortArray(1024)
                while (isAudioRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        // ARCHITECTURE PLACEHOLDER: 
                        // This is where we feed `buffer` into the on-device Vosk Speech-To-Text AI.
                        // For demonstration, if Vosk returned a bad word:
                        val detectedWord = runVoskAiPlaceholder(buffer)
                        
                        if (badWords.any { detectedWord.contains(it) }) {
                            // BLEEP IT OUT!
                            // Plays a loud censorship tone (like on TV) to drown out the bad word!
                            toneGenerator.startTone(ToneGenerator.TONE_SUP_DIAL, 1000) // 1 second duration
                        }
                    }
                }
            }.start()
            
        } catch (e: SecurityException) {
            // Missing RECORD_AUDIO permission
        }
    }
    
    private fun runVoskAiPlaceholder(audioBuffer: ShortArray): String {
        // In a production app, the 50MB Vosk Model processes the audioBuffer here.
        // Returns empty string so we don't randomly bleep.
        return ""
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(" ", "").replace("4", "a").replace("@", "a")
            .replace("0", "o").replace("3", "e").replace("1", "i").replace("$", "s")
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupVirtualDisplay()
    }

    private fun setupVirtualDisplay() {
        virtualDisplay?.release()
        imageReader?.close()

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val currentTime = System.currentTimeMillis()
            
            if (isBatterySaverMode && (currentTime - lastCaptureTime) < 500) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            if (isProcessing) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isProcessing = true
            lastCaptureTime = currentTime
            
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = android.graphics.Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                val croppedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
                val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                
                val boxesToDraw = mutableListOf<Rect>()
                
                if (settings.isVisualCensorOn) {
                    textRecognizer.process(inputImage).addOnSuccessListener { visionText ->
                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {
                                    val text = normalizeText(element.text)
                                    if (badWords.any { text.contains(it) }) {
                                        element.boundingBox?.let { boxesToDraw.add(it) }
                                    }
                                }
                            }
                        }
                        
                        if (settings.isImageCensorOn) {
                            imageLabeler.process(inputImage).addOnSuccessListener { labels ->
                                for (label in labels) {
                                    if (label.confidence > 0.7f && nsfwLabels.any { label.text.lowercase().contains(it) }) {
                                        boxesToDraw.add(Rect(0, 0, width, height))
                                        break
                                    }
                                }
                                overlayView.updateBoxes(boxesToDraw)
                                finishProcessing(image)
                            }.addOnFailureListener { finishProcessing(image) }
                        } else {
                            overlayView.updateBoxes(boxesToDraw)
                            finishProcessing(image)
                        }
                    }.addOnFailureListener { finishProcessing(image) }
                } else if (settings.isImageCensorOn) {
                    imageLabeler.process(inputImage).addOnSuccessListener { labels ->
                        for (label in labels) {
                            if (label.confidence > 0.7f && nsfwLabels.any { label.text.lowercase().contains(it) }) {
                                boxesToDraw.add(Rect(0, 0, width, height))
                                break
                            }
                        }
                        overlayView.updateBoxes(boxesToDraw)
                        finishProcessing(image)
                    }.addOnFailureListener { finishProcessing(image) }
                } else {
                    finishProcessing(image)
                }
            } catch (e: Exception) {
                finishProcessing(image)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun finishProcessing(image: android.media.Image) {
        isProcessing = false
        image.close() 
    }

    override fun onDestroy() {
        super.onDestroy()
        isAudioRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        toneGenerator.release()
        
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        if (::windowManager.isInitialized && ::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CENSOR_CHANNEL", "Censor Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

class OverlayView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private var boxesToDraw = listOf<Rect>()

    fun updateBoxes(boxes: List<Rect>) {
        boxesToDraw = boxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxesToDraw) {
            canvas.drawRect(
                (box.left - 10).toFloat(), 
                (box.top - 10).toFloat(), 
                (box.right + 10).toFloat(), 
                (box.bottom + 10).toFloat(), 
                paint
            )
        }
    }
}

package com.example.censorship

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class CensorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // The Visual AI Engines
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )
    
    private var badWords = emptyList<String>()
    private val nsfwLabels = listOf("weapon", "gun", "knife", "blood", "violence", "underwear", "swimwear", "flesh")
    
    private var isProcessing = false
    private lateinit var settings: SettingsManager
    
    // Background thread for heavy AI processing
    private val aiThread = HandlerThread("AIEngine").apply { start() }
    private val aiHandler = Handler(aiThread.looper)
    
    // The Audio Bleeper
    private var audioEngine: AudioEngine? = null
    
    // Memory to prevent blinking
    private val currentCensors = mutableListOf<OverlayView.CensorData>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupOverlayWindow()
        
        settings = SettingsManager(this)
        badWords = settings.getBadWords().map { normalizeText(it) }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "CENSOR_CHANNEL")
            .setContentTitle("CensorAI is Active")
            .setContentText("Visual protection running...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(1, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData: Intent? = intent?.getParcelableExtra("RESULT_DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, resultData)
            setupVirtualDisplay()
            
            // Start the Audio Bleeper Engine
            if (settings.isAudioBleeperOn) {
                audioEngine = AudioEngine(this, mediaProjection!!, badWords)
                audioEngine?.start()
            }
        }
        return START_NOT_STICKY
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
            "ScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            
            if (isProcessing) {
                image.close()
                return@setOnImageAvailableListener
            }
            
            isProcessing = true
            
            aiHandler.post {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val rawBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val bitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                    
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val newFoundBoxes = mutableListOf<OverlayView.CensorData>()
                    
                    // 1. Text Engine
                    if (settings.isVisualCensorOn) {
                        val textResult = Tasks.await(textRecognizer.process(inputImage))
                        for (block in textResult.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {
                                    val text = normalizeText(element.text)
                                    if (badWords.any { text.contains(it) }) {
                                        element.boundingBox?.let { rect ->
                                            newFoundBoxes.add(OverlayView.CensorData(rect, createPixelatedBlur(bitmap, rect)))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 2. Targeted Image Engine
                    if (settings.isImageCensorOn) {
                        // Find all prominent objects/photos on the screen
                        val objects = Tasks.await(objectDetector.process(inputImage))
                        for (obj in objects) {
                            val rect = obj.boundingBox
                            if (rect.width() > 50 && rect.height() > 50) { // Ignore tiny icons
                                val crop = createCrop(bitmap, rect)
                                if (crop != null) {
                                    // Scan just that specific photo for NSFW content
                                    val labels = Tasks.await(imageLabeler.process(InputImage.fromBitmap(crop, 0)))
                                    if (labels.any { it.confidence > 0.65f && nsfwLabels.any { bad -> it.text.lowercase().contains(bad) } }) {
                                        newFoundBoxes.add(OverlayView.CensorData(rect, createPixelatedBlur(bitmap, rect)))
                                    }
                                }
                            }
                        }
                    }
                    
                    // 3. Anti-Blink Memory System
                    val currentTime = System.currentTimeMillis()
                    
                    // Remove boxes that haven't been seen in 1000ms (1 second memory)
                    currentCensors.removeAll { currentTime - it.lastSeenTime > 1000 }
                    
                    // Add new boxes, or refresh existing ones
                    for (newBox in newFoundBoxes) {
                        // Check if this box is already in memory (close coordinates)
                        val existing = currentCensors.find { Rect.intersects(it.rect, newBox.rect) }
                        if (existing != null) {
                            existing.rect = newBox.rect
                            existing.blurredBitmap = newBox.blurredBitmap
                            existing.lastSeenTime = currentTime
                        } else {
                            newBox.lastSeenTime = currentTime
                            currentCensors.add(newBox)
                        }
                    }
                    
                    // Push to UI
                    Handler(Looper.getMainLooper()).post {
                        overlayView.updateBoxes(currentCensors.toList())
                    }
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                    isProcessing = false
                }
            }
        }, aiHandler)
    }

    private fun createCrop(source: Bitmap, rect: Rect): Bitmap? {
        return try {
            // Ensure bounds don't crash the cropper
            val left = rect.left.coerceAtLeast(0)
            val top = rect.top.coerceAtLeast(0)
            val right = rect.right.coerceAtMost(source.width)
            val bottom = rect.bottom.coerceAtMost(source.height)
            if (right > left && bottom > top) {
                Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            } else null
        } catch (e: Exception) { null }
    }

    // Mathematical Frosted Glass / Pixelation effect
    private fun createPixelatedBlur(source: Bitmap, rect: Rect): Bitmap? {
        val crop = createCrop(source, rect) ?: return null
        return try {
            // Scale down to 1/15th size to destroy the details
            val tinyWidth = (crop.width / 15).coerceAtLeast(1)
            val tinyHeight = (crop.height / 15).coerceAtLeast(1)
            val tiny = Bitmap.createScaledBitmap(crop, tinyWidth, tinyHeight, false)
            
            // Scale back up to create a heavy blocky "Minecraft" blur effect 
            Bitmap.createScaledBitmap(tiny, crop.width, crop.height, false)
        } catch (e: Exception) { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine?.stop()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        aiThread.quitSafely()
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
    data class CensorData(
        var rect: Rect,
        var blurredBitmap: Bitmap?,
        var lastSeenTime: Long = 0
    )
    
    private val paint = Paint()
    private var boxesToDraw = listOf<CensorData>()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            // Force continuous redraws to smoothly fade out expired boxes
            invalidate()
            handler.postDelayed(this, 100)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(refreshRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(refreshRunnable)
    }

    fun updateBoxes(boxes: List<CensorData>) {
        boxesToDraw = boxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentTime = System.currentTimeMillis()
        
        for (box in boxesToDraw) {
            // Only draw if it hasn't expired
            if (currentTime - box.lastSeenTime <= 1000) {
                if (box.blurredBitmap != null) {
                    // Draw the beautiful frosted glass / pixelated bitmap!
                    canvas.drawBitmap(box.blurredBitmap!!, box.rect.left.toFloat(), box.rect.top.toFloat(), paint)
                } else {
                    // Fallback to black if blur fails
                    paint.color = Color.BLACK
                    canvas.drawRect(box.rect, paint)
                }
            }
        }
    }
}

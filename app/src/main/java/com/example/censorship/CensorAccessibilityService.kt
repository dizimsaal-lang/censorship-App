package com.example.censorship

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CensorAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: AccessibilityOverlayView
    
    // We will load this list from the database when the service starts
    private var badWords = emptyList<String>()
    
    private val currentBoxes = mutableListOf<Rect>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        setupOverlayWindow()
        
        // Pull the custom words from the user's local database
        val settings = SettingsManager(this)
        badWords = settings.getBadWords().map { normalizeText(it) }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(" ", "")
            .replace("4", "a")
            .replace("@", "a")
            .replace("0", "o")
            .replace("3", "e")
            .replace("1", "i")
            .replace("$", "s")
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = AccessibilityOverlayView(this)

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        
        currentBoxes.clear()
        searchForBadWords(rootNode)
        
        // BUG FIX: Prevent Memory Leak
        rootNode.recycle() 
        
        overlayView.updateBoxes(currentBoxes)
    }

    private fun searchForBadWords(node: AccessibilityNodeInfo) {
        if (node.text != null) {
            val originalText = node.text.toString()
            val normalized = normalizeText(originalText)
            
            for (badWord in badWords) {
                val startIndex = normalized.indexOf(badWord)
                if (startIndex != -1) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    
                    // BUG FIX: The "Whole Paragraph" bug. 
                    // Instead of blacking out the entire message, we calculate roughly where the word is!
                    val totalLength = normalized.length.coerceAtLeast(1)
                    val widthPerChar = rect.width() / totalLength
                    
                    val wordLeft = rect.left + (startIndex * widthPerChar)
                    val wordRight = wordLeft + (badWord.length * widthPerChar)
                    
                    // We only block out the specific X coordinates of the bad word!
                    val exactWordRect = Rect(wordLeft, rect.top, wordRight, rect.bottom)
                    currentBoxes.add(exactWordRect)
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                searchForBadWords(child)
                child.recycle() // Prevent memory leaks
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}

class AccessibilityOverlayView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private var boxesToDraw = listOf<Rect>()

    fun updateBoxes(boxes: List<Rect>) {
        boxesToDraw = boxes.toList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxesToDraw) {
            canvas.drawRect(
                (box.left - 10).toFloat(), 
                (box.top - 5).toFloat(), 
                (box.right + 10).toFloat(), 
                (box.bottom + 5).toFloat(), 
                paint
            )
        }
    }
}

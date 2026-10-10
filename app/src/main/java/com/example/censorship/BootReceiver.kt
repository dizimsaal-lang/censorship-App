package com.example.censorship

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            
            // Check if the user wants the app to be running
            if (settings.isVisualCensorOn || settings.isAudioBleeperOn) {
                
                // Note: The AccessibilityService (Text Engine) automatically restarts itself on boot!
                // However, Android security blocks us from silently starting the Screen Recorder (Image Engine)
                // without the user explicitly clicking "Start" on a pop-up.
                
                // So, we trigger a notification reminding the user/child that the app is active
                showBootNotification(context)
            }
        }
    }

    private fun showBootNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("BOOT_CHANNEL", "System Status", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "BOOT_CHANNEL")
            .setContentTitle("CensorAI Started")
            .setContentText("Text protection is active. Open app to enable Image protection.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true) // Makes it hard to swipe away
            .build()

        manager.notify(999, notification)
    }
}

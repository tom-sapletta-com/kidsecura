package com.parentalcontrol.mvp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.parentalcontrol.mvp.EventHistoryActivity
import com.parentalcontrol.mvp.R

class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_ALERTS = "alerts_channel"
        private const val NOTIFICATION_ID_BASE = 1000
        private var notificationIdCounter = NOTIFICATION_ID_BASE
    }
    
    init {
        createAlertChannel()
    }
    
    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Powiadomienia o wykrytych zagrożeniach"
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun sendAlertNotification(title: String, message: String) {
        val intent = Intent(context, EventHistoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(notificationIdCounter++, notification)
        }
    }
}

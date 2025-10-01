// MonitoringEvent.kt
package com.parentalcontrol.mvp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_events")
data class MonitoringEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val confidence: Float,
    val description: String,
    val ocrText: String? = null,
    val screenshotPath: String? = null,
    val isReviewed: Boolean = false,
    val isFalsePositive: Boolean = false
)

// EventDao.kt
package com.parentalcontrol.mvp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: MonitoringEvent)
    
    @Update
    suspend fun updateEvent(event: MonitoringEvent)
    
    @Delete
    suspend fun deleteEvent(event: MonitoringEvent)
    
    @Query("SELECT * FROM monitoring_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<MonitoringEvent>>
    
    @Query("SELECT * FROM monitoring_events WHERE isReviewed = 0 ORDER BY timestamp DESC")
    fun getUnreviewedEvents(): Flow<List<MonitoringEvent>>
    
    @Query("SELECT * FROM monitoring_events WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getEventsSince(since: Long): Flow<List<MonitoringEvent>>
    
    @Query("UPDATE monitoring_events SET isReviewed = 1 WHERE id = :eventId")
    suspend fun markAsReviewed(eventId: Long)
    
    @Query("UPDATE monitoring_events SET isFalsePositive = 1 WHERE id = :eventId")
    suspend fun markAsFalsePositive(eventId: Long)
    
    @Query("DELETE FROM monitoring_events WHERE timestamp < :before")
    suspend fun deleteOldEvents(before: Long)
}

// MonitoringDatabase.kt
package com.parentalcontrol.mvp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MonitoringEvent::class],
    version = 1,
    exportSchema = false
)
abstract class MonitoringDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    
    companion object {
        @Volatile
        private var INSTANCE: MonitoringDatabase? = null
        
        fun getInstance(context: Context): MonitoringDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MonitoringDatabase::class.java,
                    "monitoring_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// PreferencesManager.kt
package com.parentalcontrol.mvp.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "monitoring_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_CAPTURE_INTERVAL = "capture_interval"
        private const val KEY_CROP_BOTTOM = "crop_bottom"
        private const val KEY_LOCAL_ANALYSIS = "local_analysis"
        private const val KEY_SAVE_SCREENSHOTS = "save_screenshots"
        private const val KEY_PARENT_PHONE = "parent_phone"
        private const val KEY_PARENT_EMAIL = "parent_email"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_DEVICE_PAIRED = "device_paired"
    }
    
    fun getCaptureInterval(): Int = prefs.getInt(KEY_CAPTURE_INTERVAL, 2)
    fun setCaptureInterval(interval: Int) = prefs.edit().putInt(KEY_CAPTURE_INTERVAL, interval).apply()
    
    fun isCropBottomEnabled(): Boolean = prefs.getBoolean(KEY_CROP_BOTTOM, true)
    fun setCropBottomEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_CROP_BOTTOM, enabled).apply()
    
    fun isLocalAnalysisEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_ANALYSIS, true)
    fun setLocalAnalysisEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOCAL_ANALYSIS, enabled).apply()
    
    fun isSaveScreenshotsEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_SCREENSHOTS, false)
    fun setSaveScreenshotsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SAVE_SCREENSHOTS, enabled).apply()
    
    fun getParentPhone(): String? = prefs.getString(KEY_PARENT_PHONE, null)
    fun setParentPhone(phone: String) = prefs.edit().putString(KEY_PARENT_PHONE, phone).apply()
    
    fun getParentEmail(): String? = prefs.getString(KEY_PARENT_EMAIL, null)
    fun setParentEmail(email: String) = prefs.edit().putString(KEY_PARENT_EMAIL, email).apply()
    
    fun getPairingCode(): String? = prefs.getString(KEY_PAIRING_CODE, null)
    fun setPairingCode(code: String) = prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    
    fun isDevicePaired(): Boolean = prefs.getBoolean(KEY_DEVICE_PAIRED, false)
    fun setDevicePaired(paired: Boolean) = prefs.edit().putBoolean(KEY_DEVICE_PAIRED, paired).apply()
}

// ImageUtils.kt
package com.parentalcontrol.mvp.utils

import android.graphics.Bitmap

object ImageUtils {
    
    fun cropBottomHalf(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val width = bitmap.width
        val startY = height / 2
        val croppedHeight = height - startY
        
        return Bitmap.createBitmap(bitmap, 0, startY, width, croppedHeight)
    }
    
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth) {
            return bitmap
        }
        
        val aspectRatio = height.toFloat() / width.toFloat()
        val newWidth = maxWidth
        val newHeight = (maxWidth * aspectRatio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    fun blurFaces(bitmap: Bitmap): Bitmap {
        // TODO: Implement face detection and blurring
        // Use ML Kit Face Detection
        return bitmap
    }
}

// NotificationHelper.kt
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
                "Alerty bezpieczeństwa",
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
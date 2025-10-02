package com.parentalcontrol.mvp.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FileLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "FileLogger"
        private const val LOG_DIR_NAME = "KidSecura"
        private const val LOG_FILE_PREFIX = "monitoring_log_"
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10 MB
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * Pobiera folder Downloads
     */
    private fun getLogDirectory(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logDir = File(downloadsDir, LOG_DIR_NAME)
            
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            if (logDir.exists() && logDir.canWrite()) {
                logDir
            } else {
                Log.e(TAG, "Cannot access log directory: ${logDir.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting log directory", e)
            null
        }
    }
    
    /**
     * Pobiera plik logu dla dzisiejszej daty
     */
    private fun getLogFile(): File? {
        val logDir = getLogDirectory() ?: return null
        val dateStr = fileDateFormat.format(Date())
        return File(logDir, "${LOG_FILE_PREFIX}${dateStr}.txt")
    }
    
    /**
     * Zapisuje log do pliku
     */
    suspend fun log(message: String) {
        withContext(Dispatchers.IO) {
            try {
                val logFile = getLogFile()
                if (logFile == null) {
                    Log.e(TAG, "Cannot get log file")
                    return@withContext
                }
                
                // Sprawdź rozmiar pliku i rotuj jeśli za duży
                if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                    rotateLogFile(logFile)
                }
                
                val timestamp = dateFormat.format(Date())
                val logEntry = "[$timestamp] $message\n"
                
                FileWriter(logFile, true).use { writer ->
                    writer.append(logEntry)
                    writer.flush()
                }
                
                Log.d(TAG, "Log written: $message")
                
            } catch (e: IOException) {
                Log.e(TAG, "Error writing log", e)
            }
        }
    }
    
    /**
     * Zapisuje log o wykryciu podejrzanej treści
     */
    suspend fun logSuspiciousContent(
        appName: String,
        packageName: String,
        detectionType: String,
        description: String,
        confidence: Float,
        extractedText: String? = null
    ) {
        val logMessage = buildString {
            append("ALERT: Suspicious content detected\n")
            append("  App: $appName ($packageName)\n")
            append("  Type: $detectionType\n")
            append("  Description: $description\n")
            append("  Confidence: ${(confidence * 100).toInt()}%")
            if (!extractedText.isNullOrBlank()) {
                append("\n  Extracted text: ${extractedText.take(200)}") // Ogranicz do 200 znaków
            }
        }
        log(logMessage)
    }
    
    /**
     * Zapisuje log o aktywności aplikacji
     */
    suspend fun logAppActivity(appName: String, packageName: String) {
        log("APP_ACTIVITY: $appName ($packageName)")
    }
    
    /**
     * Zapisuje log o starcie/stopie serwisu
     */
    suspend fun logServiceEvent(event: String) {
        log("SERVICE: $event")
    }
    
    /**
     * Zapisuje szczegółowy log dla debugowania
     */
    suspend fun logDebug(tag: String, message: String, includeTimestamp: Boolean = true) {
        val prefsManager = PreferencesManager(context)
        if (prefsManager.getBoolean("verbose_logging_enabled", true)) {
            val logMsg = if (includeTimestamp) {
                "DEBUG [$tag]: $message"
            } else {
                "DEBUG: $message"
            }
            log(logMsg)
            prefsManager.incrementLogCount()
        }
    }
    
    /**
     * Zapisuje wykrycie słowa kluczowego
     */
    suspend fun logKeywordDetection(
        keyword: String,
        context: String,
        appName: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val logMessage = buildString {
            append("🔍 KEYWORD DETECTED\n")
            append("  Keyword: '$keyword'\n")
            append("  Context: ${context.take(100)}\n")
            append("  App: $appName\n")
            append("  Time: ${dateFormat.format(Date(timestamp))}")
        }
        log(logMessage)
        
        val prefsManager = PreferencesManager(context)
        prefsManager.incrementLogCount()
        
        // Send debug notification if enabled
        if (prefsManager.getBoolean("debug_notifications_enabled", false)) {
            sendDebugNotification(keyword, appName)
        }
    }
    
    /**
     * Wysyła powiadomienie debugowania
     */
    private fun sendDebugNotification(keyword: String, appName: String) {
        try {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showDebugNotification(
                "🔍 Wykryto: '$keyword'",
                "W aplikacji: $appName"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending debug notification", e)
        }
    }
    
    /**
     * Zapisuje informację o lokalizacji
     */
    suspend fun logLocation(latitude: Double, longitude: Double, accuracy: Float) {
        log("📍 LOCATION: Lat=$latitude, Lon=$longitude, Accuracy=${accuracy}m")
    }
    
    /**
     * Zapisuje czas ekranowy
     */
    suspend fun logScreenTime(durationMs: Long, appName: String) {
        val minutes = durationMs / (1000 * 60)
        log("⏱️ SCREEN_TIME: $appName - ${minutes}min")
    }
    
    /**
     * Zapisuje zdarzenie sesji dziecka
     */
    suspend fun logChildSession(action: String, durationMinutes: Int = 0) {
        val msg = if (durationMinutes > 0) {
            "👶 CHILD_SESSION: $action (Duration: ${durationMinutes}min)"
        } else {
            "👶 CHILD_SESSION: $action"
        }
        log(msg)
    }
    
    /**
     * Rotuje plik logu jeśli jest za duży
     */
    private fun rotateLogFile(logFile: File) {
        try {
            val backupFile = File(logFile.parent, "${logFile.name}.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
            Log.d(TAG, "Log file rotated")
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating log file", e)
        }
    }
    
    /**
     * Czyści stare logi (starsze niż 30 dni)
     */
    suspend fun cleanOldLogs() {
        withContext(Dispatchers.IO) {
            try {
                val logDir = getLogDirectory() ?: return@withContext
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                
                logDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < thirtyDaysAgo) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old log file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning old logs", e)
            }
        }
    }
    
    /**
     * Sprawdza czy można zapisywać do pliku
     */
    fun canWriteToStorage(): Boolean {
        return try {
            val state = Environment.getExternalStorageState()
            state == Environment.MEDIA_MOUNTED
        } catch (e: Exception) {
            false
        }
    }
}

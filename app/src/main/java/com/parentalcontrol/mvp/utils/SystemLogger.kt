package com.parentalcontrol.mvp.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Advanced system logger that writes all app logs to file for debugging
 * Captures all Log.d, Log.e, Log.i, Log.w calls and saves them persistently
 */
class SystemLogger(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemLogger"
        private const val LOG_DIR = "KidSecura"
        private const val SYSTEM_LOG_PREFIX = "system_log_"
        private const val MAX_LOG_FILES = 7 // Keep 7 days of logs
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB per file
        
        @Volatile
        private var INSTANCE: SystemLogger? = null
        
        fun getInstance(context: Context): SystemLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SystemLogger(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val logDir: File by lazy {
        File(context.getExternalFilesDir(null), LOG_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "✅ Created system log directory: $absolutePath")
            }
        }
    }
    
    init {
        Log.d(TAG, "🚀 SystemLogger initialized")
        cleanupOldLogs()
    }
    
    /**
     * Log system event with level and details
     */
    fun logSystem(level: String, tag: String, message: String, throwable: Throwable? = null) {
        scope.launch {
            try {
                val timestamp = timestampFormat.format(Date())
                val logEntry = buildString {
                    append("[$timestamp] [$level] [$tag] $message")
                    if (throwable != null) {
                        append("\n")
                        append("EXCEPTION: ${throwable.message}")
                        append("\n")
                        append("STACK TRACE:\n")
                        append(throwable.stackTraceToString())
                    }
                    append("\n")
                }
                
                writeToFile(logEntry)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error writing system log", e)
            }
        }
    }
    
    /**
     * Log DEBUG level
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        logSystem("DEBUG", tag, message)
    }
    
    /**
     * Log ERROR level
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        logSystem("ERROR", tag, message, throwable)
    }
    
    /**
     * Log INFO level  
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        logSystem("INFO", tag, message)
    }
    
    /**
     * Log WARN level
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        logSystem("WARN", tag, message, throwable)
    }
    
    /**
     * Log critical app event
     */
    fun logCritical(tag: String, event: String, details: String = "") {
        val message = "🚨 CRITICAL: $event ${if (details.isNotEmpty()) "- $details" else ""}"
        e(tag, message)
    }
    
    /**
     * Log button click events
     */
    fun logButtonClick(buttonName: String, activityName: String, success: Boolean = true, error: String? = null) {
        val message = if (success) {
            "🔘 BUTTON CLICK: '$buttonName' in $activityName - SUCCESS"
        } else {
            "🔘 BUTTON CLICK: '$buttonName' in $activityName - FAILED: $error"
        }
        logSystem("BUTTON", "UI", message)
    }
    
    /**
     * Log activity lifecycle events
     */
    fun logActivity(activityName: String, lifecycle: String, details: String = "") {
        val message = "📱 ACTIVITY: $activityName.$lifecycle ${if (details.isNotEmpty()) "- $details" else ""}"
        logSystem("ACTIVITY", "Lifecycle", message)
    }
    
    private suspend fun writeToFile(logEntry: String) {
        try {
            val today = dateFormat.format(Date())
            val logFile = File(logDir, "$SYSTEM_LOG_PREFIX$today.txt")
            
            // Check if file is too large, create new with timestamp
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
                val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
                val newLogFile = File(logDir, "${SYSTEM_LOG_PREFIX}${today}_$timestamp.txt")
                logFile.renameTo(newLogFile)
            }
            
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error writing to log file", e)
        }
    }
    
    private fun cleanupOldLogs() {
        scope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (MAX_LOG_FILES * 24 * 60 * 60 * 1000L)
                
                logDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(SYSTEM_LOG_PREFIX) && file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            Log.d(TAG, "🗑️ Deleted old log file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cleaning up old logs", e)
            }
        }
    }
    
    /**
     * Get recent log entries for display
     */
    suspend fun getRecentLogs(maxLines: Int = 50): List<String> = withContext(Dispatchers.IO) {
        try {
            val today = dateFormat.format(Date())
            val logFile = File(logDir, "$SYSTEM_LOG_PREFIX$today.txt")
            
            if (logFile.exists()) {
                logFile.readLines().takeLast(maxLines)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading recent logs", e)
            emptyList()
        }
    }
    
    fun cleanup() {
        scope.cancel()
        INSTANCE = null
    }
}

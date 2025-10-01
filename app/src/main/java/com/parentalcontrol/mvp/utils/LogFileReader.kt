package com.parentalcontrol.mvp.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: String,
    val type: LogType,
    val message: String,
    val fullContent: String
)

enum class LogType {
    SERVICE,      // SERVICE: Service started
    APP_ACTIVITY, // APP_ACTIVITY: WhatsApp (com.whatsapp)
    ALERT,        // ALERT: Suspicious content detected
    UNKNOWN
}

class LogFileReader(private val context: Context) {
    
    companion object {
        private const val TAG = "LogFileReader"
        private const val LOG_DIR_NAME = "KidSecura"
        private const val LOG_FILE_PREFIX = "monitoring_log_"
    }
    
    /**
     * Pobiera folder logów z Downloads
     */
    private fun getLogDirectory(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logDir = File(downloadsDir, LOG_DIR_NAME)
            
            if (logDir.exists() && logDir.canRead()) {
                logDir
            } else {
                Log.w(TAG, "Log directory not accessible: ${logDir.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing log directory", e)
            null
        }
    }
    
    /**
     * Pobiera listę wszystkich plików logów
     */
    suspend fun getLogFiles(): List<File> = withContext(Dispatchers.IO) {
        try {
            val logDir = getLogDirectory() ?: return@withContext emptyList()
            
            logDir.listFiles { file ->
                file.isFile && file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting log files", e)
            emptyList()
        }
    }
    
    /**
     * Czyta wszystkie logi z wszystkich plików
     */
    suspend fun readAllLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
        val allLogs = mutableListOf<LogEntry>()
        
        try {
            val logFiles = getLogFiles()
            
            for (file in logFiles) {
                val fileLogs = readLogFile(file)
                allLogs.addAll(fileLogs)
            }
            
            // Sortuj po czasie (najnowsze pierwsze)
            allLogs.sortedByDescending { parseTimestamp(it.timestamp) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading all logs", e)
            emptyList()
        }
    }
    
    /**
     * Czyta logi z konkretnego pliku
     */
    suspend fun readLogFile(file: File): List<LogEntry> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<LogEntry>()
        
        try {
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "Cannot read file: ${file.absolutePath}")
                return@withContext emptyList()
            }
            
            val lines = file.readLines()
            var currentEntry: StringBuilder? = null
            var currentTimestamp: String? = null
            var currentType: LogType = LogType.UNKNOWN
            
            for (line in lines) {
                if (line.startsWith("[") && line.contains("]")) {
                    // Nowa linia z timestampem - zapisz poprzedni wpis
                    if (currentEntry != null && currentTimestamp != null) {
                        val content = currentEntry.toString().trim()
                        val message = extractMessage(content, currentType)
                        
                        logs.add(LogEntry(
                            timestamp = currentTimestamp,
                            type = currentType,
                            message = message,
                            fullContent = content
                        ))
                    }
                    
                    // Rozpocznij nowy wpis
                    currentTimestamp = extractTimestamp(line)
                    currentType = determineLogType(line)
                    currentEntry = StringBuilder(line)
                    
                } else if (currentEntry != null) {
                    // Kontynuacja poprzedniego wpisu
                    currentEntry.append("\n").append(line)
                }
            }
            
            // Dodaj ostatni wpis
            if (currentEntry != null && currentTimestamp != null) {
                val content = currentEntry.toString().trim()
                val message = extractMessage(content, currentType)
                
                logs.add(LogEntry(
                    timestamp = currentTimestamp,
                    type = currentType,
                    message = message,
                    fullContent = content
                ))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file: ${file.name}", e)
        }
        
        logs
    }
    
    /**
     * Filtruje logi według typu
     */
    fun filterLogs(logs: List<LogEntry>, type: LogType?): List<LogEntry> {
        return if (type == null) {
            logs
        } else {
            logs.filter { it.type == type }
        }
    }
    
    /**
     * Filtruje logi według daty
     */
    fun filterLogsByDate(logs: List<LogEntry>, daysBack: Int): List<LogEntry> {
        if (daysBack <= 0) return logs
        
        val cutoffTime = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysBack)
        }.time
        
        return logs.filter { entry ->
            val timestamp = parseTimestamp(entry.timestamp)
            timestamp?.after(cutoffTime) ?: false
        }
    }
    
    private fun extractTimestamp(line: String): String {
        val start = line.indexOf('[')
        val end = line.indexOf(']')
        return if (start >= 0 && end > start) {
            line.substring(start + 1, end)
        } else {
            ""
        }
    }
    
    private fun determineLogType(line: String): LogType {
        return when {
            line.contains("SERVICE:") -> LogType.SERVICE
            line.contains("APP_ACTIVITY:") -> LogType.APP_ACTIVITY
            line.contains("ALERT:") -> LogType.ALERT
            else -> LogType.UNKNOWN
        }
    }
    
    private fun extractMessage(content: String, type: LogType): String {
        return when (type) {
            LogType.SERVICE -> {
                val match = Regex("SERVICE: (.+)").find(content)
                match?.groupValues?.get(1) ?: "Service event"
            }
            LogType.APP_ACTIVITY -> {
                val match = Regex("APP_ACTIVITY: (.+)").find(content)
                match?.groupValues?.get(1) ?: "App activity"
            }
            LogType.ALERT -> {
                val lines = content.split("\n")
                val appLine = lines.find { it.trim().startsWith("App:") }
                val typeLine = lines.find { it.trim().startsWith("Type:") }
                
                if (appLine != null && typeLine != null) {
                    val app = appLine.substringAfter("App:").trim()
                    val alertType = typeLine.substringAfter("Type:").trim()
                    "$alertType w $app"
                } else {
                    "Suspicious content detected"
                }
            }
            LogType.UNKNOWN -> content.take(50) + if (content.length > 50) "..." else ""
        }
    }
    
    private fun parseTimestamp(timestamp: String): Date? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.parse(timestamp)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Sprawdza czy można odczytać pliki logów
     */
    fun canReadLogs(): Boolean {
        return try {
            val logDir = getLogDirectory()
            logDir != null && logDir.canRead()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Pobiera informacje o plikach logów
     */
    suspend fun getLogFilesInfo(): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        try {
            val logFiles = getLogFiles()
            logFiles.map { file ->
                val dateStr = file.name.removePrefix(LOG_FILE_PREFIX).removeSuffix(".txt")
                val size = file.length()
                dateStr to size
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting log files info", e)
            emptyList()
        }
    }
}

package com.parentalcontrol.mvp.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.parentalcontrol.mvp.analyzer.ContentAnalyzer
import com.parentalcontrol.mvp.data.MonitoringDatabase
import com.parentalcontrol.mvp.data.MonitoringEvent
import com.parentalcontrol.mvp.utils.AppMonitor
import com.parentalcontrol.mvp.utils.FileLogger
import kotlinx.coroutines.*

class AccessibilityMonitoringService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AccessibilityMonitor"
        var isRunning = false
            private set
    }
    
    private lateinit var fileLogger: FileLogger
    private lateinit var database: MonitoringDatabase
    private lateinit var contentAnalyzer: ContentAnalyzer
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Przechowuje ostatnią aplikację aby nie logować duplikatów
    private var lastAppPackage: String? = null
    private var lastTextContent: String? = null
    private var lastAnalysisTime = 0L
    
    // Minimalne opóźnienie między analizami (w ms)
    private val analysisDelay = 2000L
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        
        // Inicjalizacja komponentów
        fileLogger = FileLogger(this)
        database = MonitoringDatabase.getInstance(this)
        contentAnalyzer = ContentAnalyzer(this)
        
        serviceScope.launch {
            fileLogger.logServiceEvent("AccessibilityService started - discrete monitoring active")
        }
        
        Log.d(TAG, "AccessibilityService connected - monitoring started")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleWindowEvent(event)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handleTextEvent(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling accessibility event", e)
        }
    }
    
    private fun handleWindowEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Ignoruj własną aplikację
        if (packageName == this.packageName) return
        
        // Loguj zmianę aplikacji jeśli to nowa aplikacja
        if (packageName != lastAppPackage) {
            lastAppPackage = packageName
            val appName = getAppName(packageName)
            
            serviceScope.launch {
                fileLogger.logAppActivity(appName, packageName)
            }
            
            Log.d(TAG, "App changed to: $appName ($packageName)")
        }
        
        // Analizuj zawartość okna
        analyzeWindowContent()
    }
    
    private fun handleTextEvent(event: AccessibilityEvent) {
        // Analizuj tekst gdy użytkownik coś wpisuje lub fokusuje
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime > analysisDelay) {
            analyzeWindowContent()
            lastAnalysisTime = currentTime
        }
    }
    
    private fun analyzeWindowContent() {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Pobierz cały tekst z aktywnego okna
            val extractedText = extractTextFromNode(rootNode)
            
            if (extractedText.isNotEmpty() && extractedText != lastTextContent) {
                lastTextContent = extractedText
                
                // Analizuj tekst w tle
                serviceScope.launch {
                    analyzeExtractedText(extractedText)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing window content", e)
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val textBuilder = StringBuilder()
        
        try {
            // Dodaj tekst z bieżącego node'a
            node.text?.let { text ->
                if (text.isNotEmpty()) {
                    textBuilder.append(text).append(" ")
                }
            }
            
            // Dodaj content description
            node.contentDescription?.let { desc ->
                if (desc.isNotEmpty()) {
                    textBuilder.append(desc).append(" ")
                }
            }
            
            // Rekursywnie przejdź przez dzieci
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    textBuilder.append(extractTextFromNode(child))
                    child.recycle()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from node", e)
        }
        
        return textBuilder.toString().trim()
    }
    
    private suspend fun analyzeExtractedText(text: String) {
        if (text.length < 10) return // Ignoruj bardzo krótkie teksty
        
        try {
            // Użyj ContentAnalyzer do analizy tekstu (bez bitmap)
            val textAnalysis = contentAnalyzer.analyzeTextOnly(text)
            
            if (textAnalysis.isSuspicious) {
                val currentApp = lastAppPackage ?: "unknown"
                val appName = getAppName(currentApp)
                
                // Zapisz do bazy danych
                val event = MonitoringEvent(
                    timestamp = System.currentTimeMillis(),
                    type = textAnalysis.detectionType,
                    confidence = textAnalysis.confidence,
                    description = textAnalysis.description,
                    ocrText = text.take(500), // Ogranicz długość
                    screenshotPath = null
                )
                
                database.eventDao().insertEvent(event)
                
                // Loguj do pliku
                fileLogger.logSuspiciousContent(
                    appName = appName,
                    packageName = currentApp,
                    detectionType = textAnalysis.detectionType,
                    description = textAnalysis.description,
                    confidence = textAnalysis.confidence,
                    extractedText = text.take(200)
                )
                
                Log.d(TAG, "Suspicious content detected in $appName: ${textAnalysis.description}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing extracted text", e)
        }
    }
    
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        serviceScope.launch {
            fileLogger.logServiceEvent("AccessibilityService stopped")
            fileLogger.cleanOldLogs()
        }
        
        serviceScope.cancel()
        Log.d(TAG, "AccessibilityService destroyed")
    }
}

package com.parentalcontrol.mvp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.parentalcontrol.mvp.MainActivity
import com.parentalcontrol.mvp.R
import com.parentalcontrol.mvp.analyzer.ContentAnalyzer
import com.parentalcontrol.mvp.utils.FileLogger
import com.parentalcontrol.mvp.utils.AppMonitor
import com.parentalcontrol.mvp.utils.NotificationHelper
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.util.*

class KeywordMonitorService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1003
        const val CHANNEL_ID = "KeywordMonitorChannel"
        private const val TAG = "KeywordMonitorService"
        
        @Volatile
        var isRunning = false
            private set
    }
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var contentAnalyzer: ContentAnalyzer
    private lateinit var fileLogger: FileLogger
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var appMonitor: AppMonitor
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var captureJob: Job? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var resultCode = 0
    private var resultData: Intent? = null
    private var monitorInterval = 10 // sekund
    private var autoStopDuration = 60 // sekund
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Auto-stop timer
    private var autoStopJob: Job? = null
    
    // Statistics
    private var detectionCount = 0
    private var sessionStartTime = 0L
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔍 KeywordMonitorService onCreate")
        
        // Inicjalizacja MediaProjectionManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Inicjalizacja ML Kit Text Recognition
        val options = TextRecognizerOptions.Builder().build()
        textRecognizer = TextRecognition.getClient(options)
        
        // Inicjalizacja komponentów
        contentAnalyzer = ContentAnalyzer(this)
        fileLogger = FileLogger(this)
        notificationHelper = NotificationHelper(this)
        appMonitor = AppMonitor(this)
        
        // Pobierz parametry ekranu
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        sessionStartTime = System.currentTimeMillis()
        isRunning = true
        
        // Loguj start serwisu
        serviceScope.launch {
            fileLogger.logServiceEvent("🔍 KeywordMonitorService started - monitoring for dangerous keywords")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔍 KeywordMonitorService onStartCommand")
        
        if (intent != null) {
            resultCode = intent.getIntExtra("RESULT_CODE", 0)
            resultData = intent.getParcelableExtra("DATA")
            monitorInterval = intent.getIntExtra("MONITOR_INTERVAL", 10)
            autoStopDuration = intent.getIntExtra("AUTO_STOP_DURATION", 60)
            
            startForeground(NOTIFICATION_ID, createNotification())
            startScreenCapture()
            
            // Auto-stop po określonym czasie
            startAutoStopTimer()
        }
        
        return START_STICKY
    }
    
    private fun startAutoStopTimer() {
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay((autoStopDuration * 1000).toLong())
            Log.d(TAG, "🛑 Auto-stopping KeywordMonitor after $autoStopDuration seconds")
            
            // Wyślij podsumowanie sesji
            sendSessionSummary()
            stopSelf()
        }
    }
    
    private fun createNotification(): Notification {
        // Utwórz kanał notyfikacji dla Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor Słownika",
                NotificationManager.IMPORTANCE_HIGH // WYSOKIE - bo to alerty bezpieczeństwa!
            ).apply {
                description = "Powiadomienia o wykrytych niebezpiecznych słowach"
                setShowBadge(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Utwórz intent dla powrotu do MainActivity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent do zatrzymania serwisu
        val stopIntent = Intent(this, KeywordMonitorService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val elapsedSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
        val remainingSeconds = autoStopDuration - elapsedSeconds
        
        // Zbuduj notyfikację
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔍 Monitor Słownika Aktywny")
            .setContentText("Wykryto: $detectionCount | Pozostało: ${remainingSeconds}s | Interwał: ${monitorInterval}s")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }
    
    private fun startScreenCapture() {
        Log.d(TAG, "🎬 Starting screen capture for keyword monitoring")
        
        try {
            if (resultData == null) {
                Log.e(TAG, "❌ No result data for MediaProjection")
                stopSelf()
                return
            }
            
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)
            
            if (mediaProjection == null) {
                Log.e(TAG, "❌ MediaProjection is null")
                stopSelf()
                return
            }
            
            // Utwórz ImageReader do przechwytywania obrazów
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            
            // Utwórz VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "KeywordMonitorDisplay",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )
            
            // Rozpocznij okresowe przechwytywanie
            startPeriodicCapture()
            
            Log.d(TAG, "✅ Screen capture for keyword monitoring started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting screen capture for keyword monitoring", e)
            stopSelf()
        }
    }
    
    private fun startPeriodicCapture() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                captureAndAnalyzeScreen()
                delay(monitorInterval * 1000L)
            }
        }
    }
    
    private suspend fun captureAndAnalyzeScreen(): Unit = withContext(Dispatchers.IO) {
        try {
            val sessionId = System.currentTimeMillis()
            Log.d(TAG, "🔍 Capturing screen for keyword analysis (session: $sessionId)")
            
            // Loguj rozpoczęcie sesji
            fileLogger.logServiceEvent("🔍 Keyword Monitor Session: $sessionId - scanning for dangerous content")
            
            val image = imageReader?.acquireLatestImage()
            image?.let { img ->
                try {
                    Log.d(TAG, "🖼️ Processing image ${img.width}x${img.height} for keyword detection")
                    
                    // Konwertuj Image na Bitmap
                    val bitmap = imageToBitmap(img)
                    img.close()
                    
                    // Wykryj aktualną aplikację
                    val currentApp = appMonitor.getCurrentApp()
                    val appName = currentApp?.appName ?: "Unknown App"
                    val packageName = currentApp?.packageName ?: "unknown.package"
                    
                    // Loguj szczegóły przetwarzania
                    fileLogger.logServiceEvent("🔄 Keyword Analysis: ${bitmap.width}x${bitmap.height} in app: $appName")
                    
                    // Wykonaj OCR i analizę słownika
                    performOCRAndKeywordAnalysis(bitmap, appName, packageName)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing image for keyword monitoring", e)
                    fileLogger.logServiceEvent("❌ Keyword Processing Error: ${e.message}")
                    img.close()
                }
            }
            
            // Loguj zakończenie sesji
            fileLogger.logServiceEvent("✅ Keyword Monitor Session: $sessionId completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturing screen for keyword monitoring", e)
            fileLogger.logServiceEvent("❌ Keyword Capture Error: ${e.message}")
        }
    }
    
    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val fullBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(buffer)
        
        // Przytnij do właściwego rozmiaru
        val correctBitmap = if (rowPadding == 0) {
            fullBitmap
        } else {
            Bitmap.createBitmap(fullBitmap, 0, 0, image.width, image.height)
        }
        
        // Zwolnij pamięć niepotrzebnych bitmap
        if (fullBitmap != correctBitmap) {
            fullBitmap.recycle()
        }
        
        return correctBitmap
    }
    
    private suspend fun performOCRAndKeywordAnalysis(bitmap: Bitmap, appName: String, packageName: String) {
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        analyzeTextForKeywords(visionText.text, appName, packageName)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ OCR failed for keyword monitoring", e)
                        serviceScope.launch {
                            fileLogger.logServiceEvent("❌ OCR Error in keyword monitoring: ${e.message}")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error performing OCR for keyword monitoring", e)
                serviceScope.launch {
                    fileLogger.logServiceEvent("❌ Keyword OCR Error: ${e.message}")
                }
            }
        }
    }
    
    private fun analyzeTextForKeywords(text: String, appName: String, packageName: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "🔍 Analyzing ${text.length} characters for dangerous keywords in $appName")
                
                // Loguj surowy tekst
                fileLogger.logServiceEvent("🔍 KEYWORD ANALYSIS: Scanning ${text.length} chars from $appName")
                fileLogger.logServiceEvent("📝 RAW TEXT FROM $appName: '$text'")
                
                // Użyj ContentAnalyzer do wykrycia niebezpiecznej zawartości
                val analysisResult = contentAnalyzer.analyzeTextOnly(text)
            
            if (analysisResult.isSuspicious) {
                // WYKRYTO NIEBEZPIECZNĄ ZAWARTOŚĆ!
                detectionCount++
                
                val alertMessage = """
                    🚨 WYKRYTO NIEBEZPIECZNĄ ZAWARTOŚĆ!
                    
                    📱 Aplikacja: $appName
                    🔍 Typ zagrożenia: ${analysisResult.detectionType}
                    ⚠️ Poziom ryzyka: ${(analysisResult.confidence * 100).toInt()}%
                    📝 Opis: ${analysisResult.description}
                    
                    Znaleziony tekst: "${analysisResult.extractedText?.take(100)}..."
                """.trimIndent()
                
                Log.w(TAG, "🚨 KEYWORD ALERT: ${analysisResult.detectionType} detected in $appName")
                
                // Loguj szczegółowy alert
                serviceScope.launch {
                    fileLogger.logServiceEvent("🚨 KEYWORD ALERT #$detectionCount: ${analysisResult.detectionType} in $appName")
                    fileLogger.logServiceEvent("🚨 SUSPICIOUS TEXT: '${analysisResult.extractedText}'")
                    fileLogger.logServiceEvent("🚨 ALERT DETAILS: ${analysisResult.description} (confidence: ${analysisResult.confidence})")
                }
                
                // Wyślij powiadomienie do urządzenia rodzica
                sendParentAlert(alertMessage, appName, analysisResult)
                
                // Zaktualizuj notyfikację
                updateNotification()
                
            } else {
                Log.d(TAG, "✅ No dangerous keywords detected in $appName")
                fileLogger.logServiceEvent("✅ KEYWORD CHECK: No threats detected in $appName")
            }
            
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error analyzing keywords", e)
                fileLogger.logServiceEvent("❌ Keyword Analysis Error: ${e.message}")
            }
        }
    }
    
    private fun sendParentAlert(alertMessage: String, appName: String, analysisResult: com.parentalcontrol.mvp.analyzer.AnalysisResult) {
        try {
            Log.d(TAG, "📤 Sending parent alert for detection in $appName")
            
            // Wyślij natychmiastowe powiadomienie na tym urządzeniu
            try {
                notificationHelper.showAlert(
                    title = "🚨 WYKRYTO NIEBEZPIECZNĄ ZAWARTOŚĆ",
                    message = "W aplikacji $appName wykryto: ${analysisResult.detectionType}",
                    confidence = (analysisResult.confidence * 100).toInt()
                )
                
                serviceScope.launch {
                    fileLogger.logServiceEvent("📤 URGENT ALERT: Critical notification shown to parent")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending urgent notification", e)
                serviceScope.launch {
                    fileLogger.logServiceEvent("❌ URGENT ALERT ERROR: ${e.message}")
                }
            }
            
            // Loguj szczegółowy alert do pliku (dostępny w Podglądzie Logów)
            serviceScope.launch {
                try {
                    fileLogger.logServiceEvent("🚨 PARENT ALERT #$detectionCount:")
                    fileLogger.logServiceEvent("📱 App: $appName")
                    fileLogger.logServiceEvent("🔍 Threat: ${analysisResult.detectionType}")
                    fileLogger.logServiceEvent("⚠️ Risk Level: ${(analysisResult.confidence * 100).toInt()}%")
                    fileLogger.logServiceEvent("📝 Description: ${analysisResult.description}")
                    fileLogger.logServiceEvent("💬 Found Text: '${analysisResult.extractedText?.take(200)}...'")
                    fileLogger.logServiceEvent("⏰ Time: ${Date()}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error logging parent alert", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in sendParentAlert", e)
        }
    }
    
    private fun updateNotification() {
        try {
            val updatedNotification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            Log.d(TAG, "📱 Notification updated with detection count: $detectionCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating notification", e)
        }
    }
    
    private fun sendSessionSummary() {
        try {
            val sessionDurationMs = System.currentTimeMillis() - sessionStartTime
            val sessionDurationMin = (sessionDurationMs / 60000).toInt()
            
            val summaryMessage = """
                📊 PODSUMOWANIE SESJI MONITORINGU
                
                ⏱️ Czas trwania: ${sessionDurationMin} minut
                🔍 Wykryto zagrożeń: $detectionCount
                📱 Interwał skanowania: ${monitorInterval}s
                📅 Data: ${Date()}
                
                ${if (detectionCount > 0) "⚠️ UWAGA: Wykryto podejrzaną aktywność!" else "✅ Brak wykrytych zagrożeń"}
            """.trimIndent()
            
            Log.d(TAG, "📊 Session summary: $detectionCount detections in ${sessionDurationMin} minutes")
            
            // Wyślij podsumowanie przez powiadomienie
            try {
                notificationHelper.showAlert(
                    title = "📊 Podsumowanie Monitoringu Słownika",
                    message = "Sesja ${sessionDurationMin}min: wykryto $detectionCount zagrożeń",
                    confidence = if (detectionCount > 0) 100 else 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing summary notification", e)
            }
            
            // Loguj szczegółowe podsumowanie
            serviceScope.launch {
                try {
                    fileLogger.logServiceEvent("📊 SESSION SUMMARY:")
                    fileLogger.logServiceEvent("⏱️ Duration: ${sessionDurationMin} minutes")
                    fileLogger.logServiceEvent("🔍 Detections: $detectionCount threats found")
                    fileLogger.logServiceEvent("📱 Scan Interval: ${monitorInterval}s")
                    fileLogger.logServiceEvent("📅 End Time: ${Date()}")
                    fileLogger.logServiceEvent("🎯 Status: ${if (detectionCount > 0) "THREATS DETECTED" else "CLEAN SESSION"}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error logging session summary", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating session summary", e)
        }
    }
    
    private fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up KeywordMonitor resources")
        
        // Zatrzymaj auto-stop timer
        autoStopJob?.cancel()
        
        // Zatrzymaj przechwytywanie
        captureJob?.cancel()
        
        // Zwolnij zasoby MediaProjection
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        // Zwolnij text recognizer
        textRecognizer.close()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "🛑 KeywordMonitorService onDestroy")
        isRunning = false
        
        // Wyślij podsumowanie sesji
        sendSessionSummary()
        
        // Loguj zakończenie
        serviceScope.launch {
            fileLogger.logServiceEvent("🛑 KeywordMonitorService stopped - $detectionCount total detections")
        }
        
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

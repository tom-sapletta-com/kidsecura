package com.parentalcontrol.mvp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.parentalcontrol.mvp.MainActivity
import com.parentalcontrol.mvp.R
import com.parentalcontrol.mvp.analyzer.ContentAnalyzer
import com.parentalcontrol.mvp.data.MonitoringEvent
import com.parentalcontrol.mvp.data.MonitoringDatabase
import com.parentalcontrol.mvp.utils.ImageUtils
import com.parentalcontrol.mvp.utils.NotificationHelper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*

class ScreenCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screen_capture_channel"
        var isRunning = false
            private set
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    
    private var resultCode: Int = 0
    private lateinit var data: Intent
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null
    
    // Parametry konfiguracji
    private var captureInterval: Int = 2
    private var cropBottom: Boolean = true
    private var localAnalysis: Boolean = true
    private var saveScreenshots: Boolean = false
    
    private lateinit var contentAnalyzer: ContentAnalyzer
    private lateinit var database: MonitoringDatabase
    private lateinit var notificationHelper: NotificationHelper
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Inicjalizacja komponentów
        contentAnalyzer = ContentAnalyzer(this)
        database = MonitoringDatabase.getInstance(this)
        notificationHelper = NotificationHelper(this)
        
        // Pobierz metryki ekranu
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            resultCode = intent.getIntExtra("RESULT_CODE", -1)
            data = intent.getParcelableExtra("DATA")!!
            
            captureInterval = intent.getIntExtra("CAPTURE_INTERVAL", 2)
            cropBottom = intent.getBooleanExtra("CROP_BOTTOM", true)
            localAnalysis = intent.getBooleanExtra("LOCAL_ANALYSIS", true)
            saveScreenshots = intent.getBooleanExtra("SAVE_SCREENSHOTS", false)
            
            startForeground(NOTIFICATION_ID, createNotification())
            startScreenCapture()
            startPeriodicCapture()
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitorowanie ekranu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Powiadomienie o aktywnym monitorowaniu ekranu"
                setSound(null, null)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitorowanie aktywne")
            .setContentText("Aplikacja monitoruje treści na ekranie")
            .setSmallIcon(R.drawable.ic_monitoring)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startScreenCapture() {
        try {
            // Ustaw ImageReader do przechwytywania obrazów
            imageReader = ImageReader.newInstance(
                screenWidth, 
                screenHeight, 
                PixelFormat.RGBA_8888, 
                2
            )
            
            // Utwórz MediaProjection
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            // Callback dla MediaProjection
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped")
                    cleanup()
                }
            }, handler)
            
            // Utwórz VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )
            
            Log.d(TAG, "Screen capture started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
            stopSelf()
        }
    }
    
    private fun startPeriodicCapture() {
        captureRunnable = object : Runnable {
            override fun run() {
                captureScreen()
                handler.postDelayed(this, (captureInterval * 1000).toLong())
            }
        }
        handler.post(captureRunnable!!)
    }
    
    private fun captureScreen() {
        serviceScope.launch {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()
                    
                    // Przytnij do dolnej połowy jeśli włączone
                    val processedBitmap = if (cropBottom) {
                        ImageUtils.cropBottomHalf(bitmap)
                    } else {
                        bitmap
                    }
                    
                    // Zmniejsz rozmiar dla analizy (oszczędność pamięci)
                    val resizedBitmap = ImageUtils.resizeBitmap(processedBitmap, 480)
                    
                    // Analiza treści
                    if (localAnalysis) {
                        analyzeContent(resizedBitmap)
                    }
                    
                    // Zapisz zrzut jeśli włączone
                    if (saveScreenshots) {
                        saveScreenshot(resizedBitmap)
                    }
                    
                    // Zwolnij pamięć
                    if (bitmap != processedBitmap) {
                        bitmap.recycle()
                    }
                    if (processedBitmap != resizedBitmap) {
                        processedBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screen", e)
            }
        }
    }
    
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }
    
    private suspend fun analyzeContent(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                // Uruchom analizę
                val analysisResult = contentAnalyzer.analyze(bitmap)
                
                // Jeśli wykryto podejrzaną treść
                if (analysisResult.isSuspicious) {
                    val event = MonitoringEvent(
                        timestamp = System.currentTimeMillis(),
                        type = analysisResult.detectionType,
                        confidence = analysisResult.confidence,
                        description = analysisResult.description,
                        ocrText = analysisResult.extractedText,
                        screenshotPath = null // Zostanie ustawione jeśli saveScreenshots = true
                    )
                    
                    // Zapisz wydarzenie w bazie
                    database.eventDao().insertEvent(event)
                    
                    // Wyślij powiadomienie rodzicowi
                    notificationHelper.sendAlertNotification(
                        "Wykryto podejrzaną treść",
                        "${analysisResult.detectionType}: ${analysisResult.description}"
                    )
                    
                    Log.d(TAG, "Suspicious content detected: ${analysisResult.description}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing content", e)
            }
        }
    }
    
    private suspend fun saveScreenshot(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            try {
                val screenshotsDir = File(filesDir, "screenshots")
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val file = File(screenshotsDir, "screenshot_$timestamp.jpg")
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                
                Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
                
                // Usuń stare zrzuty (starsze niż 7 dni)
                cleanOldScreenshots(screenshotsDir)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving screenshot", e)
            }
        }
    }
    
    private fun cleanOldScreenshots(dir: File) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < sevenDaysAgo) {
                file.delete()
                Log.d(TAG, "Deleted old screenshot: ${file.name}")
            }
        }
    }
    
    private fun cleanup() {
        captureRunnable?.let { handler.removeCallbacks(it) }
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        imageReader?.close()
        imageReader = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cleanup()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
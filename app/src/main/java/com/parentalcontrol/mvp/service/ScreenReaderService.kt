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
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.*

class ScreenReaderService : Service(), TextToSpeech.OnInitListener {
    
    companion object {
        const val ACTION_UPDATE_INTERVAL = "com.parentalcontrol.mvp.UPDATE_INTERVAL"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "ScreenReaderChannel"
        private const val TAG = "ScreenReaderService"
        
        @Volatile
        var isRunning = false
            private set
    }
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private lateinit var tts: TextToSpeech
    private var ttsInitialized = false
    
    private lateinit var textRecognizer: TextRecognizer
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var captureJob: Job? = null
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var resultCode = 0
    private var resultData: Intent? = null
    private var readInterval = 2 // sekund - szybsze dla lepszego UX
    private var speechRate = 1.0f
    private var language = "pl_PL"
    
    private val lastReadText = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    
    // Auto-stop timer
    private var autoStopJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔊 ScreenReaderService onCreate")
        
        // Inicjalizacja MediaProjectionManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Inicjalizacja Text-to-Speech
        tts = TextToSpeech(this, this)
        
        // Inicjalizacja ML Kit Text Recognition
        val options = TextRecognizerOptions.Builder().build()
        textRecognizer = TextRecognition.getClient(options)
        
        // Pobierz parametry ekranu
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔊 ScreenReaderService onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_UPDATE_INTERVAL -> {
                readInterval = intent.getIntExtra("READ_INTERVAL", 2)
                Log.d(TAG, "Updated read interval to $readInterval seconds")
                restartCapture()
                return START_STICKY
            }
            else -> {
                if (intent != null) {
                    resultCode = intent.getIntExtra("RESULT_CODE", 0)
                    resultData = intent.getParcelableExtra("DATA")
                    readInterval = intent.getIntExtra("READ_INTERVAL", 2)
                    speechRate = intent.getFloatExtra("SPEECH_RATE", 1.0f)
                    language = intent.getStringExtra("LANGUAGE") ?: "pl_PL"
                    
                    if (ttsInitialized) {
                        tts.setSpeechRate(speechRate)
                        setTtsLanguage(language)
                    }
                    
                    startForeground(NOTIFICATION_ID, createNotification())
                    startScreenCapture()
                    
                    // Auto-stop po 30 sekundach
                    startAutoStopTimer()
                }
            }
        }
        
        return START_STICKY
    }
    
    private fun startAutoStopTimer() {
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(30_000) // 30 sekund
            Log.d(TAG, "🛑 Auto-stopping ScreenReader after 30 seconds")
            stopSelf()
        }
    }
    
    private fun createNotification(): Notification {
        // Utwórz kanał notyfikacji dla Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Czytnik Ekranu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Powiadomienie o działającym czytniku ekranu"
                setShowBadge(false)
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
        val stopIntent = Intent(this, ScreenReaderService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Zbuduj notyfikację
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔊 Czytnik Ekranu Aktywny")
            .setContentText("Czytanie ekranu co $readInterval sekundy (auto-stop za 30s)")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startScreenCapture() {
        Log.d(TAG, "🎬 Starting screen capture for TTS")
        
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
                "ScreenReaderDisplay",
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
            
            Log.d(TAG, "✅ Screen capture for TTS started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting screen capture for TTS", e)
            stopSelf()
        }
    }
    
    private fun startPeriodicCapture() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                captureAndReadScreen()
                delay(readInterval * 1000L)
            }
        }
    }
    
    private fun restartCapture() {
        startPeriodicCapture()
    }
    
    private suspend fun captureAndReadScreen() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📸 Capturing screen for TTS...")
            
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                try {
                    // Konwertuj Image na Bitmap
                    val bitmap = imageToBitmap(image)
                    image.close()
                    
                    // Wykonaj OCR
                    performOCR(bitmap)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing image for TTS", e)
                    image.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturing screen for TTS", e)
        }
    }
    
    private fun imageToBitmap(image: android.media.Image): Bitmap {
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
        
        // Przytnij bitmap do właściwego rozmiaru
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }
    
    private suspend fun performOCR(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    processRecognizedText(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ OCR failed for TTS", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error performing OCR for TTS", e)
        }
    }
    
    private fun processRecognizedText(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "🔊 No text detected on screen")
            return
        }
        
        // Podziel tekst na linie i oczyść
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 2 } // Tylko dłuższe linie
            .filter { line -> 
                // Filtruj tylko sensowny tekst (polskie znaki, cyfry, podstawowe znaki)
                line.matches(Regex(".*[a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9].*"))
            }
        
        // Znajdź nowy tekst (którego jeszcze nie przeczytaliśmy)
        val newLines = lines.filter { line ->
            !lastReadText.contains(line)
        }
        
        if (newLines.isNotEmpty()) {
            // Zaktualizuj zbiór przeczytanego tekstu
            lastReadText.clear()
            lastReadText.addAll(lines.takeLast(5)) // Zapamiętaj ostatnie 5 linii
            
            // Przeczytaj nowy tekst (max 2 linie, żeby nie było za długo)
            val textToRead = newLines.take(2).joinToString(". ")
            
            if (textToRead.length > 10) { // Tylko jeśli tekst ma sens
                speakText("Wykryto tekst: $textToRead")
                Log.d(TAG, "🔊 Reading text: $textToRead")
            }
        } else {
            Log.d(TAG, "🔊 No new text to read")
        }
    }
    
    private fun speakText(text: String) {
        if (ttsInitialized && text.isNotBlank()) {
            // Zatrzymaj poprzednie czytanie
            tts.stop()
            
            // Skróć tekst jeśli za długi
            val textToSpeak = if (text.length > 150) {
                text.take(150) + "... i dalej"
            } else {
                text
            }
            
            // Rozpocznij nowe czytanie
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null)
            }
            
            Log.d(TAG, "🔊 TTS speaking: ${textToSpeak.take(50)}...")
        }
    }
    
    private fun setTtsLanguage(languageTag: String) {
        try {
            val parts = languageTag.split("_")
            val locale = if (parts.size >= 2) {
                Locale(parts[0], parts[1])
            } else {
                Locale(languageTag)
            }
            
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "⚠️ Language $languageTag not supported, using Polish")
                tts.setLanguage(Locale("pl", "PL"))
            } else {
                Log.d(TAG, "✅ TTS language set to $languageTag")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting TTS language", e)
            tts.setLanguage(Locale("pl", "PL"))
        }
    }
    
    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            tts.setSpeechRate(speechRate)
            setTtsLanguage(language)
            
            // Powiedz że usługa się uruchomiła
            speakText("Czytnik ekranu uruchomiony. Czytanie co $readInterval sekundy.")
            
            Log.d(TAG, "✅ TTS initialized successfully")
        } else {
            Log.e(TAG, "❌ TTS initialization failed")
        }
    }
    
    private fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up ScreenReader resources")
        
        // Zatrzymaj auto-stop timer
        autoStopJob?.cancel()
        
        // Zatrzymaj przechwytywanie
        captureJob?.cancel()
        
        // Zatrzymaj TTS
        if (ttsInitialized) {
            tts.stop()
            tts.shutdown()
        }
        
        // Zwolnij zasoby MediaProjection
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        // Zwolnij text recognizer
        textRecognizer.close()
        
        // Wyczyść zapamiętany tekst
        lastReadText.clear()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "🛑 ScreenReaderService onDestroy")
        isRunning = false
        
        // Powiedz że się kończy
        if (ttsInitialized) {
            speakText("Czytnik ekranu zatrzymany")
        }
        
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

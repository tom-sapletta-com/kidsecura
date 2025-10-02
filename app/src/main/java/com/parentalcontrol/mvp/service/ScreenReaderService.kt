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
import com.parentalcontrol.mvp.utils.FileLogger
import kotlinx.coroutines.*
import java.io.File
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
    private var readInterval = 10 // 10 sekund - nowy domyślny interwał
    private var speechRate = 3.0f // 3x szybciej - nowa domyślna prędkość
    private var language = "pl_PL"
    private var topCrop = 10 // % górnej części do pominięcia
    private var bottomCrop = 10 // % dolnej części do pominięcia
    
    // Do logowania i wyświetlania
    private var currentReadingText = ""
    private lateinit var fileLogger: FileLogger
    
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
        
        // Inicjalizacja FileLogger
        fileLogger = FileLogger(this)
        
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
                    readInterval = intent.getIntExtra("READ_INTERVAL", 10)
                    speechRate = intent.getFloatExtra("SPEECH_RATE", 3.0f)
                    language = intent.getStringExtra("LANGUAGE") ?: "pl_PL"
                    topCrop = intent.getIntExtra("TOP_CROP", 10)
                    bottomCrop = intent.getIntExtra("BOTTOM_CROP", 10)
                    
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
        
        // Zbuduj notyfikację z aktualnie czytanym tekstem
        val notificationText = if (currentReadingText.isNotEmpty()) {
            "Czyta: ${currentReadingText.take(50)}${if (currentReadingText.length > 50) "..." else ""}"
        } else {
            "Czytanie co ${readInterval}s, pomija górne i dolne 10% (auto-stop za 30s)"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔊 Screen Reader 3x Speed")
            .setContentText(notificationText)
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
    
    private suspend fun captureAndReadScreen(): Unit = withContext(Dispatchers.IO) {
        try {
            val sessionId = System.currentTimeMillis()
            Log.d(TAG, "📸 Capturing screen for TTS (session: $sessionId)...")
            
            // Loguj rozpoczęcie sesji
            fileLogger.logServiceEvent("🎬 Capture Start: Session $sessionId - interval ${readInterval}s")
            
            val image = imageReader?.acquireLatestImage()
            image?.let { img ->
                try {
                    Log.d(TAG, "🖼️ Processing image ${img.width}x${img.height}")
                    
                    // Konwertuj Image na Bitmap
                    val bitmap = imageToBitmap(img)
                    img.close()
                    
                    // Loguj szczegóły przetwarzania
                    fileLogger.logServiceEvent("🔄 Image Processing: ${bitmap.width}x${bitmap.height} -> cropped (top:${topCrop}%, bottom:${bottomCrop}%)")
                    
                    // Wykonaj OCR
                    Log.d(TAG, "🔍 Starting OCR on cropped image ${bitmap.width}x${bitmap.height}")
                    performOCR(bitmap)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing image for TTS", e)
                    fileLogger.logServiceEvent("❌ Image Processing Error: ${e.message}")
                    img.close()
                }
            }
            
            // Loguj zakończenie sesji
            fileLogger.logServiceEvent("✅ Capture Complete: Session $sessionId finished")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturing screen for TTS", e)
            fileLogger.logServiceEvent("❌ Capture Error: ${e.message}")
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
        
        // Przytnij do obszaru zgodnie z ustawieniami
        val topCropPixels = (correctBitmap.height * topCrop / 100.0).toInt()  // Górne X%
        val bottomCropPixels = (correctBitmap.height * (100 - bottomCrop) / 100.0).toInt()  // Do (100-Y)%
        val croppedHeight = bottomCropPixels - topCropPixels
        
        Log.d(TAG, "🖼️ Cropping bitmap: original ${correctBitmap.width}x${correctBitmap.height} -> ${correctBitmap.width}x${croppedHeight} (skip top ${topCrop}%, bottom ${bottomCrop}%)")
        
        val croppedBitmap = Bitmap.createBitmap(
            correctBitmap, 
            0,                    // x - cała szerokość
            topCropPixels,        // y - od X% wysokości
            correctBitmap.width,  // width - cała szerokość
            croppedHeight         // height - obszar między cropami
        )
        
        // Zapisz miniaturkę do logowania
        saveScreenshotThumbnail(croppedBitmap)
        
        // Zwolnij pamięć niepotrzebnych bitmap
        if (fullBitmap != correctBitmap) {
            fullBitmap.recycle()
        }
        if (correctBitmap != croppedBitmap) {
            correctBitmap.recycle()
        }
        
        return croppedBitmap
    }
    
    /**
     * Zapisuje miniaturkę ekranu do celów debugowania
     */
    private fun saveScreenshotThumbnail(bitmap: Bitmap) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "screen_reader_${timestamp}.jpg"
            
            val downloadsDir = File("/storage/emulated/0/Download/KidSecura/ScreenReader")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, fileName)
            
            // Stwórz mniejszą miniaturkę (max 400px szerokości)
            val maxWidth = 400
            val scale = if (bitmap.width > maxWidth) {
                maxWidth.toFloat() / bitmap.width.toFloat()
            } else {
                1.0f
            }
            
            val thumbnailWidth = (bitmap.width * scale).toInt()
            val thumbnailHeight = (bitmap.height * scale).toInt()
            val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)
            
            file.outputStream().use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
            }
            
            Log.d(TAG, "📷 Thumbnail saved: $fileName (${thumbnailWidth}x${thumbnailHeight})")
            
            if (thumbnail != bitmap) {
                thumbnail.recycle()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving thumbnail", e)
        }
    }
    
    private suspend fun performOCR(bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
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
    }
    
    private fun processRecognizedText(text: String) {
        val timestamp = System.currentTimeMillis()
        
        // Loguj rozpoczęcie przetwarzania
        serviceScope.launch {
            fileLogger.logServiceEvent("🔍 OCR Start: Processing ${text.length} characters")
        }
        
        if (text.isBlank()) {
            Log.d(TAG, "🔊 No text detected on screen")
            serviceScope.launch {
                fileLogger.logServiceEvent("⚠️ OCR Result: No text detected")
            }
            return
        }
        
        Log.d(TAG, "📝 Raw OCR text (${text.length} chars): ${text.take(100)}...")
        
        // Loguj do FileLogger pełny surowy tekst
        serviceScope.launch {
            fileLogger.logServiceEvent("📝 RAW OCR TEXT (${text.length} chars): '$text'")
        }
        
        // Podziel tekst na linie i oczyść - ale zachowaj więcej tekstu
        val allLines = text.split("\n").map { it.trim() }
        val nonEmptyLines = allLines.filter { it.isNotEmpty() && it.length > 1 }
        
        // BARDZIEJ PERMISYWNE FILTROWANIE - akceptuj więcej typów tekstu
        val filteredLines = nonEmptyLines.filter { line -> 
            // Akceptuj linie z jakimikolwiek literami, cyframi lub podstawowymi znakami
            line.matches(Regex(".*[a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9].*")) ||
            line.length >= 3 // Lub jakąkolwiek linię z co najmniej 3 znakami
        }
        
        // Loguj odrzucone linie żeby zobaczyć co tracimy
        val rejectedLines = nonEmptyLines.filter { line ->
            !line.matches(Regex(".*[a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9].*")) && line.length < 3
        }
        
        if (rejectedLines.isNotEmpty()) {
            Log.d(TAG, "❌ Rejected ${rejectedLines.size} lines: ${rejectedLines.take(3)}")
            serviceScope.launch {
                fileLogger.logServiceEvent("❌ REJECTED LINES (${rejectedLines.size}): ${rejectedLines.joinToString(" | ")}")
            }
        }
        
        Log.d(TAG, "✂️ Text processing: ${allLines.size} total lines -> ${nonEmptyLines.size} non-empty -> ${filteredLines.size} filtered")
        Log.d(TAG, "✂️ Filtered lines: ${filteredLines.take(5)}")
        
        // Loguj szczegóły filtrowania
        serviceScope.launch {
            fileLogger.logServiceEvent("✂️ TEXT FILTERING: ${allLines.size} total -> ${nonEmptyLines.size} non-empty -> ${filteredLines.size} valid lines")
            fileLogger.logServiceEvent("✂️ FILTERED LINES: ${filteredLines.joinToString(" | ")}")
        }
        
        val lines = filteredLines
        
        // NOWA LOGIKA: Czytaj WSZYSTKIE dostępne linie, nie tylko "nowe"
        // Znajdź nowy tekst (ale jeśli nie ma nowego, czytaj wszystko)
        val newLines = lines.filter { line ->
            !lastReadText.contains(line)
        }
        
        // Jeśli nie ma nowych linii, użyj wszystkich dostępnych linii
        val linesToProcess = if (newLines.isEmpty()) {
            Log.d(TAG, "⚠️ No new lines found, using ALL ${lines.size} lines")
            serviceScope.launch {
                fileLogger.logServiceEvent("⚠️ NO NEW LINES: Using ALL ${lines.size} lines instead")
            }
            lines
        } else {
            Log.d(TAG, "✅ Found ${newLines.size} new lines out of ${lines.size} total")
            serviceScope.launch {
                fileLogger.logServiceEvent("✅ NEW LINES FOUND: ${newLines.size} new out of ${lines.size} total")
            }
            newLines
        }
        
        if (linesToProcess.isNotEmpty()) {
            // Zaktualizuj zbiór przeczytanego tekstu (tylko jeśli były nowe linie)
            if (newLines.isNotEmpty()) {
                lastReadText.clear()
                lastReadText.addAll(lines.takeLast(15)) // Zwiększyłem z 10 na 15
            }
            
            // PRZECZYTAJ WIĘCEJ TEKSTU - ZWIĘKSZAMY DO 500 znaków!
            var textToRead = ""
            var charCount = 0
            val targetLength = 500  // ZWIĘKSZONE z 250 na 500!
            
            // Loguj szczegóły budowania tekstu
            Log.d(TAG, "🔧 Building text from ${linesToProcess.size} lines (${newLines.size} new), target: $targetLength chars")
            serviceScope.launch {
                fileLogger.logServiceEvent("🔧 TEXT BUILDING: ${linesToProcess.size} lines available (${newLines.size} new), target: $targetLength chars")
            }
            
            for ((index, line) in linesToProcess.withIndex()) {
                val lineWithSeparator = if (textToRead.isEmpty()) line else ". $line"
                val wouldBeLength = charCount + lineWithSeparator.length
                
                Log.d(TAG, "🔧 Line $index: '$line' (${line.length} chars) -> total would be $wouldBeLength")
                
                if (wouldBeLength <= targetLength) {
                    textToRead += lineWithSeparator
                    charCount += lineWithSeparator.length
                    Log.d(TAG, "✅ Added line $index, total now: $charCount chars")
                } else {
                    Log.d(TAG, "❌ Skipping line $index - would exceed $targetLength limit ($wouldBeLength chars)")
                    // NOWA LOGIKA: Jeśli mamy jeszcze miejsce, dodaj skróconą wersję
                    val remainingSpace = targetLength - charCount
                    if (remainingSpace > 20) { // Jeśli zostało co najmniej 20 znaków
                        val truncatedLine = line.take(remainingSpace - 3) + "..."
                        textToRead += if (textToRead.isEmpty()) truncatedLine else ". $truncatedLine"
                        Log.d(TAG, "📝 Added truncated line: '${truncatedLine}'")
                    }
                    break
                }
            }
            
            // Loguj finalne wyniki
            serviceScope.launch {
                fileLogger.logServiceEvent("🔧 FINAL TEXT BUILD: ${textToRead.length} chars from ${linesToProcess.size} available lines (target was $targetLength)")
            }
            
            if (textToRead.length > 5) { // Zmniejszyłem próg z 10 na 5
                currentReadingText = textToRead
                
                // Loguj szczegóły czytania
                serviceScope.launch {
                    fileLogger.logServiceEvent("🔊 TTS Reading: ${textToRead.length} chars - \"${textToRead.take(50)}...\"")
                }
                
                // Zaktualizuj notyfikację z aktualnie czytanym tekstem
                updateNotification()
                
                // Czytaj bezpośrednio bez prefiksu
                speakTextDirect(textToRead)
                Log.d(TAG, "🔊 Reading ${textToRead.length} chars: $textToRead")
                
                // Zapisz też do FileLogger żeby było widoczne w Podglądzie Logów
                serviceScope.launch {
                    fileLogger.logServiceEvent("🔊 READING: ${textToRead.length} chars - '$textToRead'")
                }
            }
        } else {
            Log.d(TAG, "🔊 No new text to read")
            serviceScope.launch {
                fileLogger.logServiceEvent("🔄 OCR Result: No new text (${lines.size} lines already read)")
            }
        }
    }
    
    private fun speakText(text: String) {
        if (ttsInitialized && text.isNotBlank()) {
            // Zatrzymaj poprzednie czytanie
            tts.stop()
            
            // Skróć tekst jeśli za długi (używa starych ustawień dla kompatybilności)
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
    
    /**
     * Aktualizuje notyfikację z aktualnie czytanym tekstem
     */
    private fun updateNotification() {
        try {
            val updatedNotification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
            Log.d(TAG, "📱 Notification updated with reading text")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating notification", e)
        }
    }
    
    /**
     * Czyta tekst bezpośrednio z konfigurowalnymi ustawieniami skracania
     */
    private fun speakTextDirect(text: String) {
        if (ttsInitialized && text.isNotBlank()) {
            // Loguj rozpoczęcie TTS
            serviceScope.launch {
                fileLogger.logServiceEvent("🎤 TTS Start: Speaking ${text.length} chars at ${speechRate}x speed")
            }
            
            // Zatrzymaj poprzednie czytanie
            tts.stop()
            
            // Pobierz ustawienia skracania z preferencji (domyślne: skracaj do 250 znaków)
            val maxLength = 250 // Stała wartość jak prosił user
            val shouldTruncate = true // Domyślnie skracaj
            
            // Skróć tekst jeśli włączone skracanie i tekst jest za długi
            val textToSpeak = if (shouldTruncate && text.length > maxLength) {
                text.take(maxLength) + "..."
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
            
            Log.d(TAG, "🔊 TTS speaking directly (${textToSpeak.length} chars): ${textToSpeak.take(50)}...")
            
            // Loguj szczegóły TTS do FileLogger
            serviceScope.launch {
                fileLogger.logServiceEvent("🎤 TTS SPEAKING: '${textToSpeak}' (${textToSpeak.length} chars at ${speechRate}x)")
            }
            
            // Loguj zakończenie TTS (przybliżony czas)
            serviceScope.launch {
                val estimatedDurationMs = (textToSpeak.length * 50 / speechRate).toLong() // Szacowana długość
                delay(estimatedDurationMs)
                fileLogger.logServiceEvent("✅ TTS Complete: Finished speaking '${textToSpeak.take(30)}...'")
            }
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
            speakText("Screen Reader aktywny. Szybkość 3x, co $readInterval sekund.")
            
            Log.d(TAG, "✅ TTS initialized successfully (3x speed, 10s interval, crop top+bottom 10%)")
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

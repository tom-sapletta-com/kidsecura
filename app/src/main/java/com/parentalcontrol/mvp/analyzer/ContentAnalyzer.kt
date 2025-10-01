package com.parentalcontrol.mvp.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class AnalysisResult(
    val isSuspicious: Boolean,
    val confidence: Float,
    val detectionType: String,
    val description: String,
    val extractedText: String? = null,
    val violenceScore: Float = 0f,
    val inappropriateContentScore: Float = 0f
)

class ContentAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "ContentAnalyzer"
        private const val MODEL_PATH = "violence_detection_model.tflite"
        private const val INPUT_SIZE = 224
        private const val VIOLENCE_THRESHOLD = 0.7f
        private const val TEXT_THREAT_THRESHOLD = 0.6f
    }
    
    private var interpreter: Interpreter? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // Lista słów kluczowych do wykrywania zagrożeń
    private val threatKeywords = listOf(
        // Przemoc
        "zabić", "zabije", "zabiję", "śmierć", "samobójstwo", "krzywda", "ból",
        "pobić", "pobiję", "uderzyć", "uderzę", "zniszczyć", "zniszczę",
        
        // Cyberprzemoc
        "nienawidzę", "nienawiść", "głupi", "brzydki", "gruby", "idiota",
        "debil", "frajer", "śmieć",
        
        // Grooming / nadużycia
        "tajemnica", "nie mów", "sekret", "spotkajmy się", "sam na sam",
        "zdjęcie", "nago", "pokażesz",
        
        // Substancje
        "narkotyki", "alkohol", "papierosy", "używki", "dealer"
    )
    
    // Lista aplikacji wysokiego ryzyka
    private val riskyApps = listOf(
        "discord", "snapchat", "kik", "omegle", "chatroulette",
        "telegram", "whisper", "tiktok"
    )
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            // W MVP używamy prostego modelu lub placeholder
            // W produkcji załaduj prawdziwy model TFLite
            val modelFile = context.assets.openFd(MODEL_PATH)
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            interpreter = Interpreter(modelBuffer)
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model, using fallback analysis", e)
            // W MVP używamy analizy heurystycznej jeśli model niedostępny
        }
    }
    
    /**
     * Analizuje tylko tekst bez przetwarzania obrazu - dla AccessibilityService
     */
    suspend fun analyzeTextOnly(text: String): AnalysisResult {
        val textAnalysis = analyzeText(text)
        
        return AnalysisResult(
            isSuspicious = textAnalysis.isSuspicious,
            confidence = textAnalysis.score,
            detectionType = textAnalysis.type,
            description = "Wykryto: ${textAnalysis.keywords.joinToString(", ")}",
            extractedText = text
        )
    }
    
    suspend fun analyze(bitmap: Bitmap): AnalysisResult {
        // 1. Ekstrakcja tekstu (OCR)
        val extractedText = extractText(bitmap)
        
        // 2. Analiza tekstu
        val textAnalysis = analyzeText(extractedText)
        
        // 3. Analiza wizualna (jeśli model dostępny)
        val visualAnalysis = if (interpreter != null) {
            analyzeVisualContent(bitmap)
        } else {
            // Fallback - prosta analiza kolorów dla wykrycia potencjalnej przemocy
            analyzeFallbackVisual(bitmap)
        }
        
        // 4. Kombinacja wyników
        return combineAnalysisResults(textAnalysis, visualAnalysis, extractedText)
    }
    
    private suspend fun extractText(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text", e)
            ""
        }
    }
    
    private fun analyzeText(text: String): TextAnalysisResult {
        if (text.isEmpty()) {
            return TextAnalysisResult(false, 0f, "", emptyList())
        }
        
        val lowercaseText = text.lowercase()
        val foundKeywords = mutableListOf<String>()
        var threatScore = 0f
        
        // Sprawdź słowa kluczowe
        threatKeywords.forEach { keyword ->
            if (lowercaseText.contains(keyword)) {
                foundKeywords.add(keyword)
                threatScore += 0.3f
            }
        }
        
        // Sprawdź aplikacje wysokiego ryzyka
        riskyApps.forEach { app ->
            if (lowercaseText.contains(app)) {
                foundKeywords.add("Aplikacja: $app")
                threatScore += 0.2f
            }
        }
        
        // Analiza kontekstu (prosty przykład)
        if (lowercaseText.contains("spotkanie") && lowercaseText.contains("tajemnica")) {
            threatScore += 0.4f
            foundKeywords.add("Podejrzany kontekst: spotkanie + tajemnica")
        }
        
        // Wykrywanie numerów telefonu i adresów
        val phonePattern = Regex("\\b\\d{9,12}\\b")
        val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        
        if (phonePattern.containsMatchIn(text)) {
            foundKeywords.add("Wykryto numer telefonu")
            threatScore += 0.2f
        }
        
        if (emailPattern.containsMatchIn(text)) {
            foundKeywords.add("Wykryto adres email")
            threatScore += 0.1f
        }
        
        val isSuspicious = threatScore >= TEXT_THREAT_THRESHOLD
        val detectionType = when {
            foundKeywords.any { it in threatKeywords.take(10) } -> "Przemoc/Zagrożenie"
            foundKeywords.any { it in threatKeywords.slice(10..20) } -> "Cyberprzemoc"
            foundKeywords.any { it.startsWith("Aplikacja:") } -> "Ryzykowna aplikacja"
            else -> "Podejrzana treść"
        }
        
        return TextAnalysisResult(isSuspicious, threatScore.coerceIn(0f, 1f), detectionType, foundKeywords)
    }
    
    private fun analyzeVisualContent(bitmap: Bitmap): VisualAnalysisResult {
        try {
            // Przygotuj obraz do modelu
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = preprocessImage(resizedBitmap)
            
            // Przygotuj output
            val output = Array(1) { FloatArray(2) } // [normalny, przemoc]
            
            // Uruchom inference
            interpreter?.run(inputBuffer, output)
            
            val violenceScore = output[0][1]
            val isSuspicious = violenceScore >= VIOLENCE_THRESHOLD
            
            return VisualAnalysisResult(
                isSuspicious,
                violenceScore,
                if (isSuspicious) "Potencjalna przemoc wizualna" else "Bezpieczna treść"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in visual analysis", e)
            return analyzeFallbackVisual(bitmap)
        }
    }
    
    private fun analyzeFallbackVisual(bitmap: Bitmap): VisualAnalysisResult {
        // Prosta analiza heurystyczna jako fallback
        val redPixels = countRedPixels(bitmap)
        val darkPixels = countDarkPixels(bitmap)
        
        val totalPixels = bitmap.width * bitmap.height
        val redRatio = redPixels.toFloat() / totalPixels
        val darkRatio = darkPixels.toFloat() / totalPixels
        
        // Heurystyka: dużo czerwieni może oznaczać przemoc/krew
        // Dużo ciemnych kolorów może oznaczać mroczną treść
        val suspicionScore = (redRatio * 0.6f + darkRatio * 0.4f).coerceIn(0f, 1f)
        val isSuspicious = suspicionScore > 0.3f
        
        return VisualAnalysisResult(
            isSuspicious,
            suspicionScore,
            when {
                redRatio > 0.2f -> "Wykryto dużo czerwieni"
                darkRatio > 0.5f -> "Mroczna/ciemna treść"
                else -> "Normalna treść"
            }
        )
    }
    
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        
        return inputBuffer
    }
    
    private fun countRedPixels(bitmap: Bitmap): Int {
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            
            // Czerwony dominujący
            if (r > 150 && r > g * 1.5 && r > b * 1.5) {
                count++
            }
        }
        return count
    }
    
    private fun countDarkPixels(bitmap: Bitmap): Int {
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            
            // Ciemne piksele
            if ((r + g + b) < 150) {
                count++
            }
        }
        return count
    }
    
    private fun combineAnalysisResults(
        textResult: TextAnalysisResult,
        visualResult: VisualAnalysisResult,
        extractedText: String
    ): AnalysisResult {
        // Kombinacja wyników z różnych analiz
        val combinedScore = (textResult.score * 0.6f + visualResult.score * 0.4f).coerceIn(0f, 1f)
        val isSuspicious = textResult.isSuspicious || visualResult.isSuspicious || combinedScore >= 0.5f
        
        val description = buildString {
            if (textResult.isSuspicious) {
                append("Tekst: ${textResult.keywords.joinToString(", ")}")
            }
            if (visualResult.isSuspicious) {
                if (isNotEmpty()) append(" | ")
                append("Obraz: ${visualResult.description}")
            }
        }
        
        val detectionType = when {
            textResult.isSuspicious && visualResult.isSuspicious -> "Złożone zagrożenie"
            textResult.isSuspicious -> textResult.type
            visualResult.isSuspicious -> visualResult.description
            else -> "Bezpieczna treść"
        }
        
        return AnalysisResult(
            isSuspicious = isSuspicious,
            confidence = combinedScore,
            detectionType = detectionType,
            description = if (description.isNotEmpty()) description else "Nie wykryto zagrożeń",
            extractedText = if (extractedText.length > 500) extractedText.take(500) + "..." else extractedText,
            violenceScore = visualResult.score,
            inappropriateContentScore = textResult.score
        )
    }
    
    fun cleanup() {
        interpreter?.close()
        textRecognizer.close()
    }
    
    private data class TextAnalysisResult(
        val isSuspicious: Boolean,
        val score: Float,
        val type: String,
        val keywords: List<String>
    )
    
    private data class VisualAnalysisResult(
        val isSuspicious: Boolean,
        val score: Float,
        val description: String
    )
}

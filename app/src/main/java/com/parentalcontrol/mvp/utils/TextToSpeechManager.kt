package com.parentalcontrol.mvp.utils

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val UTTERANCE_ID = "screen_reader_tts"
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalVolume = 0
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        try {
            tts = TextToSpeech(context, this)
            Log.d(TAG, "🔊 TextToSpeech initialization started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing TTS", e)
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts?.let { ttsInstance ->
                    // Ustaw język polski
                    val result = ttsInstance.setLanguage(Locale("pl", "PL"))
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // Fallback na angielski
                        Log.w(TAG, "⚠️ Polish not supported, using English")
                        ttsInstance.setLanguage(Locale.ENGLISH)
                    }
                    
                    // Ustaw parametry TTS
                    ttsInstance.setSpeechRate(1.0f)  // Normalna prędkość
                    ttsInstance.setPitch(1.0f)       // Normalny ton
                    
                    // Callback dla statusu czytania
                    ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            Log.d(TAG, "🔊 TTS started: $utteranceId")
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            Log.d(TAG, "✅ TTS completed: $utteranceId")
                        }
                        
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "❌ TTS error: $utteranceId")
                        }
                    })
                    
                    isInitialized = true
                    Log.d(TAG, "✅ TTS initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error configuring TTS", e)
            }
        } else {
            Log.e(TAG, "❌ TTS initialization failed with status: $status")
        }
    }
    
    /**
     * Czyta tekst na głos z maksymalną głośnością
     */
    fun speakText(text: String) {
        if (!isInitialized || text.isBlank()) {
            Log.w(TAG, "⚠️ TTS not initialized or empty text")
            return
        }
        
        try {
            // Zapisz aktualną głośność
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            // Ustaw maksymalną głośność
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            
            // Czyść kolejkę i mów
            tts?.stop()
            
            // Skróć tekst jeśli za długi
            val textToSpeak = if (text.length > 200) {
                text.take(200) + "... koniec tekstu"
            } else {
                text
            }
            
            Log.d(TAG, "🔊 Speaking: ${textToSpeak.take(50)}...")
            
            val result = tts?.speak(
                textToSpeak,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UTTERANCE_ID
            )
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "❌ Error speaking text")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in speakText", e)
        }
    }
    
    /**
     * Czyta tekst z prefiksem informującym o źródle
     */
    fun speakScreenText(text: String, appName: String) {
        if (text.isBlank()) {
            speakText("Brak tekstu na ekranie w aplikacji $appName")
        } else {
            speakText("Tekst z aplikacji $appName: $text")
        }
    }
    
    /**
     * Zatrzymuje czytanie i przywraca głośność
     */
    fun stop() {
        try {
            Log.d(TAG, "🛑 Stopping TTS")
            
            tts?.stop()
            
            // Przywróć oryginalną głośność
            if (originalVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping TTS", e)
        }
    }
    
    /**
     * Sprawdza czy TTS jest gotowy
     */
    fun isReady(): Boolean = isInitialized && tts != null
    
    /**
     * Zwalnia zasoby TTS
     */
    fun cleanup() {
        try {
            Log.d(TAG, "🧹 Cleaning up TTS")
            
            stop()
            
            tts?.shutdown()
            tts = null
            isInitialized = false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cleaning up TTS", e)
        }
    }
}

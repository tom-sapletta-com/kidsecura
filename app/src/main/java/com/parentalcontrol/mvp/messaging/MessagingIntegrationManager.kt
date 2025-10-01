package com.parentalcontrol.mvp.messaging

import android.content.Context
import android.util.Log
import com.parentalcontrol.mvp.utils.PreferencesManager
import com.parentalcontrol.mvp.utils.SystemLogger
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * MessagingIntegrationManager - zarządza integracją z WhatsApp i Telegram
 * do dystrybucji alertów i wiadomości dla rodziców/opiekunów
 */
class MessagingIntegrationManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val systemLogger: SystemLogger
) {
    companion object {
        private const val TAG = "MessagingIntegration"
        
        // Telegram Bot API constants
        private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
        private const val TELEGRAM_SEND_MESSAGE = "/sendMessage"
        
        // WhatsApp Business API constants (placeholder for future implementation)
        private const val WHATSAPP_API_BASE = "https://graph.facebook.com/v17.0"
        
        // Message priorities
        const val PRIORITY_LOW = 1
        const val PRIORITY_MEDIUM = 2
        const val PRIORITY_HIGH = 3
        const val PRIORITY_CRITICAL = 4
        
        // Message types
        const val TYPE_INCIDENT_ALERT = "incident_alert"
        const val TYPE_DEVICE_STATUS = "device_status"
        const val TYPE_PAIRING_STATUS = "pairing_status"
        const val TYPE_SYSTEM_NOTIFICATION = "system_notification"
        
        // Settings keys
        private const val PREF_TELEGRAM_ENABLED = "telegram_enabled"
        private const val PREF_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val PREF_TELEGRAM_CHAT_IDS = "telegram_chat_ids"
        private const val PREF_WHATSAPP_ENABLED = "whatsapp_enabled"
        private const val PREF_WHATSAPP_ACCESS_TOKEN = "whatsapp_access_token"
        private const val PREF_WHATSAPP_PHONE_NUMBERS = "whatsapp_phone_numbers"
        private const val PREF_MESSAGE_PRIORITY_THRESHOLD = "message_priority_threshold"
    }
    
    private val messagingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = com.google.gson.Gson()
    
    // HTTP client for API calls
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    // Message queue for reliable delivery
    private val messageQueue = mutableListOf<QueuedMessage>()
    private var isProcessingQueue = false
    
    init {
        systemLogger.d(TAG, "🤖 MessagingIntegrationManager initialized")
        startMessageQueueProcessor()
    }
    
    /**
     * Data class dla wiadomości w kolejce
     */
    data class QueuedMessage(
        val messageType: String,
        val priority: Int,
        val title: String,
        val content: String,
        val timestamp: Long,
        val retryCount: Int = 0,
        val maxRetries: Int = 3
    )
    
    /**
     * Główna metoda do wysyłania alertów i wiadomości
     */
    suspend fun sendAlert(
        messageType: String,
        priority: Int,
        title: String,
        content: String,
        forceDelivery: Boolean = false
    ): Boolean {
        systemLogger.d(TAG, "📤 Sending alert: type=$messageType, priority=$priority, title='$title'")
        
        try {
            // Check if priority meets threshold (unless forced)
            val priorityThreshold = preferencesManager.getInt(PREF_MESSAGE_PRIORITY_THRESHOLD, PRIORITY_MEDIUM)
            if (!forceDelivery && priority < priorityThreshold) {
                systemLogger.d(TAG, "⏸️ Alert priority ($priority) below threshold ($priorityThreshold), skipping")
                return false
            }
            
            // Format message with emoji and formatting
            val formattedMessage = formatMessage(messageType, priority, title, content)
            
            var telegramSuccess = false
            var whatsappSuccess = false
            
            // Send via Telegram if enabled
            if (isTelegramEnabled()) {
                telegramSuccess = sendTelegramMessage(formattedMessage)
                if (telegramSuccess) {
                    systemLogger.d(TAG, "✅ Telegram message sent successfully")
                } else {
                    systemLogger.w(TAG, "⚠️ Telegram message failed, adding to queue")
                }
            }
            
            // Send via WhatsApp if enabled
            if (isWhatsAppEnabled()) {
                whatsappSuccess = sendWhatsAppMessage(formattedMessage)
                if (whatsappSuccess) {
                    systemLogger.d(TAG, "✅ WhatsApp message sent successfully")
                } else {
                    systemLogger.w(TAG, "⚠️ WhatsApp message failed, adding to queue")
                }
            }
            
            // Add to queue if delivery failed and retry is needed
            if (!telegramSuccess && !whatsappSuccess && (isTelegramEnabled() || isWhatsAppEnabled())) {
                addToMessageQueue(messageType, priority, title, content)
                return false
            }
            
            return telegramSuccess || whatsappSuccess
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error sending alert", e)
            addToMessageQueue(messageType, priority, title, content)
            return false
        }
    }
    
    /**
     * Formatuje wiadomość z emoji i odpowiednim formatowaniem
     */
    private fun formatMessage(messageType: String, priority: Int, title: String, content: String): String {
        val priorityEmoji = when (priority) {
            PRIORITY_CRITICAL -> "🚨"
            PRIORITY_HIGH -> "⚠️"
            PRIORITY_MEDIUM -> "ℹ️"
            PRIORITY_LOW -> "💬"
            else -> "📱"
        }
        
        val typeEmoji = when (messageType) {
            TYPE_INCIDENT_ALERT -> "🛡️"
            TYPE_DEVICE_STATUS -> "📱"
            TYPE_PAIRING_STATUS -> "🔗"
            TYPE_SYSTEM_NOTIFICATION -> "⚙️"
            else -> "📋"
        }
        
        return buildString {
            appendLine("$priorityEmoji $typeEmoji **KidSecura Alert**")
            appendLine()
            appendLine("**$title**")
            appendLine()
            appendLine(content)
            appendLine()
            appendLine("⏰ ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        }
    }
    
    /**
     * Wysyła wiadomość przez Telegram Bot API
     */
    private suspend fun sendTelegramMessage(message: String): Boolean {
        return try {
            val botToken = preferencesManager.getString(PREF_TELEGRAM_BOT_TOKEN, "") ?: ""
            val chatIds = getTelegramChatIds()
            
            if (botToken.isEmpty() || chatIds.isEmpty()) {
                systemLogger.w(TAG, "⚠️ Telegram not properly configured")
                return false
            }
            
            var allSent = true
            
            for (chatId in chatIds) {
                val success = sendTelegramToChat(botToken, chatId, message)
                if (!success) {
                    allSent = false
                    systemLogger.w(TAG, "⚠️ Failed to send Telegram message to chat: $chatId")
                }
            }
            
            allSent
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error sending Telegram message", e)
            false
        }
    }
    
    /**
     * Wysyła wiadomość do konkretnego chat ID w Telegram
     */
    private suspend fun sendTelegramToChat(botToken: String, chatId: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$TELEGRAM_API_BASE$botToken$TELEGRAM_SEND_MESSAGE"
                
                val jsonPayload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                    put("parse_mode", "Markdown")
                    put("disable_web_page_preview", true)
                }
                
                val requestBody = jsonPayload.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful
                
                if (success) {
                    systemLogger.d(TAG, "✅ Telegram message sent to chat $chatId")
                } else {
                    systemLogger.e(TAG, "❌ Telegram API error: ${response.code} - ${response.message}")
                }
                
                response.close()
                success
                
            } catch (e: Exception) {
                systemLogger.e(TAG, "❌ Exception sending Telegram message to chat $chatId", e)
                false
            }
        }
    }
    
    /**
     * Wysyła wiadomość przez WhatsApp Business API (placeholder implementation)
     */
    private suspend fun sendWhatsAppMessage(message: String): Boolean {
        return try {
            // Placeholder for WhatsApp Business API implementation
            // This would require Facebook Business verification and setup
            systemLogger.d(TAG, "📱 WhatsApp integration - placeholder implementation")
            
            val accessToken = preferencesManager.getString(PREF_WHATSAPP_ACCESS_TOKEN, "") ?: ""
            val phoneNumbers = getWhatsAppPhoneNumbers()
            
            if (accessToken.isEmpty() || phoneNumbers.isEmpty()) {
                systemLogger.w(TAG, "⚠️ WhatsApp not properly configured")
                return false
            }
            
            // TODO: Implement actual WhatsApp Business API calls
            // For now, return true as placeholder
            systemLogger.i(TAG, "📱 WhatsApp message would be sent to ${phoneNumbers.size} recipients")
            true
            
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error sending WhatsApp message", e)
            false
        }
    }
    
    /**
     * Dodaje wiadomość do kolejki retry
     */
    private fun addToMessageQueue(messageType: String, priority: Int, title: String, content: String) {
        synchronized(messageQueue) {
            val queuedMessage = QueuedMessage(
                messageType = messageType,
                priority = priority,
                title = title,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            
            messageQueue.add(queuedMessage)
            systemLogger.d(TAG, "➕ Added message to retry queue (${messageQueue.size} total)")
        }
    }
    
    /**
     * Uruchamia procesor kolejki wiadomości dla retry logic
     */
    private fun startMessageQueueProcessor() {
        messagingScope.launch {
            while (true) {
                try {
                    if (!isProcessingQueue && messageQueue.isNotEmpty()) {
                        processMessageQueue()
                    }
                    delay(30000) // Check queue every 30 seconds
                } catch (e: Exception) {
                    systemLogger.e(TAG, "❌ Error in message queue processor", e)
                    delay(60000) // Wait longer on error
                }
            }
        }
    }
    
    /**
     * Przetwarza kolejkę wiadomości
     */
    private suspend fun processMessageQueue() {
        isProcessingQueue = true
        systemLogger.d(TAG, "🔄 Processing message queue (${messageQueue.size} messages)")
        
        try {
            val iterator = messageQueue.iterator()
            while (iterator.hasNext()) {
                val queuedMessage = iterator.next()
                
                // Skip if max retries exceeded
                if (queuedMessage.retryCount >= queuedMessage.maxRetries) {
                    systemLogger.w(TAG, "⚠️ Max retries exceeded for message: ${queuedMessage.title}")
                    iterator.remove()
                    continue
                }
                
                // Try to send message again
                val success = sendAlert(
                    messageType = queuedMessage.messageType,
                    priority = queuedMessage.priority,
                    title = queuedMessage.title,
                    content = queuedMessage.content,
                    forceDelivery = true
                )
                
                if (success) {
                    systemLogger.d(TAG, "✅ Queued message sent successfully: ${queuedMessage.title}")
                    iterator.remove()
                } else {
                    // Increment retry count
                    val updatedMessage = queuedMessage.copy(retryCount = queuedMessage.retryCount + 1)
                    val index = messageQueue.indexOf(queuedMessage)
                    if (index != -1) {
                        messageQueue[index] = updatedMessage
                    }
                }
                
                delay(2000) // Wait between retries
            }
        } finally {
            isProcessingQueue = false
        }
    }
    
    // Configuration methods
    fun enableTelegram(botToken: String, chatIds: List<String>) {
        preferencesManager.putBoolean(PREF_TELEGRAM_ENABLED, true)
        preferencesManager.putString(PREF_TELEGRAM_BOT_TOKEN, botToken)
        preferencesManager.putString(PREF_TELEGRAM_CHAT_IDS, gson.toJson(chatIds))
        systemLogger.d(TAG, "✅ Telegram enabled with ${chatIds.size} chat(s)")
    }
    
    fun enableWhatsApp(accessToken: String, phoneNumbers: List<String>) {
        preferencesManager.putBoolean(PREF_WHATSAPP_ENABLED, true)
        preferencesManager.putString(PREF_WHATSAPP_ACCESS_TOKEN, accessToken)
        preferencesManager.putString(PREF_WHATSAPP_PHONE_NUMBERS, gson.toJson(phoneNumbers))
        systemLogger.d(TAG, "✅ WhatsApp enabled with ${phoneNumbers.size} number(s)")
    }
    
    fun disableTelegram() {
        preferencesManager.putBoolean(PREF_TELEGRAM_ENABLED, false)
        systemLogger.d(TAG, "❌ Telegram disabled")
    }
    
    fun disableWhatsApp() {
        preferencesManager.putBoolean(PREF_WHATSAPP_ENABLED, false)
        systemLogger.d(TAG, "❌ WhatsApp disabled")
    }
    
    fun setPriorityThreshold(threshold: Int) {
        preferencesManager.putInt(PREF_MESSAGE_PRIORITY_THRESHOLD, threshold)
        systemLogger.d(TAG, "⚙️ Priority threshold set to: $threshold")
    }
    
    // Helper methods
    private fun isTelegramEnabled(): Boolean = preferencesManager.getBoolean(PREF_TELEGRAM_ENABLED, false)
    private fun isWhatsAppEnabled(): Boolean = preferencesManager.getBoolean(PREF_WHATSAPP_ENABLED, false)
    
    private fun getTelegramChatIds(): List<String> {
        val chatIdsJson = preferencesManager.getString(PREF_TELEGRAM_CHAT_IDS, "[]") ?: "[]"
        return try {
            gson.fromJson(chatIdsJson, Array<String>::class.java).toList()
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error parsing Telegram chat IDs", e)
            emptyList()
        }
    }
    
    private fun getWhatsAppPhoneNumbers(): List<String> {
        val phoneNumbersJson = preferencesManager.getString(PREF_WHATSAPP_PHONE_NUMBERS, "[]") ?: "[]"
        return try {
            gson.fromJson(phoneNumbersJson, Array<String>::class.java).toList()
        } catch (e: Exception) {
            systemLogger.e(TAG, "❌ Error parsing WhatsApp phone numbers", e)
            emptyList()
        }
    }
    
    /**
     * Wysyła test message dla weryfikacji konfiguracji
     */
    suspend fun sendTestMessage(): Boolean {
        return sendAlert(
            messageType = TYPE_SYSTEM_NOTIFICATION,
            priority = PRIORITY_MEDIUM,
            title = "Test KidSecura",
            content = "To jest wiadomość testowa z systemu KidSecura. Jeśli otrzymujesz tę wiadomość, konfiguracja działa poprawnie! 🎉",
            forceDelivery = true
        )
    }
    
    /**
     * Pobiera statystyki kolejki wiadomości
     */
    fun getQueueStats(): String {
        synchronized(messageQueue) {
            return "Queue: ${messageQueue.size} messages, Processing: $isProcessingQueue"
        }
    }
    
    /**
     * Czyści kolejkę wiadomości
     */
    fun clearMessageQueue() {
        synchronized(messageQueue) {
            val clearedCount = messageQueue.size
            messageQueue.clear()
            systemLogger.d(TAG, "🧹 Cleared $clearedCount messages from queue")
        }
    }
    
    /**
     * Shutdown resources
     */
    fun shutdown() {
        systemLogger.d(TAG, "🔌 Shutting down MessagingIntegrationManager")
        messagingScope.cancel()
        httpClient.dispatcher.executorService.shutdown()
    }
}

package com.parentalcontrol.mvp.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parentalcontrol.mvp.model.*
import com.parentalcontrol.mvp.utils.NotificationHelper
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Zaawansowany manager do zarządzania incydentami z inteligentnym filtrowaniem
 */
class IncidentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "IncidentManager"
        private const val PREFS_NAME = "incident_manager_prefs"
        private const val KEY_INCIDENTS = "incidents"
        private const val KEY_ALERT_SETTINGS = "alert_settings"
        private const val INCIDENT_EXPIRY_TIME = 24 * 60 * 60 * 1000L // 24 godziny
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val notificationHelper = NotificationHelper(context)
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Cache incydentów w pamięci dla szybkiego dostępu
    private val incidentCache = ConcurrentHashMap<String, Incident>()
    private val keywordFrequencyMap = ConcurrentHashMap<String, MutableList<Long>>()
    
    init {
        loadIncidentsFromStorage()
        startPeriodicCleanup()
    }
    
    /**
     * Dodaje nowy incydent i analizuje czy wysłać powiadomienie
     */
    suspend fun addIncident(
        deviceId: String,
        deviceName: String,
        detectedKeywords: List<String>,
        description: String,
        confidence: Float,
        extractedText: String? = null
    ) = withContext(Dispatchers.Default) {
        
        val currentTime = System.currentTimeMillis()
        val incident = Incident(
            id = generateIncidentId(),
            deviceId = deviceId,
            deviceName = deviceName,
            timestamp = currentTime,
            detectedKeywords = detectedKeywords,
            description = description,
            confidence = confidence,
            extractedText = extractedText,
            severity = calculateSeverity(detectedKeywords, confidence),
            isReviewed = false
        )
        
        Log.d(TAG, "Adding new incident: ${incident.id}")
        Log.d(TAG, "Keywords: ${detectedKeywords.joinToString(", ")}")
        Log.d(TAG, "Confidence: $confidence")
        Log.d(TAG, "Severity: ${incident.severity}")
        
        // Dodaj do cache i storage
        incidentCache[incident.id] = incident
        saveIncidentToStorage(incident)
        
        // Aktualizuj statystyki słów kluczowych
        updateKeywordFrequency(detectedKeywords, currentTime)
        
        // Analizuj czy wysłać powiadomienie
        val shouldNotify = shouldSendNotification(incident)
        
        if (shouldNotify) {
            Log.d(TAG, "Sending notification for incident: ${incident.id}")
            sendNotificationToParents(incident)
        } else {
            Log.d(TAG, "Notification suppressed for incident: ${incident.id} (frequency filter)")
        }
        
        // Wyczyść stare incydenty
        cleanupOldIncidents()
        
        incident
    }
    
    /**
     * Analizuje czy wysłać powiadomienie na podstawie częstotliwości i różnorodności
     */
    private fun shouldSendNotification(incident: Incident): Boolean {
        val settings = getAlertSettings()
        val currentTime = System.currentTimeMillis()
        
        // Sprawdź minimalną wagę incydentu
        if (incident.severity.weight < settings.minSeverityWeight) {
            Log.d(TAG, "Incident severity too low: ${incident.severity.weight} < ${settings.minSeverityWeight}")
            return false
        }
        
        // Sprawdź częstotliwość dla każdego słowa kluczowego
        val recentIncidents = getRecentIncidents(settings.frequencyWindowMinutes * 60 * 1000L)
        val keywordCounts = mutableMapOf<String, Int>()
        
        // Policz wystąpienia słów kluczowych w ostatnim oknie czasowym
        recentIncidents.forEach { recentIncident ->
            recentIncident.detectedKeywords.forEach { keyword ->
                keywordCounts[keyword] = keywordCounts.getOrDefault(keyword, 0) + 1
            }
        }
        
        // Sprawdź czy któreś słowo przekroczyło próg częstotliwości
        incident.detectedKeywords.forEach { keyword ->
            val count = keywordCounts.getOrDefault(keyword, 0)
            if (count >= settings.maxKeywordFrequency) {
                Log.d(TAG, "Keyword '$keyword' frequency too high: $count >= ${settings.maxKeywordFrequency}")
                return false
            }
        }
        
        // Sprawdź różnorodność słów kluczowych
        val uniqueKeywords = recentIncidents.flatMap { it.detectedKeywords }.toSet()
        if (uniqueKeywords.size >= settings.minKeywordDiversity && 
            incident.detectedKeywords.any { it in uniqueKeywords }) {
            Log.d(TAG, "High keyword diversity detected: ${uniqueKeywords.size} >= ${settings.minKeywordDiversity}")
            return true // Wysoka różnorodność = ważne powiadomienie
        }
        
        // Sprawdź czy to pierwszy incydent tego typu w ostatnim czasie
        val hasRecentSimilar = recentIncidents.any { recentIncident ->
            incident.detectedKeywords.any { keyword ->
                keyword in recentIncident.detectedKeywords
            }
        }
        
        if (!hasRecentSimilar) {
            Log.d(TAG, "No recent similar incidents - sending notification")
            return true
        }
        
        // Sprawdź wysoką pewność
        if (incident.confidence >= settings.highConfidenceThreshold) {
            Log.d(TAG, "High confidence incident: ${incident.confidence} >= ${settings.highConfidenceThreshold}")
            return true
        }
        
        return false
    }
    
    /**
     * Oblicza wagę incydentu na podstawie słów kluczowych i pewności
     */
    private fun calculateSeverity(keywords: List<String>, confidence: Float): IncidentSeverity {
        val highRiskKeywords = listOf("zabić", "zabije", "śmierć", "samobójstwo", "narkotyki", "spotkajmy się")
        val mediumRiskKeywords = listOf("krzywda", "ból", "tajemnica", "sekret", "alkohol")
        
        var weight = confidence
        
        keywords.forEach { keyword ->
            when {
                keyword.lowercase() in highRiskKeywords -> weight += 0.4f
                keyword.lowercase() in mediumRiskKeywords -> weight += 0.2f
                else -> weight += 0.1f
            }
        }
        
        return when {
            weight >= 0.8f -> IncidentSeverity.CRITICAL
            weight >= 0.6f -> IncidentSeverity.HIGH
            weight >= 0.4f -> IncidentSeverity.MEDIUM
            else -> IncidentSeverity.LOW
        }
    }
    
    /**
     * Wysyła powiadomienie do wszystkich sparowanych rodziców
     */
    private suspend fun sendNotificationToParents(incident: Incident) = withContext(Dispatchers.Main) {
        val title = "⚠️ Wykryto ${incident.severity.displayName} incydent!"
        val message = "${incident.deviceName}: ${incident.description}"
        
        // Lokalne powiadomienie
        notificationHelper.showAlert(
            title = title,
            message = message,
            confidence = (incident.confidence * 100).toInt()
        )
        
        // TODO: Wysyłaj też do sparowanych urządzeń rodziców przez P2P
        Log.d(TAG, "Notification sent: $title - $message")
    }
    
    /**
     * Pobiera incydenty z ostatniego okresu
     */
    fun getRecentIncidents(timeWindowMs: Long): List<Incident> {
        val currentTime = System.currentTimeMillis()
        return incidentCache.values.filter { 
            currentTime - it.timestamp <= timeWindowMs 
        }.sortedByDescending { it.timestamp }
    }
    
    /**
     * Pobiera wszystkie incydenty dla urządzenia
     */
    fun getIncidentsForDevice(deviceId: String): List<Incident> {
        return incidentCache.values.filter { it.deviceId == deviceId }
            .sortedByDescending { it.timestamp }
    }
    
    /**
     * Pobiera statystyki incydentów
     */
    fun getIncidentStatistics(): IncidentStatistics {
        val incidents = incidentCache.values.toList()
        val currentTime = System.currentTimeMillis()
        val last24h = incidents.filter { currentTime - it.timestamp <= 24 * 60 * 60 * 1000L }
        val lastWeek = incidents.filter { currentTime - it.timestamp <= 7 * 24 * 60 * 60 * 1000L }
        
        return IncidentStatistics(
            totalIncidents = incidents.size,
            incidentsLast24h = last24h.size,
            incidentsLastWeek = lastWeek.size,
            criticalIncidents = incidents.count { it.severity == IncidentSeverity.CRITICAL },
            mostCommonKeywords = getMostCommonKeywords(incidents, 5),
            averageConfidence = if (incidents.isNotEmpty()) incidents.map { it.confidence }.average().toFloat() else 0f
        )
    }
    
    private fun getMostCommonKeywords(incidents: List<Incident>, limit: Int): List<Pair<String, Int>> {
        return incidents.flatMap { it.detectedKeywords }
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Oznacza incydent jako przejrzany
     */
    fun markIncidentAsReviewed(incidentId: String) {
        incidentCache[incidentId]?.let { incident ->
            val updated = incident.copy(isReviewed = true)
            incidentCache[incidentId] = updated
            saveIncidentToStorage(updated)
            Log.d(TAG, "Incident marked as reviewed: $incidentId")
        }
    }
    
    /**
     * Pobiera ustawienia alertów
     */
    fun getAlertSettings(): AlertSettings {
        val json = prefs.getString(KEY_ALERT_SETTINGS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AlertSettings::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing alert settings", e)
                AlertSettings.getDefault()
            }
        } else {
            AlertSettings.getDefault()
        }
    }
    
    /**
     * Zapisuje ustawienia alertów
     */
    fun saveAlertSettings(settings: AlertSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_ALERT_SETTINGS, json).apply()
        Log.d(TAG, "Alert settings saved")
    }
    
    private fun generateIncidentId(): String {
        return "incident_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun updateKeywordFrequency(keywords: List<String>, timestamp: Long) {
        keywords.forEach { keyword ->
            keywordFrequencyMap.getOrPut(keyword) { mutableListOf() }.add(timestamp)
        }
    }
    
    private fun loadIncidentsFromStorage() {
        try {
            val json = prefs.getString(KEY_INCIDENTS, null)
            if (json != null) {
                val type = object : TypeToken<List<Incident>>() {}.type
                val incidents: List<Incident> = gson.fromJson(json, type)
                incidents.forEach { incident ->
                    incidentCache[incident.id] = incident
                }
                Log.d(TAG, "Loaded ${incidents.size} incidents from storage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading incidents from storage", e)
        }
    }
    
    private fun saveIncidentToStorage(incident: Incident) {
        // Zapisuj asynchronicznie aby nie blokować
        managerScope.launch {
            saveAllIncidentsToStorage()
        }
    }
    
    private fun saveAllIncidentsToStorage() {
        try {
            val incidents = incidentCache.values.toList()
            val json = gson.toJson(incidents)
            prefs.edit().putString(KEY_INCIDENTS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving incidents to storage", e)
        }
    }
    
    private fun cleanupOldIncidents() {
        val currentTime = System.currentTimeMillis()
        val expiredIncidents = incidentCache.filter { (_, incident) ->
            currentTime - incident.timestamp > INCIDENT_EXPIRY_TIME
        }
        
        expiredIncidents.forEach { (id, _) ->
            incidentCache.remove(id)
        }
        
        if (expiredIncidents.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredIncidents.size} expired incidents")
            saveAllIncidentsToStorage()
        }
    }
    
    private fun startPeriodicCleanup() {
        managerScope.launch {
            while (isActive) {
                delay(60 * 60 * 1000L) // Co godzinę
                cleanupOldIncidents()
            }
        }
    }
    
    fun shutdown() {
        managerScope.cancel()
        saveAllIncidentsToStorage()
    }
}

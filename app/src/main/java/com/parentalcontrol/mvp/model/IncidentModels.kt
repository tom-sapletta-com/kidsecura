package com.parentalcontrol.mvp.model

/**
 * Model incydentu bezpieczeństwa
 */
data class Incident(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val detectedKeywords: List<String>,
    val description: String,
    val confidence: Float,
    val extractedText: String? = null,
    val severity: IncidentSeverity,
    val isReviewed: Boolean = false
)

/**
 * Poziomy wagi incydentu
 */
enum class IncidentSeverity(val displayName: String, val weight: Float, val colorRes: Int) {
    LOW("Niski", 0.25f, android.R.color.holo_green_light),
    MEDIUM("Średni", 0.5f, android.R.color.holo_orange_light),
    HIGH("Wysoki", 0.75f, android.R.color.holo_red_light),
    CRITICAL("Krytyczny", 1.0f, android.R.color.holo_red_dark)
}

/**
 * Ustawienia alertów dla inteligentnego powiadamiania
 */
data class AlertSettings(
    // Progi częstotliwości
    val frequencyThreshold: Int = 5,               // Maksymalna liczba incydentów na godzinę
    val timeWindowMinutes: Int = 60,               // Okno czasowe w minutach
    
    // Wagi incydentów
    val criticalWeight: Float = 5.0f,              // Waga incydentów krytycznych
    val highWeight: Float = 3.0f,                  // Waga incydentów wysokiej wagi
    val minSeverityForAlert: IncidentSeverity = IncidentSeverity.MEDIUM, // Minimalny poziom do alertu
    
    // Różnorodność słów kluczowych
    val diversityThreshold: Int = 3,               // Minimalna liczba różnych słów kluczowych
    val requireDiversity: Boolean = true,          // Wymagaj różnorodności do wysłania alertu
    
    // Ustawienia powiadomień
    val enableNotifications: Boolean = true,       // Włącz powiadomienia push
    val enableSounds: Boolean = true,              // Odtwarzaj dźwięk powiadomienia
    val enableVibration: Boolean = true,           // Wibracje przy alertach
    val notificationCooldownMinutes: Int = 15,     // Cooldown powiadomień w minutach
    
    // Stare właściwości dla kompatybilności wstecznej
    val minSeverityWeight: Float = 0.4f,           // Minimalna waga incydentu do powiadomienia
    val maxKeywordFrequency: Int = frequencyThreshold, // Maksymalna częstotliwość słowa w oknie czasowym
    val frequencyWindowMinutes: Int = timeWindowMinutes, // Okno czasowe dla liczenia częstotliwości (minuty)
    val minKeywordDiversity: Int = diversityThreshold, // Minimalna różnorodność słów dla wysokiego alertu
    val highConfidenceThreshold: Float = 0.8f,     // Próg wysokiej pewności
    val enableSmartFiltering: Boolean = true,       // Włącz inteligentne filtrowanie
    val parentNotificationEnabled: Boolean = enableNotifications, // Powiadomienia dla rodziców
    val localNotificationEnabled: Boolean = enableNotifications  // Lokalne powiadomienia
) {
    companion object {
        fun getDefault() = AlertSettings()
    }
}

/**
 * Statystyki incydentów
 */
data class IncidentStatistics(
    val totalIncidents: Int,
    val incidentsLast24h: Int,
    val incidentsLastWeek: Int,
    val criticalIncidents: Int,
    val mostCommonKeywords: List<Pair<String, Int>>, // (słowo, liczba wystąpień)
    val averageConfidence: Float
)

/**
 * Model sparowanego urządzenia
 */
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long,
    val connectionStatus: ConnectionStatus,
    val pairingDate: Long,
    val isActive: Boolean = true,
    val nickname: String? = null // Opcjonalna nazwa nadana przez użytkownika
)

/**
 * Status zarządzania urządzeniami
 */
data class DeviceManagementStatus(
    val totalPairedDevices: Int,
    val activeDevices: Int,
    val parentDevices: Int,
    val childDevices: Int,
    val lastSyncTime: Long?,
    val pendingIncidents: Int
)

/**
 * Filtr dla wyświetlania incydentów
 */
data class IncidentFilter(
    val deviceIds: List<String>? = null,           // Filtruj według urządzeń
    val severityLevels: List<IncidentSeverity>? = null, // Filtruj według wagi
    val keywords: List<String>? = null,            // Filtruj według słów kluczowych
    val timeRangeStart: Long? = null,              // Początek zakresu czasowego
    val timeRangeEnd: Long? = null,                // Koniec zakresu czasowego
    val showReviewedOnly: Boolean = false,         // Tylko przejrzane
    val showUnreviewedOnly: Boolean = false        // Tylko nieprzejrzane
)

/**
 * Grupowanie incydentów według typu
 */
data class IncidentGroup(
    val groupType: String,                         // np. "Przemoc", "Cyberprzemoc"
    val incidents: List<Incident>,
    val totalCount: Int,
    val latestTimestamp: Long,
    val averageConfidence: Float
)

/**
 * Powiadomienie o incydencie wysyłane do urządzeń rodziców przez P2P
 */
data class IncidentNotification(
    val incidentId: String,
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long,
    val severity: IncidentSeverity,
    val description: String,
    val confidence: Float,
    val detectedKeywords: List<String>,
    val extractedText: String? = null
)

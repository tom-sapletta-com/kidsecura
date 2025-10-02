package com.parentalcontrol.mvp.config

/**
 * Centralna konfiguracja parowania urządzeń
 * 
 * Ten obiekt zawiera wszystkie stałe konfiguracyjne używane
 * w procesie parowania urządzeń rodzic-dziecko przez WiFi.
 */
object PairingConfig {
    
    /**
     * Port TCP używany do parowania i komunikacji między urządzeniami
     * 
     * WAŻNE: Oba urządzenia (rodzic i dziecko) muszą używać tego samego portu!
     * 
     * Domyślnie: 8888
     * - Alternatywny port HTTP (standardowy 8080 często zajęty)
     * - Zakres portów użytkownika (1024-49151)
     * - Mniej prawdopodobne konflikty z innymi aplikacjami
     * 
     * Zmiana portu:
     * 1. Zmień wartość PAIRING_PORT
     * 2. Przebuduj aplikację (./gradlew assembleDebug)
     * 3. Zainstaluj na WSZYSTKICH urządzeniach
     */
    const val PAIRING_PORT = 8888
    
    /**
     * Timeout dla skanowania sieci WiFi (w milisekundach)
     * 
     * Czas oczekiwania na odpowiedź z każdego adresu IP podczas skanowania.
     * - Krótszy = szybsze skanowanie, ale może przegapić wolne urządzenia
     * - Dłuższy = dokładniejsze, ale wolniejsze skanowanie
     */
    const val NETWORK_SCAN_TIMEOUT_MS = 2000
    
    /**
     * Maksymalna liczba równoczesnych skanów podczas wykrywania urządzeń
     * 
     * Kontroluje ile adresów IP jest sprawdzanych jednocześnie.
     * - Wyższa wartość = szybsze skanowanie, ale większe obciążenie sieci
     * - Niższa wartość = wolniejsze, ale bezpieczniejsze dla sieci
     */
    const val MAX_PARALLEL_SCANS = 50
    
    /**
     * Timeout dla połączenia TCP (w milisekundach)
     */
    const val CONNECTION_TIMEOUT_MS = 3000
    
    /**
     * Timeout dla odczytu danych (w milisekundach)
     */
    const val READ_TIMEOUT_MS = 2000
    
    /**
     * Timeout dla zapisu danych (w milisekundach)
     */
    const val WRITE_TIMEOUT_MS = 2000
    
    /**
     * Interwał heartbeat między urządzeniami (w milisekundach)
     * 
     * Jak często urządzenia sprawdzają czy są nadal połączone.
     */
    const val HEARTBEAT_INTERVAL_MS = 5000L
    
    /**
     * Timeout całego procesu parowania (w milisekundach)
     */
    const val PAIRING_TIMEOUT_MS = 10000L
    
    /**
     * Długość kodu parowania (liczba cyfr)
     */
    const val PAIRING_CODE_LENGTH = 6
    
    /**
     * Długość klucza bezpieczeństwa (liczba znaków)
     */
    const val SECURITY_KEY_LENGTH = 32
}

package com.parentalcontrol.mvp.config

/**
 * Centralna konfiguracja parowania urządzeń
 * 
 * Ten obiekt zawiera wszystkie stałe konfiguracyjne używane
 * w procesie parowania urządzeń rodzic-dziecko przez WiFi.
 */
object PairingConfig {
    
    /**
     * Lista portów TCP używanych do parowania i komunikacji między urządzeniami
     * 
     * System automatycznie próbuje kolejnych portów z listy:
     * - 8000 - Alternatywny HTTP
     * - 8080 - Popularny alternatywny HTTP (może być zajęty)
     * - 8443 - Alternatywny HTTPS
     * - 8888 - Mniej popularny, mniej kolizji
     * 
     * TRYB DZIECKA:
     * - Próbuje otworzyć porty w kolejności
     * - Używa pierwszego wolnego portu
     * - Testuje czy port faktycznie działa
     * 
     * TRYB RODZICA:
     * - Skanuje wszystkie porty na każdym urządzeniu
     * - Łączy się z pierwszym znalezionym otwartym portem
     * 
     * Zmiana listy portów:
     * 1. Edytuj AVAILABLE_PORTS
     * 2. Przebuduj aplikację (./gradlew assembleDebug)
     * 3. Zainstaluj na WSZYSTKICH urządzeniach
     */
    val AVAILABLE_PORTS = intArrayOf(8000, 8080, 8443, 8888)
    
    /**
     * Domyślny port (pierwszy z listy)
     * Używany jako fallback
     */
    val PAIRING_PORT: Int
        get() = AVAILABLE_PORTS[0]
    
    /**
     * Port preferowany (ostatni - najmniej kolizji)
     */
    val PREFERRED_PORT: Int
        get() = AVAILABLE_PORTS.last()
    
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
    
    /**
     * Timeout dla testowania czy port jest faktycznie otwarty (ms)
     */
    const val PORT_TEST_TIMEOUT_MS = 1000
    
    /**
     * Liczba prób testowania portu
     */
    const val PORT_TEST_RETRIES = 3
    
    /**
     * Delay między próbami testowania portu (ms)
     */
    const val PORT_TEST_RETRY_DELAY_MS = 500L
}

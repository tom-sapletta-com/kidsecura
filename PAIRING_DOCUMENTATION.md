# 📡 Dokumentacja Systemu Parowania WiFi

## Spis Treści
1. [Przegląd Systemu](#przegląd-systemu)
2. [Konfiguracja Portu](#konfiguracja-portu)
3. [Architektura Systemu](#architektura-systemu)
4. [Proces Parowania](#proces-parowania)
5. [Rozwiązywanie Problemów](#rozwiązywanie-problemów)
6. [FAQ](#faq)

---

## 📋 Przegląd Systemu

KidSecura używa bezpośredniego połączenia WiFi między urządzeniem **RODZICA** i **DZIECKA** do synchronizacji danych monitoringu w czasie rzeczywistym.

### Główne Komponenty

```
┌─────────────────┐         WiFi/TCP        ┌─────────────────┐
│   URZĄDZENIE    │ ◄──────────────────────► │   URZĄDZENIE    │
│     RODZICA     │    Port: 8888           │     DZIECKA     │
│   (Klient)      │    (konfigurowalne)     │   (Serwer)      │
└─────────────────┘                         └─────────────────┘
```

### Kluczowe Cechy

✅ **Peer-to-peer** - bezpośrednia komunikacja bez serwera zewnętrznego  
✅ **Automatyczne wykrywanie** - skanowanie sieci WiFi  
✅ **Szyfrowanie** - bezpieczna wymiana danych  
✅ **Heartbeat** - monitorowanie połączenia co 5 sekund  
✅ **Auto-reconnect** - automatyczne ponowne łączenie  

---

## ⚙️ Konfiguracja Portu

### Centralna Konfiguracja

Wszystkie ustawienia parowania znajdują się w:
```
app/src/main/java/com/parentalcontrol/mvp/config/PairingConfig.kt
```

### Zmiana Portu Parowania

**KROK 1: Edytuj PairingConfig.kt**
```kotlin
object PairingConfig {
    /**
     * Port TCP używany do parowania i komunikacji
     * 
     * WAŻNE: Oba urządzenia muszą używać tego samego portu!
     */
    const val PAIRING_PORT = 8888  // ← ZMIEŃ TUTAJ
}
```

**KROK 2: Przebuduj Aplikację**
```bash
cd kidsecura
./gradlew clean assembleDebug
```

**KROK 3: Zainstaluj na WSZYSTKICH Urządzeniach**
```bash
./gradlew installDebug
```

### Zalecane Porty

| Port | Opis | Zalecenie |
|------|------|-----------|
| 8080 | Standardowy alternatywny HTTP | ⚠️ Często zajęty |
| 8888 | Alternatywny HTTP | ✅ **Domyślny** |
| 9090 | Alternatywny | ✅ Dobry wybór |
| 7777 | Niestandardowy | ✅ Mało kolizji |

### Inne Konfiguracje

```kotlin
// Timeout skanowania sieci (ms)
const val NETWORK_SCAN_TIMEOUT_MS = 2000

// Maksymalna liczba równoczesnych skanów
const val MAX_PARALLEL_SCANS = 50

// Timeout połączenia TCP (ms)
const val CONNECTION_TIMEOUT_MS = 3000

// Interwał heartbeat (ms)
const val HEARTBEAT_INTERVAL_MS = 5000L

// Timeout całego procesu parowania (ms)
const val PAIRING_TIMEOUT_MS = 10000L
```

---

## 🏗️ Architektura Systemu

### Komponenty

```
┌──────────────────────────────────────────────────────────┐
│                    PairingConfig                         │
│  (Centralna konfiguracja - SINGLE SOURCE OF TRUTH)       │
└──────────────────────────────────────────────────────────┘
                            ▲
                            │ używa
                ┌───────────┴───────────┐
                │                       │
┌───────────────▼──────┐    ┌──────────▼──────────┐
│   NetworkScanner     │    │   PairingService    │
│  - Skanuje sieć      │    │  - Otwiera port     │
│  - Wykrywa urządzen  │    │  - Nasłuchuje       │
│  - Sprawdza porty    │    │  - Łączy urządzenia │
└──────────────────────┘    └─────────────────────┘
         ▲                           ▲
         │                           │
         │                           │
┌────────┴────────┐         ┌────────┴────────┐
│  MainActivity   │         │ PairingActivity │
│  - Skanuj Sieć  │         │ - Wybór typu    │
│  - Wyświetl IP  │         │ - QR kod        │
└─────────────────┘         │ - Start serwera │
                            └─────────────────┘
```

### Przepływ Danych

```
URZĄDZENIE DZIECKA:
1. PairingActivity.startPairingServer()
2. PairingService.startListeningServer()
3. ServerSocket(PairingConfig.PAIRING_PORT)
4. ✅ Port OTWARTY - czeka na połączenie

URZĄDZENIE RODZICA:
1. MainActivity → "🌐 Skanuj Sieć WiFi"
2. NetworkScanner.scanForPairingDevices()
3. Sprawdza każdy IP:PairingConfig.PAIRING_PORT
4. Znajduje otwarty port → Łączy się
```

---

## 🔄 Proces Parowania

### SCENARIUSZ 1: Automatyczne Wykrywanie

#### Na Urządzeniu DZIECKA:
```
1. Kliknij "🔗 Sparuj"
2. Wybierz "DZIECKO"
3. ✅ Serwer uruchomiony - Port 8888 OTWARTY
4. Wyświetl kod QR (opcjonalnie)
5. Czekaj na połączenie...
```

#### Na Urządzeniu RODZICA:
```
1. Kliknij "🌐 Skanuj Sieć WiFi"
2. Zobacz listę urządzeń:
   📱 Urządzenie #1
   🌐 IP: 192.168.1.15
   🔌 Port 8888: ✅ OTWARTY
3. Kliknij "🔗 Sparuj" → "RODZIC"
4. Aplikacja automatycznie wykryje dziecko
5. ✅ Połączono!
```

### SCENARIUSZ 2: Ręczne Wprowadzenie IP

```
1. Na dziecku: Zobacz IP (np. 192.168.1.15)
2. Na rodzicu: "🔗 Sparuj" → "RODZIC"
3. Wprowadź IP ręcznie: 192.168.1.15
4. ✅ Połączono!
```

### Sekwencja Techniczna

```sequence
DZIECKO                    SIEĆ                    RODZIC
   │                         │                        │
   │  ServerSocket(8888)     │                        │
   │─────────────────────────►                        │
   │  ✅ Port OTWARTY         │                        │
   │                         │                        │
   │                         │   scanForPairingDevices()
   │                         ◄────────────────────────│
   │                         │                        │
   │                         │   checkPort(IP, 8888)  │
   │◄────────────────────────┼────────────────────────│
   │                         │                        │
   │  ✅ Port OPEN           │                        │
   │─────────────────────────┼───────────────────────►│
   │                         │                        │
   │                         │   TCP Connect          │
   │◄────────────────────────┼────────────────────────│
   │                         │                        │
   │  ✅ POŁĄCZONO           │                        │
   │◄───────────────────────────────────────────────►│
   │                         │                        │
```

---

## 🔧 Rozwiązywanie Problemów

### Problem: "Port zajęty (EADDRINUSE)"

**Przyczyna:** Port 8888 jest już używany przez inną aplikację

**Rozwiązanie:**
```
OPCJA 1: Restart urządzenia
1. Wyłącz telefon
2. Włącz ponownie
3. Uruchom aplikację

OPCJA 2: Zmień port
1. Edytuj PairingConfig.PAIRING_PORT = 9090
2. Przebuduj aplikację
3. Zainstaluj na OBU urządzeniach
```

### Problem: "Nie znaleziono urządzeń"

**Przyczyny i Rozwiązania:**

1. **Różne sieci WiFi**
   ```
   ✅ Sprawdź: Oba urządzenia w tej samej sieci
   📱 Urządzenie 1: WiFi "MójDom"
   📱 Urządzenie 2: WiFi "MójDom" (to samo!)
   ```

2. **Firewall blokuje port**
   ```
   ✅ Sprawdź ustawienia routera
   ✅ Wyłącz firewall tymczasowo (test)
   ✅ Dodaj regułę dla portu 8888
   ```

3. **Urządzenie dziecka nie ma włączonego serwera**
   ```
   ✅ Na dziecku: "🔗 Sparuj" → "DZIECKO"
   ✅ Poczekaj na "✅ Port 8888 otwarty"
   ```

4. **Timeout zbyt krótki**
   ```
   ✅ Edytuj: NETWORK_SCAN_TIMEOUT_MS = 3000
   ✅ (domyślnie 2000ms)
   ```

### Problem: "Połączenie się rozłącza"

**Rozwiązania:**
```
1. Sprawdź stabilność WiFi
2. Zmniejsz HEARTBEAT_INTERVAL_MS (domyślnie 5000)
3. Wyłącz optymalizację baterii dla aplikacji
4. Ustaw aplikację jako "nieoptymalizowaną"
```

### Diagnostyka

**Włącz szczegółowe logi:**
```
W Android Studio:
1. Logcat → Filter: "Pairing"
2. Szukaj:
   - "🎧 Starting listening server"
   - "✅ Successfully bound to port"
   - "🔍 Scanning for pairing devices"
```

**Użyj narzędzi diagnostycznych:**
```
1. W aplikacji: "📋 Logi" 
2. Zobacz status:
   - KeywordMonitor: 🟢/🔴
   - WiFi: SSID (IP)
3. "📋 Otwórz Pełne Logi"
```

---

## ❓ FAQ

### Czy mogę używać Internetu mobilnego?

❌ **NIE** - Oba urządzenia muszą być w tej samej sieci WiFi lokalnej. 
Internet mobilny nie pozwala na bezpośrednie połączenia peer-to-peer.

### Czy mogę sparować więcej niż 2 urządzenia?

✅ **TAK** - Jeden rodzic może monitorować wiele dzieci.
Każde dziecko musi uruchomić serwer osobno.

### Czy dane są szyfrowane?

✅ **TAK** - Każde parowanie generuje unikalny klucz AES-256.
Klucz jest wymieniany podczas pierwszego połączenia.

### Co się dzieje gdy WiFi się rozłączy?

🔄 **Auto-reconnect** - Aplikacja automatycznie próbuje ponownie połączyć się
co 5 sekund przez maksymalnie 5 prób.

### Czy muszę mieć stały adres IP?

❌ **NIE** - Skanowanie sieci znajduje urządzenia automatycznie.
Adres IP może się zmieniać - aplikacja wykryje nowy adres.

### Czy router musi mieć specjalną konfigurację?

⚠️ **Zazwyczaj NIE** - Ale jeśli używasz:
- Izolacji klientów WiFi (AP Isolation) - wyłącz to
- Bardzo restrykcyjnego firewalla - dodaj wyjątek dla portu 8888

### Jak zmienić port bez przebudowy?

❌ **Nie można** - Port musi być zakodowany w aplikacji.
Musi być taki sam na obu urządzeniach.

---

## 📝 Podsumowanie

### Kluczowe Punkty

1. **Centralna konfiguracja** - `PairingConfig.kt` to JEDYNE miejsce do zmiany portu
2. **Synchronizacja wersji** - Oba urządzenia muszą mieć tę samą wersję aplikacji
3. **Ta sama sieć WiFi** - Bezwzględny wymóg
4. **Port musi być wolny** - Tylko jedna aplikacja może używać danego portu

### Najczęstsze Błędy

❌ Urządzenia w różnych sieciach WiFi  
❌ Aplikacja z różnych wersji na urządzeniach  
❌ Port zajęty przez inną aplikację  
❌ Firewall blokuje komunikację  
❌ Urządzenie dziecka nie ma włączonego serwera  

### Szybki Start

```bash
# 1. Zmień port (opcjonalnie)
nano app/src/main/java/com/parentalcontrol/mvp/config/PairingConfig.kt

# 2. Zbuduj
./gradlew assembleDebug

# 3. Zainstaluj na obu urządzeniach
./gradlew installDebug

# 4. Na dziecku: "Sparuj" → "DZIECKO"
# 5. Na rodzicu: "Skanuj Sieć WiFi" → Zobacz urządzenia
# 6. "Sparuj" → "RODZIC" → Automatyczne połączenie
```

---

**Ostatnia aktualizacja:** 2025-10-02  
**Wersja dokumentacji:** 1.0  
**Domyślny port:** 8888

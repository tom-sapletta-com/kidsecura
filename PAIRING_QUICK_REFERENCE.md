# 🚀 Parowanie WiFi - Quick Reference v2.0

## 🆕 CO NOWEGO?

### ✨ System Wieloportowy z Autotestowaniem

- **4 porty**: 8000, 8080, 8443, 8888
- **Automatyczny wybór**: Pierwszy wolny port
- **Autotestowanie**: Port jest testowany przed użyciem
- **QR z walidacją**: WiFi SSID, IP i Port w kodzie QR
- **Smart scanning**: Skanuje wszystkie porty na każdym urządzeniu

---

## 📍 Gdzie Zmienić Porty?

### Jeden Plik - Lista Portów

```kotlin
// app/src/main/java/com/parentalcontrol/mvp/config/PairingConfig.kt

object PairingConfig {
    val AVAILABLE_PORTS = intArrayOf(8000, 8080, 8443, 8888)
    //                               ↑     ↑     ↑     ↑
    //                          Dodaj lub usuń porty
}
```

### Po Zmianie:

```bash
./gradlew clean assembleDebug installDebug
```

✅ **WAŻNE:** Zainstaluj na WSZYSTKICH urządzeniach!

---

## ⚡ Szybki Start

### 1️⃣ Urządzenie DZIECKA

```
🔗 Sparuj → DZIECKO 
   ↓
🔌 Szukam wolnego portu...
   ↓
✅ Port 8888 OTWARTY i przetestowany!
   ↓
📱 QR kod z IP:8888 + WiFi SSID
```

### 2️⃣ Urządzenie RODZICA

```
🌐 Skanuj Sieć WiFi 
   ↓
Zobacz urządzenia:
📱 Samsung:8888 ✅
📱 Xiaomi:8080 ✅
   ↓
🔗 Sparuj → RODZIC → Auto-połączenie
```

---

## 🐛 Szybkie Rozwiązania

| Problem | Rozwiązanie |
|---------|-------------|
| 🔴 Wszystkie porty zajęte | System próbuje 4 portów - sprawdź logi |
| 🔴 Brak urządzeń | Sprawdź czy oba w tej samej WiFi |
| 🔴 Port test failed | Firewall blokuje - wyłącz tymczasowo |
| 🔴 Rozłącza się | Wyłącz optymalizację baterii |

---

## 📋 Konfiguracje

```kotlin
AVAILABLE_PORTS           = [8000, 8080, 8443, 8888]  // Lista portów
NETWORK_SCAN_TIMEOUT_MS   = 2000   // Timeout skanowania
MAX_PARALLEL_SCANS        = 50     // Równoczesne skany
CONNECTION_TIMEOUT_MS     = 3000   // Timeout połączenia
HEARTBEAT_INTERVAL_MS     = 5000   // Interwał heartbeat
PORT_TEST_TIMEOUT_MS      = 1000   // Timeout testu portu
PORT_TEST_RETRIES         = 3      // Liczba prób testowania
```

---

## 🔍 Diagnostyka

### W Aplikacji:
```
📋 Logi → Status serwisów i WiFi
🌐 Skanuj Sieć → Lista urządzeń z portami
```

### W Android Studio:
```
Logcat → Filter: "Pairing"
```

### Kluczowe Logi:
```
✅ "🎧 Starting listening server - trying available ports"
✅ "🔌 Trying port 8000..."
✅ "✅ Port 8888 opened successfully"
✅ "🧪 Testing port 8888..."
✅ "✅ Port 8888 test passed - server is accessible"
✅ "✅ Found open port 8888 on 192.168.1.15"
```

---

## 📖 Pełna Dokumentacja

Zobacz: [`PAIRING_DOCUMENTATION.md`](PAIRING_DOCUMENTATION.md)

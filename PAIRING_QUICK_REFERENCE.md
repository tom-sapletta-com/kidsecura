# 🚀 Parowanie WiFi - Quick Reference

## 📍 Gdzie Zmienić Port?

### Jeden Plik - Jedna Zmiana

```kotlin
// app/src/main/java/com/parentalcontrol/mvp/config/PairingConfig.kt

object PairingConfig {
    const val PAIRING_PORT = 8888  // ← ZMIEŃ TUTAJ
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
🔗 Sparuj → DZIECKO → ✅ Port 8888 OTWARTY
```

### 2️⃣ Urządzenie RODZICA

```
🌐 Skanuj Sieć WiFi → Zobacz urządzenia → 🔗 Sparuj → RODZIC
```

---

## 🐛 Szybkie Rozwiązania

| Problem | Rozwiązanie |
|---------|-------------|
| 🔴 Port zajęty | Restart telefonu LUB zmień port |
| 🔴 Brak urządzeń | Sprawdź czy oba w tej samej WiFi |
| 🔴 Rozłącza się | Wyłącz optymalizację baterii |

---

## 📋 Konfiguracje

```kotlin
PAIRING_PORT              = 8888   // Port TCP
NETWORK_SCAN_TIMEOUT_MS   = 2000   // Timeout skanowania
MAX_PARALLEL_SCANS        = 50     // Równoczesne skany
CONNECTION_TIMEOUT_MS     = 3000   // Timeout połączenia
HEARTBEAT_INTERVAL_MS     = 5000   // Interwał heartbeat
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
✅ "Starting listening server on port 8888"
✅ "Successfully bound to port 8888"
✅ "Port 8888 is OPEN on 192.168.1.15"
```

---

## 📖 Pełna Dokumentacja

Zobacz: [`PAIRING_DOCUMENTATION.md`](PAIRING_DOCUMENTATION.md)

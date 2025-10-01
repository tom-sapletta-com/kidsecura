# 🚀 Instrukcja instalacji i uruchomienia - KidSecura

## Wymagania systemowe

- **Android Studio**: Hedgehog (2023.1.1) lub nowszy
- **JDK**: 17 lub nowszy
- **Android SDK**: API 34 (Android 14)
- **Minimalna wersja Android**: API 24 (Android 7.0)
- **Kotlin**: 1.9.20+

## Krok 1: Przygotowanie środowiska

### Instalacja Android Studio

1. Pobierz Android Studio z [developer.android.com](https://developer.android.com/studio)
2. Zainstaluj wraz z Android SDK
3. Skonfiguruj SDK Manager:
   - Tools → SDK Manager
   - Zainstaluj SDK Platform 34
   - Zainstaluj Build Tools 34.0.0

### Konfiguracja JDK

1. W Android Studio: File → Project Structure
2. Wybierz JDK 17 w SDK Location

## Krok 2: Otwarcie projektu

```bash
# Sklonuj repozytorium
git clone https://github.com/tom-sapletta-com/kidsecura.git
cd kidsecura

# Otwórz w Android Studio
# File → Open → wybierz folder kidsecura
```

## Krok 3: Synchronizacja Gradle

1. Android Studio automatycznie rozpocznie synchronizację Gradle
2. Poczekaj na zakończenie (pierwsze uruchomienie może potrwać kilka minut)
3. Jeśli wystąpią błędy:
   - File → Invalidate Caches / Restart
   - Build → Clean Project
   - Build → Rebuild Project

## Krok 4: Konfiguracja emulatora lub urządzenia

### Opcja A: Emulator

1. Tools → Device Manager
2. Create Device
3. Wybierz urządzenie (np. Pixel 6)
4. Wybierz system: API 34 (Android 14)
5. Kliknij Finish

### Opcja B: Fizyczne urządzenie

1. Na urządzeniu Android:
   - Ustawienia → O telefonie
   - Kliknij 7 razy na "Numer kompilacji"
   - Wróć do Ustawień → Opcje deweloperskie
   - Włącz "Debugowanie USB"
2. Podłącz urządzenie USB do komputera
3. Zaakceptuj debugowanie USB na urządzeniu

## Krok 5: Uruchomienie aplikacji

1. W Android Studio kliknij **Run** (zielony trójkąt) lub wciśnij `Shift + F10`
2. Wybierz emulator lub podłączone urządzenie
3. Poczekaj na instalację i uruchomienie

## Krok 6: Pierwsze uruchomienie aplikacji

### Udzielenie uprawnień

Po uruchomieniu aplikacja poprosi o następujące uprawnienia:

1. **Dostęp do ekranu (MediaProjection)**
   - Wymagane do przechwytywania ekranu
   - Pojawi się systemowe okno dialogowe
   - Kliknij "Rozpocznij teraz"

2. **Powiadomienia** (Android 13+)
   - Wymagane do alertów bezpieczeństwa
   - Zaakceptuj w systemowym oknie

3. **Overlay permission**
   - Opcjonalne, ale zalecane
   - Ustawienia → Aplikacje → KidSecura → Wyświetlanie nad innymi aplikacjami

### Konfiguracja monitorowania

1. Ustaw interwał przechwytywania (domyślnie: 2 sekundy)
2. Włącz/wyłącz opcje:
   - Analiza dolnej połowy ekranu
   - Analiza lokalna (bez wysyłania do chmury)
   - Zapisywanie zrzutów ekranu
3. Kliknij "Rozpocznij monitorowanie"
4. Zaakceptuj dialog zgody

## Rozwiązywanie problemów

### Błąd: "SDK location not found"

```bash
# Utwórz plik local.properties w katalogu głównym projektu
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# Przykład dla Linux/Mac:
# sdk.dir=/home/username/Android/Sdk

# Przykład dla Windows:
# sdk.dir=C\:\\Users\\Username\\AppData\\Local\\Android\\Sdk
```

### Błąd: "Unsupported class file version"

- Upewnij się, że używasz JDK 17
- File → Project Structure → SDK Location → JDK location

### Błąd: "Failed to resolve: com.google.mlkit..."

```bash
# W terminalu Android Studio:
./gradlew clean
./gradlew build
```

### Aplikacja się nie uruchamia

1. Sprawdź logi: Logcat w Android Studio
2. Upewnij się, że urządzenie/emulator ma Android 7.0+
3. Wyczyść cache: Build → Clean Project

## Testowanie funkcji

### Test 1: Przechwytywanie ekranu

1. Uruchom aplikację
2. Rozpocznij monitorowanie
3. Otwórz inną aplikację (np. przeglądarkę)
4. Sprawdź czy w Historii pojawiają się zdarzenia

### Test 2: Wykrywanie słów kluczowych

1. Otwórz aplikację z tekstem (np. notatki)
2. Wpisz jedno ze słów testowych:
   - "nienawidzę"
   - "głupi"
   - "tajemnica"
3. Sprawdź czy pojawi się powiadomienie

### Test 3: Historia zdarzeń

1. Kliknij przycisk "Historia"
2. Przeglądaj wykryte zdarzenia
3. Testuj filtrowanie (Wszystkie/Nieprzejrzane/Dzisiaj/Tydzień)
4. Oznacz zdarzenie jako przejrzane lub fałszywy alarm

## Budowanie wersji release

```bash
# W terminalu Android Studio:
./gradlew assembleRelease

# APK będzie w:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

### Podpisywanie APK

1. Build → Generate Signed Bundle / APK
2. Wybierz APK
3. Utwórz nowy keystore lub użyj istniejącego
4. Wypełnij dane certyfikatu
5. Wybierz build variant: release
6. Kliknij Finish

## Wsparcie

W przypadku problemów:
- Sprawdź dokumentację: [README.md](README.md)
- Zgłoś issue na GitHub
- Skontaktuj się z zespołem deweloperskim

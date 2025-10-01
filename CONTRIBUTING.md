# 🤝 Przewodnik dla kontrybutorów

Dziękujemy za zainteresowanie projektem KidSecura! Ten dokument pomoże Ci rozpocząć pracę nad projektem.

## 📋 Spis treści

- [Code of Conduct](#code-of-conduct)
- [Jak mogę pomóc?](#jak-mogę-pomóc)
- [Struktura projektu](#struktura-projektu)
- [Standardy kodowania](#standardy-kodowania)
- [Proces zgłaszania zmian](#proces-zgłaszania-zmian)
- [Testowanie](#testowanie)

## Code of Conduct

- Bądź uprzejmy i szanuj innych
- Konstruktywna krytyka jest mile widziana
- Zero tolerancji dla nękania i dyskryminacji
- Szanuj prywatność użytkowników

## Jak mogę pomóc?

### Zgłaszanie błędów

Przed zgłoszeniem błędu:
1. Sprawdź czy nie został już zgłoszony
2. Upewnij się, że używasz najnowszej wersji
3. Przygotuj szczegółowy opis problemu

Szablon zgłoszenia błędu:
```markdown
**Opis błędu**
Krótki opis problemu

**Kroki do reprodukcji**
1. Uruchom...
2. Kliknij...
3. Obserwuj błąd...

**Oczekiwane zachowanie**
Co powinno się stać

**Aktualne zachowanie**
Co się dzieje

**Środowisko**
- Wersja aplikacji:
- Wersja Androida:
- Model urządzenia:
- Logi (jeśli dostępne):
```

### Propozycje nowych funkcji

Format propozycji:
```markdown
**Problem do rozwiązania**
Opisz problem, który chcesz rozwiązać

**Proponowane rozwiązanie**
Jak widzisz implementację

**Alternatywy**
Inne rozważane opcje

**Dodatkowy kontekst**
Screenshots, mockupy, itp.
```

### Pull Requests

Mile widziane obszary:
- 🐛 Poprawki błędów
- 📝 Poprawa dokumentacji
- 🎨 Ulepszenia UI/UX
- ⚡ Optymalizacja wydajności
- 🔒 Usprawnienia bezpieczeństwa
- 🧪 Dodawanie testów
- 🌍 Tłumaczenia

## Struktura projektu

```
app/src/main/
├── java/com/parentalcontrol/mvp/
│   ├── MainActivity.kt                 # Główna aktywność
│   ├── EventHistoryActivity.kt        # Historia zdarzeń
│   ├── service/
│   │   └── ScreenCaptureService.kt    # Serwis przechwytywania
│   ├── analyzer/
│   │   └── ContentAnalyzer.kt         # Analiza treści
│   ├── data/
│   │   ├── MonitoringEvent.kt        # Model danych
│   │   ├── EventDao.kt               # DAO
│   │   └── MonitoringDatabase.kt     # Baza danych
│   └── utils/
│       ├── PreferencesManager.kt     # Preferencje
│       ├── ImageUtils.kt             # Narzędzia graficzne
│       └── NotificationHelper.kt     # Powiadomienia
└── res/
    ├── layout/                        # Layouty XML
    ├── values/                        # Stringi, kolory, style
    └── drawable/                      # Ikony i grafiki
```

## Standardy kodowania

### Kotlin Style Guide

Przestrzegamy [oficjalnego Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Dobrze
class ContentAnalyzer(private val context: Context) {
    fun analyze(bitmap: Bitmap): AnalysisResult {
        // Implementacja
    }
}

// ❌ Źle
class ContentAnalyzer(private val context:Context){
    fun analyze(bitmap:Bitmap):AnalysisResult
    {
        // Implementacja
    }
}
```

### Konwencje nazewnictwa

- **Klasy**: PascalCase (`ContentAnalyzer`)
- **Funkcje/zmienne**: camelCase (`analyzeContent`)
- **Stałe**: UPPER_SNAKE_CASE (`MAX_ATTEMPTS`)
- **Pliki XML**: snake_case (`activity_main.xml`)
- **Zasoby**: prefix_name (`ic_warning`, `btn_start`)

### Komentarze

```kotlin
/**
 * Analizuje treść obrazu i wykrywa potencjalne zagrożenia.
 *
 * @param bitmap Obraz do analizy
 * @return Wynik analizy z poziomem pewności i opisem
 */
suspend fun analyze(bitmap: Bitmap): AnalysisResult {
    // Implementacja
}
```

### Bezpieczeństwo i prywatność

**WAŻNE**: Ten projekt dotyczy bezpieczeństwa dzieci. Zachowaj szczególną ostrożność:

- ❌ NIE loguj wrażliwych danych (OCR tekst, screenshots)
- ❌ NIE wysyłaj danych bez szyfrowania
- ✅ Szyfruj lokalną bazę danych
- ✅ Używaj bezpiecznych metod przechowywania
- ✅ Respektuj ustawienia prywatności użytkownika

```kotlin
// ❌ NIGDY nie rób tego
Log.d(TAG, "Extracted text: ${ocrResult.text}")

// ✅ Zamiast tego
Log.d(TAG, "Text extraction completed, length: ${ocrResult.text.length}")
```

## Proces zgłaszania zmian

### 1. Fork i Clone

```bash
# Fork repozytorium na GitHubie, potem:
git clone https://github.com/twoj-username/kidsecura.git
cd kidsecura
git remote add upstream https://github.com/tom-sapletta-com/kidsecura.git
```

### 2. Utwórz branch

```bash
# Konwencja nazewnictwa:
# feature/nazwa-funkcji
# bugfix/opis-bledu
# docs/co-poprawione
# refactor/co-refaktorowane

git checkout -b feature/dodaj-filtrowanie-eventow
```

### 3. Wprowadź zmiany

- Pisz czytelny kod
- Dodaj komentarze gdzie potrzeba
- Zaktualizuj dokumentację
- Dodaj testy jeśli możliwe

### 4. Commit

```bash
# Używaj opisowych commitów:
git add .
git commit -m "feat: dodaj filtrowanie eventów po typie

- Dodano ChipGroup z filtrami
- Zaktualizowano EventDao o query z filtrem typu
- Dodano testy dla nowego filtra"
```

Format commitów (Conventional Commits):
- `feat:` - nowa funkcja
- `fix:` - poprawka błędu
- `docs:` - zmiany w dokumentacji
- `style:` - formatowanie, brak zmian w kodzie
- `refactor:` - refaktoryzacja
- `test:` - dodanie testów
- `chore:` - aktualizacja zależności, konfiguracja

### 5. Push i Pull Request

```bash
git push origin feature/dodaj-filtrowanie-eventow
```

Na GitHubie:
1. Otwórz Pull Request
2. Wypełnij template PR
3. Połącz z odpowiednim Issue (jeśli istnieje)
4. Poczekaj na review

### Template Pull Request

```markdown
## Opis zmian
Krótki opis co zostało zmienione i dlaczego

## Typ zmiany
- [ ] 🐛 Poprawka błędu
- [ ] ✨ Nowa funkcja
- [ ] 📝 Dokumentacja
- [ ] 🎨 Ulepszenie UI
- [ ] ⚡ Wydajność
- [ ] 🔒 Bezpieczeństwo

## Jak przetestowano
Opisz kroki testowania

## Checklist
- [ ] Kod działa lokalnie
- [ ] Dodano/zaktualizowano testy
- [ ] Zaktualizowano dokumentację
- [ ] Kod jest zgodny ze standardami
- [ ] Nie ma konfliktów z main
- [ ] PR jest gotowy do review
```

## Testowanie

### Testy jednostkowe

```bash
./gradlew test
```

### Testy instrumentalne

```bash
./gradlew connectedAndroidTest
```

### Testy manualne

Przed zgłoszeniem PR przetestuj:
1. Instalację aplikacji
2. Podstawowy flow (start/stop monitorowania)
3. Wykrywanie testowych słów kluczowych
4. Historię zdarzeń
5. Wszystkie buttony i UI elementy

## Pytania?

- Otwórz Discussion na GitHubie
- Skontaktuj się z maintainerami
- Przeczytaj dokumentację projektu

## Licencja

Zgłaszając Pull Request, zgadzasz się na udostępnienie swojego kodu na licencji projektu (sprawdź LICENSE).

---

**Dziękujemy za wkład w bezpieczeństwo dzieci! 💚**

# 📱 KidSecura - Przewodnik Użytkownika dla Rodziców

## 🎯 **Wprowadzenie**

KidSecura to zaawansowana aplikacja kontroli rodzicielskiej, która zapewnia ciągły monitoring aktywności dziecka na urządzeniu Android. Aplikacja działa w **trybie ukrytym** i komunikuje się z urządzeniem rodzica przez **bezpośrednie połączenie P2P** oraz **powiadomienia Telegram/WhatsApp**.

---

## 🚀 **Szybki Start - Instrukcja Krok po Krok**

### **Krok 1: Instalacja na Urządzeniu Dziecka**

1. **Pobierz APK**: Uzyskaj plik KidSecura.apk od administratora
2. **Włącz "Nieznane źródła"**: 
   - Przejdź do: Ustawienia → Bezpieczeństwo → Nieznane źródła ✅
3. **Zainstaluj aplikację**: Dotknij pliku APK i potwierdź instalację
4. **Nadaj uprawnienia**:
   - ✅ **Dostęp do przechwytyania ekranu** (wymagane)
   - ✅ **Dostęp do plików** (dla logów)
   - ✅ **Dostęp do sieci** (dla komunikacji P2P)

### **Krok 2: Pierwsze Uruchomienie**

1. **Uruchom KidSecura** na urządzeniu dziecka
2. **Skonfiguruj dane rodzica**:
   - 📱 Numer telefonu rodzica
   - 📧 Email rodzica (opcjonalnie)
3. **Wygeneruj kod parowania**: Zanotuj 6-cyfrowy kod wyświetlany na ekranie

### **Krok 3: Parowanie z Urządzeniem Rodzica**

1. **Zainstaluj KidSecura na swoim urządzeniu** (urządzenie rodzica)
2. **Rozpocznij parowanie**:
   - Dotknij przycisk "🔗 Paruj urządzenie"
   - Wprowadź kod z urządzenia dziecka
3. **Potwierdź połączenie**: Oba urządzenia pokażą potwierdzenie parowania

---

## 📡 **Konfiguracja Powiadomień Telegram/WhatsApp**

### **🤖 Telegram Bot - Konfiguracja**

#### **A. Utworzenie Bota Telegram**
1. **Otwórz Telegram** i znajdź **@BotFather**
2. **Wyślij**: `/newbot`
3. **Nadaj nazwę botowi**: np. "KidSecura Alert Bot"
4. **Nadaj username**: np. "@KidSecuraAlertBot"
5. **Skopiuj TOKEN**: Otrzymasz token typu `123456789:ABCdefGHijklMN...`

#### **B. Uzyskanie Chat ID**
1. **Dodaj swojego bota** do grupowej konwersacji lub wyślij mu wiadomość prywatną
2. **Wyślij dowolną wiadomość** do bota
3. **Otwórz link**: `https://api.telegram.org/bot[TOKEN]/getUpdates`
   - Zastąp `[TOKEN]` swoim tokenem bota
4. **Znajdź "chat":{"id":}** - skopiuj numer ID (np. -123456789)

#### **C. Konfiguracja w KidSecura**
1. **Otwórz KidSecura** na urządzeniu rodzica
2. **Dotknij**: "📱 Konfiguruj Messaging"
3. **Wprowadź dane**:
   - 🤖 **Bot Token**: Wklej token z BotFather
   - 💬 **Chat ID**: Wklej ID konwersacji
4. **Test wiadomości**: Dotknij "🧪 Wyślij test" dla weryfikacji
5. **Zapisz ustawienia**: Dotknij "💾 Zapisz"

### **📱 WhatsApp Business (Planowane)**
*Funkcja w przygotowaniu - wymaga weryfikacji konta WhatsApp Business*

---

## ⚙️ **Konfiguracja Słów Kluczowych**

### **Edycja Słów Kluczowych**
1. **Otwórz**: "🔍 Edytuj słowa kluczowe"
2. **Dostępne akcje**:
   - ➕ **Dodaj nowe słowo**: Wprowadź i potwierdź
   - ✕ **Usuń słowo**: Dotknij czerwony przycisk obok słowa
   - 🔄 **Przywróć domyślne**: Reset do fabrycznych ustawień

### **Kategorie Słów Kluczowych**
- 🚨 **Przemoc**: zagrożenia, groźby, agresja
- 💔 **Cyberbullying**: nękanie, szykanowanie
- 🔞 **Treści nieodpowiednie**: treści dla dorosłych
- 💊 **Substancje**: narkotyki, alkohol
- 👤 **Grooming**: niebezpieczne kontakty

---

## 🕵️ **Tryb Ukryty (Stealth Mode)**

### **Aktywacja Trybu Ukrytego**
1. **Dotknij**: "👻 Tryb ukryty"
2. **Wybierz maskę aplikacji**:
   - 🔧 **System Update** (domyślne)
   - 🧮 **Kalkulator**
   - ⚙️ **System Settings**
3. **Ustaw PIN dostępu**: 4-6 cyfr dla bezpieczeństwa
4. **Aktywuj**: Aplikacja zmieni ikonę i nazwę

### **Dostęp do Ukrytej Aplikacji**
1. **Znajdź zamaskowaną ikonę** (np. "System Update")
2. **Uruchom aplikację**
3. **Wprowadź PIN**: Wpisz ustawiony wcześniej PIN
4. **Dostęp przyznany**: Powrót do normalnego interfejsu

### **Funkcje Ochrony przed Wykryciem**
- 🔒 **Ukrywanie z recent apps**: Automatyczne usunięcie z listy ostatnich aplikacji
- 🛡️ **Anti-tampering**: Wykrywanie prób manipulacji
- 🎭 **Dynamiczna zmiana ikon**: Okresowa zmiana wyglądu aplikacji

---

## 📊 **Monitorowanie i Alerty**

### **Rodzaje Powiadomień**

#### **🔴 Alerty Wysokiego Priorytetu**
- 🚨 Wykryte zagrożenia słowami kluczowymi
- ⚠️ Próby manipulacji aplikacją
- 📱 Utrata połączenia P2P

#### **🟡 Alerty Średniego Priorytetu**
- 📊 Codzienne podsumowania aktywności
- 🔧 Zmiany konfiguracji na urządzeniu dziecka

#### **🟢 Alerty Niskiego Priorytetu**
- ✅ Potwierdzenia stanu systemu
- 📈 Statystyki tygodniowe

### **Kanały Powiadomień**
1. **P2P (Główny)**: Bezpośrednie powiadomienia między urządzeniami
2. **Telegram**: Powiadomienia na telefonie rodzica przez bota
3. **WhatsApp**: Planowane - przez WhatsApp Business API

---

## 🔧 **Zaawansowane Funkcje**

### **Historia Zdarzeń**
- **Dostęp**: Sekcja "📜 Ostatnie zdarzenia" w aplikacji
- **Informacje**: Data, czas, typ zagrożenia, treść
- **Eksport**: Możliwość zapisania logów na zewnętrznym nośniku

### **Synchronizacja Ustawień**
- **Automatyczna**: Zmiany na urządzeniu rodzica automatycznie synchronizowane
- **Ręczna**: Przycisk "🔄 Synchronizuj ustawienia" w razie problemów

### **Monitoring Stanu Połączenia**
- 🟢 **Połączony**: Zielony status - wszystko działa poprawnie
- 🟡 **Próba połączenia**: Żółty status - tymczasowe problemy
- 🔴 **Brak połączenia**: Czerwony status - wymaga interwencji

---

## 🛠️ **Rozwiązywanie Problemów**

### **Częste Problemy i Rozwiązania**

#### **❌ Brak połączenia P2P**
**Przyczyna**: Urządzenia w różnych sieciach WiFi lub Bluetooth wyłączony
**Rozwiązanie**:
1. Upewnij się, że oba urządzenia są w tej samej sieci WiFi
2. Włącz Bluetooth na obu urządzeniach
3. Zrestartuj funkcję parowania

#### **📱 Nie działają powiadomienia Telegram**
**Przyczyna**: Niepoprawny token bota lub Chat ID
**Rozwiązanie**:
1. Sprawdź poprawność tokenu bota u @BotFather
2. Zweryfikuj Chat ID przez API Telegram
3. Użyj funkcji "🧪 Wyślij test" do weryfikacji

#### **👻 Aplikacja staje się widoczna**
**Przyczyna**: Aktualizacja systemu lub reset ustawień
**Rozwiązanie**:
1. Ponownie aktywuj tryb ukryty
2. Ustaw nowy PIN dostępu
3. Sprawdź, czy aplikacja nie jest na liście "Recent Apps"

#### **🔋 Problemy z wydajnością/baterią**
**Przyczyna**: Zbyt częste screenshoty lub intensive monitoring
**Rozwiązanie**:
1. Zwiększ interwał pomiędzy screenshotami (Ustawienia → Interwał: 3-5 sekund)
2. Wyłącz zapisywanie screenshotów na dysk jeśli niepotrzebne
3. Zrestartuj urządzenie

---

## ⚡ **Optymalizacja Wydajności**

### **Ustawienia Baterii**
- **Wyłącz optymalizację baterii** dla KidSecura:
  - Ustawienia → Bateria → Optymalizacja baterii → KidSecura → Nie optymalizuj

### **Ustawienia Sieci**
- **WiFi**: Utrzymuj połączenie WiFi dla najlepszej wydajności P2P
- **Dane mobilne**: Zapewnij dostęp do internetu dla Telegram/WhatsApp

### **Zarządzanie Pamięcią**
- **Czyść logi**: Automatyczne czyszczenie starych logów po 30 dniach
- **Eksportuj dane**: Regularnie eksportuj ważne logi przed automatycznym usunięciem

---

## 🔐 **Bezpieczeństwo i Prywatność**

### **Ochrona Danych**
- 🏠 **Lokalne przetwarzanie**: Wszystkie analizy wykonywane na urządzeniu
- 🔒 **Szyfrowane komunikacje**: P2P i messaging używają szyfrowania
- 🚫 **Brak chmury**: Żadne dane nie są wysyłane do zewnętrznych serwerów

### **Kontrola Dostępu**
- 🔑 **PIN zabezpieczający**: Dostęp do ukrytej aplikacji tylko z PIN-em
- 👤 **Autoryzacja rodzica**: Tylko sparowane urządzenia mogą odbierać alerty
- 🛡️ **Anti-tampering**: Automatyczne wykrywanie prób manipulacji

---

## 📞 **Wsparcie Techniczne**

### **Kontakt**
- **Email**: support@kidsecura.app
- **Dokumentacja**: Sprawdź pliki docs/ w projekcie
- **Logi systemowe**: Eksportuj logi przed kontaktem z supportem

### **Informacje Diagnostyczne**
Przed kontaktem z supportem przygotuj:
- 📱 Model urządzenia i wersja Android
- 🔄 Wersja aplikacji KidSecura
- 📊 Logi systemowe (jeśli dostępne)
- 📝 Opis problemu krok po krok

---

## 🎯 **Najlepsze Praktyki**

### **Dla Rodziców**
1. **Regularnie sprawdzaj** status połączenia i alerty
2. **Aktualizuj słowa kluczowe** zgodnie z wiekiem dziecka
3. **Testuj komunikację** co tydzień używając funkcji testowych
4. **Zabezpiecz PIN** dostępu - nie udostępniaj dziecku
5. **Edukuj dziecko** o bezpieczeństwie online

### **Dla Administratorów IT**
1. **Monitoring sieci**: Upewnij się, że WiFi Direct nie jest blokowane
2. **Firewall**: Dodaj wyjątki dla komunikacji P2P i Telegram API
3. **Aktualizacje**: Regularnie aktualizuj aplikację do najnowszej wersji
4. **Backup**: Eksportuj konfiguracje przed większymi zmianami systemu

---

*📝 Ostatnia aktualizacja: Styczeń 2025*
*🔧 Wersja przewodnika: 2.2.0*

---

**⚠️ Ważne**: KidSecura jest narzędziem wspomagającym kontrolę rodzicielską. Nie zastępuje otwartej komunikacji z dzieckiem o bezpieczeństwie w internecie.

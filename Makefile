.PHONY: help install setup build clean test run release deploy lint format check doctor

# Kolory do wyświetlania
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

# Zmienne
GRADLE := ./gradlew
APP_NAME := KidSecura
PACKAGE_NAME := com.parentalcontrol.mvp

help: ## Wyświetl tę pomoc
	@echo "$(GREEN)Dostępne komendy dla projektu $(APP_NAME):$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-15s$(NC) %s\n", $$1, $$2}'
	@echo ""

install: setup ## Pełna instalacja projektu (wrapper + dependencies)
	@echo "$(GREEN)📦 Instalacja projektu $(APP_NAME)...$(NC)"
	@$(MAKE) setup
	@echo "$(GREEN)✅ Instalacja zakończona!$(NC)"

setup: ## Pobierz gradle-wrapper.jar i zsynchronizuj zależności
	@echo "$(GREEN)🔧 Konfiguracja środowiska...$(NC)"
	@if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then \
		echo "$(YELLOW)Pobieranie gradle-wrapper.jar...$(NC)"; \
		mkdir -p gradle/wrapper; \
		curl -L -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar; \
	fi
	@echo "$(GREEN)Synchronizacja zależności...$(NC)"
	@$(GRADLE) --version || (echo "$(RED)Błąd Gradle. Sprawdź JAVA_HOME.$(NC)" && exit 1)
	@echo "$(GREEN)✅ Setup zakończony!$(NC)"

build: ## Zbuduj projekt (debug)
	@echo "$(GREEN)🔨 Budowanie projektu...$(NC)"
	@$(GRADLE) build
	@echo "$(GREEN)✅ Build zakończony!$(NC)"

clean: ## Wyczyść projekt
	@echo "$(YELLOW)🧹 Czyszczenie projektu...$(NC)"
	@$(GRADLE) clean
	@echo "$(GREEN)✅ Projekt wyczyszczony!$(NC)"

rebuild: clean build ## Wyczyść i zbuduj ponownie

test: ## Uruchom testy jednostkowe
	@echo "$(GREEN)🧪 Uruchamianie testów...$(NC)"
	@$(GRADLE) test
	@echo "$(GREEN)✅ Testy zakończone!$(NC)"

test-instrumented: ## Uruchom testy instrumentalne (wymaga urządzenia/emulatora)
	@echo "$(GREEN)📱 Uruchamianie testów instrumentalnych...$(NC)"
	@$(GRADLE) connectedAndroidTest

run: ## Zainstaluj i uruchom aplikację na urządzeniu/emulatorze
	@echo "$(GREEN)🚀 Instalowanie i uruchamianie aplikacji...$(NC)"
	@$(GRADLE) installDebug
	@adb shell am start -n $(PACKAGE_NAME)/.MainActivity
	@echo "$(GREEN)✅ Aplikacja uruchomiona!$(NC)"

install-debug: ## Zainstaluj wersję debug na urządzeniu
	@echo "$(GREEN)📲 Instalowanie wersji debug...$(NC)"
	@$(GRADLE) installDebug

uninstall: ## Odinstaluj aplikację z urządzenia
	@echo "$(YELLOW)🗑️  Odinstalowywanie aplikacji...$(NC)"
	@adb uninstall $(PACKAGE_NAME) || true
	@echo "$(GREEN)✅ Aplikacja odinstalowana!$(NC)"

release: ## Zbuduj wersję release (unsigned)
	@echo "$(GREEN)📦 Budowanie wersji release...$(NC)"
	@$(GRADLE) assembleRelease
	@echo "$(GREEN)✅ APK release: app/build/outputs/apk/release/$(NC)"
	@ls -lh app/build/outputs/apk/release/*.apk

bundle: ## Zbuduj Android App Bundle (.aab)
	@echo "$(GREEN)📦 Budowanie Android App Bundle...$(NC)"
	@$(GRADLE) bundleRelease
	@echo "$(GREEN)✅ AAB: app/build/outputs/bundle/release/$(NC)"
	@ls -lh app/build/outputs/bundle/release/*.aab

lint: ## Uruchom lint Android
	@echo "$(GREEN)🔍 Analiza kodu (lint)...$(NC)"
	@$(GRADLE) lint

format: ## Formatuj kod Kotlin (ktlint)
	@echo "$(GREEN)✨ Formatowanie kodu...$(NC)"
	@find app/src -name "*.kt" -exec echo "Formatting: {}" \;
	@echo "$(YELLOW)⚠️  Zainstaluj ktlint dla automatycznego formatowania$(NC)"

check: lint test ## Uruchom wszystkie sprawdzenia (lint + testy)
	@echo "$(GREEN)✅ Wszystkie sprawdzenia zakończone!$(NC)"

doctor: ## Sprawdź środowisko (Java, Android SDK, ADB)
	@echo "$(GREEN)🏥 Diagnoza środowiska...$(NC)"
	@echo ""
	@echo "$(YELLOW)Java:$(NC)"
	@java -version 2>&1 | head -n 1 || echo "$(RED)❌ Java nie znalezione$(NC)"
	@echo ""
	@echo "$(YELLOW)JAVA_HOME:$(NC)"
	@echo "$$JAVA_HOME" || echo "$(RED)❌ JAVA_HOME nie ustawione$(NC)"
	@echo ""
	@echo "$(YELLOW)Android SDK:$(NC)"
	@echo "$$ANDROID_HOME" || echo "$(RED)❌ ANDROID_HOME nie ustawione$(NC)"
	@echo ""
	@echo "$(YELLOW)ADB:$(NC)"
	@adb version 2>&1 | head -n 1 || echo "$(RED)❌ ADB nie znalezione$(NC)"
	@echo ""
	@echo "$(YELLOW)Podłączone urządzenia:$(NC)"
	@adb devices
	@echo ""
	@echo "$(YELLOW)Gradle:$(NC)"
	@$(GRADLE) --version 2>&1 | head -n 5 || echo "$(RED)❌ Gradle wrapper nie działa$(NC)"

devices: ## Pokaż podłączone urządzenia Android
	@echo "$(GREEN)📱 Podłączone urządzenia:$(NC)"
	@adb devices -l

logcat: ## Pokaż logi aplikacji
	@echo "$(GREEN)📋 Logi aplikacji (Ctrl+C aby zakończyć):$(NC)"
	@adb logcat | grep -i "$(PACKAGE_NAME)\|AndroidRuntime"

logcat-clear: ## Wyczyść logi
	@adb logcat -c
	@echo "$(GREEN)✅ Logi wyczyszczone$(NC)"

deps: ## Pokaż drzewo zależności
	@$(GRADLE) :app:dependencies

deps-updates: ## Sprawdź aktualizacje zależności
	@echo "$(GREEN)🔄 Sprawdzanie aktualizacji zależności...$(NC)"
	@$(GRADLE) dependencyUpdates

sync: ## Synchronizuj projekt Gradle
	@echo "$(GREEN)🔄 Synchronizacja Gradle...$(NC)"
	@$(GRADLE) --refresh-dependencies

stats: ## Statystyki projektu (linie kodu)
	@echo "$(GREEN)📊 Statystyki projektu:$(NC)"
	@echo ""
	@echo "$(YELLOW)Kotlin (.kt):$(NC)"
	@find app/src -name "*.kt" | xargs wc -l | tail -n 1
	@echo ""
	@echo "$(YELLOW)XML (.xml):$(NC)"
	@find app/src -name "*.xml" | xargs wc -l | tail -n 1
	@echo ""
	@echo "$(YELLOW)Pliki:$(NC)"
	@echo "  Kotlin: $$(find app/src -name '*.kt' | wc -l)"
	@echo "  XML: $$(find app/src -name '*.xml' | wc -l)"

screenshot: ## Zrób screenshot z urządzenia
	@echo "$(GREEN)📸 Robienie screenshota...$(NC)"
	@mkdir -p screenshots
	@adb shell screencap -p /sdcard/screenshot.png
	@adb pull /sdcard/screenshot.png screenshots/screenshot-$$(date +%Y%m%d-%H%M%S).png
	@adb shell rm /sdcard/screenshot.png
	@echo "$(GREEN)✅ Screenshot zapisany w screenshots/$(NC)"

open-android-studio: ## Otwórz projekt w Android Studio (Linux)
	@echo "$(GREEN)🚀 Otwieranie Android Studio...$(NC)"
	@studio.sh . &

workspace: ## Przygotuj workspace (tworzenie katalogów)
	@mkdir -p app/src/main/assets
	@mkdir -p app/src/test/java
	@mkdir -p app/src/androidTest/java
	@mkdir -p screenshots
	@echo "$(GREEN)✅ Workspace przygotowany$(NC)"

# Aliasy
all: build ## Alias dla build
deploy: release ## Alias dla release
start: run ## Alias dla run

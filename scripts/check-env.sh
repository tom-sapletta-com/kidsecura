#!/bin/bash

# Skrypt sprawdzający środowisko deweloperskie dla projektu Android

set -e

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🏥 Sprawdzanie środowiska deweloperskiego...${NC}"
echo ""

# Sprawdź Java
echo -e "${YELLOW}1. Sprawdzanie Java...${NC}"
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | head -n 1)
    echo -e "${GREEN}✅ Java zainstalowana: $java_version${NC}"
    
    if [ -n "$JAVA_HOME" ]; then
        echo -e "${GREEN}✅ JAVA_HOME ustawione: $JAVA_HOME${NC}"
    else
        echo -e "${RED}❌ JAVA_HOME nie jest ustawione${NC}"
        echo -e "${YELLOW}   Ustaw JAVA_HOME w ~/.bashrc lub ~/.zshrc${NC}"
    fi
else
    echo -e "${RED}❌ Java nie znalezione${NC}"
    echo -e "${YELLOW}   Zainstaluj JDK 17+: sudo apt install openjdk-17-jdk${NC}"
    exit 1
fi
echo ""

# Sprawdź Android SDK
echo -e "${YELLOW}2. Sprawdzanie Android SDK...${NC}"
if [ -n "$ANDROID_HOME" ]; then
    echo -e "${GREEN}✅ ANDROID_HOME ustawione: $ANDROID_HOME${NC}"
    
    if [ -d "$ANDROID_HOME" ]; then
        echo -e "${GREEN}✅ Android SDK istnieje${NC}"
    else
        echo -e "${RED}❌ Katalog Android SDK nie istnieje: $ANDROID_HOME${NC}"
    fi
else
    echo -e "${RED}❌ ANDROID_HOME nie jest ustawione${NC}"
    echo -e "${YELLOW}   Zainstaluj Android Studio i ustaw ANDROID_HOME${NC}"
    echo -e "${YELLOW}   export ANDROID_HOME=\$HOME/Android/Sdk${NC}"
fi
echo ""

# Sprawdź ADB
echo -e "${YELLOW}3. Sprawdzanie ADB...${NC}"
if command -v adb &> /dev/null; then
    adb_version=$(adb version 2>&1 | head -n 1)
    echo -e "${GREEN}✅ ADB zainstalowane: $adb_version${NC}"
    
    echo -e "${YELLOW}   Podłączone urządzenia:${NC}"
    adb devices | tail -n +2
else
    echo -e "${RED}❌ ADB nie znalezione${NC}"
    echo -e "${YELLOW}   Dodaj Android SDK platform-tools do PATH${NC}"
fi
echo ""

# Sprawdź Gradle wrapper
echo -e "${YELLOW}4. Sprawdzanie Gradle Wrapper...${NC}"
if [ -f "./gradlew" ]; then
    echo -e "${GREEN}✅ gradlew istnieje${NC}"
    
    if [ -x "./gradlew" ]; then
        echo -e "${GREEN}✅ gradlew ma uprawnienia wykonywania${NC}"
    else
        echo -e "${RED}❌ gradlew nie ma uprawnień wykonywania${NC}"
        echo -e "${YELLOW}   Wykonaj: chmod +x gradlew${NC}"
    fi
    
    if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
        echo -e "${GREEN}✅ gradle-wrapper.jar istnieje${NC}"
    else
        echo -e "${RED}❌ gradle-wrapper.jar nie istnieje${NC}"
        echo -e "${YELLOW}   Wykonaj: make setup${NC}"
    fi
else
    echo -e "${RED}❌ gradlew nie znalezione${NC}"
fi
echo ""

# Podsumowanie
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}📋 Podsumowanie:${NC}"
echo ""

all_ok=true

if ! command -v java &> /dev/null || [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}❌ Skonfiguruj Java i JAVA_HOME${NC}"
    all_ok=false
fi

if [ -z "$ANDROID_HOME" ]; then
    echo -e "${RED}❌ Skonfiguruj Android SDK i ANDROID_HOME${NC}"
    all_ok=false
fi

if ! command -v adb &> /dev/null; then
    echo -e "${YELLOW}⚠️  ADB nie znalezione (opcjonalne)${NC}"
fi

if [ ! -f "./gradlew" ] || [ ! -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
    echo -e "${RED}❌ Gradle wrapper nie jest skonfigurowany${NC}"
    echo -e "${YELLOW}   Wykonaj: make setup${NC}"
    all_ok=false
fi

if $all_ok; then
    echo -e "${GREEN}✅ Środowisko jest gotowe do pracy!${NC}"
    echo -e "${GREEN}   Możesz teraz uruchomić: make build${NC}"
else
    echo -e "${RED}❌ Środowisko wymaga konfiguracji${NC}"
    echo -e "${YELLOW}   Zobacz INSTALACJA.md dla szczegółów${NC}"
    exit 1
fi

echo ""

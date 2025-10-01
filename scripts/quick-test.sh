#!/bin/bash

# Szybki test aplikacji - uruchom z testowymi scenariuszami

set -e

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

PACKAGE_NAME="com.parentalcontrol.mvp"

echo -e "${GREEN}🧪 Szybki test aplikacji KidSecura${NC}"
echo ""

# Sprawdź czy urządzenie jest podłączone
echo -e "${YELLOW}Sprawdzanie urządzenia...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ Brak podłączonego urządzenia Android${NC}"
    echo -e "${YELLOW}   Podłącz urządzenie lub uruchom emulator${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Urządzenie podłączone${NC}"
echo ""

# Zainstaluj aplikację
echo -e "${YELLOW}Instalowanie aplikacji...${NC}"
./gradlew installDebug
echo -e "${GREEN}✅ Aplikacja zainstalowana${NC}"
echo ""

# Uruchom aplikację
echo -e "${YELLOW}Uruchamianie aplikacji...${NC}"
adb shell am start -n $PACKAGE_NAME/.MainActivity
sleep 2
echo -e "${GREEN}✅ Aplikacja uruchomiona${NC}"
echo ""

# Wyświetl logi
echo -e "${GREEN}📋 Logi aplikacji (Ctrl+C aby zakończyć):${NC}"
adb logcat | grep -E "$PACKAGE_NAME|AndroidRuntime:E"

#!/bin/bash

# Skrypt konfiguracyjny dla nowych deweloperów

set -e

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🚀 Setup środowiska deweloperskiego KidSecura${NC}"
echo ""

# 1. Sprawdź Git
echo -e "${YELLOW}1. Sprawdzanie Git...${NC}"
if command -v git &> /dev/null; then
    echo -e "${GREEN}✅ Git zainstalowany${NC}"
else
    echo -e "${RED}❌ Git nie znaleziony${NC}"
    exit 1
fi
echo ""

# 2. Konfiguracja Git hooks (opcjonalne)
echo -e "${YELLOW}2. Konfiguracja Git hooks...${NC}"
if [ -d ".git" ]; then
    mkdir -p .git/hooks
    
    # Pre-commit hook - sprawdzenie formatowania
    cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash
echo "🔍 Sprawdzanie kodu przed commitem..."

# Sprawdź czy są zmiany w plikach Kotlin
if git diff --cached --name-only | grep -q "\.kt$"; then
    echo "✅ Znaleziono pliki Kotlin"
    # Tutaj można dodać ktlint lub detekt
fi

exit 0
EOF
    chmod +x .git/hooks/pre-commit
    echo -e "${GREEN}✅ Git hooks skonfigurowane${NC}"
else
    echo -e "${YELLOW}⚠️  Nie jest to repozytorium Git${NC}"
fi
echo ""

# 3. Gradle Wrapper
echo -e "${YELLOW}3. Konfiguracja Gradle Wrapper...${NC}"
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo -e "${YELLOW}Pobieranie gradle-wrapper.jar...${NC}"
    mkdir -p gradle/wrapper
    curl -L -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar
    echo -e "${GREEN}✅ Gradle wrapper pobrany${NC}"
else
    echo -e "${GREEN}✅ Gradle wrapper już istnieje${NC}"
fi

chmod +x gradlew
echo ""

# 4. Weryfikacja Java
echo -e "${YELLOW}4. Weryfikacja Java...${NC}"
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo -e "${GREEN}✅ Java $java_version${NC}"
    
    if [ -z "$JAVA_HOME" ]; then
        echo -e "${YELLOW}⚠️  JAVA_HOME nie jest ustawione${NC}"
        echo -e "${YELLOW}   Dodaj do ~/.bashrc:${NC}"
        echo -e "${YELLOW}   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64${NC}"
    fi
else
    echo -e "${RED}❌ Java nie znalezione${NC}"
    echo -e "${YELLOW}   Zainstaluj: sudo apt install openjdk-17-jdk${NC}"
fi
echo ""

# 5. Tworzenie katalogów
echo -e "${YELLOW}5. Tworzenie katalogów roboczych...${NC}"
mkdir -p app/src/main/assets
mkdir -p screenshots
mkdir -p docs
echo -e "${GREEN}✅ Katalogi utworzone${NC}"
echo ""

# 6. Synchronizacja Gradle
echo -e "${YELLOW}6. Synchronizacja projektu Gradle...${NC}"
echo -e "${YELLOW}   To może potrwać kilka minut przy pierwszym uruchomieniu...${NC}"
if ./gradlew --version > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Gradle działa poprawnie${NC}"
    ./gradlew tasks --all > /dev/null 2>&1 || true
else
    echo -e "${RED}❌ Problem z Gradle${NC}"
fi
echo ""

# Podsumowanie
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}✅ Setup zakończony!${NC}"
echo ""
echo -e "${YELLOW}Następne kroki:${NC}"
echo -e "  1. ${GREEN}make doctor${NC}     - Sprawdź środowisko"
echo -e "  2. ${GREEN}make build${NC}      - Zbuduj projekt"
echo -e "  3. ${GREEN}make run${NC}        - Uruchom na urządzeniu"
echo ""
echo -e "Zobacz ${GREEN}make help${NC} dla wszystkich dostępnych komend."
echo ""

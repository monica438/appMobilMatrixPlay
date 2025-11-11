#!/bin/bash

# Script para compilar, instalar y ejecutar appMobilMatrixPlay en el móvil
# Uso: ./run_app.sh [clean] [nocompile]

# Colores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Rutas
GRADLE_BIN="/tmp/gradle-8.13/bin/gradle"
GRADLE_ZIP="/tmp/gradle.zip"
GRADLE_URL="https://services.gradle.org/distributions/gradle-8.13-bin.zip"
ADB="$HOME/Android/Sdk/platform-tools/adb"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.example.appMobilMatrixPlay"
ACTIVITY_NAME=".SplashActivity"

echo -e "${BLUE}═══════════════════════════════════════════════${NC}"
echo -e "${BLUE}    appMobilMatrixPlay - Deploy Script${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════${NC}"

# Verificar si ADB está disponible
if [ ! -f "$ADB" ]; then
    echo -e "${RED}✗ Error: ADB no encontrado en $ADB${NC}"
    exit 1
fi

# Verificar dispositivos conectados
echo -e "\n${BLUE}Verificando dispositivos conectados...${NC}"
DEVICES=$($ADB devices | grep -v "List" | grep "device$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo -e "${RED}✗ No hay dispositivos conectados${NC}"
    echo -e "${YELLOW}  Conecta tu móvil por USB y activa 'Depuración USB'${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Dispositivo conectado${NC}"
$ADB devices -l | grep "device$" || true

# Compilar (si no se pasa el argumento nocompile)
if [ "$1" != "nocompile" ] && [ "$2" != "nocompile" ]; then
    # Verificar si Gradle está disponible
    if [ ! -f "$GRADLE_BIN" ]; then
        echo -e "${YELLOW}⚠ Gradle no encontrado. Descargando...${NC}"
        curl -fL -o "$GRADLE_ZIP" "$GRADLE_URL"
        unzip -q "$GRADLE_ZIP" -d /tmp
        echo -e "${GREEN}✓ Gradle descargado${NC}"
    fi

    # Compilar
    if [ "$1" == "clean" ]; then
        echo -e "\n${BLUE}Limpiando proyecto...${NC}"
        rm -rf app/build
    fi

    echo -e "\n${BLUE}Compilando APK...${NC}"
    $GRADLE_BIN :app:assembleDebug --no-daemon --quiet

    if [ ! -f "$APK_PATH" ]; then
        echo -e "${RED}✗ Error: APK no generado${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ APK compilado: $APK_PATH${NC}"

    # Instalar
    echo -e "\n${BLUE}Instalando en el dispositivo...${NC}"

    # Verificar si la app ya está instalada
    if $ADB shell pm list packages | grep -q "$PACKAGE_NAME"; then
        echo -e "${YELLOW}  App ya instalada, reinstalando...${NC}"
        $ADB install -r "$APK_PATH" 2>&1 | grep -v "Performing" || {
            echo -e "${YELLOW}  Fallo al reinstalar, desinstalando primero...${NC}"
            $ADB uninstall "$PACKAGE_NAME" > /dev/null 2>&1
            $ADB install "$APK_PATH" 2>&1 | grep -v "Performing"
        }
    else
        $ADB install "$APK_PATH" 2>&1 | grep -v "Performing"
    fi

    echo -e "${GREEN}✓ App instalada${NC}"
else
    echo -e "\n${YELLOW}⊳ Saltando compilación (usando APK existente)${NC}"
fi

# Limpiar logcat
echo -e "\n${BLUE}Limpiando logcat...${NC}"
$ADB logcat -c

# Ejecutar
echo -e "\n${BLUE}Ejecutando app en el dispositivo...${NC}"
$ADB shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"

echo -e "${GREEN}✓ App iniciada${NC}"

# Mostrar logs en tiempo real
echo -e "\n${BLUE}═══════════════════════════════════════════════${NC}"
echo -e "${BLUE}    Logs de la app (Ctrl+C para salir)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════${NC}\n"

$ADB logcat | grep --line-buffered -E "(appMobilMatrixPlay|AndroidRuntime|WebSocket)"

#!/bin/bash

# Script para compilar, instalar y ejecutar appMobilMatrixPlay
# Uso simple: ./run_app.sh

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuración
GRADLE_BIN="/tmp/gradle-8.13/bin/gradle"
ADB="$HOME/Android/Sdk/platform-tools/adb"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.example.appMobilMatrixPlay"
ACTIVITY_NAME=".SplashActivity"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Ping Pong Online - Compilar y Ejecutar   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"

# Verificar dispositivo
echo -e "\n${BLUE}[1/4]${NC} Verificando dispositivo Android..."
if ! $ADB devices | grep -q "device$"; then
    echo -e "${RED}✗ No hay dispositivo conectado${NC}"
    echo -e "${YELLOW}  → Conecta tu móvil y activa Depuración USB${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Dispositivo conectado${NC}"

# Compilar
echo -e "\n${BLUE}[2/4]${NC} Compilando APK..."
$GRADLE_BIN assembleDebug --no-daemon 2>&1 | grep -E "(BUILD|FAILED|error)" || echo -n ""

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}✗ Error al compilar${NC}"
    exit 1
fi
echo -e "${GREEN}✓ APK compilado exitosamente${NC}"

# Instalar
echo -e "\n${BLUE}[3/4]${NC} Instalando en el dispositivo..."
$ADB install -r "$APK_PATH" 2>&1 | tail -1
echo -e "${GREEN}✓ App instalada${NC}"

# Ejecutar
echo -e "\n${BLUE}[4/4]${NC} Ejecutando aplicación..."
$ADB logcat -c
$ADB shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" > /dev/null 2>&1
echo -e "${GREEN}✓ App iniciada en el dispositivo${NC}"

echo -e "\n${GREEN}════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ Ping Pong Online ejecutándose${NC}"
echo -e "${GREEN}════════════════════════════════════════════${NC}\n"

# Opción de ver logs
read -p "¿Deseas ver los logs? (s/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[SsYy]$ ]]; then
    echo -e "\n${BLUE}Mostrando logs (Ctrl+C para salir)...${NC}\n"
    $ADB logcat | grep --line-buffered -E "(appMobilMatrixPlay|AndroidRuntime|WebSocket)"
fi

#!/bin/bash

# Script simple para ejecutar la app en el móvil (sin compilar)
# Uso: ./launch_app.sh

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

ADB="$HOME/Android/Sdk/platform-tools/adb"
PACKAGE_NAME="com.example.appMobilMatrixPlay"
ACTIVITY_NAME=".SplashActivity"

echo -e "${BLUE}Lanzando app en el dispositivo...${NC}"

# Verificar dispositivo
if ! $ADB devices | grep -q "device$"; then
    echo -e "${RED}✗ No hay dispositivos conectados${NC}"
    exit 1
fi

# Limpiar logcat
$ADB logcat -c

# Ejecutar app
$ADB shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME"

echo -e "${GREEN}✓ App ejecutada${NC}"
echo -e "${BLUE}Mostrando logs (Ctrl+C para salir)...${NC}\n"

# Mostrar logs
$ADB logcat | grep --line-buffered -E "(appMobilMatrixPlay|WebSocket|MainActivity|ConfigActivity)"

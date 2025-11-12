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
ADB="$HOME/Android/Sdk/platform-tools/adb"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.example.appMobilMatrixPlay"
ACTIVITY_NAME=".SplashActivity"

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Ping Pong Online - Compilar y Ejecutar   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"

# Verificar ADB
if [ ! -f "$ADB" ]; then
    echo -e "${RED}✗ ADB no encontrado en $ADB${NC}"
    exit 1
fi

# Reiniciar servidor ADB para asegurar detección
echo -e "\n${BLUE}[1/5]${NC} Reiniciando servidor ADB..."
$ADB kill-server > /dev/null 2>&1
$ADB start-server > /dev/null 2>&1
sleep 1

# Verificar dispositivos conectados
echo -e "\n${BLUE}[2/5]${NC} Detectando dispositivos Android..."
DEVICE_LIST=($($ADB devices | grep -E "device$|emulator" | awk '{print $1}'))
DEVICE_COUNT=${#DEVICE_LIST[@]}

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}✗ No hay dispositivos conectados${NC}"
    echo -e "${YELLOW}  → Conecta tu móvil/emulador y activa Depuración USB${NC}"
    echo -e "\nDispositivos disponibles:"
    $ADB devices
    exit 1
fi

echo -e "${GREEN}✓ $DEVICE_COUNT dispositivo(s) conectado(s)${NC}"

# Mostrar lista de dispositivos con números
echo -e "\n${BLUE}Dispositivos disponibles:${NC}"
for i in "${!DEVICE_LIST[@]}"; do
    DEVICE_ID="${DEVICE_LIST[$i]}"
    # Obtener información adicional del dispositivo
    MODEL=$($ADB -s "$DEVICE_ID" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    MANUFACTURER=$($ADB -s "$DEVICE_ID" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
    
    if [ -z "$MODEL" ]; then
        MODEL="Desconocido"
    fi
    if [ -z "$MANUFACTURER" ]; then
        MANUFACTURER="Desconocido"
    fi
    
    echo -e "  ${YELLOW}[$((i+1))]${NC} $DEVICE_ID - ${MANUFACTURER} ${MODEL}"
done

# Seleccionar dispositivo
SELECTED_DEVICE=""
if [ "$DEVICE_COUNT" -eq 1 ]; then
    SELECTED_DEVICE="${DEVICE_LIST[0]}"
    echo -e "\n${GREEN}→ Usando automáticamente:${NC} $SELECTED_DEVICE"
else
    echo -e "\n${BLUE}Selecciona un dispositivo [1-$DEVICE_COUNT] (Enter para usar el primero):${NC} "
    read -t 10 -r CHOICE
    
    # Si no hay respuesta o timeout, usar el primero
    if [ -z "$CHOICE" ]; then
        SELECTED_DEVICE="${DEVICE_LIST[0]}"
        echo -e "${YELLOW}→ Usando por defecto:${NC} $SELECTED_DEVICE"
    elif [[ "$CHOICE" =~ ^[0-9]+$ ]] && [ "$CHOICE" -ge 1 ] && [ "$CHOICE" -le "$DEVICE_COUNT" ]; then
        SELECTED_DEVICE="${DEVICE_LIST[$((CHOICE-1))]}"
        echo -e "${GREEN}→ Dispositivo seleccionado:${NC} $SELECTED_DEVICE"
    else
        echo -e "${RED}✗ Selección inválida, usando el primero${NC}"
        SELECTED_DEVICE="${DEVICE_LIST[0]}"
    fi
fi

# Compilar
echo -e "\n${BLUE}[3/5]${NC} Compilando APK..."
./gradlew assembleDebug --no-daemon 2>&1 | grep -E "(BUILD|FAILED|error)" || echo -n ""

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}✗ Error al compilar${NC}"
    exit 1
fi
echo -e "${GREEN}✓ APK compilado exitosamente${NC}"

# Instalar
echo -e "\n${BLUE}[4/5]${NC} Instalando en el dispositivo $SELECTED_DEVICE..."
$ADB -s "$SELECTED_DEVICE" install -r "$APK_PATH" 2>&1 | tail -3
if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Error al instalar${NC}"
    exit 1
fi
echo -e "${GREEN}✓ App instalada${NC}"

# Ejecutar
echo -e "\n${BLUE}[5/5]${NC} Ejecutando aplicación en $SELECTED_DEVICE..."
$ADB -s "$SELECTED_DEVICE" logcat -c
$ADB -s "$SELECTED_DEVICE" shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ App iniciada en el dispositivo${NC}"
else
    echo -e "${YELLOW}⚠ Intenta iniciar manualmente desde el dispositivo${NC}"
fi

echo -e "\n${GREEN}════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✓ Ping Pong Online ejecutándose${NC}"
echo -e "${GREEN}════════════════════════════════════════════${NC}\n"

# Opción de ver logs
read -p "¿Deseas ver los logs? (s/N): " -t 5 -n 1 -r
echo
if [[ $REPLY =~ ^[SsYy]$ ]]; then
    echo -e "\n${BLUE}Mostrando logs de $SELECTED_DEVICE (Ctrl+C para salir)...${NC}\n"
    $ADB -s "$SELECTED_DEVICE" logcat | grep --line-buffered -E "(appMobilMatrixPlay|AndroidRuntime|WebSocket|WaitingRoom|MainActivity)"
fi

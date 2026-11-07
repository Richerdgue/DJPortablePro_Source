#!/bin/bash

# --- DevOps & Release Automation Script for DJ Portable Pro ---
# Propósito: Automatizar el proceso de build, testing y empaquetado de producción.

set -e # Detener ante cualquier error

echo "🚀 Iniciando proceso de compilación de producción..."

# 1. Limpieza
echo "🧹 Limpiando proyecto..."
./gradlew clean

# 2. Testing
echo "🧪 Ejecutando tests unitarios de release..."
./gradlew testReleaseUnitTest

# 3. Generación del App Bundle (AAB)
echo "📦 Generando Android App Bundle (.aab)..."
./gradlew bundleRelease

# 4. Empaquetado de Símbolos Nativos
echo "🔍 Empaquetando símbolos nativos para Play Console..."
SYMBOLS_PATH="app/build/intermediates/cmake/release/obj"
OUTPUT_ZIP="native_symbols_$(date +%Y%m%d_%H%M%S).zip"

if [ -d "$SYMBOLS_PATH" ]; then
    zip -r "$OUTPUT_ZIP" "$SYMBOLS_PATH" -i "*.so"
    echo "✅ Símbolos nativos guardados en: $OUTPUT_ZIP"
else
    echo "⚠️ ADVERTENCIA: No se encontraron binarios nativos en $SYMBOLS_PATH"
fi

echo "🎉 Proceso completado exitosamente."
echo "Ubicación del bundle: app/build/outputs/bundle/release/app-release.aab"

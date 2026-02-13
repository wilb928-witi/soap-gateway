#!/bin/bash

# Script de inicio rápido para SOAP Gateway (Gradle)

echo "================================"
echo "SOAP Gateway - Inicio Rápido"
echo "================================"
echo ""

# Verificar Java
if ! command -v java &> /dev/null; then
    echo "❌ Java no está instalado. Por favor instala Java 17 o superior."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Se requiere Java 17 o superior. Versión actual: $JAVA_VERSION"
    exit 1
fi

echo "✓ Java $JAVA_VERSION detectado"
echo ""

# Compilar proyecto
echo "Compilando proyecto con Gradle..."
./gradlew clean build -x test

if [ $? -ne 0 ]; then
    echo "❌ Error al compilar el proyecto"
    exit 1
fi

echo ""
echo "✓ Compilación exitosa"
echo ""

# Ejecutar aplicación
echo "Iniciando aplicación..."
echo "================================"
echo ""

java -jar build/libs/soap-gateway-1.0.0.jar


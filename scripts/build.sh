#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
mkdir -p build/classes dist
javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
jar --create --file dist/SmartFitAgent.jar --main-class com.smartfitagent.App -C build/classes .
echo "dist/SmartFitAgent.jar created"

#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
mkdir -p build/classes data dist
javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
java -cp build/classes com.smartfitagent.App

#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

if [ -z "${AI_API_KEY:-}" ]; then
  printf "请输入 DeepSeek API Key: "
  stty -echo
  read -r AI_API_KEY
  stty echo
  printf "\n"
  export AI_API_KEY
fi

export AI_PROVIDER=deepseek
export AI_MODEL="${AI_MODEL:-deepseek-chat}"

mkdir -p build/classes data dist
javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
java -cp build/classes com.smartfitagent.App

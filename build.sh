#!/bin/sh
# 构建 wolweb.jar (需要 JDK 17+)
set -e
cd "$(dirname "$0")"
mkdir -p build/classes
javac --release 17 -encoding UTF-8 -d build/classes src/com/wolweb/*.java
jar cfe build/wolweb.jar com.wolweb.Main -C build/classes .
echo "构建完成: build/wolweb.jar"
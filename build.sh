#!/bin/bash
echo "========================================"
echo "  CHAT APP - TCP/IP (Linux Build Script)"
echo "========================================"

MYSQL_JAR="lib/mysql-connector-j-9.6.0.jar"

if [ ! -f "$MYSQL_JAR" ]; then
    echo "[LOI] Khong tim thay MySQL Connector: $MYSQL_JAR"
    exit 1
fi

mkdir -p build/classes

echo "[1/2] Dang bien dich toan bo source..."
javac -encoding UTF-8 -cp "$MYSQL_JAR" -d build/classes $(find src -name "*.java")

if [ $? -ne 0 ]; then
    echo "[LOI] Bien dich that bai!"
    exit 1
fi

echo "[2/2] Hoan thanh! Code da duoc bien dich vao thu muc build/classes"
echo ""
echo "De chay Server: bash run_server.sh hoac java -cp \"$MYSQL_JAR:build/classes\" Chat.server.Main"

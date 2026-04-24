#!/bin/bash
MYSQL_JAR="lib/mysql-connector-j-9.6.0.jar"
echo "Dang khoi dong Chat Server..."
java -cp "$MYSQL_JAR:build/classes" Chat.server.Main

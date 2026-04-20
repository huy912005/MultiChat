@echo off
chcp 65001 > nul
echo ========================================
echo   CHAT CLIENT - Dang khoi dong...
echo ========================================

set "MYSQL_JAR=%CHAT_MYSQL_JAR%"
if "%MYSQL_JAR%"=="" set "MYSQL_JAR=lib\mysql-connector-j-9.6.0.jar"

if not exist "build\classes" (
    echo [LOI] Chua build source. Hay chay build.bat truoc.
    pause
    exit /b 1
)

if not exist "%MYSQL_JAR%" (
    echo [LOI] Khong tim thay MySQL Connector: %MYSQL_JAR%
    pause
    exit /b 1
)

java -cp "build\classes;%MYSQL_JAR%" Chat.client.Main
pause

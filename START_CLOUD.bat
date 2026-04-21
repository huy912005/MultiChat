@echo off
chcp 65001 > nul
echo ========================================
echo   CHAT APP - Cloud Mode
echo ========================================

REM Set Cloud DB environment
set "CHAT_DB_URL=jdbc:mysql://159.65.134.130:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
set "CHAT_DB_USER=chatuser"
set "CHAT_DB_PASS=ChatApp@123"

REM Check build
if not exist "build\classes" (
    echo [SYSTEM] Dang build...
    call build.bat
    if errorlevel 1 (
        echo [LOI] Build that bai!
        pause
        exit /b 1
    )
)

echo.
echo [SYSTEM] Khoi dong Server (Cloud DB)...
start "CHAT SERVER" cmd /k "cd /d %cd% && java -cp "build\classes;lib\*" Chat.server.Main"

timeout /t 2

echo [SYSTEM] Khoi dong Client...
start "CHAT CLIENT" cmd /k "cd /d %cd% && java -cp "build\classes;lib\*" Chat.client.Main"

echo.
echo ========================================
echo   Server (Cloud) + Client da chay!
echo ========================================
pause

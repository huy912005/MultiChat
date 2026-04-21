@echo off
chcp 65001 > nul
echo ========================================
echo   CHAT CLIENT - Cloud Mode
echo ========================================

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
echo [SYSTEM] Khoi dong Client...
echo.
java -cp "build\classes;lib\*" Chat.client.Main

pause

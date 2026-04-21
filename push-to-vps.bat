@echo off
REM Script push code to VPS server via SSH
REM Requires: sshpass installed and password set in environment variable

setlocal enabledelayedexpansion

set VPS_USER=root
set VPS_HOST=159.65.134.130
set VPS_PORT=22
set VPS_REPO_PATH=/root/chat-repo
set VPS_PASSWORD=Pham9Minh1Huy

REM Check if sshpass is installed
where sshpass >nul 2>&1
if %errorlevel% neq 0 (
    echo sshpass not found. Installing...
    REM You may need to install sshpass first
    echo Please install sshpass and add to PATH
    exit /b 1
)

echo ======================================
echo Pushing to VPS server...
echo ======================================

REM Add remote if not exists
git remote rm vps 2>nul
git remote add vps ssh://%VPS_USER%@%VPS_HOST%:%VPS_PORT%%VPS_REPO_PATH%

REM Push using sshpass
sshpass -p "%VPS_PASSWORD%" git push -u vps main --force

if %errorlevel% equ 0 (
    echo.
    echo ✓ Successfully pushed to VPS!
) else (
    echo.
    echo ✗ Failed to push to VPS
    exit /b 1
)

endlocal

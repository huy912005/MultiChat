@echo off
chcp 65001 > nul
echo ========================================
echo   CHAT APP - TCP/IP (Build Script)
echo ========================================

set "MYSQL_JAR=%CHAT_MYSQL_JAR%"
if "%MYSQL_JAR%"=="" set "MYSQL_JAR=lib\mysql-connector-j-9.6.0.jar"

if not exist "%MYSQL_JAR%" (
    echo [LOI] Khong tim thay MySQL Connector: %MYSQL_JAR%
    echo Dat bien moi truong CHAT_MYSQL_JAR hoac copy jar vao lib\
    pause
    exit /b 1
)

:: Tao thu muc build neu chua co
if not exist "build\classes" mkdir "build\classes"

echo [1/2] Dang bien dich toan bo source...
javac -encoding UTF-8 -cp "%MYSQL_JAR%" -d build\classes ^
src\Chat\Dao\DBContext.java ^
src\Chat\server\model\*.java ^
src\Chat\server\network\*.java ^
src\Chat\server\view\*.java ^
src\Chat\server\Main.java ^
src\Chat\client\model\*.java ^
src\Chat\client\network\*.java ^
src\Chat\client\view\*.java ^
src\Chat\client\controller\*.java ^
src\Chat\client\Main.java
if %errorlevel% neq 0 (
    echo [LOI] Bien dich that bai!
    pause
    exit /b 1
)

echo [2/2] Hoan thanh! Code da duoc bien dich vao thu muc build\classes
echo.
echo De chay:
echo   - Server: run_server.bat
echo   - Client: run_client.bat  (mo nhieu cua so de test)
echo.
pause

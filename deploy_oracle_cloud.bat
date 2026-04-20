@REM Oracle Cloud Deployment Helper (Windows)
@REM Chạy script này từ thư mục project để upload lên Oracle Cloud

@echo off
setlocal enabledelayedexpansion

cls
echo.
echo ========================================
echo  Chat App - Oracle Cloud Deployment Helper
echo ========================================
echo.

:menu
echo.
echo Chọn tác vụ:
echo 1 - Upload JAR file
echo 2 - Upload MySQL Connector
echo 3 - SSH vào VM
echo 4 - Start Server (SSH)
echo 5 - View Logs
echo 6 - Stop Server
echo 0 - Exit
echo.

set /p choice="Nhập lựa chọn (0-6): "

if "%choice%"=="1" goto upload_jar
if "%choice%"=="2" goto upload_mysql
if "%choice%"=="3" goto ssh_vm
if "%choice%"=="4" goto start_server
if "%choice%"=="5" goto view_logs
if "%choice%"=="6" goto stop_server
if "%choice%"=="0" goto end

echo Lựa chọn không hợp lệ!
goto menu

:upload_jar
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

if not exist "dist\ChatApp-TCP.jar" (
    echo ✗ Không tìm thấy dist\ChatApp-TCP.jar
    echo Vui lòng chạy "Clean and Build" trong NetBeans trước!
    pause
    goto menu
)

echo Uploading JAR file...
scp dist\ChatApp-TCP.jar !oracle_user!@!oracle_ip!:/home/!oracle_user!/ChatApp/
echo ✓ Upload thành công!
pause
goto menu

:upload_mysql
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

echo Uploading MySQL Connector...
REM Thay đổi path này nếu MySQL Connector ở chỗ khác
set mysql_jar=F:\2025-2026\2026\mysql\mysql-connector-j-9.6.0\mysql-connector-j-9.6.0\mysql-connector-j-9.6.0.jar

if not exist "!mysql_jar!" (
    echo ✗ Không tìm thấy MySQL Connector tại: !mysql_jar!
    echo Vui lòng cập nhật path trong script này
    pause
    goto menu
)

scp "!mysql_jar!" !oracle_user!@!oracle_ip!:/home/!oracle_user!/ChatApp/
echo ✓ Upload thành công!
pause
goto menu

:ssh_vm
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

echo Kết nối SSH...
ssh !oracle_user!@!oracle_ip!
goto menu

:start_server
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

echo Khởi động server...
ssh !oracle_user!@!oracle_ip! "cd ~/ChatApp && nohup java -jar ChatApp-TCP.jar > logs/server.log 2>&1 &"
echo ✓ Server khởi động (chạy background)
echo Để xem log: chọn "View Logs"
pause
goto menu

:view_logs
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

echo Xem log (Ctrl+C để thoát)...
ssh !oracle_user!@!oracle_ip! "tail -f ~/ChatApp/logs/server.log"
goto menu

:stop_server
echo.
set /p oracle_ip="Nhập Oracle Cloud IP: "
set /p oracle_user="Nhập username (default: ubuntu): "
if "!oracle_user!"=="" set oracle_user=ubuntu

echo Dừng server...
ssh !oracle_user!@!oracle_ip! "pkill -f 'java -jar' && echo Server đã dừng"
pause
goto menu

:end
echo.
echo Tạm biệt!
endlocal
exit /b 0

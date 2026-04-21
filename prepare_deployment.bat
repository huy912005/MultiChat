@echo off
REM Chuẩn bị deployment folder
REM Chạy sau khi Clean and Build trong NetBeans

echo Creating deployment folder...
if not exist "deployment" mkdir deployment

echo Copying JAR file...
copy /Y dist\ChatApp-TCP.jar deployment\

echo Copying MySQL connector...
if exist "lib\mysql-connector-j-9.6.0.jar" (
    copy /Y "lib\mysql-connector-j-9.6.0.jar" deployment\
) else (
    echo [CANH BAO] MySQL connector khong tim thay trong lib\
    echo Kiem tra: lib\mysql-connector-j-9.6.0.jar
)

echo Creating run scripts...

REM Tạo script chạy server
(
  echo @echo off
  echo cd /d "%%~dp0"
  echo java -cp ChatApp-TCP.jar;mysql-connector-j-9.6.0.jar server.Server
  echo pause
) > deployment\run_server.bat

REM Tạo script chạy client
(
  echo @echo off
  echo cd /d "%%~dp0"
  echo java -cp ChatApp-TCP.jar client.Main
  echo pause
) > deployment\run_client.bat

REM Tạo README deployment
(
  echo # Chat App - Deployment Package
  echo.
  echo ## Files:
  echo - ChatApp-TCP.jar: Main application
  echo - mysql-connector-j-9.6.0.jar: Database driver
  echo - run_server.bat: Chạy server
  echo - run_client.bat: Chạy client
  echo.
  echo ## Server Setup:
  echo 1. Cài MySQL
  echo 2. Chạy: run_server.bat
  echo.
  echo ## Client Setup:
  echo 1. Chạy: run_client.bat
  echo 2. Nhập IP server: localhost (nếu cùng máy^) hoặc external IP
  echo.
) > deployment\README.txt

echo ✅ Deployment folder ready!
echo 📁 Location: %CD%\deployment\
pause

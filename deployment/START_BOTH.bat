@echo off
chcp 65001 > nul
cd /d "%~dp0"

setx CHAT_DB_URL "jdbc:mysql://159.65.134.130:3306/ChatAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC" >nul 2>&1
setx CHAT_DB_USER "chatuser" >nul 2>&1
setx CHAT_DB_PASS "ChatApp@123" >nul 2>&1

start "CHAT_SERVER" cmd /k "java -cp ChatApp-TCP.jar;mysql-connector-j-9.6.0.jar Chat.server.Main"
timeout /t 3
start "CHAT_CLIENT" cmd /k "java -cp ChatApp-TCP.jar Chat.client.Main"

@echo off
chcp 65001 > nul
cd /d "%~dp0"
java -cp ChatApp-TCP.jar Chat.client.Main

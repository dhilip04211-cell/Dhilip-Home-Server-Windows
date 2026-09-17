@echo off
cd /d "%~dp0.."
if not exist ".env" copy ".env.example" ".env" >nul
if not exist "media" mkdir media
if not exist "data" mkdir data
if not exist "logs" mkdir logs
echo Starting DhilipHome Server...
echo Keep this window open while the server is running.
echo.
DhilipHomeServer.exe
pause

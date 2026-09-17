@echo off
setlocal
cd /d "%~dp0.."
echo ==========================================
echo   DHILIP HOME SERVER - GUI EXE BUILD
echo ==========================================
py -3.11 -m venv .venv
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt
pip install pyinstaller
python -m PyInstaller --clean --noconfirm windows\DhilipHomeServer.spec
if errorlevel 1 goto failed
echo.
echo BUILD COMPLETE
echo EXE: %CD%\dist\DhilipHomeServer.exe
pause
exit /b 0
:failed
echo BUILD FAILED
pause
exit /b 1

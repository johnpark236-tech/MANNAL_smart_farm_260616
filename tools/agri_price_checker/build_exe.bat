@echo off
setlocal
cd /d "%~dp0"
set PYTHON_CMD=python
python --version >nul 2>&1
if errorlevel 1 (
  set PYTHON_CMD=py -3
)
%PYTHON_CMD% -m pip install pyinstaller
%PYTHON_CMD% -m PyInstaller --onefile --name AgriPriceChecker agri_price_webapp.py
echo.
echo EXE path: %~dp0dist\AgriPriceChecker.exe
pause

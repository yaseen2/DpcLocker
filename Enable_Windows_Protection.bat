@echo off
:: Batch Wrapper to run enable_windows_protection.ps1 with ExecutionPolicy Bypass
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ========================================================
    echo  ERROR: Please Right-Click this file and select:
    echo  "Run as administrator"
    echo ========================================================
    echo.
    pause
    exit /b
)

powershell -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
echo.
pause

@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: WINDOWS BROWSER PROTECTION LAUNCHER

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ===============================================================================
    echo  [*] ADMINISTRATOR PRIVILEGES REQUIRED
    echo ===============================================================================
    echo.
    echo  Requesting Windows UAC Elevation...
    echo.
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
echo.
pause

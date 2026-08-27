@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: INSTALL PERMANENT SENTINEL & AUTO-STARTUP

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

echo ===============================================================================
echo  [#] DPCLOCKER :: INSTALLING PERMANENT AUTO-STARTUP SENTINEL
echo ===============================================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "START_BAT=%SCRIPT_DIR%Start_Proxy_Sentinel.bat"

:: Clean up any duplicate startup folder shortcuts
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1
del "%ALLUSERSPROFILE%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1

:: 1. Register Windows Task Scheduler Task (Highest Privileges, Hidden from Startup Tab)
echo  [1/2] Registering in Windows Task Scheduler (Highest Privileges)...
schtasks /create /tn "DpcLockerSentinel" /tr "\"%START_BAT%\"" /sc onlogon /rl highest /f >nul 2>&1
echo       [+] Registered 'DpcLockerSentinel' in Windows Task Scheduler.

:: 2. Register HKLM Enterprise Registry Run Key
echo  [2/2] Adding Enterprise Run Key in HKLM Registry...
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "DpcLockerSentinel" /t REG_SZ /d "\"%START_BAT%\"" /f >nul 2>&1
echo       [+] Added HKLM Run Key.

echo.
echo ===============================================================================
echo  [+] PERMANENT PROTECTION INSTALLED (CLEAN SINGLE ENTRY)!
echo ===============================================================================
echo  The Sentinel will start automatically every time your PC boots.
echo.
pause

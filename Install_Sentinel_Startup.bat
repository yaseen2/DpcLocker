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
echo  [#] DPCLOCKER :: INSTALLING PERMANENT MULTI-LAYERED AUTO-STARTUP SENTINEL
echo ===============================================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "START_BAT=%SCRIPT_DIR%Start_Proxy_Sentinel.bat"

:: 1. Register Windows Task Scheduler Tasks (Hidden from Task Manager Startup Tab)
echo  [1/3] Registering in Windows Task Scheduler (Highest Privileges)...
schtasks /create /tn "DpcLockerSentinelLogon" /tr "\"%START_BAT%\"" /sc onlogon /rl highest /f >nul 2>&1
schtasks /create /tn "DpcLockerSentinelBoot" /tr "\"%START_BAT%\"" /sc onstart /rl highest /f >nul 2>&1
echo       [+] Registered 'DpcLockerSentinelLogon' and 'DpcLockerSentinelBoot'

:: 2. Register HKLM Enterprise Registry Run Key
echo  [2/3] Adding Enterprise Run Key in HKLM Registry...
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "DpcLockerSentinel" /t REG_SZ /d "\"%START_BAT%\"" /f >nul 2>&1
echo       [+] Added HKLM Run Key.

:: 3. Create Windows Startup Folder Shortcut
echo  [3/3] Creating Windows Startup Folder Shortcut...
set "STARTUP_FOLDER=%ALLUSERSPROFILE%\Microsoft\Windows\Start Menu\Programs\Startup"
set "VBS_SCRIPT=%TEMP%\create_sentinel_shortcut.vbs"

echo Set oWS = WScript.CreateObject("WScript.Shell") > "%VBS_SCRIPT%"
echo sLinkFile = "%STARTUP_FOLDER%\WindowsProxySentinel.lnk" >> "%VBS_SCRIPT%"
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> "%VBS_SCRIPT%"
echo oLink.TargetPath = "%START_BAT%" >> "%VBS_SCRIPT%"
echo oLink.WorkingDirectory = "%SCRIPT_DIR%" >> "%VBS_SCRIPT%"
echo oLink.WindowStyle = 7 >> "%VBS_SCRIPT%"
echo oLink.IconLocation = "shell32.dll,48" >> "%VBS_SCRIPT%"
echo oLink.Description = "DpcLocker Windows Real-time Proxy Sentinel" >> "%VBS_SCRIPT%"
echo oLink.Save >> "%VBS_SCRIPT%"

cscript //nologo "%VBS_SCRIPT%" >nul 2>&1
del "%VBS_SCRIPT%" >nul 2>&1
echo       [+] Added system-wide Startup link.

echo.
echo ===============================================================================
echo  [+] PERMANENT PROTECTION INSTALLED!
echo ===============================================================================
echo  Even if disabled in Task Manager's Startup tab, Windows Task Scheduler
echo  and the HKLM Enterprise registry will automatically start the Sentinel!
echo.
pause

@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER - UNLOCK TEST DPC
color 0A

set ADB="C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe"

:START
cls
echo ===============================================================================
echo  [#] DPCLOCKER :: UNLOCK TEST DPC (WIRELESS ^& USB)
echo ===============================================================================
echo.

REM 1. Check if an online device is already active
%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    goto PERFORM_UNLOCK
)

echo  [-] No active device detected. Scanning wireless network (mDNS)...
%ADB% disconnect > nul 2>&1
for /f "tokens=3" %%A in ('%ADB% mdns services ^| findstr "_adb-tls-connect._tcp"') do (
    echo  [+] Discovered dynamic port: %%A
    echo  [*] Connecting...
    %ADB% connect %%A
)

%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    goto PERFORM_UNLOCK
)

:RECOVERY_MENU
echo.
echo  =============================================================================
echo   [!] DEVICE NOT REACHABLE OR PORT CHANGED AFTER TOGGLING WIRELESS DEBUGGING
echo  =============================================================================
echo.
echo   Options:
echo     [1] Auto-Scan mDNS again
echo     [2] Enter Port manually from phone (Developer Options -^> Wireless Debugging)
echo     [3] Pair device with pairing code
echo     [4] Restart ADB server daemon (clears zombie connections)
echo     [5] Launch Full DpcLocker Cyber Control Center
echo     [0] Cancel
echo.
set /p REC=" [>] Select option [1-5]: "

if "%REC%"=="1" goto START
if "%REC%"=="2" goto MANUAL_PORT
if "%REC%"=="3" goto PAIR_DEVICE
if "%REC%"=="4" goto RESTART_ADB
if "%REC%"=="5" (
    call "DpcLocker_Control_Center.bat"
    exit /b 0
)
if "%REC%"=="0" exit /b 1
goto RECOVERY_MENU

:MANUAL_PORT
set DEF_IP=192.168.1.13
set /p DEF_IP=" [?] Enter Phone IP [%DEF_IP%]: "
set /p M_PORT=" [?] Enter 5-digit Port on phone screen: "
if "%M_PORT%"=="" goto RECOVERY_MENU
echo [*] Connecting to %DEF_IP%:%M_PORT%...
%ADB% connect %DEF_IP%:%M_PORT%
%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 goto PERFORM_UNLOCK
echo [-] Failed to connect to %DEF_IP%:%M_PORT%.
pause
goto RECOVERY_MENU

:PAIR_DEVICE
set DEF_IP=192.168.1.13
set /p DEF_IP=" [?] Enter Phone IP [%DEF_IP%]: "
set /p P_PORT=" [?] Enter Port from 'Pair with pairing code' popup: "
set /p P_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code: "
%ADB% pair %DEF_IP%:%P_PORT% %P_CODE%
echo.
echo [*] If paired, enter the main Wireless Debugging Port:
goto MANUAL_PORT

:RESTART_ADB
echo [*] Killing ADB daemon...
%ADB% kill-server
ping 127.0.0.1 -n 2 > nul
%ADB% start-server
echo [+] Server restarted.
goto START

:PERFORM_UNLOCK
echo.
echo  [*] Sending Unlock Signal...
%ADB% shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    ping 127.0.0.1 -n 2 > nul
    %ADB% shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  ===========================================================================
    echo   [OK] TEST DPC IS UNLOCKED AND OPENED ON YOUR PHONE SCREEN!
    echo   (dpclocker_enabled = 0)
    echo  ===========================================================================
) else (
    echo  [!] Command failed. Re-opening recovery menu...
    goto RECOVERY_MENU
)
echo.
pause

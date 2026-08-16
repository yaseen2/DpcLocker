@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER CYBER CONSOLE - WIRELESS ADB POLICY ENGINE
color 0A

set ADB="C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe"

:MAIN_MENU
cls
echo ===============================================================================
echo  [#] DPCLOCKER CYBER CONTROL CENTER :: WIRELESS ^& POLICY ENGINE v2.5
echo ===============================================================================
echo.
echo  [*] ADB CORE STATUS:
echo  -----------------------------------------------------------------------------
%ADB% devices -l
echo  -----------------------------------------------------------------------------
echo.
echo  [CONNECTION ^& PAIRING]
echo    [1] Auto-Scan ^& Connect   (Purge stale sockets ^& discover dynamic mDNS port)
echo    [2] Manual IP:Port Connect (Enter IP and Port shown on phone screen)
echo    [3] Wireless Pair Device   (Enter Pairing Port ^& 6-digit Wi-Fi Code)
echo    [4] Reset ADB Subsystem    (Kill server, purge zombies, restart daemon)
echo.
echo  [POLICY ENFORCEMENT]
echo    [5] UNLOCK Test DPC        (dpclocker_enabled = 0 ^& Launch App)
echo    [6] LOCK Test DPC          (dpclocker_enabled = 1 ^& Force Stop)
echo.
echo  [DIAGNOSTICS ^& TELEMETRY]
echo    [7] Inspect Device Policy  (List all suspended packages ^& DPM active policies)
echo    [8] Stream Live Logs       (SecurityLogger / Pipeline / Blockers)
echo    [9] Wireless Port Scanner  (Raw mDNS query ^& network probe)
echo.
echo    [0] Exit Console
echo.
echo ===============================================================================
set /p CHOICE=" [>] Enter Command [0-9]: "

if "%CHOICE%"=="1" goto AUTO_CONNECT
if "%CHOICE%"=="2" goto MANUAL_CONNECT
if "%CHOICE%"=="3" goto PAIR_DEVICE
if "%CHOICE%"=="4" goto RESET_ADB
if "%CHOICE%"=="5" goto UNLOCK_DPC
if "%CHOICE%"=="6" goto LOCK_DPC
if "%CHOICE%"=="7" goto INSPECT_POLICY
if "%CHOICE%"=="8" goto STREAM_LOGS
if "%CHOICE%"=="9" goto SCAN_MDNS
if "%CHOICE%"=="0" goto EXIT_PROMPT

echo [!] Invalid option selected.
timeout /t 2 > nul
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [1] AUTO-SCAN & CONNECT
:: --------------------------------------------------------------------------------
:AUTO_CONNECT
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: AUTO-SCAN ^& WIRELESS CONNECT
echo ===============================================================================
echo.
echo  [1/3] Disconnecting stale/ghost sockets...
%ADB% disconnect > nul 2>&1
echo  [+] Stale sockets purged.
echo.
echo  [2/3] Querying Android mDNS Discovery Services...
echo  -----------------------------------------------------------------------------
%ADB% mdns services
echo  -----------------------------------------------------------------------------
echo.
echo  [3/3] Attempting auto-connection to discovered endpoints...
set FOUND=0
for /f "tokens=3" %%A in ('%ADB% mdns services ^| findstr "_adb-tls-connect._tcp"') do (
    set FOUND=1
    echo  [+] Detected target: %%A
    echo  [*] Handshaking...
    %ADB% connect %%A
)

if "!FOUND!"=="0" (
    echo.
    echo  [-] No mDNS service broadcast detected automatically.
    echo  [*] Fallback suggestion: Use option [2] to enter the Port shown on your phone.
)
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [2] MANUAL CONNECT
:: --------------------------------------------------------------------------------
:MANUAL_CONNECT
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: MANUAL IP ^& PORT CONNECTION
echo ===============================================================================
echo.
echo  Look at your phone: Developer Options -^> Wireless Debugging
echo  Note the "IP address ^& Port" (e.g. 192.168.1.13:34195)
echo.
set TARGET_IP=192.168.1.13
set /p TARGET_IP=" [?] Enter Phone IP address [%TARGET_IP%]: "
set /p TARGET_PORT=" [?] Enter Wireless Debugging Port (5 digits): "

if "%TARGET_PORT%"=="" (
    echo [!] Port cannot be empty!
    pause
    goto MANUAL_CONNECT
)

echo.
echo  [*] Initiating TCP handshake with %TARGET_IP%:%TARGET_PORT%...
%ADB% connect %TARGET_IP%:%TARGET_PORT%
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [3] PAIR NEW DEVICE
:: --------------------------------------------------------------------------------
:PAIR_DEVICE
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: WIRELESS DEBUGGING PAIRING WIZARD
echo ===============================================================================
echo.
echo  1. On Phone: Tap "Pair device with pairing code" in Wireless Debugging
echo  2. Note the "IP address ^& Port" shown ON THAT POPUP (port differs from main screen)
echo  3. Note the 6-digit Wi-Fi pairing code
echo.
set PAIR_IP=192.168.1.13
set /p PAIR_IP=" [?] Enter Pairing IP address [%PAIR_IP%]: "
set /p PAIR_PORT=" [?] Enter Pairing Port shown in popup: "
set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code: "

echo.
echo  [*] Sending TLS Pairing Request...
%ADB% pair %PAIR_IP%:%PAIR_PORT% %PAIR_CODE%
echo.
echo  [*] If pairing succeeded, now connect using option [1] or [2]!
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [4] RESET ADB SUBSYSTEM
:: --------------------------------------------------------------------------------
:RESET_ADB
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: RESET ADB SUBSYSTEM
echo ===============================================================================
echo.
echo  [*] Terminating adb daemon and clearing TCP sockets...
%ADB% kill-server
ping 127.0.0.1 -n 2 > nul
echo  [*] Spawning fresh ADB server daemon...
%ADB% start-server
echo  [+] Server daemon restarted successfully.
echo.
%ADB% devices
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [5] UNLOCK TEST DPC
:: --------------------------------------------------------------------------------
:UNLOCK_DPC
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: UNLOCK TEST DPC
echo ===============================================================================
echo.
REM Check if online device exists
%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% NEQ 0 (
    echo  [-] No online device detected. Attempting auto-reconnect...
    for /f "tokens=3" %%A in ('%ADB% mdns services ^| findstr "_adb-tls-connect._tcp"') do (
        %ADB% connect %%A
    )
)

echo  [*] Sending Signal: dpclocker_enabled = 0 (UNLOCKED)
%ADB% shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 0
    echo  [*] Launching Test DPC Management UI on phone...
    %ADB% shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now UNLOCKED and accessible on your phone screen!
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver unlock payload to phone.
    echo  [*] Check that Wireless Debugging is ON and use option [2] to reconnect.
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [6] LOCK TEST DPC
:: --------------------------------------------------------------------------------
:LOCK_DPC
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: LOCK TEST DPC
echo ===============================================================================
echo.
REM Check if online device exists
%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% NEQ 0 (
    echo  [-] No online device detected. Attempting auto-reconnect...
    for /f "tokens=3" %%A in ('%ADB% mdns services ^| findstr "_adb-tls-connect._tcp"') do (
        %ADB% connect %%A
    )
)

echo  [*] Sending Signal: dpclocker_enabled = 1 (LOCKED)
%ADB% shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 1
    echo  [*] Force-stopping Test DPC activity...
    %ADB% shell am force-stop com.afwsamples.testdpc
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now LOCKED! Any launch attempt from phone will be blocked.
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver lock payload to phone.
    echo  [*] Check that Wireless Debugging is ON and use option [2] to reconnect.
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [7] INSPECT DEVICE POLICY
:: --------------------------------------------------------------------------------
:INSPECT_POLICY
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: DEVICE POLICY ^& SUSPENDED PACKAGES INSPECTOR
echo ===============================================================================
echo.
echo  [*] Querying Device Policy Manager policies...
echo  -----------------------------------------------------------------------------
%ADB% shell "dumpsys device_policy | grep -E 'mSuspendedPackages|PackageNameSetPolicyValue|dpclocker'"
echo  -----------------------------------------------------------------------------
echo.
echo  [*] Checking Global DpcLocker Enabled Status:
%ADB% shell settings get global dpclocker_enabled
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [8] STREAM LIVE LOGS
:: --------------------------------------------------------------------------------
:STREAM_LOGS
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: LIVE TELEMETRY STREAM (Press Ctrl+C to stop)
echo ===============================================================================
echo.
%ADB% logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [9] SCAN MDNS
:: --------------------------------------------------------------------------------
:SCAN_MDNS
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: RAW MDNS NETWORK PROBE
echo ===============================================================================
echo.
%ADB% mdns services
echo.
%ADB% mdns check
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:EXIT_PROMPT
cls
echo [*] Exiting DpcLocker Cyber Console.
timeout /t 1 > nul
exit /b 0

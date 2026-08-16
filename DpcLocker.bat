@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER CYBER CONSOLE :: ALL-IN-ONE POLICY ENGINE
color 0A

set ADB=C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe

REM --- Command-line arguments handler ---
if /i "%1"=="unlock" goto UNLOCK_DPC_DIRECT
if /i "%1"=="lock" goto LOCK_DPC_DIRECT
if /i "%1"=="scan" goto AUTO_CONNECT
if /i "%1"=="pair" goto PAIR_DEVICE
if /i "%1"=="logs" goto STREAM_LOGS
if /i "%1"=="policy" goto INSPECT_POLICY

:MAIN_MENU
cls
call :DETECT_PRIMARY_TARGET
echo ===============================================================================
echo  [#] DPCLOCKER CYBER CONTROL CENTER :: ALL-IN-ONE POLICY ENGINE v3.1
echo ===============================================================================
echo.
echo  [*] ACTIVE TARGET: !TARGET_DISPLAY!
echo  -----------------------------------------------------------------------------
"%ADB%" devices -l
echo  -----------------------------------------------------------------------------
echo.
echo  [CORE ACTIONS]
echo    [1] UNLOCK Test DPC        (dpclocker_enabled = 0 ^& Launch App)
echo    [2] LOCK Test DPC          (dpclocker_enabled = 1 ^& Force Stop)
echo.
echo  [WIRELESS ^& PAIRING]
echo    [3] Auto-Scan ^& Connect   (Purge stale sockets ^& auto-connect to active port)
echo    [4] Manual IP:Port Connect (Enter IP and Port shown on phone screen)
echo    [5] Wireless Pair Device   (Re-pair after 'Forget PC' with 6-digit code)
echo    [6] Reset ADB Subsystem    (Kill server, purge zombies, restart daemon)
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

if "%CHOICE%"=="1" goto UNLOCK_DPC
if "%CHOICE%"=="2" goto LOCK_DPC
if "%CHOICE%"=="3" goto AUTO_CONNECT
if "%CHOICE%"=="4" goto MANUAL_CONNECT
if "%CHOICE%"=="5" goto PAIR_DEVICE
if "%CHOICE%"=="6" goto RESET_ADB
if "%CHOICE%"=="7" goto INSPECT_POLICY
if "%CHOICE%"=="8" goto STREAM_LOGS
if "%CHOICE%"=="9" goto SCAN_MDNS
if "%CHOICE%"=="0" goto EXIT_PROMPT

echo [!] Invalid option selected.
ping 127.0.0.1 -n 2 > nul
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [1] UNLOCK TEST DPC
:: --------------------------------------------------------------------------------
:UNLOCK_DPC
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: UNLOCK TEST DPC
echo ===============================================================================
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found to unlock.
    pause
    goto MAIN_MENU
)

echo  [*] Target Selected: !TARGET_SERIAL!
echo  [*] Sending Signal: dpclocker_enabled = 0 (UNLOCKED)
"%ADB%" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 0
    echo  [*] Launching Test DPC Management UI on phone...
    "%ADB%" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now UNLOCKED and accessible on your phone screen!
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver unlock payload to phone.
    echo  [*] Check that Wireless Debugging is ON or use option [5] if unpaired.
)
echo.
pause
goto MAIN_MENU

:UNLOCK_DPC_DIRECT
echo ===============================================================================
echo  [*] DPCLOCKER :: DIRECT UNLOCK
echo ===============================================================================
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo [!] No device detected.
    pause
    exit /b 1
)
"%ADB%" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
ping 127.0.0.1 -n 2 > nul
"%ADB%" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
echo.
echo [OK] Test DPC UNLOCKED and opened on phone (!TARGET_SERIAL!)!
echo.
pause
exit /b 0

:: --------------------------------------------------------------------------------
:: [2] LOCK TEST DPC
:: --------------------------------------------------------------------------------
:LOCK_DPC
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: LOCK TEST DPC
echo ===============================================================================
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found to lock.
    pause
    goto MAIN_MENU
)

echo  [*] Target Selected: !TARGET_SERIAL!
echo  [*] Sending Signal: dpclocker_enabled = 1 (LOCKED)
"%ADB%" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 1
    echo  [*] Force-stopping Test DPC activity...
    "%ADB%" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now LOCKED! Any launch attempt from phone will be blocked.
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver lock payload to phone.
    echo  [*] Check that Wireless Debugging is ON or use option [5] if unpaired.
)
echo.
pause
goto MAIN_MENU

:LOCK_DPC_DIRECT
echo ===============================================================================
echo  [*] DPCLOCKER :: DIRECT LOCK
echo ===============================================================================
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo [!] No device detected.
    pause
    exit /b 1
)
"%ADB%" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
ping 127.0.0.1 -n 2 > nul
"%ADB%" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
echo.
echo [OK] Test DPC is now LOCKED (!TARGET_SERIAL!)!
echo.
pause
exit /b 0

:: --------------------------------------------------------------------------------
:: [3] AUTO-SCAN & CONNECT
:: --------------------------------------------------------------------------------
:AUTO_CONNECT
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: AUTO-SCAN ^& WIRELESS CONNECT
echo ===============================================================================
echo.
echo  [1/3] Disconnecting stale/ghost sockets...
"%ADB%" disconnect > nul 2>&1
echo  [+] Stale sockets purged.
echo.
echo  [2/3] Querying Android mDNS Discovery Services...
echo  -----------------------------------------------------------------------------
"%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
type "%TEMP%\dpclocker_mdns.tmp"
echo  -----------------------------------------------------------------------------
echo.
echo  [3/3] Attempting auto-connection to discovered endpoints...
set FOUND=0
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    set FOUND=1
    echo  [+] Detected target: %%A
    echo  [*] Handshaking...
    "%ADB%" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  ===========================================================================
    echo   [OK] WIRELESS CONNECTION ESTABLISHED: !TARGET_SERIAL!
    echo  ===========================================================================
) else (
    echo  ===========================================================================
    echo   [!] CONNECTION REJECTED OR NOT AUTHORIZED
    echo   -------------------------------------------------------------------------
    echo   * Did you tap 'Forget PC' in Developer Options?
    echo   * If so, Android requires you to re-pair before allowing connections.
    echo  ===========================================================================
    echo.
    set /p REPAIR=" [?] Would you like to pair with a 6-digit code now? (Y/N): "
    if /i "!REPAIR!"=="Y" goto PAIR_DEVICE
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [4] MANUAL CONNECT
:: --------------------------------------------------------------------------------
:MANUAL_CONNECT
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: MANUAL IP ^& PORT CONNECTION
echo ===============================================================================
echo.
echo  Look at your phone: Developer Options -^> Wireless Debugging
echo  Note the "IP address ^& Port" (e.g. 192.168.1.13:38747)
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
"%ADB%" connect %TARGET_IP%:%TARGET_PORT%
echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  [+] SUCCESS: Connected to !TARGET_SERIAL!
) else (
    echo  [-] Connection failed. If you forgot this PC on your phone, use option [5] to pair.
)
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [5] PAIR NEW DEVICE
:: --------------------------------------------------------------------------------
:PAIR_DEVICE
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: WIRELESS DEBUGGING PAIRING WIZARD
echo ===============================================================================
echo.
echo  Instructions:
echo    1. On phone, go to Developer Options -^> Wireless Debugging
echo    2. Tap "Pair device with pairing code"
echo    3. Keep the popup OPEN on your phone screen!
echo.

"%ADB%" mdns services > "%TEMP%\dpclocker_pair_mdns.tmp" 2>&1
set AUTO_PAIR_ENDPOINT=
for /f "tokens=3" %%P in ('findstr /i "_adb-tls-pairing._tcp" "%TEMP%\dpclocker_pair_mdns.tmp"') do (
    set AUTO_PAIR_ENDPOINT=%%P
)
if exist "%TEMP%\dpclocker_pair_mdns.tmp" del "%TEMP%\dpclocker_pair_mdns.tmp" > nul 2>&1

if not "!AUTO_PAIR_ENDPOINT!"=="" (
    echo  [+] AUTO-DETECTED Pairing Endpoint: !AUTO_PAIR_ENDPOINT!
    echo.
    set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code from popup: "
    echo  [*] Sending TLS Pairing Request to !AUTO_PAIR_ENDPOINT!...
    "%ADB%" pair !AUTO_PAIR_ENDPOINT! !PAIR_CODE!
) else (
    set PAIR_IP=192.168.1.13
    set /p PAIR_IP=" [?] Enter Pairing IP address [%PAIR_IP%]: "
    set /p PAIR_PORT=" [?] Enter Pairing Port shown on the popup: "
    set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code from popup: "
    echo.
    echo  [*] Sending TLS Pairing Request to !PAIR_IP!:!PAIR_PORT!...
    "%ADB%" pair !PAIR_IP!:!PAIR_PORT! !PAIR_CODE!
)

echo.
echo  [*] Attempting auto-connection to main wireless port...
ping 127.0.0.1 -n 2 > nul
"%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  [*] Connecting to %%A...
    "%ADB%" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  ===========================================================================
    echo   [OK] PAIRING AND CONNECTION SUCCESSFUL: !TARGET_SERIAL!
    echo  ===========================================================================
) else (
    echo  [*] If auto-connect didn't trigger, check the main Port on phone and use Option [4].
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [6] RESET ADB SUBSYSTEM
:: --------------------------------------------------------------------------------
:RESET_ADB
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: RESET ADB SUBSYSTEM
echo ===============================================================================
echo.
echo  [*] Terminating adb daemon and clearing TCP sockets...
"%ADB%" kill-server
ping 127.0.0.1 -n 2 > nul
echo  [*] Spawning fresh ADB server daemon...
"%ADB%" start-server
echo  [+] Server daemon restarted successfully.
echo.
"%ADB%" devices
echo.
echo ===============================================================================
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
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found.
    pause
    goto MAIN_MENU
)

echo  [*] Querying Device Policy Manager policies on !TARGET_SERIAL!...
echo  -----------------------------------------------------------------------------
"%ADB%" -s !TARGET_SERIAL! shell "dumpsys device_policy | grep -E 'mSuspendedPackages|PackageNameSetPolicyValue|dpclocker'"
echo  -----------------------------------------------------------------------------
echo.
echo  [*] Checking Global DpcLocker Enabled Status:
"%ADB%" -s !TARGET_SERIAL! shell settings get global dpclocker_enabled
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
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found.
    pause
    goto MAIN_MENU
)
"%ADB%" -s !TARGET_SERIAL! logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
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
"%ADB%" mdns services
echo.
"%ADB%" mdns check
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: HELPER: DETECT PRIMARY TARGET SERIAL
:: --------------------------------------------------------------------------------
:DETECT_PRIMARY_TARGET
set TARGET_SERIAL=
set TARGET_DISPLAY=NO ACTIVE TARGET DETECTED

"%ADB%" devices > "%TEMP%\dpclocker_devs.tmp" 2>&1
for /f "tokens=1,2" %%A in ('findstr /R "device$" "%TEMP%\dpclocker_devs.tmp"') do (
    if "!TARGET_SERIAL!"=="" (
        set TARGET_SERIAL=%%A
        set TARGET_DISPLAY=%%A [ONLINE]
    )
)
if exist "%TEMP%\dpclocker_devs.tmp" del "%TEMP%\dpclocker_devs.tmp" > nul 2>&1
exit /b 0

:: --------------------------------------------------------------------------------
:: HELPER: ENSURE ACTIVE CONNECTION
:: --------------------------------------------------------------------------------
:ENSURE_CONNECTION
call :DETECT_PRIMARY_TARGET
if "!TARGET_SERIAL!"=="" (
    echo  [-] No active target. Attempting auto-connect via mDNS...
    "%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
    for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
        "%ADB%" connect %%A > nul 2>&1
    )
    if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1
    call :DETECT_PRIMARY_TARGET
)
exit /b 0

:EXIT_PROMPT
cls
echo [*] Exiting DpcLocker Cyber Console.
ping 127.0.0.1 -n 2 > nul
exit /b 0

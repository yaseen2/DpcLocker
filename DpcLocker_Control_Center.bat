@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER CYBER CONSOLE - WIRELESS ADB POLICY ENGINE
color 0A

set ADB=C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe

:MAIN_MENU
cls
echo ===============================================================================
echo  [#] DPCLOCKER CYBER CONTROL CENTER :: WIRELESS ^& POLICY ENGINE v2.7
echo ===============================================================================
echo.
echo  [*] ADB CORE STATUS:
echo  -----------------------------------------------------------------------------
"%ADB%" devices -l
echo  -----------------------------------------------------------------------------
echo.
echo  [CONNECTION ^& PAIRING]
echo    [1] Auto-Scan ^& Connect   (Discover dynamic mDNS port ^& connect automatically)
echo    [2] Manual IP:Port Connect (Enter IP and Port shown on your phone screen)
echo    [3] Wireless Pair Device   (Initial pairing setup using 6-digit Wi-Fi code)
echo    [4] Reset ADB Subsystem    (Kill server, purge ghost sockets, restart daemon)
echo.
echo  [POLICY ENFORCEMENT]
echo    [5] UNLOCK Test DPC        (dpclocker_enabled = 0 ^& Launch Policy Manager)
echo    [6] LOCK Test DPC          (dpclocker_enabled = 1 ^& Force Stop App)
echo.
echo  [DIAGNOSTICS ^& TOOLS]
echo    [7] Inspect Device Policy  (View suspended packages ^& active Device Owner rules)
echo    [8] Stream Live Logs       (SecurityLogger / Pipeline / Blockers telemetry)
echo    [9] Wireless Network Probe (Raw mDNS service query ^& port scan)
echo    [H] Setup ^& Pairing Guide  (Help for first-time users ^& troubleshooting)
echo.
echo    [0] Exit Console
echo.
echo ===============================================================================
set /p CHOICE=" [>] Enter Command [0-9 or H]: "

if /i "%CHOICE%"=="H" goto HELP_GUIDE
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
ping 127.0.0.1 -n 2 > nul
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [H] HELP & PAIRING GUIDE
:: --------------------------------------------------------------------------------
:HELP_GUIDE
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: WIRELESS SETUP ^& PAIRING GUIDE (FOR ALL USERS)
echo ===============================================================================
echo.
echo  1. ENABLE WIRELESS DEBUGGING (ON YOUR PHONE):
echo     * Open Settings -^> System -^> Developer Options.
echo     * Make sure both "USB Debugging" and "Wireless Debugging" are turned ON.
echo     * Ensure your phone and computer are connected to the SAME Wi-Fi network.
echo.
echo  2. FIRST TIME CONNECTING TO THIS COMPUTER?
echo     * Tap directly on "Wireless Debugging" text to enter its settings screen.
echo     * Tap "Pair device with pairing code".
echo       (Note: Do NOT use 'QR code' unless using Android Studio GUI scanner;
echo        command-line terminal tools use 'Pair device with pairing code').
echo     * Select option [3] in this console, which will guide you through entering
echo       the 6-digit code shown on your phone's screen.
echo     * Once paired, your phone permanently trusts this PC!
echo.
echo  3. ALREADY PAIRED PREVIOUSLY?
echo     * Simply choose option [1] (Auto-Scan) to connect in 1-second.
echo     * If Android changed its port after a reboot or toggle, use option [2]
echo       to enter the 5-digit port displayed under "IP address ^& Port".
echo.
echo ===============================================================================
pause
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
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    echo  ===========================================================================
    echo   [OK] WIRELESS CONNECTION ESTABLISHED SUCCESSFULLY!
    echo  ===========================================================================
) else (
    echo  ===========================================================================
    echo   [!] CONNECTION NOT ESTABLISHED OR DEVICE UNPAIRED
    echo   -------------------------------------------------------------------------
    echo   * If connecting this phone to this computer for the first time,
    echo     or if the device was forgotten/reset, initial pairing is required.
    echo   * Make sure phone and PC are on the same Wi-Fi network.
    echo  ===========================================================================
    echo.
    set /p REPAIR=" [?] Would you like to run the Pairing Wizard now? (Y/N): "
    if /i "!REPAIR!"=="Y" goto PAIR_DEVICE
)
echo.
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
echo  On your phone: Settings -^> Developer Options -^> Wireless Debugging
echo  Look at "IP address ^& Port" (for example: 192.168.1.13:38747)
echo.
set TARGET_IP=192.168.1.13
set /p TARGET_IP=" [?] Enter Phone IP address [%TARGET_IP%]: "
set /p TARGET_PORT=" [?] Enter 5-digit Wireless Port from phone screen: "

if "%TARGET_PORT%"=="" (
    echo [!] Port cannot be empty!
    pause
    goto MANUAL_CONNECT
)

echo.
echo  [*] Initiating TCP handshake with %TARGET_IP%:%TARGET_PORT%...
"%ADB%" connect %TARGET_IP%:%TARGET_PORT%
echo.
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Connected to %TARGET_IP%:%TARGET_PORT%
) else (
    echo  [-] Connection failed. If this is a first-time connection or device was forgotten,
    echo      use option [3] to pair with a 6-digit code.
)
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
echo  Follow these quick steps on your phone:
echo    1. Open Settings -^> Developer Options -^> Wireless Debugging
echo    2. Tap "Pair device with pairing code"
echo       (Do NOT choose 'QR code'; select 'Pair device with pairing code')
echo    3. KEEP THE POPUP OPEN on your phone screen until pairing finishes!
echo.

REM Scan if pairing service is broadcasting
"%ADB%" mdns services > "%TEMP%\dpclocker_pair_mdns.tmp" 2>&1
set AUTO_PAIR_ENDPOINT=
for /f "tokens=3" %%P in ('findstr /i "_adb-tls-pairing._tcp" "%TEMP%\dpclocker_pair_mdns.tmp"') do (
    set AUTO_PAIR_ENDPOINT=%%P
)
if exist "%TEMP%\dpclocker_pair_mdns.tmp" del "%TEMP%\dpclocker_pair_mdns.tmp" > nul 2>&1

if not "!AUTO_PAIR_ENDPOINT!"=="" (
    echo  [+] AUTO-DETECTED Pairing Endpoint from Wi-Fi broadcast: !AUTO_PAIR_ENDPOINT!
    echo.
    set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code shown on popup: "
    echo  [*] Sending TLS Pairing Request to !AUTO_PAIR_ENDPOINT!...
    "%ADB%" pair !AUTO_PAIR_ENDPOINT! !PAIR_CODE!
) else (
    echo  [-] Note: Could not auto-detect pairing port from broadcast.
    echo      Look at the pairing popup on your phone screen for the IP and Port.
    echo.
    set PAIR_IP=192.168.1.13
    set /p PAIR_IP=" [?] Enter IP address shown on popup [%PAIR_IP%]: "
    set /p PAIR_PORT=" [?] Enter Pairing Port shown on popup: "
    set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code: "
    echo.
    echo  [*] Sending TLS Pairing Request to !PAIR_IP!:!PAIR_PORT!...
    "%ADB%" pair !PAIR_IP!:!PAIR_PORT! !PAIR_CODE!
)

echo.
echo  [*] Attempting auto-connection to main wireless debugging service...
ping 127.0.0.1 -n 2 > nul
"%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  [*] Connecting to %%A...
    "%ADB%" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    echo  ===========================================================================
    echo   [OK] PAIRING AND CONNECTION SUCCESSFUL! Device is online and trusted.
    echo  ===========================================================================
) else (
    echo  [*] Pairing command sent. Now close the pairing popup, check the main
    echo      Port shown under 'IP address ^& Port' on your phone, and use Option [2].
)
echo.
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
:: [5] UNLOCK TEST DPC
:: --------------------------------------------------------------------------------
:UNLOCK_DPC
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: UNLOCK TEST DPC
echo ===============================================================================
echo.
REM Check if online device exists
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% NEQ 0 (
    echo  [-] No online device detected. Attempting auto-reconnect...
    "%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
    for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
        "%ADB%" connect %%A
    )
    if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1
)

echo  [*] Sending Signal: dpclocker_enabled = 0 (UNLOCKED)
"%ADB%" shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 0
    echo  [*] Launching Test DPC Management UI on phone...
    "%ADB%" shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now UNLOCKED and accessible on your phone screen!
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver unlock payload to phone.
    echo  [*] Check that Wireless Debugging is ON and use option [3] if unpaired.
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
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% NEQ 0 (
    echo  [-] No online device detected. Attempting auto-reconnect...
    "%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
    for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
        "%ADB%" connect %%A
    )
    if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1
)

echo  [*] Sending Signal: dpclocker_enabled = 1 (LOCKED)
"%ADB%" shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 1
    echo  [*] Force-stopping Test DPC activity...
    "%ADB%" shell am force-stop com.afwsamples.testdpc
    echo.
    echo  ===========================================================================
    echo   [OK] Test DPC is now LOCKED! Any launch attempt from phone will be blocked.
    echo  ===========================================================================
) else (
    echo.
    echo  [!] FAILED: Could not deliver lock payload to phone.
    echo  [*] Check that Wireless Debugging is ON and use option [3] if unpaired.
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
"%ADB%" shell "dumpsys device_policy | grep -E 'mSuspendedPackages|PackageNameSetPolicyValue|dpclocker'"
echo  -----------------------------------------------------------------------------
echo.
echo  [*] Checking Global DpcLocker Enabled Status:
"%ADB%" shell settings get global dpclocker_enabled
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
"%ADB%" logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
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

:EXIT_PROMPT
cls
echo [*] Exiting DpcLocker Cyber Console.
ping 127.0.0.1 -n 2 > nul
exit /b 0

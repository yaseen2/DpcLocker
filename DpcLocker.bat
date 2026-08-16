@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER MASTER SUITE :: STANDALONE PROTECTION ENGINE
color 0A

:: --------------------------------------------------------------------------------
:: INITIALIZATION: RESOLVE PORTABLE ADB & CONFIGURATION
:: --------------------------------------------------------------------------------
call :RESOLVE_ADB
call :LOAD_CONFIG

:: Command-line argument dispatchers
if /i "%1"=="unlock" goto UNLOCK_DPC_DIRECT
if /i "%1"=="lock" goto LOCK_DPC_DIRECT
if /i "%1"=="scan" goto AUTO_CONNECT
if /i "%1"=="pair" goto PAIR_DEVICE
if /i "%1"=="install" goto INSTALL_APK_DIRECT
if /i "%1"=="setup" goto SETUP_DEVICE_OWNER
if /i "%1"=="logs" goto STREAM_LOGS
if /i "%1"=="policy" goto INSPECT_POLICY

:: --------------------------------------------------------------------------------
:: MAIN DASHBOARD
:: --------------------------------------------------------------------------------
:MAIN_MENU
cls
call :DETECT_PRIMARY_TARGET
echo ===============================================================================
echo  [#] DPCLOCKER MASTER SUITE :: STANDALONE PROTECTION ENGINE v3.5
echo ===============================================================================
echo.
echo  [*] ADB ENGINE  : !ADB_TYPE!
echo  [*] ACTIVE PHONE: !TARGET_DISPLAY!
echo  -----------------------------------------------------------------------------
"!ADB!" devices -l
echo  -----------------------------------------------------------------------------
echo.
echo  [ANDROID POLICY ^& LOCK]
echo    [1] UNLOCK Test DPC              (Allow settings access on phone)
echo    [2] LOCK Test DPC                (Block settings access on phone)
echo.
echo  [WIRELESS ^& PAIRING]
echo    [3] Auto-Scan ^& Connect Wi-Fi    (Dynamic mDNS Discovery ^& Port Detection)
echo    [4] Manual IP:Port Connect       (Enter IP and Port from phone screen)
echo    [5] Pair Phone with 6-Digit Code (Pairing Wizard after 'Forget PC')
echo    [6] Reset ADB Subsystem          (Kill server, purge zombies, restart daemon)
echo.
echo  [SETUP ^& DEPLOYMENT]
echo    [7] Install/Update TestDPC APK   (Deploy pre-built APK over USB/Wi-Fi)
echo    [8] 1-Click Set Device Owner     (First-time Provisioning Wizard)
echo.
echo  [DIAGNOSTICS ^& WINDOWS PROTECTION]
echo    [9] Inspect Policies ^& Logs      (View suspended apps / Live Logcat)
echo    [W] Windows Browser Protection   (Lockdown Incognito on Chrome/Edge/Brave)
echo.
echo    [0] Exit Console
echo.
echo ===============================================================================
set /p CHOICE=" [>] Select Option: "

if "%CHOICE%"=="1" goto UNLOCK_DPC
if "%CHOICE%"=="2" goto LOCK_DPC
if "%CHOICE%"=="3" goto AUTO_CONNECT
if "%CHOICE%"=="4" goto MANUAL_CONNECT
if "%CHOICE%"=="5" goto PAIR_DEVICE
if "%CHOICE%"=="6" goto RESET_ADB
if "%CHOICE%"=="7" goto INSTALL_APK
if "%CHOICE%"=="8" goto SETUP_DEVICE_OWNER
if "%CHOICE%"=="9" goto DIAGNOSTICS_SUBMENU
if /i "%CHOICE%"=="W" goto WINDOWS_PROTECTION
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
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 0
    echo  [*] Launching Test DPC Management UI on phone...
    "!ADB!" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
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
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
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
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  [+] SUCCESS: Device Global Setting applied: dpclocker_enabled = 1
    echo  [*] Force-stopping Test DPC activity...
    "!ADB!" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
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
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
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
"!ADB!" disconnect > nul 2>&1
echo  [+] Stale sockets purged.
echo.
echo  [2/3] Querying Android mDNS Discovery Services...
echo  -----------------------------------------------------------------------------
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
type "%TEMP%\dpclocker_mdns.tmp"
echo  -----------------------------------------------------------------------------
echo.
echo  [3/3] Attempting auto-connection to discovered endpoints...
set FOUND=0
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    set FOUND=1
    echo  [+] Detected target: %%A
    echo  [*] Handshaking...
    "!ADB!" connect %%A
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
echo  Note the "IP address ^& Port" (e.g. !SAVED_IP!:38747)
echo.
set /p TARGET_IP=" [?] Enter Phone IP address [!SAVED_IP!]: "
if "!TARGET_IP!"=="" set TARGET_IP=!SAVED_IP!
set /p TARGET_PORT=" [?] Enter Wireless Debugging Port (5 digits): "

if "%TARGET_PORT%"=="" (
    echo [!] Port cannot be empty!
    pause
    goto MANUAL_CONNECT
)

call :SAVE_CONFIG "!TARGET_IP!"

echo.
echo  [*] Initiating TCP handshake with %TARGET_IP%:%TARGET_PORT%...
"!ADB!" connect %TARGET_IP%:%TARGET_PORT%
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

"!ADB!" mdns services > "%TEMP%\dpclocker_pair_mdns.tmp" 2>&1
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
    "!ADB!" pair !AUTO_PAIR_ENDPOINT! !PAIR_CODE!
) else (
    set /p PAIR_IP=" [?] Enter Pairing IP address [!SAVED_IP!]: "
    if "!PAIR_IP!"=="" set PAIR_IP=!SAVED_IP!
    set /p PAIR_PORT=" [?] Enter Pairing Port shown on the popup: "
    set /p PAIR_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code from popup: "
    call :SAVE_CONFIG "!PAIR_IP!"
    echo.
    echo  [*] Sending TLS Pairing Request to !PAIR_IP!:!PAIR_PORT!...
    "!ADB!" pair !PAIR_IP!:!PAIR_PORT! !PAIR_CODE!
)

echo.
echo  [*] Attempting auto-connection to main wireless port...
ping 127.0.0.1 -n 2 > nul
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  [*] Connecting to %%A...
    "!ADB!" connect %%A
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
"!ADB!" kill-server
ping 127.0.0.1 -n 2 > nul
echo  [*] Spawning fresh ADB server daemon...
"!ADB!" start-server
echo  [+] Server daemon restarted successfully.
echo.
"!ADB!" devices
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [7] INSTALL / UPDATE APK
:: --------------------------------------------------------------------------------
:INSTALL_APK
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: DEPLOY TEST DPC APK
echo ===============================================================================
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found to install APK.
    pause
    goto MAIN_MENU
)

set APK_FILE=%~dp0TestDPC.apk
if not exist "%APK_FILE%" (
    echo  [!] TestDPC.apk not found in root directory!
    echo  [*] Looking for build artifacts...
    set APK_FILE=%~dp0testdpc_source\app\build\outputs\apk\normal\debug\TestDPC-normal-debug.apk
)

if not exist "%APK_FILE%" (
    echo  [!] Could not locate compiled APK.
    pause
    goto MAIN_MENU
)

echo  [*] Target Device : !TARGET_SERIAL!
echo  [*] APK Payload   : %APK_FILE%
echo.
echo  [*] Installing / Updating on device...
"!ADB!" -s !TARGET_SERIAL! install -r -d "%APK_FILE%"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  ===========================================================================
    echo   [OK] TestDPC APK INSTALLED SUCCESSFULLY ON PHONE!
    echo  ===========================================================================
) else (
    echo.
    echo  [!] Installation failed. Check device storage and permissions.
)
echo.
pause
goto MAIN_MENU

:INSTALL_APK_DIRECT
call :ENSURE_CONNECTION
"!ADB!" -s !TARGET_SERIAL! install -r -d "%~dp0TestDPC.apk"
exit /b 0

:: --------------------------------------------------------------------------------
:: [8] 1-CLICK SET DEVICE OWNER
:: --------------------------------------------------------------------------------
:SETUP_DEVICE_OWNER
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: FIRST-TIME DEVICE OWNER PROVISIONING WIZARD
echo ===============================================================================
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  [!] No active device found. Connect via USB or Wi-Fi first.
    pause
    goto MAIN_MENU
)

echo  [Step 1/3] Checking for active user accounts on phone...
"!ADB!" -s !TARGET_SERIAL! shell dumpsys account > "%TEMP%\dpclocker_acc.tmp" 2>&1
findstr /i "Account {" "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  ---------------------------------------------------------------------------
    echo   [!] WARNING: ACCOUNTS DETECTED ON PHONE!
    echo   Android OS strictly forbids setting Device Owner if Google, WhatsApp,
    echo   or any other user accounts are currently logged in.
    echo.
    echo   ACTION REQUIRED ON YOUR PHONE:
    echo     1. Go to Settings -^> Passwords ^& Accounts (or Accounts)
    echo     2. Temporarily REMOVE all logged-in accounts
    echo     3. (You can log back into all accounts right after this step succeeds!)
    echo  ---------------------------------------------------------------------------
    echo.
    set /p PROCEED=" [?] Have you removed all accounts from the phone? (Y/N): "
    if /i not "!PROCEED!"=="Y" (
        if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
        goto MAIN_MENU
    )
)
if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1

echo.
echo  [Step 2/3] Removing residual secondary profiles...
"!ADB!" -s !TARGET_SERIAL! shell pm remove-user 10 > nul 2>&1

echo  [Step 3/3] Setting com.afwsamples.testdpc as Device Owner...
"!ADB!" -s !TARGET_SERIAL! shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver

if %ERRORLEVEL% EQU 0 (
    echo.
    echo  ===========================================================================
    echo   [OK] SUCCESS: TEST DPC IS NOW PERMANENT DEVICE OWNER!
    echo   * You can now re-add your Google and WhatsApp accounts on your phone.
    echo   * All DpcLocker security pipelines and browser guards are active.
    echo  ===========================================================================
) else (
    echo.
    echo  [!] Provisioning failed. Make sure all accounts are removed and try again.
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [9] DIAGNOSTICS & TELEMETRY SUBMENU
:: --------------------------------------------------------------------------------
:DIAGNOSTICS_SUBMENU
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: DIAGNOSTICS ^& TELEMETRY CENTER
echo ===============================================================================
echo.
echo    [1] Inspect Device Policy  (List all suspended packages ^& DPM active policies)
echo    [2] Stream Live Logs       (SecurityLogger / Pipeline / Blockers)
echo    [3] Wireless Port Scanner  (Raw mDNS query ^& network probe)
echo    [0] Back to Main Menu
echo.
set /p DS=" [>] Select Option [0-3]: "
if "%DS%"=="1" goto INSPECT_POLICY
if "%DS%"=="2" goto STREAM_LOGS
if "%DS%"=="3" goto SCAN_MDNS
goto MAIN_MENU

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
"!ADB!" -s !TARGET_SERIAL! shell "dumpsys device_policy | grep -E 'mSuspendedPackages|PackageNameSetPolicyValue|dpclocker'"
echo  -----------------------------------------------------------------------------
echo.
echo  [*] Checking Global DpcLocker Enabled Status:
"!ADB!" -s !TARGET_SERIAL! shell settings get global dpclocker_enabled
echo.
echo ===============================================================================
pause
goto MAIN_MENU

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
"!ADB!" -s !TARGET_SERIAL! logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
goto MAIN_MENU

:SCAN_MDNS
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: RAW MDNS NETWORK PROBE
echo ===============================================================================
echo.
"!ADB!" mdns services
echo.
"!ADB!" mdns check
echo.
echo ===============================================================================
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [W] WINDOWS BROWSER PROTECTION
:: --------------------------------------------------------------------------------
:WINDOWS_PROTECTION
cls
echo ===============================================================================
echo  [*] DPCLOCKER :: WINDOWS BROWSER INCOGNITO LOCKDOWN
echo ===============================================================================
echo.
echo  This tool locks down Windows browsers (Chrome, Edge, Brave):
echo    * Disables Incognito / InPrivate Mode
echo    * Enforces SafeSearch across Google, Bing, YouTube
echo.
echo  Options:
echo    [1] Enable Windows Incognito Lockdown (Run PowerShell Engine)
echo    [2] Apply Direct Registry Policies (.reg)
echo    [0] Back to Main Menu
echo.
set /p WIN_OPT=" [>] Select Option [0-2]: "
if "%WIN_OPT%"=="1" (
    powershell -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
    pause
    goto MAIN_MENU
)
if "%WIN_OPT%"=="2" (
    reg import "%~dp0enable_windows_protection.reg"
    echo [+] Registry policies imported successfully.
    pause
    goto MAIN_MENU
)
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: HELPER: RESOLVE PORTABLE ADB WITH AUTO-DOWNLOADER
:: --------------------------------------------------------------------------------
:RESOLVE_ADB
set ADB_TYPE=LOCAL SDK

REM 1. Check local bundled platform-tools in repo
if exist "%~dp0tools\platform-tools\adb.exe" (
    set "ADB=%~dp0tools\platform-tools\adb.exe"
    set ADB_TYPE=PORTABLE (tools/platform-tools)
    exit /b 0
)

REM 2. Check Android Studio standard path
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    set ADB_TYPE=ANDROID STUDIO SDK
    exit /b 0
)

REM 3. Check system PATH
where adb.exe > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "ADB=adb.exe"
    set ADB_TYPE=SYSTEM PATH
    exit /b 0
)

REM 4. Auto-Download official Google Platform-Tools
cls
echo ===============================================================================
echo  [*] DPCLOCKER STANDALONE SETUP :: ADB NOT DETECTED
echo ===============================================================================
echo.
echo  No Android SDK or ADB tool was found on this computer.
echo  Downloading official Google Android Platform-Tools (Portable)...
echo.
if not exist "%~dp0tools" mkdir "%~dp0tools"
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'platform-tools.zip'; Write-Host 'Downloading platform-tools from Google CDN...'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip; Write-Host 'Extracting portable binaries...'; Expand-Archive -Path $zip -DestinationPath '%~dp0tools' -Force; Remove-Item $zip; Write-Host 'Setup Complete!'"

if exist "%~dp0tools\platform-tools\adb.exe" (
    set "ADB=%~dp0tools\platform-tools\adb.exe"
    set ADB_TYPE=PORTABLE AUTO-DOWNLOADED
    echo.
    echo  [+] Portable ADB installed successfully to: tools/platform-tools/
    ping 127.0.0.1 -n 2 > nul
    exit /b 0
) else (
    echo.
    echo  [!] Failed to download ADB automatically. Please install platform-tools.
    pause
    exit /b 1
)

:: --------------------------------------------------------------------------------
:: HELPER: LOAD & SAVE PERSISTENT CONFIG
:: --------------------------------------------------------------------------------
:LOAD_CONFIG
set CONFIG_FILE=%~dp0dpclocker_config.ini
set SAVED_IP=192.168.1.13

if exist "%CONFIG_FILE%" (
    for /f "tokens=1,2 delims==" %%I in ('findstr /i "PHONE_IP" "%CONFIG_FILE%"') do (
        if "%%I"=="PHONE_IP" set SAVED_IP=%%J
    )
) else (
    echo PHONE_IP=192.168.1.13 > "%CONFIG_FILE%"
)
exit /b 0

:SAVE_CONFIG
if "%~1"=="" exit /b 0
set SAVED_IP=%~1
echo PHONE_IP=%~1 > "%~dp0dpclocker_config.ini"
exit /b 0

:: --------------------------------------------------------------------------------
:: HELPER: DETECT PRIMARY TARGET SERIAL
:: --------------------------------------------------------------------------------
:DETECT_PRIMARY_TARGET
set TARGET_SERIAL=
set TARGET_DISPLAY=NO ACTIVE TARGET DETECTED

"!ADB!" devices > "%TEMP%\dpclocker_devs.tmp" 2>&1
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
    "!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
    for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
        "!ADB!" connect %%A > nul 2>&1
    )
    if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1
    call :DETECT_PRIMARY_TARGET
)
exit /b 0

:EXIT_PROMPT
cls
echo [*] Exiting DpcLocker Master Suite.
ping 127.0.0.1 -n 2 > nul
exit /b 0

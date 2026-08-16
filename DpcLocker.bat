@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER MASTER SUITE :: STANDALONE PROTECTION ENGINE

:: --------------------------------------------------------------------------------
:: INITIALIZATION: COLOR PALETTE & ESCAPE ENGINE
:: --------------------------------------------------------------------------------
for /F "delims=" %%a in ('powershell -NoProfile -Command "[char]27"') do set "ESC=%%a"

set "R=!ESC![0m"
set "B=!ESC![1m"
set "DIM=!ESC![2m"

:: Professional Aesthetic Palette
set "C_HDR=!ESC![1;38;5;39m"       :: Electric Cyan
set "C_BORDER=!ESC![38;5;240m"     :: Dark Slate Border
set "C_BORDER_HI=!ESC![38;5;39m"   :: Highlighted Cyan Border
set "C_SEC=!ESC![1;38;5;75m"       :: Ice Blue Section Header
set "C_NUM=!ESC![1;38;5;221m"      :: Warm Gold / Amber Numbering
set "C_TXT=!ESC![38;5;253m"        :: Crisp Clean Text
set "C_SUB=!ESC![38;5;244m"        :: Slate Gray Subtext / Descriptions
set "C_OK=!ESC![1;38;5;82m"        :: Vivid Emerald Green (Online / Success)
set "C_WARN=!ESC![1;38;5;214m"     :: Amber Warning
set "C_ERR=!ESC![1;38;5;203m"      :: Crimson Coral Red (Locked / Error)
set "C_PROMPT=!ESC![1;38;5;51m"    :: Neon Turquoise Input Prompt
set "C_ACCENT=!ESC![1;38;5;141m"   :: Purple / Violet Accent

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
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![#] DPCLOCKER MASTER SUITE !R!!C_SUB!:: !C_ACCENT!STANDALONE PROTECTION ENGINE v3.5!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo   !C_SUB!ADB Engine   :!R! !C_TXT!!ADB_TYPE!!R!
echo   !C_SUB!Active Target:!R! !C_TARGET_COLOR!!TARGET_DISPLAY!!R!
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
"!ADB!" devices -l
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
echo.
echo  !C_SEC![ANDROID POLICY ^& LOCK]!R!
echo    !C_NUM![1]!R! !C_TXT!UNLOCK Test DPC             !C_SUB!(Allow device policy access on phone)!R!
echo    !C_NUM![2]!R! !C_TXT!LOCK Test DPC               !C_SUB!(Block device policy access on phone)!R!
echo.
echo  !C_SEC![WIRELESS ^& PAIRING]!R!
echo    !C_NUM![3]!R! !C_TXT!Auto-Scan ^& Connect Wi-Fi   !C_SUB!(Dynamic mDNS Discovery ^& Port Detection)!R!
echo    !C_NUM![4]!R! !C_TXT!Manual IP:Port Connect      !C_SUB!(Enter IP and Port from phone screen)!R!
echo    !C_NUM![5]!R! !C_TXT!Pair Phone with 6-Digit Code!C_SUB!(Pairing Wizard after 'Forget PC')!R!
echo    !C_NUM![6]!R! !C_TXT!Reset ADB Subsystem         !C_SUB!(Kill server, purge zombies, restart daemon)!R!
echo.
echo  !C_SEC![SETUP ^& DEPLOYMENT]!R!
echo    !C_NUM![7]!R! !C_TXT!Install/Update TestDPC APK  !C_SUB!(Deploy pre-built APK over USB/Wi-Fi)!R!
echo    !C_NUM![8]!R! !C_TXT!1-Click Set Device Owner    !C_SUB!(First-time Provisioning Wizard)!R!
echo.
echo  !C_SEC![DIAGNOSTICS ^& WINDOWS PROTECTION]!R!
echo    !C_NUM![9]!R! !C_TXT!Inspect Policies ^& Logs     !C_SUB!(View suspended apps / Live Logcat)!R!
echo    !C_NUM![W]!R! !C_TXT!Windows Browser Protection  !C_SUB!(Lockdown Incognito on Chrome/Edge/Brave)!R!
echo.
echo    !C_NUM![0]!R! !C_SUB!Exit Console!R!
echo.
echo !C_BORDER_HI!===============================================================================!R!
set /p CHOICE=" !C_PROMPT![>] Select Option: !R!"

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

echo !C_ERR![!] Invalid option selected.!R!
ping 127.0.0.1 -n 2 > nul
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [1] UNLOCK TEST DPC
:: --------------------------------------------------------------------------------
:UNLOCK_DPC
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: UNLOCK TEST DPC!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found to unlock.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target:!R! !C_OK!!TARGET_SERIAL!!R!
echo  !C_SUB![*] Sending Payload:!R! !C_TXT!dpclocker_enabled = 0 (UNLOCKED)!R!
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  !C_OK![+] SUCCESS: Setting applied (dpclocker_enabled = 0)!R!
    echo  !C_SUB![*] Launching Test DPC on phone...!R!
    "!ADB!" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] TEST DPC IS NOW UNLOCKED AND OPEN ON YOUR PHONE SCREEN!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] FAILED: Could not deliver unlock payload to phone.!R!
    echo  !C_WARN![*] Check Wireless Debugging or use option [5] if unpaired.!R!
)
echo.
pause
goto MAIN_MENU

:UNLOCK_DPC_DIRECT
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DIRECT UNLOCK!R!
echo !C_BORDER_HI!===============================================================================!R!
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo !C_ERR![!] No device detected.!R!
    pause
    exit /b 1
)
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 0
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
echo.
echo !C_OK![OK] Test DPC UNLOCKED and opened on phone (!TARGET_SERIAL!)!!R!
echo.
pause
exit /b 0

:: --------------------------------------------------------------------------------
:: [2] LOCK TEST DPC
:: --------------------------------------------------------------------------------
:LOCK_DPC
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: LOCK TEST DPC!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found to lock.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target:!R! !C_OK!!TARGET_SERIAL!!R!
echo  !C_SUB![*] Sending Payload:!R! !C_ERR!dpclocker_enabled = 1 (LOCKED)!R!
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  !C_OK![+] SUCCESS: Setting applied (dpclocker_enabled = 1)!R!
    echo  !C_SUB![*] Force-stopping Test DPC activity...!R!
    "!ADB!" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
    echo.
    echo  !C_ERR!===========================================================================!R!
    echo   !C_ERR!!B![OK] TEST DPC IS NOW LOCKED! Any launch from phone will be blocked.!R!
    echo  !C_ERR!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] FAILED: Could not deliver lock payload to phone.!R!
    echo  !C_WARN![*] Check Wireless Debugging or use option [5] if unpaired.!R!
)
echo.
pause
goto MAIN_MENU

:LOCK_DPC_DIRECT
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DIRECT LOCK!R!
echo !C_BORDER_HI!===============================================================================!R!
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo !C_ERR![!] No device detected.!R!
    pause
    exit /b 1
)
"!ADB!" -s !TARGET_SERIAL! shell settings put global dpclocker_enabled 1
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
echo.
echo !C_ERR![OK] Test DPC is now LOCKED (!TARGET_SERIAL!)!!R!
echo.
pause
exit /b 0

:: --------------------------------------------------------------------------------
:: [3] AUTO-SCAN & CONNECT
:: --------------------------------------------------------------------------------
:AUTO_CONNECT
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: AUTO-SCAN ^& WIRELESS CONNECT!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_SUB![1/3] Disconnecting stale/ghost sockets...!R!
"!ADB!" disconnect > nul 2>&1
echo  !C_OK![+] Stale sockets purged.!R!
echo.
echo  !C_SUB![2/3] Querying Android mDNS Discovery Services...!R!
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
type "%TEMP%\dpclocker_mdns.tmp"
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
echo.
echo  !C_SUB![3/3] Attempting auto-connection to discovered endpoints...!R!
set FOUND=0
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    set FOUND=1
    echo  !C_TXT![+] Detected target:!R! !C_OK!%%A!R!
    echo  !C_SUB![*] Handshaking...!R!
    "!ADB!" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] WIRELESS CONNECTION ESTABLISHED: !TARGET_SERIAL!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo  !C_WARN!===========================================================================!R!
    echo   !C_WARN![!] CONNECTION REJECTED OR NOT AUTHORIZED!R!
    echo   !C_SUB!-------------------------------------------------------------------------!R!
    echo   !C_TXT!* Did you tap 'Forget PC' in Developer Options?!R!
    echo   !C_TXT!* If so, Android requires you to re-pair before allowing connections.!R!
    echo  !C_WARN!===========================================================================!R!
    echo.
    set /p REPAIR=" !C_PROMPT![?] Would you like to pair with a 6-digit code now? (Y/N): !R!"
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
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: MANUAL IP ^& PORT CONNECTION!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_TXT!Look at your phone: Developer Options -^> Wireless Debugging!R!
echo  !C_SUB!Note the "IP address ^& Port" (e.g. !SAVED_IP!:38747)!R!
echo.
set /p TARGET_IP=" !C_PROMPT![?] Enter Phone IP address [!SAVED_IP!]: !R!"
if "!TARGET_IP!"=="" set TARGET_IP=!SAVED_IP!
set /p TARGET_PORT=" !C_PROMPT![?] Enter Wireless Debugging Port (5 digits): !R!"

if "%TARGET_PORT%"=="" (
    echo !C_ERR![!] Port cannot be empty!!R!
    pause
    goto MANUAL_CONNECT
)

call :SAVE_CONFIG "!TARGET_IP!"

echo.
echo  !C_SUB![*] Initiating TCP handshake with %TARGET_IP%:%TARGET_PORT%...!R!
"!ADB!" connect %TARGET_IP%:%TARGET_PORT%
echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  !C_OK![+] SUCCESS: Connected to !TARGET_SERIAL!!R!
) else (
    echo  !C_ERR![-] Connection failed. If you forgot this PC on phone, use option [5] to pair.!R!
)
echo !C_BORDER_HI!===============================================================================!R!
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [5] PAIR NEW DEVICE
:: --------------------------------------------------------------------------------
:PAIR_DEVICE
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: WIRELESS DEBUGGING PAIRING WIZARD!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_SEC!Instructions:!R!
echo    !C_TXT!1. On phone, go to Developer Options -^> Wireless Debugging!R!
echo    !C_TXT!2. Tap "Pair device with pairing code"!R!
echo    !C_WARN!3. Keep the popup OPEN on your phone screen!!R!
echo.

"!ADB!" mdns services > "%TEMP%\dpclocker_pair_mdns.tmp" 2>&1
set AUTO_PAIR_ENDPOINT=
for /f "tokens=3" %%P in ('findstr /i "_adb-tls-pairing._tcp" "%TEMP%\dpclocker_pair_mdns.tmp"') do (
    set AUTO_PAIR_ENDPOINT=%%P
)
if exist "%TEMP%\dpclocker_pair_mdns.tmp" del "%TEMP%\dpclocker_pair_mdns.tmp" > nul 2>&1

if not "!AUTO_PAIR_ENDPOINT!"=="" (
    echo  !C_OK![+] AUTO-DETECTED Pairing Endpoint: !AUTO_PAIR_ENDPOINT!!R!
    echo.
    set /p PAIR_CODE=" !C_PROMPT![?] Enter 6-digit Wi-Fi Pairing Code from popup: !R!"
    echo  !C_SUB![*] Sending TLS Pairing Request to !AUTO_PAIR_ENDPOINT!... !R!
    "!ADB!" pair !AUTO_PAIR_ENDPOINT! !PAIR_CODE!
) else (
    set /p PAIR_IP=" !C_PROMPT![?] Enter Pairing IP address [!SAVED_IP!]: !R!"
    if "!PAIR_IP!"=="" set PAIR_IP=!SAVED_IP!
    set /p PAIR_PORT=" !C_PROMPT![?] Enter Pairing Port shown on the popup: !R!"
    set /p PAIR_CODE=" !C_PROMPT![?] Enter 6-digit Wi-Fi Pairing Code from popup: !R!"
    call :SAVE_CONFIG "!PAIR_IP!"
    echo.
    echo  !C_SUB![*] Sending TLS Pairing Request to !PAIR_IP!:!PAIR_PORT!... !R!
    "!ADB!" pair !PAIR_IP!:!PAIR_PORT! !PAIR_CODE!
)

echo.
echo  !C_SUB![*] Attempting auto-connection to main wireless port...!R!
ping 127.0.0.1 -n 2 > nul
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  !C_SUB![*] Connecting to %%A...!R!
    "!ADB!" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
call :DETECT_PRIMARY_TARGET
if not "!TARGET_SERIAL!"=="" (
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] PAIRING AND CONNECTION SUCCESSFUL: !TARGET_SERIAL!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo  !C_WARN![*] If auto-connect didn't trigger, check the main Port on phone and use Option [4].!R!
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [6] RESET ADB SUBSYSTEM
:: --------------------------------------------------------------------------------
:RESET_ADB
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: RESET ADB SUBSYSTEM!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_SUB![*] Terminating adb daemon and clearing TCP sockets...!R!
"!ADB!" kill-server
ping 127.0.0.1 -n 2 > nul
echo  !C_SUB![*] Spawning fresh ADB server daemon...!R!
"!ADB!" start-server
echo  !C_OK![+] Server daemon restarted successfully.!R!
echo.
"!ADB!" devices
echo.
echo !C_BORDER_HI!===============================================================================!R!
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [7] INSTALL / UPDATE APK
:: --------------------------------------------------------------------------------
:INSTALL_APK
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DEPLOY TEST DPC APK!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found to install APK.!R!
    pause
    goto MAIN_MENU
)

set APK_FILE=%~dp0TestDPC.apk
if not exist "%APK_FILE%" (
    echo  !C_WARN![!] TestDPC.apk not found in root directory! Looking for build artifacts...!R!
    set APK_FILE=%~dp0testdpc_source\app\build\outputs\apk\normal\debug\TestDPC-normal-debug.apk
)

if not exist "%APK_FILE%" (
    echo  !C_ERR![!] Could not locate compiled APK.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target Device :!R! !C_OK!!TARGET_SERIAL!!R!
echo  !C_SUB![*] APK Payload   :!R! !C_TXT!%APK_FILE%!R!
echo.
echo  !C_SUB![*] Installing / Updating on device...!R!
"!ADB!" -s !TARGET_SERIAL! install -r -d "%APK_FILE%"
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] TestDPC APK INSTALLED SUCCESSFULLY ON PHONE!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] Installation failed. Check device storage and permissions.!R!
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
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: FIRST-TIME DEVICE OWNER PROVISIONING WIZARD!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found. Connect via USB or Wi-Fi first.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![Step 1/3] Checking for active user accounts on phone...!R!
"!ADB!" -s !TARGET_SERIAL! shell dumpsys account > "%TEMP%\dpclocker_acc.tmp" 2>&1
findstr /i "Account {" "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  !C_WARN!---------------------------------------------------------------------------!R!
    echo   !C_WARN!!B![!] WARNING: ACCOUNTS DETECTED ON PHONE!!R!
    echo   !C_TXT!Android OS strictly forbids setting Device Owner if Google, WhatsApp,!R!
    echo   !C_TXT!or any other user accounts are currently logged in.!R!
    echo.
    echo   !C_SEC!ACTION REQUIRED ON YOUR PHONE:!R!
    echo     !C_TXT!1. Go to Settings -^> Passwords ^& Accounts (or Accounts)!R!
    echo     !C_TXT!2. Temporarily REMOVE all logged-in accounts!R!
    echo     !C_SUB!3. (You can log back into all accounts right after this step succeeds!)!R!
    echo  !C_WARN!---------------------------------------------------------------------------!R!
    echo.
    set /p PROCEED=" !C_PROMPT![?] Have you removed all accounts from the phone? (Y/N): !R!"
    if /i not "!PROCEED!"=="Y" (
        if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
        goto MAIN_MENU
    )
)
if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1

echo.
echo  !C_SUB![Step 2/3] Removing residual secondary profiles...!R!
"!ADB!" -s !TARGET_SERIAL! shell pm remove-user 10 > nul 2>&1

echo  !C_SUB![Step 3/3] Setting com.afwsamples.testdpc as Device Owner...!R!
"!ADB!" -s !TARGET_SERIAL! shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver

if %ERRORLEVEL% EQU 0 (
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] SUCCESS: TEST DPC IS NOW PERMANENT DEVICE OWNER!!R!
    echo   !C_TXT!* You can now re-add your Google and WhatsApp accounts on your phone.!R!
    echo   !C_TXT!* All DpcLocker security pipelines and browser guards are active.!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] Provisioning failed. Make sure all accounts are removed and try again.!R!
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [9] DIAGNOSTICS & TELEMETRY SUBMENU
:: --------------------------------------------------------------------------------
:DIAGNOSTICS_SUBMENU
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DIAGNOSTICS ^& TELEMETRY CENTER!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo    !C_NUM![1]!R! !C_TXT!Inspect Device Policy  !C_SUB!(List all suspended packages ^& DPM active policies)!R!
echo    !C_NUM![2]!R! !C_TXT!Stream Live Logs       !C_SUB!(SecurityLogger / Pipeline / Blockers)!R!
echo    !C_NUM![3]!R! !C_TXT!Wireless Port Scanner  !C_SUB!(Raw mDNS query ^& network probe)!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
set /p DS=" !C_PROMPT![>] Select Option [0-3]: !R!"
if "%DS%"=="1" goto INSPECT_POLICY
if "%DS%"=="2" goto STREAM_LOGS
if "%DS%"=="3" goto SCAN_MDNS
goto MAIN_MENU

:INSPECT_POLICY
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DEVICE POLICY ^& SUSPENDED PACKAGES INSPECTOR!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Querying Device Policy Manager policies on !TARGET_SERIAL!... !R!
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
"!ADB!" -s !TARGET_SERIAL! shell "dumpsys device_policy | grep -E 'mSuspendedPackages|PackageNameSetPolicyValue|dpclocker'"
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
echo.
echo  !C_SUB![*] Checking Global DpcLocker Enabled Status:!R!
"!ADB!" -s !TARGET_SERIAL! shell settings get global dpclocker_enabled
echo.
echo !C_BORDER_HI!===============================================================================!R!
pause
goto MAIN_MENU

:STREAM_LOGS
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: LIVE TELEMETRY STREAM (Press Ctrl+C to stop)!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if "!TARGET_SERIAL!"=="" (
    echo  !C_ERR![!] No active device found.!R!
    pause
    goto MAIN_MENU
)
"!ADB!" -s !TARGET_SERIAL! logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
goto MAIN_MENU

:SCAN_MDNS
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: RAW MDNS NETWORK PROBE!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
"!ADB!" mdns services
echo.
"!ADB!" mdns check
echo.
echo !C_BORDER_HI!===============================================================================!R!
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [W] WINDOWS BROWSER PROTECTION
:: --------------------------------------------------------------------------------
:WINDOWS_PROTECTION
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: WINDOWS BROWSER INCOGNITO LOCKDOWN!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_TXT!This tool locks down Windows browsers (Chrome, Edge, Brave):!R!
echo    !C_TXT!* Disables Incognito / InPrivate Mode!R!
echo    !C_TXT!* Enforces SafeSearch across Google, Bing, YouTube!R!
echo.
echo  !C_SEC!Options:!R!
echo    !C_NUM![1]!R! !C_TXT!Enable Windows Incognito Lockdown (Run PowerShell Engine)!R!
echo    !C_NUM![2]!R! !C_TXT!Apply Direct Registry Policies (.reg)!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
set /p WIN_OPT=" !C_PROMPT![>] Select Option [0-2]: !R!"
if "%WIN_OPT%"=="1" (
    powershell -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
    pause
    goto MAIN_MENU
)
if "%WIN_OPT%"=="2" (
    reg import "%~dp0enable_windows_protection.reg"
    echo !C_OK![+] Registry policies imported successfully.!R!
    pause
    goto MAIN_MENU
)
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: HELPER: RESOLVE PORTABLE ADB WITH AUTO-DOWNLOADER
:: --------------------------------------------------------------------------------
:RESOLVE_ADB
set "ADB="
set "ADB_TYPE=UNKNOWN"

REM 1. Check local bundled platform-tools in repo
if exist "%~dp0tools\platform-tools\adb.exe" (
    set "ADB=%~dp0tools\platform-tools\adb.exe"
    set "ADB_TYPE=PORTABLE [tools/platform-tools]"
    exit /b 0
)

REM 2. Check Android Studio standard path
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    set "ADB_TYPE=ANDROID STUDIO SDK"
    exit /b 0
)

REM 3. Check system PATH
where adb.exe > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "ADB=adb.exe"
    set "ADB_TYPE=SYSTEM PATH"
    exit /b 0
)

REM 4. Auto-Download official Google Platform-Tools
cls
echo ===============================================================================
echo  [*] DPCLOCKER STANDALONE SETUP :: ADB NOT DETECTED
echo ===============================================================================
echo.
echo  No Android SDK or ADB tool was found on this computer.
echo  Downloading official Google Android Platform-Tools [Portable]...
echo.
if not exist "%~dp0tools" mkdir "%~dp0tools"
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'platform-tools.zip'; Write-Host 'Downloading platform-tools from Google CDN...'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip; Write-Host 'Extracting portable binaries...'; Expand-Archive -Path $zip -DestinationPath '%~dp0tools' -Force; Remove-Item $zip; Write-Host 'Setup Complete!'"

if exist "%~dp0tools\platform-tools\adb.exe" (
    set "ADB=%~dp0tools\platform-tools\adb.exe"
    set "ADB_TYPE=PORTABLE AUTO-DOWNLOADED"
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
set "C_TARGET_COLOR=!C_ERR!"

"!ADB!" devices > "%TEMP%\dpclocker_devs.tmp" 2>&1
for /f "tokens=1,2" %%A in ('findstr /R "device$" "%TEMP%\dpclocker_devs.tmp"') do (
    if "!TARGET_SERIAL!"=="" (
        set TARGET_SERIAL=%%A
        set TARGET_DISPLAY=%%A [ONLINE]
        set "C_TARGET_COLOR=!C_OK!"
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
    echo  !C_SUB![-] No active target. Attempting auto-connect via mDNS...!R!
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
echo !C_SUB![*] Exiting DpcLocker Master Suite.!R!
ping 127.0.0.1 -n 2 > nul
exit /b 0

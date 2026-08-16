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
call :ENUMERATE_DEVICES

set "ACTIVE_TARGET_INDEX=1"
set "ACTIVE_TARGET_MODE=SINGLE"

:: Command-line argument dispatchers
if /i "%1"=="unlock" goto UNLOCK_DPC_DIRECT
if /i "%1"=="lock" goto LOCK_DPC_DIRECT
if /i "%1"=="scan" goto AUTO_CONNECT
if /i "%1"=="pair" goto PAIR_DEVICE
if /i "%1"=="install" goto INSTALL_APK_DIRECT
if /i "%1"=="setup" goto SETUP_DEVICE_OWNER
if /i "%1"=="logs" goto STREAM_LOGS
if /i "%1"=="policy" goto INSPECT_POLICY
if /i "%1"=="winprotect" goto RUN_WIN_PROTECT_DIRECT

:: --------------------------------------------------------------------------------
:: MAIN DASHBOARD
:: --------------------------------------------------------------------------------
:MAIN_MENU
cls
call :ENUMERATE_DEVICES
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![#] DPCLOCKER MASTER SUITE !R!!C_SUB!:: !C_ACCENT!STANDALONE PROTECTION ENGINE v3.7!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo   !C_SUB!ADB Engine   :!R! !C_TXT!!ADB_TYPE!!R!
call :PRINT_HEADER_TARGET
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
"!ADB!" devices -l
echo  !C_BORDER!-------------------------------------------------------------------------------!R!
echo.
echo  !C_SEC![ANDROID POLICY ^& LOCK]!R!
echo    !C_NUM![1]!R! !C_TXT!UNLOCK Test DPC             !C_SUB![Allow device policy access on target]!R!
echo    !C_NUM![2]!R! !C_TXT!LOCK Test DPC               !C_SUB![Block device policy access on target]!R!
echo.
echo  !C_SEC![DEVICE MANAGEMENT ^& WIRELESS]!R!
if !DEV_TOTAL! GTR 1 (
    echo    !C_NUM![S]!R! !C_WARN!Switch Target Device      !C_SUB![Change active device or select ALL]!R!
)
echo    !C_NUM![3]!R! !C_TXT!Auto-Scan ^& Connect Wi-Fi   !C_SUB![Dynamic mDNS Discovery ^& Port Detection]!R!
echo    !C_NUM![4]!R! !C_TXT!1-Click Wireless Pair       !C_SUB![Auto-detects phone ^& only asks for 6-digit code]!R!
echo    !C_NUM![5]!R! !C_TXT!Discovered Wi-Fi Targets    !C_SUB![List/Connect specific devices on network]!R!
echo    !C_NUM![6]!R! !C_TXT!Reset ADB Subsystem         !C_SUB![Kill server, purge zombies, restart daemon]!R!
echo.
echo  !C_SEC![SETUP ^& DEPLOYMENT]!R!
echo    !C_NUM![7]!R! !C_TXT!Install/Update TestDPC APK  !C_SUB![Deploy pre-built APK over USB/Wi-Fi]!R!
echo    !C_NUM![8]!R! !C_TXT!1-Click Set Device Owner    !C_SUB![First-time Provisioning Wizard]!R!
echo.
echo  !C_SEC![DIAGNOSTICS ^& WINDOWS PROTECTION]!R!
echo    !C_NUM![9]!R! !C_TXT!Inspect Policies ^& Logs     !C_SUB![View suspended apps / Live Logcat]!R!
echo    !C_NUM![W]!R! !C_TXT!Windows Browser Protection  !C_SUB![Lockdown Incognito on Chrome/Edge/Brave]!R!
echo.
echo    !C_NUM![0]!R! !C_SUB!Exit Console!R!
echo.
echo !C_BORDER_HI!===============================================================================!R!
set /p CHOICE=" !C_PROMPT![>] Select Option: !R!"

if "%CHOICE%"=="1" goto UNLOCK_DPC
if "%CHOICE%"=="2" goto LOCK_DPC
if /i "%CHOICE%"=="S" goto SWITCH_DEVICE_MENU
if "%CHOICE%"=="3" goto AUTO_CONNECT
if "%CHOICE%"=="4" goto PAIR_DEVICE
if "%CHOICE%"=="5" goto SELECT_CONNECT_DEVICE
if "%CHOICE%"=="6" goto RESET_ADB
if "%CHOICE%"=="7" goto INSTALL_APK
if "%CHOICE%"=="8" goto SETUP_DEVICE_OWNER
if "%CHOICE%"=="9" goto DIAGNOSTICS_SUBMENU
if /i "%CHOICE%"=="W" goto WINDOWS_PROTECTION
if "%CHOICE%"=="0" goto EXIT_PROMPT

echo !C_ERR![!] Invalid option selected.!R!
ping 127.0.0.1 -n 2 > nul
goto MAIN_MENU

:PRINT_HEADER_TARGET
if "!ACTIVE_TARGET_MODE!"=="ALL" (
    echo   !C_SUB!Active Target:!R! !C_WARN![ALL ATTACHED DEVICES - !DEV_TOTAL! TOTAL]!R! !C_SUB![Press S to Switch]!R!
    exit /b 0
)
if !DEV_TOTAL! EQU 0 (
    echo   !C_SUB!Active Target:!R! !C_ERR!NO ACTIVE TARGET DETECTED!R!
    exit /b 0
)
if !DEV_TOTAL! EQU 1 (
    echo   !C_SUB!Active Target:!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!] [ONLINE]!R!
    exit /b 0
)
echo   !C_SUB!Active Target:!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!]!R! !C_SUB![Dev !ACTIVE_TARGET_INDEX! of !DEV_TOTAL! - Press S to Switch]!R!
exit /b 0

:: --------------------------------------------------------------------------------
:: [S] SWITCH TARGET DEVICE MENU
:: --------------------------------------------------------------------------------
:SWITCH_DEVICE_MENU
cls
call :ENUMERATE_DEVICES
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: SELECT ACTIVE TARGET DEVICE!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No attached devices detected.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SEC!Attached Android Devices:!R!
for /L %%i in (1,1,!DEV_TOTAL!) do (
    set "TAG="
    if "%%i"=="!ACTIVE_TARGET_INDEX!" if not "!ACTIVE_TARGET_MODE!"=="ALL" set "TAG=!C_OK![CURRENT ACTIVE]!R!"
    echo    !C_NUM![%%i]!R! !C_TXT!!DEV_MODEL_%%i!!R! !C_SUB![!DEV_SERIAL_%%i!]!R! !TAG!
)
echo.
echo    !C_NUM![A]!R! !C_WARN!TARGET ALL ATTACHED DEVICES SIMULTANEOUSLY!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
echo !C_BORDER_HI!===============================================================================!R!
set /p SW_CHOICE=" !C_PROMPT![?] Select Target [1-!DEV_TOTAL!, A, 0]: !R!"

if "%SW_CHOICE%"=="0" goto MAIN_MENU
if /i "%SW_CHOICE%"=="A" (
    set "ACTIVE_TARGET_MODE=ALL"
    echo.
    echo  !C_OK![+] Target Mode set to: ALL ATTACHED DEVICES!R!
    ping 127.0.0.1 -n 2 > nul
    goto MAIN_MENU
)

if %SW_CHOICE% GEQ 1 if %SW_CHOICE% LEQ !DEV_TOTAL! (
    set "ACTIVE_TARGET_INDEX=%SW_CHOICE%"
    set "ACTIVE_TARGET_MODE=SINGLE"
    echo.
    echo  !C_OK![+] Active Target set to: !DEV_MODEL_%SW_CHOICE%! [!DEV_SERIAL_%SW_CHOICE%!]!R!
    ping 127.0.0.1 -n 2 > nul
    goto MAIN_MENU
)

echo !C_ERR![!] Invalid selection.!R!
ping 127.0.0.1 -n 2 > nul
goto SWITCH_DEVICE_MENU

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
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No active device found to unlock.!R!
    pause
    goto MAIN_MENU
)

if "!ACTIVE_TARGET_MODE!"=="ALL" (
    echo  !C_WARN![*] Unlocking ALL !DEV_TOTAL! attached devices...!R!
    echo.
    for /L %%i in (1,1,!DEV_TOTAL!) do (
        echo  !C_SUB![*] Device %%i/!DEV_TOTAL!:!R! !C_TXT!!DEV_MODEL_%%i!!R! [!DEV_SERIAL_%%i!]
        "!ADB!" -s !DEV_SERIAL_%%i! shell settings put global dpclocker_enabled 0
        "!ADB!" -s !DEV_SERIAL_%%i! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity > nul 2>&1
        echo  !C_OK!    [+] UNLOCKED and Launched Test DPC!R!
    )
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] ALL !DEV_TOTAL! DEVICES UNLOCKED SUCCESSFULLY!!R!
    echo  !C_OK!===========================================================================!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target:!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!]!R!
echo  !C_SUB![*] Sending Payload:!R! !C_TXT!dpclocker_enabled = 0 [UNLOCKED]!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell settings put global dpclocker_enabled 0
if %ERRORLEVEL% EQU 0 (
    echo  !C_OK![+] SUCCESS: Setting applied [dpclocker_enabled = 0]!R!
    echo  !C_SUB![*] Launching Test DPC on phone...!R!
    "!ADB!" -s !CURRENT_TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] TEST DPC IS NOW UNLOCKED AND OPEN ON YOUR PHONE SCREEN!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] FAILED: Could not deliver unlock payload to phone.!R!
    echo  !C_WARN![*] Check Wireless Debugging or use option [4] if unpaired.!R!
)
echo.
pause
goto MAIN_MENU

:UNLOCK_DPC_DIRECT
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DIRECT UNLOCK!R!
echo !C_BORDER_HI!===============================================================================!R!
call :ENSURE_CONNECTION
if !DEV_TOTAL! EQU 0 (
    echo !C_ERR![!] No device detected.!R!
    pause
    exit /b 1
)
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell settings put global dpclocker_enabled 0
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
echo.
echo !C_OK![OK] Test DPC UNLOCKED and opened on phone [!CURRENT_TARGET_SERIAL!]!!R!
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
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No active device found to lock.!R!
    pause
    goto MAIN_MENU
)

if "!ACTIVE_TARGET_MODE!"=="ALL" (
    echo  !C_WARN![*] Locking ALL !DEV_TOTAL! attached devices...!R!
    echo.
    for /L %%i in (1,1,!DEV_TOTAL!) do (
        echo  !C_SUB![*] Device %%i/!DEV_TOTAL!:!R! !C_TXT!!DEV_MODEL_%%i!!R! [!DEV_SERIAL_%%i!]
        "!ADB!" -s !DEV_SERIAL_%%i! shell settings put global dpclocker_enabled 1
        "!ADB!" -s !DEV_SERIAL_%%i! shell am force-stop com.afwsamples.testdpc > nul 2>&1
        echo  !C_ERR!    [+] LOCKED and Force-Stopped Test DPC!R!
    )
    echo.
    echo  !C_ERR!===========================================================================!R!
    echo   !C_ERR!!B![OK] ALL !DEV_TOTAL! DEVICES LOCKED SUCCESSFULLY!!R!
    echo  !C_ERR!===========================================================================!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target:!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!]!R!
echo  !C_SUB![*] Sending Payload:!R! !C_ERR!dpclocker_enabled = 1 [LOCKED]!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    echo  !C_OK![+] SUCCESS: Setting applied [dpclocker_enabled = 1]!R!
    echo  !C_SUB![*] Force-stopping Test DPC activity...!R!
    "!ADB!" -s !CURRENT_TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
    echo.
    echo  !C_ERR!===========================================================================!R!
    echo   !C_ERR!!B![OK] TEST DPC IS NOW LOCKED! Any launch from phone will be blocked.!R!
    echo  !C_ERR!===========================================================================!R!
) else (
    echo.
    echo  !C_ERR![!] FAILED: Could not deliver lock payload to phone.!R!
    echo  !C_WARN![*] Check Wireless Debugging or use option [4] if unpaired.!R!
)
echo.
pause
goto MAIN_MENU

:LOCK_DPC_DIRECT
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DIRECT LOCK!R!
echo !C_BORDER_HI!===============================================================================!R!
call :ENSURE_CONNECTION
if !DEV_TOTAL! EQU 0 (
    echo !C_ERR![!] No device detected.!R!
    pause
    exit /b 1
)
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell settings put global dpclocker_enabled 1
ping 127.0.0.1 -n 2 > nul
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell am force-stop com.afwsamples.testdpc
echo.
echo !C_ERR![OK] Test DPC is now LOCKED [!CURRENT_TARGET_SERIAL!]!!R!
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
call :ENUMERATE_DEVICES
if !DEV_TOTAL! GTR 0 (
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] WIRELESS CONNECTION ESTABLISHED [!DEV_TOTAL! DEVICES ONLINE]!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo  !C_WARN!===========================================================================!R!
    echo   !C_WARN![!] CONNECTION REJECTED OR NOT AUTHORIZED!R!
    echo   !C_SUB!-------------------------------------------------------------------------!R!
    echo   !C_TXT!* Did you tap 'Forget PC' in Developer Options?!R!
    echo   !C_TXT!* If so, Android requires you to re-pair with a 6-digit code.!R!
    echo  !C_WARN!===========================================================================!R!
    echo.
    set /p REPAIR=" !C_PROMPT![?] Would you like to pair with a 6-digit code now? [Y/N]: !R!"
    if /i "!REPAIR!"=="Y" goto PAIR_DEVICE
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [4] 1-CLICK WIRELESS PAIRING WIZARD (AUTO-DETECTS IP & PORT)
:: --------------------------------------------------------------------------------
:PAIR_DEVICE
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: 1-CLICK WIRELESS PAIRING WIZARD!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_SEC!Instructions:!R!
echo    !C_TXT!1. On phone: Go to Developer Options -^> Wireless Debugging!R!
echo    !C_TXT!2. Tap "Pair device with pairing code"!R!
echo    !C_WARN!3. KEEP THE POPUP OPEN on your phone screen! [Do not close it]!R!
echo.
echo  !C_SUB![*] Scanning local Wi-Fi for device pairing broadcast...!R!

set SCAN_COUNT=0

:RETRY_PAIR_SCAN
set PAIR_FOUND_COUNT=0
set PAIR_EP_1=
set PAIR_EP_2=
set PAIR_EP_3=
set PAIR_NAME_1=
set PAIR_NAME_2=
set PAIR_NAME_3=

"!ADB!" mdns services > "%TEMP%\dpclocker_pair_mdns.tmp" 2>&1
for /f "tokens=1,2,3" %%A in ('findstr /i "_adb-tls-pairing._tcp" "%TEMP%\dpclocker_pair_mdns.tmp"') do (
    set /a PAIR_FOUND_COUNT+=1
    if !PAIR_FOUND_COUNT! EQU 1 set "PAIR_NAME_1=%%A" & set "PAIR_EP_1=%%C"
    if !PAIR_FOUND_COUNT! EQU 2 set "PAIR_NAME_2=%%A" & set "PAIR_EP_2=%%C"
    if !PAIR_FOUND_COUNT! EQU 3 set "PAIR_NAME_3=%%A" & set "PAIR_EP_3=%%C"
)
if exist "%TEMP%\dpclocker_pair_mdns.tmp" del "%TEMP%\dpclocker_pair_mdns.tmp" > nul 2>&1

if !PAIR_FOUND_COUNT! GTR 0 goto PAIR_TARGET_FOUND

set /a SCAN_COUNT+=1
if !SCAN_COUNT! LSS 3 (
    echo  !C_SUB![*] Waiting for pairing popup to open on phone... [Attempt !SCAN_COUNT! of 3]!R!
    ping 127.0.0.1 -n 3 > nul
    goto RETRY_PAIR_SCAN
)

echo  !C_WARN![-] No pairing broadcast detected automatically.!R!
echo  !C_SUB!    Make sure the "Pair device with pairing code" popup is currently OPEN on phone.!R!
echo.
echo    !C_NUM![1]!R! !C_TXT!Scan Wi-Fi again!R!
echo    !C_NUM![2]!R! !C_TXT!Enter Port manually from popup!R!
echo    !C_NUM![0]!R! !C_SUB!Cancel!R!
echo.
set /p PCHOICE=" !C_PROMPT![>] Select Option: !R!"
if "!PCHOICE!"=="1" set SCAN_COUNT=0 & goto RETRY_PAIR_SCAN
if "!PCHOICE!"=="2" goto PAIR_MANUAL_ENTRY
goto MAIN_MENU

:PAIR_TARGET_FOUND
if !PAIR_FOUND_COUNT! EQU 1 (
    set "SELECTED_PAIR_EP=!PAIR_EP_1!"
    echo  !C_OK![+] AUTO-DETECTED Device: !PAIR_NAME_1! @ !PAIR_EP_1!!R!
    goto PROMPT_CODE
)

echo  !C_SEC!Multiple devices discovered broadcasting pairing mode on Wi-Fi:!R!
if not "!PAIR_EP_1!"=="" echo    !C_NUM![1]!R! !C_TXT!!PAIR_NAME_1! [!PAIR_EP_1!]!R!
if not "!PAIR_EP_2!"=="" echo    !C_NUM![2]!R! !C_TXT!!PAIR_NAME_2! [!PAIR_EP_2!]!R!
if not "!PAIR_EP_3!"=="" echo    !C_NUM![3]!R! !C_TXT!!PAIR_NAME_3! [!PAIR_EP_3!]!R!
echo.
set /p DEV_SEL=" !C_PROMPT![?] Select device to pair [1-!PAIR_FOUND_COUNT!]: !R!"
if "!DEV_SEL!"=="1" set "SELECTED_PAIR_EP=!PAIR_EP_1!"
if "!DEV_SEL!"=="2" set "SELECTED_PAIR_EP=!PAIR_EP_2!"
if "!DEV_SEL!"=="3" set "SELECTED_PAIR_EP=!PAIR_EP_3!"

:PROMPT_CODE
echo.
set /p PAIR_CODE=" !C_PROMPT![?] Enter 6-digit Wi-Fi Pairing Code from phone popup: !R!"

echo.
echo  !C_SUB![*] Sending TLS Pairing Request to !SELECTED_PAIR_EP!... !R!
"!ADB!" pair !SELECTED_PAIR_EP! !PAIR_CODE!
goto AUTO_LINK_AFTER_PAIR

:PAIR_MANUAL_ENTRY
echo.
set /p PAIR_IP=" !C_PROMPT![?] Enter Pairing IP address [!SAVED_IP!]: !R!"
if "!PAIR_IP!"=="" set PAIR_IP=!SAVED_IP!
set /p PAIR_PORT=" !C_PROMPT![?] Enter Pairing Port shown on the popup: !R!"
set /p PAIR_CODE=" !C_PROMPT![?] Enter 6-digit Wi-Fi Pairing Code from popup: !R!"
call :SAVE_CONFIG "!PAIR_IP!"
echo.
echo  !C_SUB![*] Sending TLS Pairing Request to !PAIR_IP!:!PAIR_PORT!... !R!
"!ADB!" pair !PAIR_IP!:!PAIR_PORT! !PAIR_CODE!

:AUTO_LINK_AFTER_PAIR
echo.
echo  !C_SUB![*] Auto-discovering main connection port and linking device...!R!
ping 127.0.0.1 -n 2 > nul
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  !C_SUB![*] Auto-Connecting to %%A...!R!
    "!ADB!" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

echo.
call :ENUMERATE_DEVICES
if !DEV_TOTAL! GTR 0 (
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] DEVICE PAIRED AND CONNECTED AUTOMATICALLY: !CURRENT_TARGET_SERIAL!!R!
    echo  !C_OK!===========================================================================!R!
) else (
    echo  !C_WARN![*] Pairing completed. Check your phone's main Wireless Port and connect.!R!
)
echo.
pause
goto MAIN_MENU

:: --------------------------------------------------------------------------------
:: [5] SELECT / CONNECT DEVICE (LISTS DISCOVERED WI-FI TARGETS)
:: --------------------------------------------------------------------------------
:SELECT_CONNECT_DEVICE
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: DISCOVERED WI-FI TARGETS!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_SUB![*] Scanning local network for active Wireless Debugging services...!R!
"!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1

set DEV_MDNS_COUNT=0
set DEV_EP_1=
set DEV_EP_2=
set DEV_EP_3=
set DEV_NAME_1=
set DEV_NAME_2=
set DEV_NAME_3=

for /f "tokens=1,2,3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    set /a DEV_MDNS_COUNT+=1
    if !DEV_MDNS_COUNT! EQU 1 set "DEV_NAME_1=%%A" & set "DEV_EP_1=%%C"
    if !DEV_MDNS_COUNT! EQU 2 set "DEV_NAME_2=%%A" & set "DEV_EP_2=%%C"
    if !DEV_MDNS_COUNT! EQU 3 set "DEV_NAME_3=%%A" & set "DEV_EP_3=%%C"
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

if !DEV_MDNS_COUNT! EQU 0 (
    echo  !C_WARN![-] No active wireless services detected on local Wi-Fi.!R!
    echo  !C_SUB!    Ensure Wireless Debugging is toggled ON in Developer Options.!R!
    echo.
    echo    !C_NUM![1]!R! !C_TXT!Enter IP and Port manually!R!
    echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
    echo.
    set /p MN_SEL=" !C_PROMPT![>] Select: !R!"
    if "!MN_SEL!"=="1" goto MANUAL_CONNECT
    goto MAIN_MENU
)

echo  !C_SEC!Discovered Active Wi-Fi Endpoints:!R!
if not "!DEV_EP_1!"=="" echo    !C_NUM![1]!R! !C_TXT!!DEV_NAME_1! !C_OK![!DEV_EP_1!]!R!
if not "!DEV_EP_2!"=="" echo    !C_NUM![2]!R! !C_TXT!!DEV_NAME_2! !C_OK![!DEV_EP_2!]!R!
if not "!DEV_EP_3!"=="" echo    !C_NUM![3]!R! !C_TXT!!DEV_NAME_3! !C_OK![!DEV_EP_3!]!R!
echo    !C_NUM![M]!R! !C_TXT!Enter custom IP:Port manually!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
set /p SEL_EP=" !C_PROMPT![?] Select device to connect: !R!"

if /i "!SEL_EP!"=="M" goto MANUAL_CONNECT
if "!SEL_EP!"=="1" set "CHOSEN_EP=!DEV_EP_1!"
if "!SEL_EP!"=="2" set "CHOSEN_EP=!DEV_EP_2!"
if "!SEL_EP!"=="3" set "CHOSEN_EP=!DEV_EP_3!"
if "!SEL_EP!"=="0" goto MAIN_MENU

if not "!CHOSEN_EP!"=="" (
    echo.
    echo  !C_SUB![*] Connecting to !CHOSEN_EP!... !R!
    "!ADB!" connect !CHOSEN_EP!
    echo.
    call :ENUMERATE_DEVICES
    if !DEV_TOTAL! GTR 0 (
        echo  !C_OK![+] CONNECTED: !CURRENT_TARGET_SERIAL!!R!
    )
)
echo !C_BORDER_HI!===============================================================================!R!
pause
goto MAIN_MENU

:MANUAL_CONNECT
echo.
set /p TARGET_IP=" !C_PROMPT![?] Enter Phone IP address [!SAVED_IP!]: !R!"
if "!TARGET_IP!"=="" set TARGET_IP=!SAVED_IP!
set /p TARGET_PORT=" !C_PROMPT![?] Enter Wireless Debugging Port (5 digits): !R!"

if "%TARGET_PORT%"=="" (
    echo !C_ERR![!] Port cannot be empty!!R!
    pause
    goto MAIN_MENU
)

call :SAVE_CONFIG "!TARGET_IP!"
echo  !C_SUB![*] Initiating TCP handshake with %TARGET_IP%:%TARGET_PORT%...!R!
"!ADB!" connect %TARGET_IP%:%TARGET_PORT%
call :ENUMERATE_DEVICES
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
if !DEV_TOTAL! EQU 0 (
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

if "!ACTIVE_TARGET_MODE!"=="ALL" (
    echo  !C_WARN![*] Deploying APK to ALL !DEV_TOTAL! attached devices...!R!
    echo.
    for /L %%i in (1,1,!DEV_TOTAL!) do (
        echo  !C_SUB![*] Installing on Device %%i/!DEV_TOTAL!:!R! !C_TXT!!DEV_MODEL_%%i!!R! [!DEV_SERIAL_%%i!]
        "!ADB!" -s !DEV_SERIAL_%%i! install -r -d "%APK_FILE%"
    )
    echo.
    echo  !C_OK!===========================================================================!R!
    echo   !C_OK!!B![OK] APK DEPLOYED TO ALL !DEV_TOTAL! DEVICES SUCCESSFULLY!!R!
    echo  !C_OK!===========================================================================!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target Device :!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!]!R!
echo  !C_SUB![*] APK Payload   :!R! !C_TXT!%APK_FILE%!R!
echo.
echo  !C_SUB![*] Installing / Updating on device...!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! install -r -d "%APK_FILE%"
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
"!ADB!" -s !CURRENT_TARGET_SERIAL! install -r -d "%~dp0TestDPC.apk"
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
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No active device found. Connect via USB or Wi-Fi first.!R!
    pause
    goto MAIN_MENU
)

echo  !C_SUB![*] Target Device:!R! !C_OK!!CURRENT_TARGET_NAME! [!CURRENT_TARGET_SERIAL!]!R!
echo.
echo  !C_SUB![Step 1/3] Checking for active user accounts on phone...!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell dumpsys account > "%TEMP%\dpclocker_acc.tmp" 2>&1
findstr /i "Account {" "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo.
    echo  !C_WARN!---------------------------------------------------------------------------!R!
    echo   !C_WARN!!B![!] WARNING: ACCOUNTS DETECTED ON PHONE!!R!
    echo   !C_TXT!Android OS strictly forbids setting Device Owner if Google, WhatsApp,!R!
    echo   !C_TXT!or any other user accounts are currently logged in.!R!
    echo.
    echo   !C_SEC!ACTION REQUIRED ON YOUR PHONE:!R!
    echo     !C_TXT!1. Go to Settings -^> Passwords ^& Accounts [or Accounts]!R!
    echo     !C_TXT!2. Temporarily REMOVE all logged-in accounts!R!
    echo     !C_SUB!3. [You can log back into all accounts right after this step succeeds!]!R!
    echo  !C_WARN!---------------------------------------------------------------------------!R!
    echo.
    set /p PROCEED=" !C_PROMPT![?] Have you removed all accounts from the phone? [Y/N]: !R!"
    if /i not "!PROCEED!"=="Y" (
        if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1
        goto MAIN_MENU
    )
)
if exist "%TEMP%\dpclocker_acc.tmp" del "%TEMP%\dpclocker_acc.tmp" > nul 2>&1

echo.
echo  !C_SUB![Step 2/3] Removing residual secondary profiles...!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell pm remove-user 10 > nul 2>&1

echo  !C_SUB![Step 3/3] Setting com.afwsamples.testdpc as Device Owner...!R!
"!ADB!" -s !CURRENT_TARGET_SERIAL! shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver

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
echo    !C_NUM![1]!R! !C_TXT!Inspect Device Policy  !C_SUB![List all suspended packages ^& DPM active policies]!R!
echo    !C_NUM![2]!R! !C_TXT!Stream Live Logs       !C_SUB![SecurityLogger / Pipeline / Blockers]!R!
echo    !C_NUM![3]!R! !C_TXT!Wireless Port Scanner  !C_SUB![Raw mDNS query ^& network probe]!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
set /p DS=" !C_PROMPT![>] Select Option [0-3]: !R!"
if "%DS%"=="1" goto INSPECT_POLICY
if "%DS%"=="2" goto STREAM_LOGS
if "%DS%"=="3" goto SCAN_MDNS
goto MAIN_MENU

:INSPECT_POLICY
cls
call :ENSURE_CONNECTION
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No active device found.!R!
    pause
    goto MAIN_MENU
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\inspect_policy.ps1" -Serial "!CURRENT_TARGET_SERIAL!" -AdbPath "!ADB!"
echo.
pause
goto MAIN_MENU

:STREAM_LOGS
cls
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER :: LIVE TELEMETRY STREAM (Press Ctrl+C to stop)!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
call :ENSURE_CONNECTION
if !DEV_TOTAL! EQU 0 (
    echo  !C_ERR![!] No active device found.!R!
    pause
    goto MAIN_MENU
)
"!ADB!" -s !CURRENT_TARGET_SERIAL! logcat -s SecurityPipeline SecurityLogger NotoriousAppBlocker BrowserBlocker ImpulseGuardService
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
echo  !C_TXT!This tool locks down Windows browsers [Chrome, Edge, Brave]:!R!
echo    !C_TXT!* Disables Incognito / InPrivate Mode!R!
echo    !C_TXT!* Enforces SafeSearch across Google, Bing, YouTube!R!
echo    !C_TXT!* Sets Cloudflare Family-Filtered DNS [1.1.1.3]!R!
echo    !C_TXT!* Blocks Adult, Notorious and Proxy Domains in Hosts!R!
echo.
echo  !C_SEC!Options:!R!
echo    !C_NUM![1]!R! !C_TXT!Enable Full Windows Protection [Run Elevated Engine]!R!
echo    !C_NUM![2]!R! !C_TXT!Audit / Inspect Windows Protection Status!R!
echo    !C_NUM![3]!R! !C_TXT!Apply Direct Registry Policies [.reg]!R!
echo    !C_NUM![0]!R! !C_SUB!Back to Main Menu!R!
echo.
set /p WIN_OPT=" !C_PROMPT![>] Select Option [0-3]: !R!"
if "%WIN_OPT%"=="1" goto RUN_WIN_PROTECT
if "%WIN_OPT%"=="2" goto AUDIT_WIN_PROTECT
if "%WIN_OPT%"=="3" goto APPLY_WIN_REG
goto MAIN_MENU

:RUN_WIN_PROTECT
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo  !C_WARN![*] Windows Browser and DNS protection requires Administrator privileges.!R!
    echo  !C_SUB![*] Spawning elevated Administrator console...!R!
    powershell -NoProfile -Command "Start-Process '%~f0' -ArgumentList 'winprotect' -Verb RunAs"
    goto MAIN_MENU
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
echo.
pause
goto MAIN_MENU

:RUN_WIN_PROTECT_DIRECT
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0enable_windows_protection.ps1"
echo.
pause
exit /b 0

:AUDIT_WIN_PROTECT
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\inspect_windows_protection.ps1"
echo.
pause
goto MAIN_MENU

:APPLY_WIN_REG
reg import "%~dp0enable_windows_protection.reg"
echo !C_OK![+] Registry policies imported successfully.!R!
echo.
pause
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
echo !C_BORDER_HI!===============================================================================!R!
echo  !C_HDR!!B![*] DPCLOCKER STANDALONE SETUP :: ADB NOT DETECTED!R!
echo !C_BORDER_HI!===============================================================================!R!
echo.
echo  !C_WARN!No Android SDK or ADB tool was found on this computer.!R!
echo  !C_TXT!Downloading official Google Android Platform-Tools [Portable]...!R!
echo.
if not exist "%~dp0tools" mkdir "%~dp0tools"
powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'platform-tools.zip'; Write-Host 'Downloading platform-tools from Google CDN...'; Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip; Write-Host 'Extracting portable binaries...'; Expand-Archive -Path $zip -DestinationPath '%~dp0tools' -Force; Remove-Item $zip; Write-Host 'Setup Complete!'"

if exist "%~dp0tools\platform-tools\adb.exe" (
    set "ADB=%~dp0tools\platform-tools\adb.exe"
    set "ADB_TYPE=PORTABLE AUTO-DOWNLOADED"
    echo.
    echo  !C_OK![+] Portable ADB installed successfully to: tools/platform-tools/!R!
    ping 127.0.0.1 -n 2 > nul
    exit /b 0
) else (
    echo.
    echo  !C_ERR![!] Failed to download ADB automatically. Please install platform-tools.!R!
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
:: HELPER: ENUMERATE ATTACHED DEVICES & MODELS (SMART DEDUPLICATION)
:: --------------------------------------------------------------------------------
:ENUMERATE_DEVICES
set DEV_TOTAL=0
set CURRENT_TARGET_SERIAL=
set CURRENT_TARGET_NAME=Device

"!ADB!" devices -l > "%TEMP%\dpclocker_devs.tmp" 2>&1

:: Parse all online attached devices
for /f "tokens=1,*" %%A in ('findstr /R /C:"^[0-9a-zA-Z.:_-]*[ ]*device " "%TEMP%\dpclocker_devs.tmp"') do (
    set "RAW_SER=%%A"
    set "RAW_INFO=%%B"
    
    :: Extract model name if available
    set "DEV_MOD=Android Device"
    for %%X in (%%B) do (
        for /f "tokens=1,2 delims=:" %%Y in ("%%X") do (
            if "%%Y"=="model" set "DEV_MOD=%%Z"
        )
    )

    :: Deduplicate if both IP and mDNS TLS alias exist for same phone
    set "IS_DUP=0"
    if !DEV_TOTAL! GTR 0 (
        for /L %%k in (1,1,!DEV_TOTAL!) do (
            if "!RAW_SER!"=="!DEV_SERIAL_%%k!" set "IS_DUP=1"
        )
    )

    if "!IS_DUP!"=="0" (
        set /a DEV_TOTAL+=1
        set "DEV_SERIAL_!DEV_TOTAL!=!RAW_SER!"
        set "DEV_MODEL_!DEV_TOTAL!=!DEV_MOD!"
    )
)
if exist "%TEMP%\dpclocker_devs.tmp" del "%TEMP%\dpclocker_devs.tmp" > nul 2>&1

:: Validate active target index bounds
if !ACTIVE_TARGET_INDEX! GTR !DEV_TOTAL! set "ACTIVE_TARGET_INDEX=1"
if !ACTIVE_TARGET_INDEX! LSS 1 set "ACTIVE_TARGET_INDEX=1"

if !DEV_TOTAL! GTR 0 (
    set "CURRENT_TARGET_SERIAL=!DEV_SERIAL_%ACTIVE_TARGET_INDEX%!"
    set "CURRENT_TARGET_NAME=!DEV_MODEL_%ACTIVE_TARGET_INDEX%!"
)
exit /b 0

:: --------------------------------------------------------------------------------
:: HELPER: ENSURE ACTIVE CONNECTION
:: --------------------------------------------------------------------------------
:ENSURE_CONNECTION
call :ENUMERATE_DEVICES
if !DEV_TOTAL! EQU 0 (
    echo  !C_SUB![-] No active target. Attempting auto-connect via mDNS...!R!
    "!ADB!" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
    for /f "tokens=3" %%A in ('findstr /i "_adb-tls-connect._tcp _adb._tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
        "!ADB!" connect %%A > nul 2>&1
    )
    if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1
    call :ENUMERATE_DEVICES
)
exit /b 0

:EXIT_PROMPT
cls
echo !C_SUB![*] Exiting DpcLocker Master Suite.!R!
ping 127.0.0.1 -n 2 > nul
exit /b 0

@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER - LOCK TEST DPC
color 0C

set ADB=C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe

:START
cls
echo ===============================================================================
echo  [#] DPCLOCKER :: LOCK TEST DPC (WIRELESS ^& USB)
echo ===============================================================================
echo.

REM 1. Check if an online device is already active
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    goto PERFORM_LOCK
)

echo  [-] No active device detected. Scanning wireless network (mDNS)...
"%ADB%" disconnect > nul 2>&1
"%ADB%" mdns services > "%TEMP%\dpclocker_mdns.tmp" 2>&1
for /f "tokens=3" %%A in ('findstr /i "_tcp" "%TEMP%\dpclocker_mdns.tmp"') do (
    echo  [+] Discovered dynamic port: %%A
    echo  [*] Connecting...
    "%ADB%" connect %%A
)
if exist "%TEMP%\dpclocker_mdns.tmp" del "%TEMP%\dpclocker_mdns.tmp" > nul 2>&1

"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 (
    goto PERFORM_LOCK
)

:RECOVERY_MENU
echo.
echo  =============================================================================
echo   [!] DEVICE NOT REACHABLE OR INITIAL PAIRING REQUIRED
echo  =============================================================================
echo.
echo   * Ensure your phone and PC are connected to the SAME Wi-Fi network.
echo   * In Developer Options -^> Wireless Debugging, make sure the toggle is ON.
echo.
echo   Options:
echo     [1] Auto-Scan mDNS network again
echo     [2] Enter Port manually from phone (Settings -^> Wireless Debugging)
echo     [3] First-Time / Re-Pair Setup (Choose 'Pair device with pairing code')
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
echo.
echo  Look at your phone screen under "IP address ^& Port"
set DEF_IP=192.168.1.13
set /p DEF_IP=" [?] Enter Phone IP [%DEF_IP%]: "
set /p M_PORT=" [?] Enter 5-digit Port shown on screen: "
if "%M_PORT%"=="" goto RECOVERY_MENU
echo [*] Connecting to %DEF_IP%:%M_PORT%...
"%ADB%" connect %DEF_IP%:%M_PORT%
"%ADB%" devices | findstr /R "device$" > NUL
if %ERRORLEVEL% EQU 0 goto PERFORM_LOCK
echo [-] Failed to connect to %DEF_IP%:%M_PORT%.
pause
goto RECOVERY_MENU

:PAIR_DEVICE
echo.
echo  Instructions:
echo    1. On phone: Tap "Pair device with pairing code" (Do NOT choose QR code)
echo    2. Keep the popup open on your phone screen!
echo.
set DEF_IP=192.168.1.13
set /p DEF_IP=" [?] Enter IP shown on popup [%DEF_IP%]: "
set /p P_PORT=" [?] Enter Pairing Port shown on popup: "
set /p P_CODE=" [?] Enter 6-digit Wi-Fi Pairing Code: "
"%ADB%" pair %DEF_IP%:%P_PORT% %P_CODE%
echo.
echo [*] Pairing sent! Now enter the main 5-digit port under 'IP address ^& Port':
goto MANUAL_PORT

:RESTART_ADB
echo [*] Killing ADB daemon...
"%ADB%" kill-server
ping 127.0.0.1 -n 2 > nul
"%ADB%" start-server
echo [+] Server restarted.
goto START

:PERFORM_LOCK
echo.
echo  [*] Sending Lock Signal...
"%ADB%" shell settings put global dpclocker_enabled 1
if %ERRORLEVEL% EQU 0 (
    ping 127.0.0.1 -n 2 > nul
    "%ADB%" shell am force-stop com.afwsamples.testdpc
    echo.
    echo  ===========================================================================
    echo   [OK] TEST DPC IS NOW LOCKED! (dpclocker_enabled = 1)
    echo   Any launch from the phone will be blocked.
    echo  ===========================================================================
) else (
    echo  [!] Command failed. Re-opening recovery menu...
    goto RECOVERY_MENU
)
echo.
pause

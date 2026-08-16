@echo off
echo ========================================================
echo   Unlocking Test DPC (USB / Wireless ADB)
echo ========================================================

set ADB="C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe"

REM Check if any device is already connected (USB or Wireless)
%ADB% devices | findstr /R "device$" > NUL
if %ERRORLEVEL% NEQ 0 (
    echo No active ADB connection detected. Scanning for wireless device...
    for /f "tokens=3" %%A in ('%ADB% mdns services ^| findstr "_adb-tls-connect._tcp"') do (
        echo Connecting to wireless device at %%A...
        %ADB% connect %%A
    )
)

echo.
echo Sending Unlock Signal...
%ADB% shell settings put global dpclocker_enabled 0
ping 127.0.0.1 -n 2 > nul
%ADB% shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity

echo.
echo ========================================================
echo   Test DPC is now UNLOCKED and opened on your phone screen!
echo   (dpclocker_enabled = 0)
echo   You can make your policy changes.
echo ========================================================
echo.
pause

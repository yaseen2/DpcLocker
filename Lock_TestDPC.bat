@echo off
echo ========================================================
echo   Locking Test DPC (USB / Wireless ADB)
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
echo Sending Lock Signal...
%ADB% shell settings put global dpclocker_enabled 1
ping 127.0.0.1 -n 2 > nul
%ADB% shell am force-stop com.afwsamples.testdpc

echo.
echo ========================================================
echo   Test DPC is now LOCKED!
echo   (dpclocker_enabled = 1)
echo   Opening Test DPC on your phone will be blocked.
echo ========================================================
echo.
pause

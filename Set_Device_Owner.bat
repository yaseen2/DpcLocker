@echo off
setlocal
echo ========================================================
echo   Setting Test DPC as Device Owner
echo ========================================================

set "ADB=C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo 1. Removing any residual secondary work profiles...
"%ADB%" shell pm remove-user 10 >nul 2>&1

echo 2. Setting com.afwsamples.testdpc as Device Owner...
"%ADB%" shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver

if %errorlevel% equ 0 (
    echo.
    echo ========================================================
    echo   SUCCESS: Test DPC is now Device Owner!
    echo   You can now re-add your Google account on your phone.
    echo ========================================================
) else (
    echo.
    echo ========================================================
    echo   NOTE: If you got an error about accounts:
    echo   Please go to Phone Settings -> Passwords & Accounts
    echo   -> Remove your Google Account temporarily, then run
    echo   this script again!
    echo ========================================================
)

pause

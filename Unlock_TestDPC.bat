@echo off
echo ========================================================
echo   Unlocking Test DPC via USB ADB
echo ========================================================
"C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell settings put global dpclocker_enabled 0
timeout /t 1 /nobreak > NUL
"C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.afwsamples.testdpc/.PolicyManagementActivity
echo.
echo Test DPC is now UNLOCKED and opened on your phone screen!
echo You can make your policy changes.
echo.
pause

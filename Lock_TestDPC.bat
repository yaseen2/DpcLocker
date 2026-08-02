@echo off
echo ========================================================
echo   Locking Test DPC via USB ADB
echo ========================================================
"C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell settings put global dpclocker_enabled 1
"C:\Users\ThinkPad\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.afwsamples.testdpc
echo.
echo Test DPC is now LOCKED!
echo Opening Test DPC on your phone will instantly send you back to Home Screen.
echo.
pause

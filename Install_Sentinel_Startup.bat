@echo off
setlocal
title DPCLOCKER :: INSTALL SENTINEL AUTO-STARTUP

echo ===============================================================================
echo  [#] DPCLOCKER :: INSTALLING PROXY SENTINEL TO WINDOWS STARTUP
echo ===============================================================================
echo.

set "STARTUP_FOLDER=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "VBS_SCRIPT=%TEMP%\create_shortcut.vbs"

echo Set oWS = WScript.CreateObject("WScript.Shell") > "%VBS_SCRIPT%"
echo sLinkFile = "%STARTUP_FOLDER%\WindowsProxySentinel.lnk" >> "%VBS_SCRIPT%"
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> "%VBS_SCRIPT%"
echo oLink.TargetPath = "%~dp0Start_Proxy_Sentinel.bat" >> "%VBS_SCRIPT%"
echo oLink.WorkingDirectory = "%~dp0" >> "%VBS_SCRIPT%"
echo oLink.WindowStyle = 7 >> "%VBS_SCRIPT%"
echo oLink.IconLocation = "shell32.dll,48" >> "%VBS_SCRIPT%"
echo oLink.Description = "DpcLocker Windows Real-time Proxy Sentinel" >> "%VBS_SCRIPT%"
echo oLink.Save >> "%VBS_SCRIPT%"

cscript //nologo "%VBS_SCRIPT%"
del "%VBS_SCRIPT%" >nul 2>&1

echo  [+] Windows Proxy Sentinel has been added to Windows Startup!
echo  [+] It will now run silently every time your PC turns on.
echo.
pause

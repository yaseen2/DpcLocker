@echo off
setlocal
title DPCLOCKER :: INSTALL SENTINEL TO TASK SCHEDULER & STARTUP

echo ===============================================================================
echo  [#] DPCLOCKER :: INSTALLING PERMANENT SELF-HEALING SENTINEL
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

cscript //nologo "%VBS_SCRIPT%" >nul 2>&1
del "%VBS_SCRIPT%" >nul 2>&1

:: Register Windows Task Scheduler Task (Highest Privileges, Auto-restart on logon/startup)
schtasks /create /tn "DpcLockerSentinel" /tr "\"%~dp0Start_Proxy_Sentinel.bat\"" /sc onlogon /rl highest /f >nul 2>&1

echo  [+] Windows Proxy Sentinel has been registered in Windows Task Scheduler!
echo  [+] Auto-startup shortcut added to Startup folder.
echo  [+] Self-healing protection is permanently locked in place.
echo.
pause

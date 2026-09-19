@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: INSTALL PERMANENT MULTI-USER SENTINEL

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ===============================================================================
    echo  [*] ADMINISTRATOR PRIVILEGES REQUIRED
    echo ===============================================================================
    echo.
    echo  Requesting Windows UAC Elevation...
    echo.
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b 0
)

echo ===============================================================================
echo  [#] DPCLOCKER :: INSTALLING PERMANENT MULTI-USER SENTINEL & SYSTEM PROTECTION
echo ===============================================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "LAUNCH_VBS=%SCRIPT_DIR%launch_sentinel_hidden.vbs"
set "PYTHON_DIR=C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311"

:: 1. Clean up any obsolete tasks and duplicate shortcuts
echo  [1/5] Removing obsolete single-user scheduled tasks...
schtasks /delete /tn "DpcLockerSentinelLogon" /f >nul 2>&1
schtasks /delete /tn "DpcLockerSentinelBoot" /f >nul 2>&1
schtasks /delete /tn "DpcLockerSentinel" /f >nul 2>&1
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1
del "%ALLUSERSPROFILE%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1
echo       [+] Obsolete tasks removed.

:: 2. Ensure System Machine PATH includes Python 3.11 for all user accounts
echo  [2/5] Configuring System Machine PATH for all user accounts...
powershell -NoProfile -Command ^
  "$p = [Environment]::GetEnvironmentVariable('Path', 'Machine'); " ^
  "$py = '%PYTHON_DIR%'; " ^
  "$pys = '%PYTHON_DIR%\Scripts'; " ^
  "if ($p -notlike ('*' + $py + '*')) { " ^
  "  [Environment]::SetEnvironmentVariable('Path', $p + ';' + $py + ';' + $pys, 'Machine'); " ^
  "  Write-Host '      [+] Added Python to System Machine PATH.'; " ^
  "} else { Write-Host '      [+] System Machine PATH already contains Python.'; }"

:: 3. Register Python 3.11 in HKLM so Windows Python Launcher works for all users
echo  [3/5] Registering Python in HKLM Registry for Python Launcher...
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /ve /t REG_SZ /d "Python 3.11 (64-bit)" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "SysArchitecture" /t REG_SZ /d "64bit" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "SysVersion" /t REG_SZ /d "3.11" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "Version" /t REG_SZ /d "3.11.9" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /ve /t REG_SZ /d "%PYTHON_DIR%\" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /v "ExecutablePath" /t REG_SZ /d "%PYTHON_DIR%\python.exe" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /v "WindowedExecutablePath" /t REG_SZ /d "%PYTHON_DIR%\pythonw.exe" /f >nul 2>&1
echo       [+] PythonCore HKLM registered.

:: 4. Apply Adult URLBlocklist into HKLM for Edge and Chrome
echo  [4/5] Applying Master Adult URLBlocklist to Edge & Chrome...
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%Apply_Adult_URLBlocklist.ps1"

:: 5. Register HKLM Enterprise Run Key to launch silently via wscript (WindowStyle 0)
echo  [5/5] Registering Silent Enterprise Run Key in HKLM...
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "DpcLockerSentinel" /t REG_SZ /d "wscript.exe //nologo \"%LAUNCH_VBS%\"" /f >nul 2>&1
echo       [+] Added HKLM Silent Run Key (Runs automatically on ANY user login).

echo.
echo ===============================================================================
echo  [+] PERMANENT MULTI-USER PROTECTION INSTALLED!
echo ===============================================================================
echo  The Sentinel will start automatically and silently for all accounts on this PC.
echo.
pause

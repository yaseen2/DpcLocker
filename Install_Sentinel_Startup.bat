@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: INSTALL PERMANENT MULTI-USER SENTINEL + SYSTEM ENFORCER v2

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ===============================================================================
    echo  [*] ADMINISTRATOR PRIVILEGES REQUIRED
    echo ===============================================================================
    echo.
    echo  Requesting Windows UAC Elevation...
    echo.
    if /i "%~1"=="--nopause" (
        powershell -NoProfile -Command "Start-Process '%~f0' -ArgumentList '--nopause' -Verb RunAs"
    ) else (
        powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    )
    exit /b 0
)

echo ===============================================================================
echo  [#] DPCLOCKER :: DEPLOYING 2-TIER WINDOWS PROTECTION (SYSTEM + INTERACTIVE)
echo ===============================================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR_CLEAN=%SCRIPT_DIR:~0,-1%"
set "LAUNCH_VBS=%SCRIPT_DIR%launch_sentinel_hidden.vbs"
set "ENFORCER_PY=%SCRIPT_DIR%windows_system_enforcer.py"
set "PYTHON_DIR=C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311"
set "PYW_EXE=%PYTHON_DIR%\pythonw.exe"
if not exist "%PYW_EXE%" set "PYW_EXE=pythonw.exe"

:: 1. Clean up obsolete tasks and duplicate shortcuts
echo  [1/6] Removing obsolete legacy scheduled tasks...
schtasks /delete /tn "DpcLockerSentinelLogon" /f >nul 2>&1
schtasks /delete /tn "DpcLockerSentinelBoot" /f >nul 2>&1
schtasks /delete /tn "DpcLockerSentinel" /f >nul 2>&1
schtasks /delete /tn "DpcLockerSystemEnforcer" /f >nul 2>&1
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1
del "%ALLUSERSPROFILE%\Microsoft\Windows\Start Menu\Programs\Startup\WindowsProxySentinel.lnk" >nul 2>&1
echo       [+] Obsolete tasks removed.

:: 2. Ensure System Machine PATH includes Python 3.11 for all user accounts & SYSTEM
echo  [2/6] Configuring System Machine PATH for all user accounts...
powershell -NoProfile -Command ^
  "$p = [Environment]::GetEnvironmentVariable('Path', 'Machine'); " ^
  "$py = '%PYTHON_DIR%'; " ^
  "$pys = '%PYTHON_DIR%\Scripts'; " ^
  "if ($p -notlike ('*' + $py + '*')) { " ^
  "  [Environment]::SetEnvironmentVariable('Path', $p + ';' + $py + ';' + $pys, 'Machine'); " ^
  "  Write-Host '      [+] Added Python to System Machine PATH.'; " ^
  "} else { Write-Host '      [+] System Machine PATH already contains Python.'; }"

:: 3. Register Python 3.11 in HKLM so Python works for all accounts
echo  [3/6] Registering Python in HKLM Registry...
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /ve /t REG_SZ /d "Python 3.11 (64-bit)" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "SysArchitecture" /t REG_SZ /d "64bit" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "SysVersion" /t REG_SZ /d "3.11" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11" /v "Version" /t REG_SZ /d "3.11.9" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /ve /t REG_SZ /d "%PYTHON_DIR%\" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /v "ExecutablePath" /t REG_SZ /d "%PYTHON_DIR%\python.exe" /f >nul 2>&1
reg add "HKLM\SOFTWARE\Python\PythonCore\3.11\InstallPath" /v "WindowedExecutablePath" /t REG_SZ /d "%PYTHON_DIR%\pythonw.exe" /f >nul 2>&1
echo       [+] PythonCore HKLM registered.

:: 4. Apply Full Windows Lockdown v2 (CleanBrowsing Adult IPv4/IPv6 DNS, IFEO, Chrome/Edge DoH + ExtensionSettings, Hosts, Cleanup)
echo  [4/6] Applying Full Windows Browser, DNS ^& IFEO Lockdown v2...
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%enable_windows_protection.ps1"

:: 5. Register Elevated Session-0 SYSTEM Scheduled Task (DpcLockerSystemEnforcer)
echo  [5/6] Installing Elevated NT AUTHORITY\SYSTEM Enforcer Task...
call "%SCRIPT_DIR%Stop_Proxy_Sentinel.bat" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$q = [char]34; " ^
  "$action = New-ScheduledTaskAction -Execute '%PYW_EXE%' -Argument ($q + '%ENFORCER_PY%' + $q) -WorkingDirectory '%SCRIPT_DIR_CLEAN%'; " ^
  "$trigger = New-ScheduledTaskTrigger -AtStartup; " ^
  "$principal = New-ScheduledTaskPrincipal -UserId 'NT AUTHORITY\SYSTEM' -LogonType ServiceAccount -RunLevel Highest; " ^
  "$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Days 0) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1); " ^
  "Register-ScheduledTask -TaskName 'DpcLockerSystemEnforcer' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null; " ^
  "Start-ScheduledTask -TaskName 'DpcLockerSystemEnforcer'; " ^
  "Write-Host '      [+] DpcLockerSystemEnforcer installed and STARTED as NT AUTHORITY\SYSTEM!'"

:: 6. Register HKLM Silent Run Key for Session-1 Interactive Browser Tab Sentinel & Launch Now
echo  [6/6] Registering ^& Starting Session-1 Interactive Browser Sentinel Triad...
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v "DpcLockerSentinel" /t REG_SZ /d "wscript.exe //nologo \"%LAUNCH_VBS%\"" /f >nul 2>&1
wscript.exe //nologo "%LAUNCH_VBS%"
echo       [+] Session-1 Interactive Browser Sentinel Triad started cleanly!

echo.
echo ===============================================================================
echo  [+] PERMANENT 2-TIER WINDOWS PROTECTION DEPLOYED SUCCESSFULLY!
echo ===============================================================================
echo  - Chrome ^& Edge managed: CleanBrowsing Adult DoH + Google SafeSearch + No VPN Extensions
echo  - Study sites restored: cssprep.com.pk ^& cssprepforum.com unblocked
echo  - Brave / Opera / Firefox / Tor / Comet blocked at Kernel IFEO + SYSTEM PE Enforcer
echo  - Zero false positives on Antigravity IDE, VS Code, WPS Office ^& good apps
echo.
if /i not "%~1"=="--nopause" pause

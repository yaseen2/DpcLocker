@echo off
setlocal
title DPCLOCKER :: START DUAL PROXY SENTINEL

echo ===============================================================================
echo  [#] DPCLOCKER :: STARTING DUAL SELF-HEALING PROXY SENTINEL (BACKGROUND)
echo ===============================================================================
echo.

set "PYW=pythonw.exe"
if exist "C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe" (
    set "PYW=C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe"
)

:: Deploy VPN blocking policies, terminate VPNs, and neutralize virtual adapters
call "%~dp0Deploy_VPN_Blocker.bat"

:: Launch Twin Process A (Sentinel)
start "" "%PYW%" "%~dp0windows_proxy_sentinel.py"

:: Launch Twin Process B (Guardian Watchdog)
start "" "%PYW%" "%~dp0windows_sentinel_watchdog.py"

echo  [+] Twin Self-Healing Sentinel processes are now active in background!
echo  [+] If either process is terminated in Task Manager, it will auto-resurrect in <100ms.
echo.
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 3 >nul

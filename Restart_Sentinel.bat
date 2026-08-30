@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: RESTART SENTINEL

net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b 0
)

echo ===============================================================================
echo  [#] DPCLOCKER :: RESTARTING SENTINEL WITH NEW RULES
echo ===============================================================================
echo.

echo  [1/2] Terminating old background processes...
taskkill /F /IM pythonw.exe >nul 2>&1
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 2 >nul

echo  [2/2] Launching updated Sentinel & Watchdog with Remote Browser Protection...
set "PYW=pythonw.exe"
if exist "C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe" (
    set "PYW=C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe"
)

start "" "%PYW%" "%~dp0windows_proxy_sentinel.py"
start "" "%PYW%" "%~dp0windows_sentinel_watchdog.py"

echo.
echo  [+] SUCCESS: Updated Sentinel is now running with Remote Browser Protection!
echo  [+] Proxies and Remote Browsers will be terminated immediately.
echo.
pause

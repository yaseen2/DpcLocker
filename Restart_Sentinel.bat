@echo off
setlocal EnableDelayedExpansion
title DPCLOCKER :: RESTART SENTINEL TRIAD MESH

net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b 0
)

echo ===============================================================================
echo  [#] DPCLOCKER :: RESTARTING SENTINEL TRIAD MESH WITH NEW RULES
echo ===============================================================================
echo.

echo  [1/2] Terminating old background processes cleanly...
call "%~dp0Stop_Proxy_Sentinel.bat" >nul 2>&1
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 2 >nul

echo  [2/2] Launching Zero-Latency Sentinel Triad Mesh (Windowless)...
wscript.exe //nologo "%~dp0launch_sentinel_hidden.vbs"

echo.
echo  [+] SUCCESS: Updated Sentinel Triad Mesh is now running in background!
echo  [+] 3-Node Triangular Mesh (Sentinel + Alpha Watchdog + Beta Guard).
echo  [+] Proxies, Remote Browsers, and Web VPNs will be terminated immediately.
echo.
pause

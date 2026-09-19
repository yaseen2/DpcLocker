@echo off
setlocal
title DPCLOCKER :: START SENTINEL TRIAD MESH

echo ===============================================================================
echo  [#] DPCLOCKER :: STARTING ZERO-LATENCY SENTINEL TRIAD MESH (BACKGROUND)
echo ===============================================================================
echo.

set "PYW=pythonw.exe"
if exist "C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe" (
    set "PYW=C:\Users\ThinkPad\AppData\Local\Programs\Python\Python311\pythonw.exe"
) else if exist "C:\Program Files\Python311\pythonw.exe" (
    set "PYW=C:\Program Files\Python311\pythonw.exe"
) else if exist "C:\Windows\pyw.exe" (
    set "PYW=C:\Windows\pyw.exe"
)

:: Ensure any existing instance for this user is stopped cleanly before restarting
call "%~dp0Stop_Proxy_Sentinel.bat" >nul 2>&1

:: Apply major adult wildcard URLBlocklist to Edge and Chrome ONLY if elevated as Administrator
net session >nul 2>&1
if %errorlevel% equ 0 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply_Adult_URLBlocklist.ps1" >nul 2>&1
)

:: Launch 3-Node Self-Healing Triad Mesh (Completely hidden, zero taskbar presence)
start "" "%PYW%" "%~dp0windows_proxy_sentinel.py"

echo  [+] Zero-Latency Guardian Triad Mesh is now active in background!
echo  [+] 3-Node Triangular Mesh (Sentinel + Alpha Watchdog + Beta Guard).
echo  [+] 0ms Win32 Kernel Handle Synchronization (WaitForSingleObject).
echo  [+] Hardened with Win32 DACL: Immune to Task Manager rapid kills.
echo  [+] Completely invisible: Zero console windows and zero taskbar buttons.
echo.
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 3 >nul

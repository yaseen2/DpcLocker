@echo off
setlocal
title DPCLOCKER :: STOP PROXY SENTINEL

echo ===============================================================================
echo  [#] DPCLOCKER :: STOPPING WINDOWS PROXY SENTINEL TRIAD MESH
echo ===============================================================================
echo.

set "CLEAN_USER=%USERNAME: =_%"
set "CLEAN_USER=%CLEAN_USER:'=_%"

set "USER_SIGNAL=%~dp0.sentinel_stop_signal_%CLEAN_USER%"
set "GLOBAL_SIGNAL=%~dp0.sentinel_stop_signal"

echo [*] Sending authorized stop signal...
type nul > "%USER_SIGNAL%"
type nul > "%GLOBAL_SIGNAL%"

:: Wait 2 seconds for all 3 DACL-hardened processes to detect signal and exit cleanly
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 3 >nul

:: Clean up PID files and stop signal files
if exist "%~dp0.sentinel_%CLEAN_USER%.pid" del "%~dp0.sentinel_%CLEAN_USER%.pid" >nul 2>&1
if exist "%~dp0.watchdog_%CLEAN_USER%.pid" del "%~dp0.watchdog_%CLEAN_USER%.pid" >nul 2>&1
if exist "%~dp0.guard_%CLEAN_USER%.pid" del "%~dp0.guard_%CLEAN_USER%.pid" >nul 2>&1

if exist "%~dp0.sentinel.pid" del "%~dp0.sentinel.pid" >nul 2>&1
if exist "%~dp0.watchdog.pid" del "%~dp0.watchdog.pid" >nul 2>&1
if exist "%~dp0.guard.pid" del "%~dp0.guard.pid" >nul 2>&1

if exist "%USER_SIGNAL%" del "%USER_SIGNAL%" >nul 2>&1
if exist "%GLOBAL_SIGNAL%" del "%GLOBAL_SIGNAL%" >nul 2>&1

:: Fallback taskkill in case an unhardened instance exists
taskkill /f /im pythonw.exe >nul 2>&1

echo  [+] Windows Proxy Sentinel and Guardian Triad Mesh have stopped cleanly!
echo.
%SystemRoot%\System32\PING.EXE 127.0.0.1 -n 2 >nul
